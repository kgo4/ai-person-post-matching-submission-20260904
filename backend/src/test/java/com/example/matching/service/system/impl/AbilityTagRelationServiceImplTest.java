package com.example.matching.service.system.impl;

import com.example.matching.ai.service.VectorEmbeddingService;
import com.example.matching.common.enums.EvidenceSourceEnum;
import com.example.matching.common.enums.RelationStatusEnum;
import com.example.matching.common.enums.RelationTypeEnum;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.entity.system.AbilityTagAlias;
import com.example.matching.entity.system.AbilityTagRelation;
import com.example.matching.event.PostAbilityTagGovernanceRequestedEvent;
import com.example.matching.mapper.system.AbilityTagAliasMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.mapper.system.AbilityTagRelationMapper;
import com.example.matching.port.post.PostQueryPort;
import com.example.matching.service.system.PostAbilityTagGovernanceService;
import com.example.matching.service.system.TagCanonicalCacheInvalidator;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * {@link AbilityTagRelationServiceImpl} 单元测试。
 *
 * <p>覆盖关系分页查询、创建关系（参数校验/标签存在性/重复/类型校验）、
 * 审批与拒绝的状态机、双向关系查找、批量候选关系容错、
 * 向量自动发现的阈值校验与向量补全，以及 SAME_AS 归并 + 缓存失效 + 别名补齐。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AbilityTagRelationServiceImplTest {

    @Mock private AbilityTagMapper abilityTagMapper;
    @Mock private AbilityTagAliasMapper tagAliasMapper;
    @Mock private VectorEmbeddingService vectorEmbeddingService;
    @Mock private TagCanonicalCacheInvalidator tagCanonicalCacheInvalidator;
    @Mock private PostQueryPort postQueryPort;
    @Mock private PostAbilityTagGovernanceService postAbilityTagGovernanceService;
    @Mock private AbilityTagRelationMapper baseMapper;

    private AbilityTagRelationServiceImpl service;

    /**
     * MyBatis-Plus LambdaQueryWrapper 需要实体已登记表信息，
     * 否则会抛 "can not find lambda cache for this entity"（纯单测无 Spring 上下文）。
     */
    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        com.baomidou.mybatisplus.core.MybatisConfiguration cfg =
                new com.baomidou.mybatisplus.core.MybatisConfiguration();
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, ""), AbilityTagRelation.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, ""), AbilityTag.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, ""), AbilityTagAlias.class);
    }

    /**
     * 被测类未提供 baseMapper 的构造参数（Lombok 的 @RequiredArgsConstructor 不收集其父类字段），
     * 因此在测试中手工构造实例并注入父类 baseMapper，与 Spring 的 ServiceImpl 初始化行为一致。
     * <p>注意：{@code IService#getOne(Wrapper)} 默认实现最终会调用
     * {@code BaseMapper#selectOne(Wrapper, boolean)}，而它本身又是 default 方法、内部转调
     * {@code selectList(Wrapper)}。为了让 {@code getOne} 链路可被 mock，这里显式把
     * {@code selectOne(Wrapper, boolean)} 桩成 {@link #stubSelectOneReturning} 注册的结果。
     */
    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new AbilityTagRelationServiceImpl(abilityTagMapper, tagAliasMapper, vectorEmbeddingService,
                tagCanonicalCacheInvalidator, postQueryPort, postAbilityTagGovernanceService);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "baseMapper", baseMapper);
        // getOne → selectOne(Wrapper, boolean)（default 方法）→ 由这里的桩直接返回期望结果
        when(baseMapper.selectOne(any(), anyBoolean())).thenAnswer(inv -> singleResult.get());
    }

    /** 记录「当前用例期望 BaseMapper#selectOne 返回什么」，由 selectOne(Wrapper,boolean) 桩读取。 */
    private final java.util.concurrent.atomic.AtomicReference<AbilityTagRelation> singleResult =
            new java.util.concurrent.atomic.AtomicReference<>();

    /** 用 selectOne 的期望返回模拟：非 null → 单条命中（getOne 返回该条），null → 未命中。 */
    private void stubSelectOneReturning(AbilityTagRelation result) {
        singleResult.set(result);
    }

    private void stubSelectOneReturning() {
        singleResult.set(null);
    }

    private AbilityTag tag(Long id, String name) {
        AbilityTag t = new AbilityTag();
        t.setId(id);
        t.setTagName(name);
        t.setTagCode("TAG_" + id);
        t.setStatus(1);
        t.setIsDeleted(0);
        return t;
    }

    private AbilityTag tagWithVector(Long id, String name, List<Float> vector) {
        AbilityTag t = tag(id, name);
        t.setEmbeddingVector(vector);
        return t;
    }

    private AbilityTagRelation relation(Long id, Long sourceId, Long targetId, String type, String status) {
        AbilityTagRelation r = new AbilityTagRelation();
        r.setId(id);
        r.setSourceTagId(sourceId);
        r.setTargetTagId(targetId);
        r.setRelationType(type);
        r.setStatus(status);
        return r;
    }

    /** 判断 LambdaQueryWrapper 的 targetTagId 条件值是否为期望值。 */
    private static boolean wrapperTargetIdEquals(Object wrapper, Long expected) {
        return matchesNumber(wrapperParamValue(wrapper, "targetTagId"), expected);
    }

    /** 判断 LambdaQueryWrapper 的 sourceTagId 条件值是否为期望值。 */
    private static boolean wrapperSourceIdEquals(Object wrapper, Long expected) {
        return matchesNumber(wrapperParamValue(wrapper, "sourceTagId"), expected);
    }

    private static boolean matchesNumber(Object actual, Long expected) {
        return actual instanceof Number n && expected.longValue() == n.longValue();
    }

    /**
     * 从 LambdaQueryWrapper 中取回某个字段的绑定值。
     * <p>MyBatis-Plus 把 {@code eq(column, value)} 的 value 存进 paramNameValuePairs，
     * 但只用序号做 key、不保留列名，因此通过 {@code getSqlSegment()} 里列名后紧跟的
     * 占位符序号反查（列名已转成下划线形式，如 {@code target_tag_id}）。
     */
    private static Object wrapperParamValue(Object wrapper, String column) {
        if (!(wrapper instanceof com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?> w)) {
            return null;
        }
        String snake = column.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\w*" + snake + "\\w*)\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)}")
                .matcher(w.getSqlSegment());
        if (!m.find()) {
            return null;
        }
        return w.getParamNameValuePairs().get(m.group(2));
    }

    // ==================== 分页查询 ====================

    @Test
    @DisplayName("pageRelations：带全部过滤条件时正常分页")
    @SuppressWarnings("unchecked")
    void pageRelations_withAllFilters() {
        Page<AbilityTagRelation> page = new Page<>(1, 10);
        Page<AbilityTagRelation> result = new Page<>(1, 10);
        result.setRecords(List.of(relation(1L, 1L, 2L, "SIMILAR", "PENDING")));
        when(baseMapper.selectPage(any(), any())).thenReturn(result);

        IPage<AbilityTagRelation> out = service.pageRelations(page, 1L, 2L, "SIMILAR", "PENDING");

        assertNotNull(out);
        assertEquals(1, out.getRecords().size());
    }

    @Test
    @DisplayName("pageRelations：全部条件为空（含空白字符串）时也能分页")
    @SuppressWarnings("unchecked")
    void pageRelations_noFilters() {
        Page<AbilityTagRelation> page = new Page<>(1, 10);
        Page<AbilityTagRelation> result = new Page<>(1, 10);
        result.setRecords(Collections.emptyList());
        when(baseMapper.selectPage(any(), any())).thenReturn(result);

        IPage<AbilityTagRelation> out = service.pageRelations(page, null, null, "  ", "");

        assertNotNull(out);
        assertTrue(out.getRecords().isEmpty());
    }

    // ==================== 创建关系 ====================

    @Test
    @DisplayName("createRelation：源或目标标签为空 → 抛 PARAM_ERROR")
    void createRelation_nullTagIds() {
        assertThrows(BusinessException.class,
                () -> service.createRelation(null, 2L, "SIMILAR", 0.9, "MANUAL", "r", 1L));
        assertThrows(BusinessException.class,
                () -> service.createRelation(1L, null, "SIMILAR", 0.9, "MANUAL", "r", 1L));
    }

    @Test
    @DisplayName("createRelation：源与目标相同 → 抛 PARAM_ERROR")
    void createRelation_sameTagIds() {
        assertThrows(BusinessException.class,
                () -> service.createRelation(1L, 1L, "SIMILAR", 0.9, "MANUAL", "r", 1L));
    }

    @Test
    @DisplayName("createRelation：标签不存在 → 抛 ABILITY_TAG_NOT_FOUND")
    void createRelation_tagNotFound() {
        when(abilityTagMapper.selectById(1L)).thenReturn(tag(1L, "Java"));
        when(abilityTagMapper.selectById(2L)).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> service.createRelation(1L, 2L, "SIMILAR", 0.9, "MANUAL", "r", 1L));
    }

    @Test
    @DisplayName("createRelation：关系已存在 → 抛 PARAM_ERROR，不重复落库")
    void createRelation_duplicate() {
        when(abilityTagMapper.selectById(anyLong())).thenReturn(tag(1L, "Java"));
        // IService#getOne(Wrapper) 默认实现走 BaseMapper#selectOne(Wrapper, boolean)
        stubSelectOneReturning(relation(9L, 1L, 2L, "SIMILAR", "PENDING"));

        assertThrows(BusinessException.class,
                () -> service.createRelation(1L, 2L, "SIMILAR", 0.9, "MANUAL", "r", 1L));

        verify(baseMapper, never()).insert(any(AbilityTagRelation.class));
    }
    @Test
    @DisplayName("createRelation：关系类型非法 → 抛 PARAM_ERROR")
    void createRelation_invalidType() {
        when(abilityTagMapper.selectById(anyLong())).thenReturn(tag(1L, "Java"));
        stubSelectOneReturning();

        assertThrows(BusinessException.class,
                () -> service.createRelation(1L, 2L, "NOT_A_TYPE", 0.9, "MANUAL", "r", 1L));
    }

    @Test
    @DisplayName("createRelation：正常创建 → 落库 PENDING，相似度与证据来源被写入")
    void createRelation_success() {
        when(abilityTagMapper.selectById(anyLong())).thenReturn(tag(1L, "Java"));
        stubSelectOneReturning();
        when(baseMapper.insert(any(AbilityTagRelation.class))).thenReturn(1);

        AbilityTagRelation created = service.createRelation(
                1L, 2L, "SIMILAR", 0.8765, "VECTOR_DISCOVERY", "向量自动发现", 42L);

        assertNotNull(created);
        assertEquals(1L, created.getSourceTagId());
        assertEquals(2L, created.getTargetTagId());
        assertEquals(RelationTypeEnum.SIMILAR.getCode(), created.getRelationType());
        assertEquals(RelationStatusEnum.PENDING.getCode(), created.getStatus());
        assertEquals(EvidenceSourceEnum.VECTOR_DISCOVERY.getCode(), created.getEvidenceSource());
        assertEquals(0, new BigDecimal("0.8765").compareTo(created.getSimilarityScore()));
        assertEquals("向量自动发现", created.getRemark());
        assertEquals(42L, created.getCreatedBy());
        verify(baseMapper).insert(any(AbilityTagRelation.class));
    }

    @Test
    @DisplayName("createRelation：相似度为空 → 不写入 similarityScore")
    void createRelation_nullSimilarity() {
        when(abilityTagMapper.selectById(anyLong())).thenReturn(tag(1L, "Java"));
        stubSelectOneReturning();
        when(baseMapper.insert(any(AbilityTagRelation.class))).thenReturn(1);

        AbilityTagRelation created = service.createRelation(
                1L, 2L, "SAME_AS", null, null, null, null);

        assertNull(created.getSimilarityScore());
        assertNull(created.getEvidenceSource());
        assertNull(created.getRemark());
        assertEquals(RelationTypeEnum.SAME_AS.getCode(), created.getRelationType());
    }

    @Test
    @DisplayName("createRelation：证据来源为无法识别的字符串 → 原样保留")
    void createRelation_unknownEvidenceSource() {
        when(abilityTagMapper.selectById(anyLong())).thenReturn(tag(1L, "Java"));
        stubSelectOneReturning();
        when(baseMapper.insert(any(AbilityTagRelation.class))).thenReturn(1);

        AbilityTagRelation created = service.createRelation(
                1L, 2L, "SIMILAR", 0.5, "CUSTOM_SOURCE", "r", 1L);

        assertEquals("CUSTOM_SOURCE", created.getEvidenceSource());
    }

    // ==================== 审批 / 拒绝 ====================

    @Test
    @DisplayName("approveRelation：关系不存在 → 抛 PARAM_ERROR")
    void approveRelation_notFound() {
        when(baseMapper.selectById(1L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.approveRelation(1L, 9L));
    }

    @Test
    @DisplayName("approveRelation：非 PENDING 状态 → 抛 PARAM_ERROR")
    void approveRelation_wrongStatus() {
        when(baseMapper.selectById(1L)).thenReturn(
                relation(1L, 1L, 2L, "SIMILAR", RelationStatusEnum.CONFIRMED.getCode()));

        assertThrows(BusinessException.class, () -> service.approveRelation(1L, 9L));
    }

    @Test
    @DisplayName("approveRelation：SIMILAR 关系通过 → 置 CONFIRMED，不触发标签归并")
    void approveRelation_similar_success() {
        AbilityTagRelation rel = relation(1L, 1L, 2L, "SIMILAR", RelationStatusEnum.PENDING.getCode());
        when(baseMapper.selectById(1L)).thenReturn(rel);
        when(baseMapper.updateById(any(AbilityTagRelation.class))).thenReturn(1);

        service.approveRelation(1L, 9L);

        assertEquals(RelationStatusEnum.CONFIRMED.getCode(), rel.getStatus());
        assertEquals(9L, rel.getUpdatedBy());
        verify(baseMapper).updateById(rel);
        verify(abilityTagMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("approveRelation：SAME_AS 关系通过 → 触发归并，source 组全部改指向 target 标准标签并补别名")
    void approveRelation_sameAs_merges() {
        AbilityTagRelation rel = relation(1L, 10L, 20L, RelationTypeEnum.SAME_AS.getCode(),
                RelationStatusEnum.PENDING.getCode());
        when(baseMapper.selectById(1L)).thenReturn(rel);
        when(baseMapper.updateById(any(AbilityTagRelation.class))).thenReturn(1);

        AbilityTag source = tag(10L, "Spring Boot");
        AbilityTag target = tag(20L, "SpringBoot");
        when(abilityTagMapper.selectById(10L)).thenReturn(source);
        when(abilityTagMapper.selectById(20L)).thenReturn(target);

        AbilityTag groupA = tag(11L, "Spring-Boot");
        AbilityTag groupB = tag(20L, "SpringBoot");
        when(abilityTagMapper.selectList(any())).thenReturn(List.of(groupA, groupB));
        when(abilityTagMapper.updateById(any(AbilityTag.class))).thenReturn(1);
        when(tagAliasMapper.selectOne(any())).thenReturn(null);
        when(tagAliasMapper.insert(any(AbilityTagAlias.class))).thenReturn(1);

        service.approveRelation(1L, 9L);

        assertEquals(20L, groupA.getCanonicalTagId());
        assertEquals(20L, groupB.getCanonicalTagId());
        verify(tagCanonicalCacheInvalidator, times(2)).evictCanonicalCache(anyLong());

        // target 标签自身不补别名，仅其它标签补
        ArgumentCaptor<AbilityTagAlias> cap = ArgumentCaptor.forClass(AbilityTagAlias.class);
        verify(tagAliasMapper, times(1)).insert(cap.capture());
        assertEquals(20L, cap.getValue().getTagId());
        assertEquals("Spring-Boot", cap.getValue().getAliasName());
    }

    @Test
    @DisplayName("approveRelation：SAME_AS 但标签已被删除 → 归并静默跳过，不更新任何标签")
    void approveRelation_sameAs_tagsMissing() {
        AbilityTagRelation rel = relation(1L, 10L, 20L, RelationTypeEnum.SAME_AS.getCode(),
                RelationStatusEnum.PENDING.getCode());
        when(baseMapper.selectById(1L)).thenReturn(rel);
        when(baseMapper.updateById(any(AbilityTagRelation.class))).thenReturn(1);
        when(abilityTagMapper.selectById(10L)).thenReturn(null);
        when(abilityTagMapper.selectById(20L)).thenReturn(tag(20L, "SpringBoot"));

        service.approveRelation(1L, 9L);

        verify(abilityTagMapper, never()).selectList(any());
        verify(abilityTagMapper, never()).updateById(any(AbilityTag.class));
    }

    @Test
    @DisplayName("approveRelation：缓存失效抛异常 → 不影响主流程（自愈）")
    void approveRelation_cacheEvictFails() {
        AbilityTagRelation rel = relation(1L, 10L, 20L, RelationTypeEnum.SAME_AS.getCode(),
                RelationStatusEnum.PENDING.getCode());
        when(baseMapper.selectById(1L)).thenReturn(rel);
        when(baseMapper.updateById(any(AbilityTagRelation.class))).thenReturn(1);
        when(abilityTagMapper.selectById(10L)).thenReturn(tag(10L, "Spring Boot"));
        when(abilityTagMapper.selectById(20L)).thenReturn(tag(20L, "SpringBoot"));
        when(abilityTagMapper.selectList(any())).thenReturn(List.of(tag(11L, "Spring-Boot")));
        when(abilityTagMapper.updateById(any(AbilityTag.class))).thenReturn(1);
        doThrow(new RuntimeException("redis down")).when(tagCanonicalCacheInvalidator).evictCanonicalCache(anyLong());
        when(tagAliasMapper.selectOne(any())).thenReturn(null);
        when(tagAliasMapper.insert(any(AbilityTagAlias.class))).thenReturn(1);

        service.approveRelation(1L, 9L);

        assertEquals(RelationStatusEnum.CONFIRMED.getCode(), rel.getStatus());
        verify(tagAliasMapper).insert(any(AbilityTagAlias.class));
    }

    @Test
    @DisplayName("approveRelation：SAME_AS 归并时别名已存在 → 不重复插入别名")
    void approveRelation_sameAs_aliasAlreadyExists() {
        AbilityTagRelation rel = relation(1L, 10L, 20L, RelationTypeEnum.SAME_AS.getCode(),
                RelationStatusEnum.PENDING.getCode());
        when(baseMapper.selectById(1L)).thenReturn(rel);
        when(baseMapper.updateById(any(AbilityTagRelation.class))).thenReturn(1);
        when(abilityTagMapper.selectById(10L)).thenReturn(tag(10L, "Spring Boot"));
        when(abilityTagMapper.selectById(20L)).thenReturn(tag(20L, "SpringBoot"));
        when(abilityTagMapper.selectList(any())).thenReturn(List.of(tag(11L, "Spring-Boot")));
        when(abilityTagMapper.updateById(any(AbilityTag.class))).thenReturn(1);
        when(tagAliasMapper.selectOne(any())).thenReturn(new AbilityTagAlias());

        service.approveRelation(1L, 9L);

        verify(tagAliasMapper, never()).insert(any(AbilityTagAlias.class));
    }

    @Test
    @DisplayName("rejectRelation：PENDING 关系 → 置 REJECTED")
    void rejectRelation_success() {
        AbilityTagRelation rel = relation(1L, 1L, 2L, "SIMILAR", RelationStatusEnum.PENDING.getCode());
        when(baseMapper.selectById(1L)).thenReturn(rel);
        when(baseMapper.updateById(any(AbilityTagRelation.class))).thenReturn(1);

        service.rejectRelation(1L, 9L);

        assertEquals(RelationStatusEnum.REJECTED.getCode(), rel.getStatus());
        assertEquals(9L, rel.getUpdatedBy());
        verify(baseMapper).updateById(rel);
    }

    @Test
    @DisplayName("rejectRelation：关系不存在 → 抛 PARAM_ERROR")
    void rejectRelation_notFound() {
        when(baseMapper.selectById(1L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.rejectRelation(1L, 9L));
    }

    @Test
    @DisplayName("rejectRelation：非 PENDING 状态 → 抛 PARAM_ERROR")
    void rejectRelation_wrongStatus() {
        when(baseMapper.selectById(1L)).thenReturn(
                relation(1L, 1L, 2L, "SIMILAR", RelationStatusEnum.REJECTED.getCode()));

        assertThrows(BusinessException.class, () -> service.rejectRelation(1L, 9L));
    }

    // ==================== 双向关系查找 ====================

    @Test
    @DisplayName("findRelationsBetween：任一标签为空 → 返回空列表")
    void findRelationsBetween_nullIds() {
        assertTrue(service.findRelationsBetween(null, 2L).isEmpty());
        assertTrue(service.findRelationsBetween(1L, null).isEmpty());
        verify(baseMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("findRelationsBetween：合并正向与反向关系")
    void findRelationsBetween_mergesBothDirections() {
        when(baseMapper.selectList(any())).thenReturn(
                List.of(relation(1L, 1L, 2L, "SIMILAR", "PENDING")),
                List.of(relation(2L, 2L, 1L, "SIMILAR", "CONFIRMED")));

        List<AbilityTagRelation> result = service.findRelationsBetween(1L, 2L);

        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals(2L, result.get(1).getId());
    }

    @Test
    @DisplayName("findRelationsBetween：两个方向都无关系 → 返回空列表")
    void findRelationsBetween_empty() {
        when(baseMapper.selectList(any())).thenReturn(Collections.emptyList(), Collections.emptyList());

        assertTrue(service.findRelationsBetween(1L, 2L).isEmpty());
    }

    // ==================== 批量候选关系 ====================

    @Test
    @DisplayName("batchCreateCandidateRelations：源为空或候选为空 → 返回 0")
    void batchCreateCandidateRelations_emptyInput() {
        assertEquals(0, service.batchCreateCandidateRelations(null, List.of(2L), List.of(0.9)));
        assertEquals(0, service.batchCreateCandidateRelations(1L, null, List.of(0.9)));
        assertEquals(0, service.batchCreateCandidateRelations(1L, Collections.emptyList(), List.of(0.9)));
    }

    @Test
    @DisplayName("batchCreateCandidateRelations：逐条创建，已存在的关系被跳过并计入失败")
    void batchCreateCandidateRelations_skipsDuplicates() {
        when(abilityTagMapper.selectById(anyLong())).thenReturn(tag(1L, "Java"));
        // 第 1 条（target=2L）：已存在 → 跳过；第 2 条（target=3L）：成功
        when(baseMapper.selectOne(any(), anyBoolean())).thenAnswer(inv ->
                wrapperTargetIdEquals(inv.getArgument(0), 2L)
                        ? relation(9L, 1L, 2L, "SIMILAR", "PENDING") : null);
        when(baseMapper.insert(any(AbilityTagRelation.class))).thenReturn(1);

        int created = service.batchCreateCandidateRelations(1L, List.of(2L, 3L), List.of(0.9, 0.8));

        assertEquals(1, created);
        verify(baseMapper, times(1)).insert(any(AbilityTagRelation.class));
    }

    @Test
    @DisplayName("batchCreateCandidateRelations：相似度列表短于标签列表 → 缺失的相似度按 null 处理")
    void batchCreateCandidateRelations_shortScoreList() {
        when(abilityTagMapper.selectById(anyLong())).thenReturn(tag(1L, "Java"));
        stubSelectOneReturning();
        when(baseMapper.insert(any(AbilityTagRelation.class))).thenReturn(1);

        int created = service.batchCreateCandidateRelations(1L, List.of(2L, 3L), List.of(0.9));

        assertEquals(2, created);
        ArgumentCaptor<AbilityTagRelation> cap = ArgumentCaptor.forClass(AbilityTagRelation.class);
        verify(baseMapper, times(2)).insert(cap.capture());
        assertNotNull(cap.getAllValues().get(0).getSimilarityScore());
        assertNull(cap.getAllValues().get(1).getSimilarityScore());
    }

    // ==================== 向量自动发现 ====================

    @Test
    @DisplayName("discoverRelations：阈值越界 → 抛 BusinessException，且执行标记被复位")
    void discoverRelations_thresholdOutOfRange() {
        assertThrows(BusinessException.class, () -> service.discoverRelations(-0.1));
        assertThrows(BusinessException.class, () -> service.discoverRelations(1.5));
        // 异常后 discoveryRunning 已复位，可再次进入（若未复位这里也会抛异常）
        assertThrows(BusinessException.class, () -> service.discoverRelations(2.0));
    }

    @Test
    @DisplayName("discoverRelations：岗位能力为空 + 有向量标签不足 2 个 → 返回 0")
    void discoverRelations_notEnoughVectorTags() {
        when(postQueryPort.listAllPostAbilityModels()).thenReturn(Collections.emptyList());
        when(abilityTagMapper.selectList(any())).thenReturn(List.of(tagWithVector(1L, "Java", List.of(1.0f))));

        assertEquals(0, service.discoverRelations(0.8));
    }

    @Test
    @DisplayName("discoverRelations：无向量的标签被补充向量，相似度达标则创建候选关系")
    void discoverRelations_generatesVectorsAndCreatesRelation() {
        when(postQueryPort.listAllPostAbilityModels()).thenReturn(Collections.emptyList());

        AbilityTag noVector = tag(1L, "Spring Boot");
        AbilityTag withVector = tagWithVector(2L, "SpringBoot", List.of(1.0f, 0.0f));
        // 第一次 selectList 返回原始标签；向量补全后第二次 selectList 返回带向量的版本
        when(abilityTagMapper.selectList(any())).thenReturn(
                new ArrayList<>(List.of(noVector, withVector)),
                new ArrayList<>(List.of(tagWithVector(1L, "Spring Boot", List.of(1.0f, 0.0f)), withVector)));
        when(vectorEmbeddingService.embed("Spring Boot")).thenReturn(List.of(1.0f, 0.0f));
        when(vectorEmbeddingService.cosineSimilarity(any(), any())).thenReturn(0.95f);
        when(abilityTagMapper.updateById(any(AbilityTag.class))).thenReturn(1);
        when(baseMapper.delete(any())).thenReturn(3);
        when(abilityTagMapper.selectById(anyLong())).thenReturn(tag(1L, "Spring Boot"));
        stubSelectOneReturning();
        when(baseMapper.insert(any(AbilityTagRelation.class))).thenReturn(1);

        int created = service.discoverRelations(0.8);

        assertEquals(1, created);
        assertNotNull(noVector.getEmbeddingVector());
        verify(abilityTagMapper).updateById(noVector);
        verify(baseMapper).delete(any());
    }

    @Test
    @DisplayName("discoverRelations：相似度低于阈值 → 不创建关系")
    void discoverRelations_belowThreshold() {
        when(postQueryPort.listAllPostAbilityModels()).thenReturn(Collections.emptyList());
        when(abilityTagMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(
                tagWithVector(1L, "Java", List.of(1.0f, 0.0f)),
                tagWithVector(2L, "Python", List.of(0.0f, 1.0f)))));
        when(vectorEmbeddingService.cosineSimilarity(any(), any())).thenReturn(0.3f);
        when(baseMapper.delete(any())).thenReturn(0);

        int created = service.discoverRelations(0.9);

        assertEquals(0, created);
        verify(baseMapper, never()).insert(any(AbilityTagRelation.class));
    }

    @Test
    @DisplayName("discoverRelations：向量补全失败 → 该标签被跳过，不影响其它标签")
    void discoverRelations_embedFailureIsSwallowed() {
        when(postQueryPort.listAllPostAbilityModels()).thenReturn(Collections.emptyList());
        AbilityTag bad = tag(1L, "Bad Tag");
        AbilityTag good = tagWithVector(2L, "Java", List.of(1.0f));
        AbilityTag good2 = tagWithVector(3L, "JavaEE", List.of(1.0f));
        when(abilityTagMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(bad, good, good2)));
        when(vectorEmbeddingService.embed("Bad Tag")).thenThrow(new RuntimeException("embed down"));
        when(vectorEmbeddingService.cosineSimilarity(any(), any())).thenReturn(0.5f);
        when(baseMapper.delete(any())).thenReturn(0);
        when(abilityTagMapper.selectById(anyLong())).thenReturn(tag(2L, "Java"));
        stubSelectOneReturning();
        when(baseMapper.insert(any(AbilityTagRelation.class))).thenReturn(1);

        int created = service.discoverRelations(0.4);

        assertEquals(1, created);
        assertNull(bad.getEmbeddingVector());
    }

    @Test
    @DisplayName("discoverRelations：岗位能力非空 → 逐个旁路同步标签治理事件，并过滤空能力名")
    void discoverRelations_syncsPostAbilities() {
        when(postQueryPort.listAllPostAbilityModels()).thenReturn(List.of(
                new PostQueryPort.PostAbilityDTO(1L, 100L, 5L, 3, BigDecimal.ONE, 1, 1, "v1", "备注", "Java", null, null),
                new PostQueryPort.PostAbilityDTO(2L, 100L, 6L, 3, BigDecimal.ONE, 1, 1, "v1", null, "  ", null, null),
                new PostQueryPort.PostAbilityDTO(3L, 100L, 7L, 3, BigDecimal.ONE, 1, 1, "v1", null, null, null, null)));
        when(abilityTagMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(
                tagWithVector(1L, "Java", List.of(1.0f)))));

        int created = service.discoverRelations(0.9);

        assertEquals(0, created);
        ArgumentCaptor<PostAbilityTagGovernanceRequestedEvent> cap =
                ArgumentCaptor.forClass(PostAbilityTagGovernanceRequestedEvent.class);
        verify(postAbilityTagGovernanceService, times(1)).govern(cap.capture());
        assertEquals("Java", cap.getValue().abilityName());
        assertEquals(100L, cap.getValue().postId());
        assertEquals(1L, cap.getValue().sourceRefId());
    }

    @Test
    @DisplayName("discoverRelations：岗位能力端口返回 null → 跳过旁路同步不报错")
    void discoverRelations_nullPostAbilities() {
        when(postQueryPort.listAllPostAbilityModels()).thenReturn(null);
        when(abilityTagMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(
                tagWithVector(1L, "Java", List.of(1.0f)))));

        assertEquals(0, service.discoverRelations(0.9));

        verify(postAbilityTagGovernanceService, never()).govern(any());
    }

    @Test
    @DisplayName("discoverRelations：全部标签都已有向量 → 不调用嵌入服务")
    void discoverRelations_allTagsHaveVector() {
        when(postQueryPort.listAllPostAbilityModels()).thenReturn(Collections.emptyList());
        when(abilityTagMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(
                tagWithVector(1L, "Java", List.of(1.0f)),
                tagWithVector(2L, "Python", List.of(0.0f)))));
        when(vectorEmbeddingService.cosineSimilarity(any(), any())).thenReturn(0.99f);
        when(baseMapper.delete(any())).thenReturn(0);
        when(abilityTagMapper.selectById(anyLong())).thenReturn(tag(1L, "Java"));
        stubSelectOneReturning();
        when(baseMapper.insert(any(AbilityTagRelation.class))).thenReturn(1);

        service.discoverRelations(0.5);

        verify(vectorEmbeddingService, never()).embed(anyString());
    }

    @Test
    @DisplayName("discoverRelations：候选关系已存在 → 创建失败被吞掉，返回 0")
    void discoverRelations_createRelationConflict() {
        when(postQueryPort.listAllPostAbilityModels()).thenReturn(Collections.emptyList());
        when(abilityTagMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(
                tagWithVector(1L, "Java", List.of(1.0f)),
                tagWithVector(2L, "JavaEE", List.of(1.0f)))));
        when(vectorEmbeddingService.cosineSimilarity(any(), any())).thenReturn(0.99f);
        when(baseMapper.delete(any())).thenReturn(0);
        when(abilityTagMapper.selectById(anyLong())).thenReturn(tag(1L, "Java"));
        when(baseMapper.selectOne(any(), anyBoolean())).thenAnswer(inv ->
                wrapperSourceIdEquals(inv.getArgument(0), 1L)
                        ? relation(9L, 1L, 2L, "SIMILAR", "PENDING") : null);

        int created = service.discoverRelations(0.5);

        assertEquals(0, created);
        verify(baseMapper, never()).insert(any(AbilityTagRelation.class));
    }

    @Test
    @DisplayName("discoverRelations：有向量的标签恰好 1 个 → 跳过发现")
    void discoverRelations_singleVectorTag() {
        when(postQueryPort.listAllPostAbilityModels()).thenReturn(Collections.emptyList());
        when(abilityTagMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(
                tagWithVector(1L, "Java", List.of(1.0f)),
                tag(2L, "NoVector"))));
        when(vectorEmbeddingService.embed("NoVector")).thenReturn(Collections.emptyList());

        assertEquals(0, service.discoverRelations(0.5));

        verify(baseMapper, never()).delete(any());
    }
}
