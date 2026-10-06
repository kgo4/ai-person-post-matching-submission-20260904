package com.example.matching.vector;

import com.example.matching.ai.service.VectorEmbeddingService;
import com.example.matching.config.MilvusConfig;
import com.example.matching.config.ResilientMilvusClient;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.system.AbilityTagMapper;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.CollectionSchema;
import io.milvus.grpc.DataType;
import io.milvus.grpc.FieldData;
import io.milvus.grpc.FieldSchema;
import io.milvus.grpc.IDs;
import io.milvus.grpc.KeyValuePair;
import io.milvus.grpc.LongArray;
import io.milvus.grpc.ScalarField;
import io.milvus.grpc.StringArray;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.SearchResultData;
import io.milvus.grpc.SearchResults;
import io.milvus.param.R;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.DescribeCollectionParam;
import io.milvus.grpc.DescribeCollectionResponse;
import io.milvus.param.collection.DropCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.dml.UpsertParam;
import io.milvus.param.index.CreateIndexParam;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link MilvusVectorService} 单元测试。
 *
 * <p>重点覆盖：
 * <ul>
 *   <li>Milvus 客户端不可用时的<b>优雅降级</b>（写操作返回 false、读操作返回空集合，且不抛异常）；</li>
 *   <li>embedding 返回 null / 空 / 全零占位向量时的跳过逻辑；</li>
 *   <li>集合初始化中 schema 三态（COMPATIBLE / INCOMPATIBLE / UNAVAILABLE）
 *       与破坏性重建开关（milvus.allow-destructive-recreate）的联动；</li>
 *   <li>检索正常返回、返回 null、非瞬时错误立即降级；</li>
 *   <li>岗位相似度的 0-1 口径与夹紧。</li>
 * </ul>
 *
 * <p>所有 Milvus RPC 均通过 mock {@link MilvusServiceClient} 拦截，测试不联网。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MilvusVectorServiceTest {

    @Mock private ResilientMilvusClient resilientMilvusClient;
    @Mock private MilvusServiceClient milvusClient;
    @Mock private VectorEmbeddingService vectorEmbeddingService;
    @Mock private AbilityTagMapper abilityTagMapper;
    @Mock private MilvusConfig milvusConfig;

    @InjectMocks
    private MilvusVectorService service;

    /**
     * MyBatis-Plus 的 LambdaQueryWrapper 需要实体已登记表信息，
     * 否则会抛 "can not find lambda cache for this entity"（纯单测无 Spring 上下文）。
     */
    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(cfg, ""), AbilityTag.class);
    }

    // ==================== 辅助 ====================

    private List<Float> validVector() {
        List<Float> v = new ArrayList<>(Collections.nCopies(1536, 0f));
        v.set(0, 0.25f);
        return v;
    }

    private List<Float> zeroVector() {
        return new ArrayList<>(Collections.nCopies(1536, 0f));
    }

    /** 让 Milvus 客户端处于「可用」状态 */
    private void available() {
        when(resilientMilvusClient.getClient()).thenReturn(milvusClient);
    }

    /** 让 Milvus 客户端处于「不可用」状态（getClient 返回 null） */
    private void unavailable() {
        when(resilientMilvusClient.getClient()).thenReturn(null);
    }

    /**
     * 构造一条 Milvus 检索响应。
     * <p>SearchResultsWrapper 依赖 numQueries 决定返回几条 query 的命中（否则抛 "This record is empty"），
     * 并且需要在 fieldsData 中回传 ref_id 字段，否则 IDScore.get("ref_id") 同样抛异常。
     */
    private R<SearchResults> searchResultsWith(long refId, float distance) {
        IDs ids = IDs.newBuilder()
                .setIntId(LongArray.newBuilder().addData(refId).build())
                .build();
        FieldData refIdField = FieldData.newBuilder()
                .setFieldName("ref_id")
                .setType(DataType.VarChar)
                .setScalars(ScalarField.newBuilder()
                        .setStringData(StringArray.newBuilder().addData(String.valueOf(refId)).build())
                        .build())
                .build();
        SearchResultData data = SearchResultData.newBuilder()
                .setTopK(1)
                .setNumQueries(1)
                .setIds(ids)
                .addScores(distance)
                .addFieldsData(refIdField)
                .addOutputFields("ref_id")
                .build();
        R<SearchResults> r = R.success();
        r.setData(SearchResults.newBuilder().setResults(data).build());
        return r;
    }

    private R<DescribeCollectionResponse> describeResult(int dimension, DataType type) {
        DescribeCollectionResponse schema = DescribeCollectionResponse.newBuilder()
                .setSchema(CollectionSchema.newBuilder()
                        .addFields(FieldSchema.newBuilder()
                                .setName("vector")
                                .setDataType(type)
                                .addTypeParams(KeyValuePair.newBuilder()
                                        .setKey("dim").setValue(String.valueOf(dimension)).build())
                                .build())
                        .build())
                .build();
        R<DescribeCollectionResponse> r = R.success();
        r.setData(schema);
        return r;
    }

    private EmpAbility ability(String name, Long tagId, Integer mastery) {
        EmpAbility a = new EmpAbility();
        a.setAbilityName(name);
        a.setTagId(tagId);
        a.setMasteryLevel(mastery);
        return a;
    }

    private PostAbilityModel requirement(String name, Long tagId, Integer minLevel) {
        PostAbilityModel m = new PostAbilityModel();
        m.setAbilityName(name);
        m.setTagId(tagId);
        m.setMinRequiredLevel(minLevel);
        return m;
    }

    private AbilityTag tag(Long id, String name) {
        AbilityTag t = new AbilityTag();
        t.setId(id);
        t.setTagName(name);
        return t;
    }

    private void verifyCreateCollection() {
        verify(milvusClient).createCollection(any(CreateCollectionParam.class));
        verify(milvusClient).createIndex(any(CreateIndexParam.class));
        verify(milvusClient).loadCollection(any(LoadCollectionParam.class));
    }

    // ==================== 原有覆盖：标签名批量解析 ====================

    @Test
    @DisplayName("resolveTagNames：去重后一次批量查询，命中名称并兜底未命中标签")
    void resolvesAllDistinctAbilityTagsWithOneBatchQuery() {
        AbilityTagMapper mapper = org.mockito.Mockito.mock(AbilityTagMapper.class);
        AbilityTag java = new AbilityTag();
        java.setId(1L);
        java.setTagName("Java");
        AbilityTag spring = new AbilityTag();
        spring.setId(2L);
        spring.setTagName("Spring");
        when(mapper.selectBatchIds(List.of(1L, 2L))).thenReturn(List.of(java, spring));

        MilvusVectorService target = new MilvusVectorService();
        ReflectionTestUtils.setField(target, "abilityTagMapper", mapper);
        @SuppressWarnings("unchecked")
        Map<Long, String> names = (Map<Long, String>) ReflectionTestUtils.invokeMethod(
                target, "resolveTagNames", List.of(1L, 2L, 1L));

        assertThat(names).containsEntry(1L, "Java").containsEntry(2L, "Spring");
        verify(mapper).selectBatchIds(List.of(1L, 2L));
    }

    @Test
    @DisplayName("resolveTagNames：全部为空 → 返回空 Map，不查库")
    void resolveTagNames_allNull() {
        @SuppressWarnings("unchecked")
        Map<Long, String> names = (Map<Long, String>) ReflectionTestUtils.invokeMethod(
                service, "resolveTagNames", Collections.singletonList((Long) null));

        assertTrue(names.isEmpty());
        verifyNoInteractions(abilityTagMapper);
    }

    @Test
    @DisplayName("resolveTagNames：库里查不到的 tagId 兜底为 ability{id}")
    void resolveTagNames_missingFallback() {
        when(abilityTagMapper.selectBatchIds(any())).thenReturn(List.of());

        @SuppressWarnings("unchecked")
        Map<Long, String> names = (Map<Long, String>) ReflectionTestUtils.invokeMethod(
                service, "resolveTagNames", List.of(7L));

        assertEquals("ability7", names.get(7L));
    }

    // ==================== 可用性判定 ====================

    @Test
    @DisplayName("isVectorSearchAvailable：客户端就绪返回 true，客户端为 null 返回 false")
    void isVectorSearchAvailable_bothStates() {
        available();
        assertTrue(service.isVectorSearchAvailable());

        unavailable();
        assertFalse(service.isVectorSearchAvailable());
    }

    @Test
    @DisplayName("ResilientMilvusClient 未注入（required=false）时：所有操作优雅降级且不抛异常")
    void nullResilientClient_degradesGracefully() {
        ReflectionTestUtils.setField(service, "resilientMilvusClient", null);

        assertFalse(service.isVectorSearchAvailable());
        assertFalse(service.insertEmployeeVector(1L, null, List.of()));
        assertFalse(service.insertPostVector(1L, null, List.of()));
        assertFalse(service.upsertTagCandidateVector(1L, "t", validVector()));
        assertFalse(service.deleteVector(1L, MilvusVectorService.TYPE_EMPLOYEE));
        assertTrue(service.searchEmployeesForPost("x", 5).isEmpty());
        assertTrue(service.searchPostsForEmployee("x", 5).isEmpty());
        assertTrue(service.searchTagCandidateVectors(validVector(), 5).isEmpty());
        assertTrue(service.searchSimilarPostsByAbilities(List.of("Java"), 5).isEmpty());
        assertDoesNotThrow(service::initCollections);
    }

    // ==================== 集合初始化 ====================

    @Test
    @DisplayName("initCollections：客户端不可用 → 跳过初始化，不抛异常")
    void initCollections_clientUnavailable() {
        unavailable();
        assertDoesNotThrow(service::initCollections);
        verify(milvusClient, never()).createCollection(any(CreateCollectionParam.class));
    }

    @Test
    @DisplayName("initCollections：集合不存在 → 创建集合/索引并 loadCollection")
    void initCollections_createsCollection() {
        available();
        R<Boolean> has = R.success();
        has.setData(false);
        when(milvusClient.hasCollection(any())).thenReturn(has);

        service.initCollections();

        verifyCreateCollection();
    }

    @Test
    @DisplayName("initCollections：集合已存在且 schema 兼容 → 不重建、不删除")
    void initCollections_existingCompatible() {
        available();
        R<Boolean> has = R.success();
        has.setData(true);
        when(milvusClient.hasCollection(any())).thenReturn(has);
        when(milvusClient.describeCollection(any(DescribeCollectionParam.class)))
                .thenReturn(describeResult(1536, DataType.FloatVector));

        service.initCollections();

        verify(milvusClient, never()).createCollection(any(CreateCollectionParam.class));
        verify(milvusClient, never()).dropCollection(any(DropCollectionParam.class));
    }

    @Test
    @DisplayName("initCollections：schema 不兼容 + 开关关闭 → 绝不执行破坏性重建")
    void initCollections_incompatible_recreateDisabled() {
        available();
        ReflectionTestUtils.setField(service, "allowDestructiveRecreate", false);
        R<Boolean> has = R.success();
        has.setData(true);
        when(milvusClient.hasCollection(any())).thenReturn(has);
        // 维度 768 ≠ 期望 1536 → INCOMPATIBLE
        when(milvusClient.describeCollection(any(DescribeCollectionParam.class)))
                .thenReturn(describeResult(768, DataType.FloatVector));

        service.initCollections();

        verify(milvusClient, never()).dropCollection(any(DropCollectionParam.class));
        verify(milvusClient, never()).createCollection(any(CreateCollectionParam.class));
    }

    @Test
    @DisplayName("initCollections：schema 不兼容 + 开关打开 → 丢弃并重建集合")
    void initCollections_incompatible_recreateEnabled() {
        available();
        ReflectionTestUtils.setField(service, "allowDestructiveRecreate", true);
        R<Boolean> has = R.success();
        has.setData(true);
        R<Boolean> notHas = R.success();
        notHas.setData(false);
        // 第一次 hasCollection=true 进入兼容性检查；重建内部再次查询返回 false
        when(milvusClient.hasCollection(any())).thenReturn(has, notHas);
        when(milvusClient.describeCollection(any(DescribeCollectionParam.class)))
                .thenReturn(describeResult(768, DataType.FloatVector));

        service.initCollections();

        verify(milvusClient).dropCollection(any(DropCollectionParam.class));
        verifyCreateCollection();
    }

    @Test
    @DisplayName("initCollections：describe 抛异常 → 判定 UNAVAILABLE，绝不删除集合")
    void initCollections_describeThrows_unavailable() {
        available();
        ReflectionTestUtils.setField(service, "allowDestructiveRecreate", true);
        R<Boolean> has = R.success();
        has.setData(true);
        when(milvusClient.hasCollection(any())).thenReturn(has);
        when(milvusClient.describeCollection(any(DescribeCollectionParam.class)))
                .thenThrow(new RuntimeException("UNAVAILABLE: connection reset"));

        service.initCollections();

        verify(milvusClient, never()).dropCollection(any(DropCollectionParam.class));
    }

    @Test
    @DisplayName("initCollections：describe 返回 data=null → UNAVAILABLE，不重建")
    void initCollections_describeNullData() {
        available();
        ReflectionTestUtils.setField(service, "allowDestructiveRecreate", true);
        R<Boolean> has = R.success();
        has.setData(true);
        when(milvusClient.hasCollection(any())).thenReturn(has);
        when(milvusClient.describeCollection(any(DescribeCollectionParam.class))).thenReturn(R.success());

        service.initCollections();

        verify(milvusClient, never()).dropCollection(any(DropCollectionParam.class));
    }

    @Test
    @DisplayName("initCollections：初始化过程抛异常被兜底捕获，降级运行")
    void initCollections_swallowsException() {
        available();
        when(milvusClient.hasCollection(any())).thenThrow(new RuntimeException("boom"));

        assertDoesNotThrow(service::initCollections);
    }

    @Test
    @DisplayName("MilvusConfig 未注入或配置非法 → 回退默认集合名与 1536 维")
    void configFallback_defaults() {
        available();
        ReflectionTestUtils.setField(service, "milvusConfig", null);
        R<Boolean> has = R.success();
        has.setData(false);
        when(milvusClient.hasCollection(any())).thenReturn(has);

        assertDoesNotThrow(service::initCollections);
        verifyCreateCollection();

        // 配置存在但值为空/非正 → 仍回退默认值
        when(milvusConfig.getProfileCollectionName()).thenReturn(null);
        when(milvusConfig.getDimension()).thenReturn(0);
        ReflectionTestUtils.setField(service, "milvusConfig", milvusConfig);
        assertDoesNotThrow(service::initCollections);
    }

    @Test
    @DisplayName("MilvusConfig 配置了集合名与维度 → 初始化使用配置值")
    void configFallback_explicitValues() {
        available();
        when(milvusConfig.getProfileCollectionName()).thenReturn("custom_vector");
        when(milvusConfig.getDimension()).thenReturn(1024);
        R<Boolean> has = R.success();
        has.setData(true);
        when(milvusClient.hasCollection(any())).thenReturn(has);
        when(milvusClient.describeCollection(any(DescribeCollectionParam.class)))
                .thenReturn(describeResult(1024, DataType.FloatVector));

        service.initCollections();

        verify(milvusClient, never()).createCollection(any(CreateCollectionParam.class));
    }

    // ==================== 员工向量写入 ====================

    @Test
    @DisplayName("insertEmployeeVector：Milvus 不可用 → 返回 false，不调用 embedding")
    void insertEmployeeVector_milvusUnavailable() {
        unavailable();
        assertFalse(service.insertEmployeeVector(1L, null, List.of(ability("Java", 5L, 3))));
        verifyNoInteractions(vectorEmbeddingService);
    }

    @Test
    @DisplayName("insertEmployeeVector：正常路径 → 解析标签名、embed、upsert 成功后清理旧记录")
    void insertEmployeeVector_success() {
        available();
        when(abilityTagMapper.selectBatchIds(any())).thenReturn(List.of(tag(5L, "Java")));
        when(vectorEmbeddingService.embed(any())).thenReturn(validVector());
        R<MutationResult> ok = R.success();
        ok.setData(MutationResult.newBuilder().build());
        when(milvusClient.upsert(any(UpsertParam.class))).thenReturn(ok);

        boolean result = service.insertEmployeeVector(1L, null,
                List.of(ability("Java", 5L, 3), ability("", 5L, 2)));

        assertTrue(result);
        // upsert 成功后用排除自身主键的表达式清理历史随机 ID 记录
        verify(milvusClient).delete(any(DeleteParam.class));
    }

    @Test
    @DisplayName("insertEmployeeVector：abilities 为 null → 构建空文本仍走 upsert")
    void insertEmployeeVector_nullAbilities() {
        available();
        when(vectorEmbeddingService.embed(any())).thenReturn(validVector());
        R<MutationResult> ok = R.success();
        ok.setData(MutationResult.newBuilder().build());
        when(milvusClient.upsert(any(UpsertParam.class))).thenReturn(ok);

        assertTrue(service.insertEmployeeVector(1L, null, null));
    }

    @Test
    @DisplayName("insertEmployeeVector：embedding 返回全零占位向量 → 跳过插入返回 false")
    void insertEmployeeVector_invalidVector() {
        available();
        when(vectorEmbeddingService.embed(any())).thenReturn(zeroVector());

        assertFalse(service.insertEmployeeVector(1L, null, List.of(ability("Java", 5L, 3))));
        verify(milvusClient, never()).upsert(any(UpsertParam.class));
    }

    @Test
    @DisplayName("insertEmployeeVector：upsert 抛异常 → 捕获并返回 false")
    void insertEmployeeVector_upsertThrows() {
        available();
        when(vectorEmbeddingService.embed(any())).thenReturn(validVector());
        when(milvusClient.upsert(any(UpsertParam.class))).thenThrow(new RuntimeException("rpc down"));

        assertFalse(service.insertEmployeeVector(1L, null, List.of(ability("Java", 5L, 3))));
    }

    @Test
    @DisplayName("insertEmployeeVector：同 refId+type 两次写入使用同一稳定主键")
    void insertEmployeeVector_stablePrimaryKey() {
        available();
        when(vectorEmbeddingService.embed(any())).thenReturn(validVector());
        R<MutationResult> ok = R.success();
        ok.setData(MutationResult.newBuilder().build());
        when(milvusClient.upsert(any(UpsertParam.class))).thenReturn(ok);

        service.insertEmployeeVector(100L, null, List.of(ability("Java", 5L, 3)));
        service.insertEmployeeVector(100L, null, List.of(ability("Java", 5L, 3)));

        ArgumentCaptor<UpsertParam> cap = ArgumentCaptor.forClass(UpsertParam.class);
        verify(milvusClient, times(2)).upsert(cap.capture());
        assertThat(cap.getAllValues()).hasSize(2);
    }

    // ==================== 岗位向量写入 ====================

    @Test
    @DisplayName("insertPostVector：正常路径 → upsert 成功返回 true")
    void insertPostVector_success() {
        available();
        when(abilityTagMapper.selectBatchIds(any())).thenReturn(List.of(tag(6L, "MySQL")));
        when(vectorEmbeddingService.embed(any())).thenReturn(validVector());
        R<MutationResult> ok = R.success();
        ok.setData(MutationResult.newBuilder().build());
        when(milvusClient.upsert(any(UpsertParam.class))).thenReturn(ok);

        assertTrue(service.insertPostVector(2L, null,
                List.of(requirement("MySQL", 6L, 4), requirement(null, 6L, 3))));
    }

    @Test
    @DisplayName("insertPostVector：Milvus 不可用 → 返回 false")
    void insertPostVector_unavailable() {
        unavailable();
        assertFalse(service.insertPostVector(2L, null, List.of(requirement("MySQL", 6L, 4))));
    }

    @Test
    @DisplayName("insertPostVector：embedding 返回 null、requirements 为 null → 跳过插入")
    void insertPostVector_nullVector() {
        available();
        when(vectorEmbeddingService.embed(any())).thenReturn(null);
        assertFalse(service.insertPostVector(2L, null, null));
    }

    // ==================== 候选标签向量 ====================

    @Test
    @DisplayName("upsertTagCandidateVector：向量无效或客户端不可用 → false；有效则 upsert")
    void upsertTagCandidateVector_branches() {
        available();
        assertFalse(service.upsertTagCandidateVector(9L, "text", zeroVector()));
        assertFalse(service.upsertTagCandidateVector(9L, "text", null));

        R<MutationResult> ok = R.success();
        ok.setData(MutationResult.newBuilder().build());
        when(milvusClient.upsert(any(UpsertParam.class))).thenReturn(ok);
        assertTrue(service.upsertTagCandidateVector(9L, "text", validVector()));

        unavailable();
        assertFalse(service.upsertTagCandidateVector(9L, "text", validVector()));
    }

    @Test
    @DisplayName("searchTagCandidateVectors：无效查询向量返回空集合；有效则检索候选簇且 score 为百分制")
    void searchTagCandidateVectors_branches() {
        available();
        assertTrue(service.searchTagCandidateVectors(null, 5).isEmpty());

        when(milvusClient.search(any(SearchParam.class))).thenReturn(searchResultsWith(31L, 0.3f));
        List<Map<String, Object>> hits = service.searchTagCandidateVectors(validVector(), 5);
        assertEquals(1, hits.size());
        assertEquals(31L, hits.get(0).get("refId"));
        assertEquals(70.0d, (Double) hits.get(0).get("score"), 0.001d);
    }

    // ==================== 检索 ====================

    @Test
    @DisplayName("searchEmployeesForPost：查询向量无效 → 短路返回空集合，不发起检索")
    void searchEmployeesForPost_invalidVector() {
        when(vectorEmbeddingService.embed(any())).thenReturn(zeroVector());
        assertTrue(service.searchEmployeesForPost("岗位文本", 10).isEmpty());
        verify(milvusClient, never()).search(any(SearchParam.class));
    }

    @Test
    @DisplayName("searchEmployeesForPost：正常命中 → refId 取 ref_id 字段，score 为百分制")
    void searchEmployeesForPost_success() {
        available();
        when(vectorEmbeddingService.embed(any())).thenReturn(validVector());
        when(milvusClient.search(any(SearchParam.class))).thenReturn(searchResultsWith(77L, 0.2f));

        List<Map<String, Object>> hits = service.searchEmployeesForPost("岗位文本", 10);

        assertEquals(1, hits.size());
        assertEquals(77L, hits.get(0).get("refId"));
        assertEquals(80.0d, (Double) hits.get(0).get("score"), 0.001d);
    }

    @Test
    @DisplayName("searchEmployeesForPost：检索返回 data=null → 降级为空集合")
    void searchEmployeesForPost_nullData() {
        available();
        when(vectorEmbeddingService.embed(any())).thenReturn(validVector());
        when(milvusClient.search(any(SearchParam.class))).thenReturn(R.success());

        assertTrue(service.searchEmployeesForPost("岗位文本", 10).isEmpty());
    }

    @Test
    @DisplayName("searchEmployeesForPost：检索抛非瞬时异常 → 立即降级为空集合，不重试")
    void searchEmployeesForPost_nonTransientFailure() {
        available();
        when(vectorEmbeddingService.embed(any())).thenReturn(validVector());
        when(milvusClient.search(any(SearchParam.class))).thenThrow(new RuntimeException("illegal param"));

        assertTrue(service.searchEmployeesForPost("岗位文本", 10).isEmpty());
        verify(milvusClient, times(1)).search(any(SearchParam.class));
    }

    @Test
    @DisplayName("searchPostsForEmployee：Milvus 不可用 → 返回空集合")
    void searchPostsForEmployee_clientUnavailable() {
        available();
        when(vectorEmbeddingService.embed(any())).thenReturn(validVector());
        unavailable();

        assertTrue(service.searchPostsForEmployee("员工文本", 10).isEmpty());
    }

    @Test
    @DisplayName("searchPostsForEmployee：正常命中返回 refId 与百分制 score")
    void searchPostsForEmployee_success() {
        available();
        when(vectorEmbeddingService.embed(any())).thenReturn(validVector());
        when(milvusClient.search(any(SearchParam.class))).thenReturn(searchResultsWith(88L, 0.1f));

        List<Map<String, Object>> hits = service.searchPostsForEmployee("员工文本", 10);

        assertEquals(88L, hits.get(0).get("refId"));
        assertEquals(90.0d, (Double) hits.get(0).get("score"), 0.001d);
    }

    @Test
    @DisplayName("searchPostsForEmployee：查询向量无效 → 返回空集合，不发起检索")
    void searchPostsForEmployee_invalidVector() {
        when(vectorEmbeddingService.embed(any())).thenReturn(null);
        assertTrue(service.searchPostsForEmployee("员工文本", 10).isEmpty());
        verify(milvusClient, never()).search(any(SearchParam.class));
    }

    // ==================== 岗位相似度（0-1 口径） ====================

    @Test
    @DisplayName("searchSimilarPostsByAbilities：入参为空/topK<=0/能力名全空 → 返回空集合")
    void searchSimilarPostsByAbilities_invalidArgs() {
        assertTrue(service.searchSimilarPostsByAbilities(null, 5).isEmpty());
        assertTrue(service.searchSimilarPostsByAbilities(List.of(), 5).isEmpty());
        assertTrue(service.searchSimilarPostsByAbilities(List.of("Java"), 0).isEmpty());
        assertTrue(service.searchSimilarPostsByAbilities(Arrays.asList("  ", null), 5).isEmpty());
    }

    @Test
    @DisplayName("searchSimilarPostsByAbilities：正常返回 0-1 相似度且被夹紧到 [0,1]")
    void searchSimilarPostsByAbilities_success() {
        available();
        when(vectorEmbeddingService.embed(any())).thenReturn(validVector());
        when(milvusClient.search(any(SearchParam.class))).thenReturn(searchResultsWith(12L, -0.5f));

        List<MilvusVectorService.VectorMatch> matches =
                service.searchSimilarPostsByAbilities(List.of("Java", "MySQL"), 5);

        assertEquals(1, matches.size());
        assertEquals(12L, matches.get(0).refId());
        // 距离 -0.5 → 相似度 1.5，夹紧到 1.0
        assertEquals(1.0d, matches.get(0).similarity(), 0.0001d);
    }

    @Test
    @DisplayName("searchSimilarPostsByAbilities：embedding 无效 → 返回空集合")
    void searchSimilarPostsByAbilities_invalidVector() {
        when(vectorEmbeddingService.embed(any())).thenReturn(zeroVector());
        assertTrue(service.searchSimilarPostsByAbilities(List.of("Java"), 5).isEmpty());
    }

    // ==================== 删除 ====================

    @Test
    @DisplayName("deleteVector：客户端可用返回 true；RPC 抛异常或客户端不可用返回 false")
    void deleteVector_branches() {
        available();
        R<MutationResult> ok = R.success();
        ok.setData(MutationResult.newBuilder().build());
        when(milvusClient.delete(any(DeleteParam.class))).thenReturn(ok);
        assertTrue(service.deleteVector(1L, MilvusVectorService.TYPE_EMPLOYEE));

        when(milvusClient.delete(any(DeleteParam.class))).thenThrow(new RuntimeException("delete failed"));
        assertFalse(service.deleteVector(1L, MilvusVectorService.TYPE_EMPLOYEE));

        unavailable();
        assertFalse(service.deleteVector(1L, MilvusVectorService.TYPE_POST));
    }
}
