package com.example.matching.service.post.impl;

import com.example.matching.ai.service.LangChain4jChatService;
import com.example.matching.ai.service.PromptTemplateService;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.converter.post.PostPostConverterImpl;
import com.example.matching.dto.post.ExcelStructureDTO;
import com.example.matching.dto.post.JdAbilityItemDTO;
import com.example.matching.dto.post.PostImportBatchVO;
import com.example.matching.dto.post.PostImportConfirmDTO;
import com.example.matching.dto.post.PostImportPreviewDTO;
import com.example.matching.entity.post.PostImportBatch;
import com.example.matching.entity.post.PostImportItem;
import com.example.matching.entity.post.PostPost;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.mapper.post.PostImportBatchMapper;
import com.example.matching.mapper.post.PostImportItemMapper;
import com.example.matching.mapper.post.PostPostMapper;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.resilience.AiServiceResilience;
import com.example.matching.service.common.EventOutboxDispatcher;
import com.example.matching.service.common.VectorRecallCacheEpoch;
import com.example.matching.service.evolution.MarketJdImportService;
import com.example.matching.service.post.PostCapabilityGenerationService;
import com.example.matching.service.post.PostPostWriteService;
import com.example.matching.service.system.AbilityTagService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link PostExcelAiImportServiceImpl} 单元测试。
 *
 * <p>覆盖上传预览、触发分析、预览查询、确认导入与消费确认导入（含市场 JD 纳入）、
 * 取消、分页、重试、删除的各类正常/异常降级分支。Excel 结构识别、Redis、MQ 与
 * AI 能力服务均 mock，不产生真实 IO。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PostExcelAiImportServiceImplTest {

    @Mock private PostImportBatchMapper importBatchMapper;
    @Mock private PostImportItemMapper importItemMapper;
    @Mock private PostAbilityModelMapper postAbilityModelMapper;
    @Mock private PostPostMapper postPostMapper;
    @Mock private PostPostWriteService postPostWriteService;
    @Mock private PostCapabilityGenerationService capabilityGenerationService;
    @Mock private AbilityTagService abilityTagService;
    @Mock private EventOutboxDispatcher outboxDispatcher;
    @Mock private LangChain4jChatService langChain4jChatService;
    @Mock private AiServiceResilience aiServiceResilience;
    @Mock private PromptTemplateService promptTemplateService;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ExcelStructureRecognizer structureRecognizer;
    @Mock private VectorRecallCacheEpoch vectorRecallCacheEpoch;
    @Mock private MarketJdImportService marketJdImportService;
    @Mock private ValueOperations<String, Object> valueOperations;

    private ObjectMapper objectMapper;
    private PostExcelAiImportServiceImpl service;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        var cfg = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, PostImportBatch.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, PostImportItem.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, PostPost.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, PostAbilityModel.class);
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new PostExcelAiImportServiceImpl(
                importBatchMapper, importItemMapper, postAbilityModelMapper, postPostMapper,
                postPostWriteService, capabilityGenerationService, abilityTagService, outboxDispatcher,
                langChain4jChatService, aiServiceResilience, promptTemplateService, objectMapper,
                redisTemplate, structureRecognizer, vectorRecallCacheEpoch, marketJdImportService);
    }

    private PostImportBatch batch(Long id, Integer status) {
        PostImportBatch b = new PostImportBatch();
        b.setId(id);
        b.setFileName("jobs.xlsx");
        b.setTotalRows(3);
        b.setImportStatus(status);
        b.setCancelFlag(0);
        return b;
    }

    private PostImportItem item(Long id, Long batchId, String postName) {
        PostImportItem i = new PostImportItem();
        i.setId(id);
        i.setBatchId(batchId);
        i.setRowIndex(1);
        i.setPostName(postName);
        i.setPostDescription("描述");
        i.setAnalysisStatus(0);
        return i;
    }

    private PostImportPreviewDTO preview(Long batchId) {
        PostImportPreviewDTO p = new PostImportPreviewDTO();
        p.setBatchId(batchId);
        p.setItems(new ArrayList<>());
        return p;
    }

    // ==================== uploadAndAnalyze ====================

    @Test
    @DisplayName("uploadAndAnalyze：非 xlsx/xls 文件名 → IllegalArgumentException")
    void uploadAndAnalyze_invalidFileName() {
        assertThrows(IllegalArgumentException.class,
                () -> service.uploadAndAnalyze(null, new ByteArrayInputStream(new byte[0])));
        assertThrows(IllegalArgumentException.class,
                () -> service.uploadAndAnalyze("data.csv", new ByteArrayInputStream(new byte[0])));
    }

    @Test
    @DisplayName("uploadAndAnalyze：正常流程 → 建批次、插明细并返回预览")
    void uploadAndAnalyze_success() {
        InputStream in = new ByteArrayInputStream(new byte[0]);
        List<List<String>> raw = List.of(List.of("岗位", "描述"));
        ExcelStructureDTO structure = new ExcelStructureDTO();
        PostImportItem it = item(null, null, "Java工程师");
        PostImportPreviewDTO expected = preview(1L);

        when(structureRecognizer.readExcelRaw(in)).thenReturn(raw);
        when(structureRecognizer.callAiForStructureRecognition(raw, "jobs.xlsx")).thenReturn("{}");
        when(structureRecognizer.parseStructureResponse("{}")).thenReturn(structure);
        when(structureRecognizer.assemblePostItems(raw, structure)).thenReturn(new ArrayList<>(List.of(it)));
        when(structureRecognizer.buildPreview(any(), anyList(), any())).thenReturn(expected);
        doAnswer(inv -> {
            PostImportBatch b = inv.getArgument(0);
            if (b.getId() == null) b.setId(1L);
            return 1;
        }).when(importBatchMapper).insert(any(PostImportBatch.class));

        PostImportPreviewDTO result = service.uploadAndAnalyze("jobs.xlsx", in);

        assertNotNull(result);
        verify(importItemMapper).insert(anyList());
    }

    @Test
    @DisplayName("uploadAndAnalyze：结构识别结果为空（0 条岗位）→ 不插明细")
    void uploadAndAnalyze_emptyItems() {
        InputStream in = new ByteArrayInputStream(new byte[0]);
        ExcelStructureDTO structure = new ExcelStructureDTO();
        when(structureRecognizer.readExcelRaw(in)).thenReturn(new ArrayList<>());
        when(structureRecognizer.callAiForStructureRecognition(any(), any())).thenReturn("{}");
        when(structureRecognizer.parseStructureResponse(any())).thenReturn(structure);
        when(structureRecognizer.assemblePostItems(any(), any())).thenReturn(new ArrayList<>());
        when(structureRecognizer.buildPreview(any(), anyList(), any())).thenReturn(preview(1L));
        doAnswer(inv -> {
            PostImportBatch b = inv.getArgument(0);
            b.setId(1L);
            return 1;
        }).when(importBatchMapper).insert(any(PostImportBatch.class));

        assertNotNull(service.uploadAndAnalyze("a.xls", in));
        verify(importItemMapper, never()).insert(anyList());
    }

    // ==================== analyzeBatch ====================

    @Test
    @DisplayName("analyzeBatch：批次不存在 → 抛未找到")
    void analyzeBatch_notFound() {
        when(importBatchMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.analyzeBatch(1L));
    }

    @Test
    @DisplayName("analyzeBatch：已在分析中(1) → 跳过，不写 Outbox")
    void analyzeBatch_alreadyAnalyzing() {
        when(importBatchMapper.selectById(1L)).thenReturn(batch(1L, 1));
        service.analyzeBatch(1L);
        verify(outboxDispatcher, never()).enqueue(any(), any(), any(), any());
    }

    @Test
    @DisplayName("analyzeBatch：状态不允许(4) → 跳过")
    void analyzeBatch_statusNotAllowed() {
        when(importBatchMapper.selectById(1L)).thenReturn(batch(1L, 4));
        service.analyzeBatch(1L);
        verify(outboxDispatcher, never()).enqueue(any(), any(), any(), any());
    }

    @Test
    @DisplayName("analyzeBatch：待分析(0) → 初始化 Redis 进度并投递 Outbox")
    void analyzeBatch_success() {
        when(importBatchMapper.selectById(1L)).thenReturn(batch(1L, 0));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        service.analyzeBatch(1L);

        verify(outboxDispatcher).enqueue(eq("EXCEL_IMPORT_ANALYZE"), any(), any(), eq(1L));
        verify(valueOperations).set(startsWith("post:import:progress"), anyMap(), anyLong(), any());
    }

    @Test
    @DisplayName("analyzeBatch：Redis 异常 → 吞掉并继续投递")
    void analyzeBatch_redisFailureStillEnqueues() {
        when(importBatchMapper.selectById(1L)).thenReturn(batch(1L, 0));
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("redis down"));

        service.analyzeBatch(1L);

        verify(outboxDispatcher).enqueue(eq("EXCEL_IMPORT_ANALYZE"), any(), any(), eq(1L));
    }

    // ==================== getPreview ====================

    @Test
    @DisplayName("getPreview：Redis 命中 → 直接返回缓存对象")
    void getPreview_cacheHit() {
        PostImportPreviewDTO cached = preview(1L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(cached);

        assertSame(cached, service.getPreview(1L));
        verify(importBatchMapper, never()).selectById(anyLong());
    }

    @Test
    @DisplayName("getPreview：Redis 读异常 → 降级查库并回填缓存")
    void getPreview_redisReadFails() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("redis down"));
        when(importBatchMapper.selectById(1L)).thenReturn(batch(1L, 2));
        when(importItemMapper.selectList(any())).thenReturn(List.of(item(1L, 1L, "岗位")));
        when(structureRecognizer.buildPreview(any(), anyList(), any())).thenReturn(preview(1L));

        assertNotNull(service.getPreview(1L));
    }

    @Test
    @DisplayName("getPreview：批次不存在 → 抛未找到")
    void getPreview_notFound() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(importBatchMapper.selectById(9L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.getPreview(9L));
    }

    @Test
    @DisplayName("getPreview：结构响应可解析 → 使用解析后的结构；写缓存异常被吞")
    void getPreview_parsesStructureAndCacheWriteFails() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        PostImportBatch b = batch(1L, 2);
        b.setAiStructureResponse("{\"sheets\":[]}");
        when(importBatchMapper.selectById(1L)).thenReturn(b);
        when(importItemMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(structureRecognizer.buildPreview(any(), anyList(), any())).thenReturn(preview(1L));
        doThrow(new RuntimeException("redis down"))
                .when(valueOperations).set(anyString(), any(), anyLong(), any());

        assertNotNull(service.getPreview(1L));
    }

    @Test
    @DisplayName("getPreview：结构响应非法 JSON → 降级为 null 结构")
    void getPreview_invalidStructureJson() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        PostImportBatch b = batch(1L, 2);
        b.setAiStructureResponse("not-json{");
        when(importBatchMapper.selectById(1L)).thenReturn(b);
        when(importItemMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(structureRecognizer.buildPreview(any(), anyList(), any())).thenReturn(preview(1L));

        assertNotNull(service.getPreview(1L));
    }

    // ==================== confirmAndImport ====================

    @Test
    @DisplayName("confirmAndImport：批次不存在 → 抛未找到")
    void confirmAndImport_notFound() {
        PostImportConfirmDTO dto = new PostImportConfirmDTO();
        dto.setBatchId(1L);
        when(importBatchMapper.selectById(1L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.confirmAndImport(dto));
    }

    @Test
    @DisplayName("confirmAndImport：幂等抢占失败（状态不允许）→ 抛参数异常")
    void confirmAndImport_guardFails() {
        PostImportConfirmDTO dto = new PostImportConfirmDTO();
        dto.setBatchId(1L);
        when(importBatchMapper.selectById(1L)).thenReturn(batch(1L, 4));
        when(importBatchMapper.confirmImport(1L)).thenReturn(0);

        assertThrows(BusinessException.class, () -> service.confirmAndImport(dto));
    }

    @Test
    @DisplayName("confirmAndImport：保存载荷失败 → 抛参数异常")
    void confirmAndImport_savePayloadFails() {
        PostImportConfirmDTO dto = new PostImportConfirmDTO();
        dto.setBatchId(1L);
        when(importBatchMapper.selectById(1L)).thenReturn(batch(1L, 2));
        when(importBatchMapper.confirmImport(1L)).thenReturn(1);
        when(importBatchMapper.saveConfirmPayload(eq(1L), anyString())).thenReturn(0);

        assertThrows(BusinessException.class, () -> service.confirmAndImport(dto));
    }

    @Test
    @DisplayName("confirmAndImport：正常确认 → 状态置 3 并投递 Outbox")
    void confirmAndImport_success() {
        PostImportConfirmDTO dto = new PostImportConfirmDTO();
        dto.setBatchId(1L);
        dto.setItems(List.of(new PostImportConfirmDTO.ConfirmItem()));
        when(importBatchMapper.selectById(1L)).thenReturn(batch(1L, 2));
        when(importBatchMapper.confirmImport(1L)).thenReturn(1);
        when(importBatchMapper.saveConfirmPayload(eq(1L), anyString())).thenReturn(1);

        service.confirmAndImport(dto);

        verify(outboxDispatcher).enqueue(eq("EXCEL_IMPORT_CONFIRM"), any(), any(), eq(1L));
    }

    // ==================== processConfirmedImport ====================

    @Test
    @DisplayName("processConfirmedImport：批次不存在或不在导入中 → 直接返回")
    void processConfirmedImport_skip() {
        when(importBatchMapper.selectById(1L)).thenReturn(null);
        service.processConfirmedImport(1L);
        verify(importItemMapper, never()).updateById(anyList());

        when(importBatchMapper.selectById(2L)).thenReturn(batch(2L, 2));
        service.processConfirmedImport(2L);
        verify(importItemMapper, never()).updateById(anyList());
    }

    @Test
    @DisplayName("processConfirmedImport：载荷损坏 → 抛参数异常")
    void processConfirmedImport_brokenPayload() {
        PostImportBatch b = batch(1L, 3);
        b.setConfirmPayload("{invalid");
        when(importBatchMapper.selectById(1L)).thenReturn(b);

        assertThrows(BusinessException.class, () -> service.processConfirmedImport(1L));
    }

    @Test
    @DisplayName("processConfirmedImport：正常导入含能力项 → 建岗位、应用能力、回填 createdPostId")
    void processConfirmedImport_success() throws Exception {
        PostImportConfirmDTO dto = new PostImportConfirmDTO();
        dto.setBatchId(1L);
        dto.setIncludeMarketJd(false);
        PostImportConfirmDTO.ConfirmItem ci = new PostImportConfirmDTO.ConfirmItem();
        ci.setItemId(10L);
        ci.setConfirmed(true);
        ci.setPostName("Java工程师");
        ci.setPostDescription("描述");
        JdAbilityItemDTO ability = new JdAbilityItemDTO();
        ability.setSuggestedName("Java");
        ci.setAbilities(List.of(ability));
        dto.setItems(List.of(ci));

        PostImportBatch b = batch(1L, 3);
        b.setConfirmPayload(objectMapper.writeValueAsString(dto));
        when(importBatchMapper.selectById(1L)).thenReturn(b);
        when(importItemMapper.selectBatchIds(anyList())).thenReturn(List.of(item(10L, 1L, "Java工程师")));
        when(abilityTagService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(new ArrayList<>());
        doAnswer(inv -> {
            List<PostPost> posts = inv.getArgument(0);
            posts.forEach(p -> p.setId(99L));
            return posts;
        }).when(postPostWriteService).batchSave(anyList());

        service.processConfirmedImport(1L);

        verify(postPostWriteService).batchSave(anyList());
        verify(capabilityGenerationService).applyAbilityItemsToPost(eq(99L), anyList(), anyMap());
        verify(vectorRecallCacheEpoch).advance();
        verify(importBatchMapper).updateById(b);
    }

    @Test
    @DisplayName("processConfirmedImport：abilities 为空但存在 AI 原始响应 → 从响应反序列化")
    void processConfirmedImport_parseAiResponseFallback() throws Exception {
        PostImportConfirmDTO dto = new PostImportConfirmDTO();
        dto.setBatchId(1L);
        PostImportConfirmDTO.ConfirmItem ci = new PostImportConfirmDTO.ConfirmItem();
        ci.setItemId(10L);
        ci.setConfirmed(true);
        ci.setAbilities(null);
        dto.setItems(List.of(ci));

        PostImportBatch b = batch(1L, 3);
        b.setConfirmPayload(objectMapper.writeValueAsString(dto));
        when(importBatchMapper.selectById(1L)).thenReturn(b);

        PostImportItem it = item(10L, 1L, "Java工程师");
        it.setAiAnalysisResponse("[{\"suggestedName\":\"Java\"}]");
        when(importItemMapper.selectBatchIds(anyList())).thenReturn(List.of(it));
        when(abilityTagService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(new ArrayList<>());
        doAnswer(inv -> {
            List<PostPost> posts = inv.getArgument(0);
            posts.forEach(p -> p.setId(99L));
            return posts;
        }).when(postPostWriteService).batchSave(anyList());

        service.processConfirmedImport(1L);

        verify(capabilityGenerationService).applyAbilityItemsToPost(eq(99L), anyList(), anyMap());
    }

    @Test
    @DisplayName("processConfirmedImport：includeMarketJd=true → 纳入市场发现")
    void processConfirmedImport_includeMarketJd() throws Exception {
        PostImportConfirmDTO dto = new PostImportConfirmDTO();
        dto.setBatchId(1L);
        dto.setIncludeMarketJd(true);
        dto.setItems(new ArrayList<>());
        PostImportBatch b = batch(1L, 3);
        b.setConfirmPayload(objectMapper.writeValueAsString(dto));
        when(importBatchMapper.selectById(1L)).thenReturn(b);
        when(abilityTagService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(new ArrayList<>());

        // includeBatchInMarketDiscovery 需要存在带 createdPostId 的明细与对应岗位
        PostImportItem it = item(10L, 1L, "Java工程师");
        it.setCreatedPostId(99L);
        when(importItemMapper.selectList(any())).thenReturn(List.of(it));
        PostPost post = new PostPost();
        post.setId(99L);
        post.setPostName("Java工程师");
        post.setJobDescription("描述");
        when(postPostMapper.selectBatchIds(anyList())).thenReturn(List.of(post));
        when(postAbilityModelMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(marketJdImportService.importVerifiedPostBatch(any(), anyList())).thenReturn(1);

        service.processConfirmedImport(1L);

        verify(marketJdImportService).importVerifiedPostBatch(eq(1L), anyList());
    }

    @Test
    @DisplayName("processConfirmedImport：未确认的项被跳过，未找到 item 也跳过")
    void processConfirmedImport_skipsUnconfirmed() throws Exception {
        PostImportConfirmDTO dto = new PostImportConfirmDTO();
        dto.setBatchId(1L);
        PostImportConfirmDTO.ConfirmItem unconfirmed = new PostImportConfirmDTO.ConfirmItem();
        unconfirmed.setItemId(10L);
        unconfirmed.setConfirmed(false);
        PostImportConfirmDTO.ConfirmItem missing = new PostImportConfirmDTO.ConfirmItem();
        missing.setItemId(11L);
        missing.setConfirmed(true);
        dto.setItems(List.of(unconfirmed, missing));

        PostImportBatch b = batch(1L, 3);
        b.setConfirmPayload(objectMapper.writeValueAsString(dto));
        when(importBatchMapper.selectById(1L)).thenReturn(b);
        when(importItemMapper.selectBatchIds(anyList())).thenReturn(new ArrayList<>());
        when(abilityTagService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(new ArrayList<>());

        service.processConfirmedImport(1L);

        verify(postPostWriteService, never()).batchSave(anyList());
    }

    // ==================== includeBatchInMarketDiscovery ====================

    @Test
    @DisplayName("includeBatchInMarketDiscovery：批次不存在 → 未找到；状态非4 → 参数异常")
    void includeBatchInMarketDiscovery_guards() {
        when(importBatchMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.includeBatchInMarketDiscovery(1L));

        when(importBatchMapper.selectById(2L)).thenReturn(batch(2L, 3));
        assertThrows(BusinessException.class, () -> service.includeBatchInMarketDiscovery(2L));
    }

    @Test
    @DisplayName("includeBatchInMarketDiscovery：无已创建岗位 → 返回 0")
    void includeBatchInMarketDiscovery_noItems() {
        when(importBatchMapper.selectById(1L)).thenReturn(batch(1L, 4));
        when(importItemMapper.selectList(any())).thenReturn(new ArrayList<>());

        assertEquals(0, service.includeBatchInMarketDiscovery(1L));
    }

    @Test
    @DisplayName("includeBatchInMarketDiscovery：正常 → 聚合岗位与标签并委托导入，缺失岗位跳过")
    void includeBatchInMarketDiscovery_success() {
        when(importBatchMapper.selectById(1L)).thenReturn(batch(1L, 4));

        PostImportItem it = item(10L, 1L, "Java工程师");
        it.setCreatedPostId(99L);
        PostImportItem orphan = item(11L, 1L, "孤立");
        orphan.setCreatedPostId(100L);
        when(importItemMapper.selectList(any())).thenReturn(List.of(it, orphan));

        PostPost post = new PostPost();
        post.setId(99L);
        post.setPostName("Java工程师");
        post.setJobDescription("描述");
        when(postPostMapper.selectBatchIds(anyList())).thenReturn(List.of(post)); // 100 缺失

        PostAbilityModel model = new PostAbilityModel();
        model.setPostId(99L);
        model.setTagId(5L);
        when(postAbilityModelMapper.selectList(any())).thenReturn(List.of(model));
        when(marketJdImportService.importVerifiedPostBatch(eq(1L), anyList())).thenReturn(1);

        assertEquals(1, service.includeBatchInMarketDiscovery(1L));
        verify(marketJdImportService).importVerifiedPostBatch(eq(1L), anyList());
    }

    // ==================== cancelBatch ====================

    @Test
    @DisplayName("cancelBatch：批次不存在 → 未找到；非分析中 → 不取消")
    void cancelBatch_variants() {
        when(importBatchMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.cancelBatch(1L));

        when(importBatchMapper.selectById(2L)).thenReturn(batch(2L, 4));
        service.cancelBatch(2L);
        verify(importBatchMapper, never()).updateById(any(PostImportBatch.class));
    }

    @Test
    @DisplayName("cancelBatch：分析中 → 置取消标志并失效缓存")
    void cancelBatch_setsFlag() {
        PostImportBatch b = batch(1L, 1);
        when(importBatchMapper.selectById(1L)).thenReturn(b);

        service.cancelBatch(1L);

        assertEquals(1, b.getCancelFlag());
        verify(importBatchMapper).updateById(b);
    }

    // ==================== pageBatches ====================

    @Test
    @DisplayName("pageBatches：空页 → 返回空 VO 列表")
    void pageBatches_empty() {
        Page<PostImportBatch> dbPage = new Page<>(1, 10);
        dbPage.setRecords(new ArrayList<>());
        when(importBatchMapper.selectPage(any(), any())).thenReturn(dbPage);

        assertTrue(service.pageBatches(1, 10, null).getRecords().isEmpty());
    }

    @Test
    @DisplayName("pageBatches：带过滤并聚合各分析状态计数")
    void pageBatches_withStats() {
        Page<PostImportBatch> dbPage = new Page<>(1, 10);
        dbPage.setRecords(List.of(batch(1L, 2), batch(2L, 4)));
        dbPage.setTotal(2);
        when(importBatchMapper.selectPage(any(), any())).thenReturn(dbPage);

        List<Map<String, Object>> stats = new ArrayList<>();
        stats.add(Map.of("batch_id", 1L, "analysis_status", 2, "cnt", 5));
        stats.add(Map.of("batch_id", 1L, "analysis_status", 3, "cnt", 1));
        stats.add(Map.of("batch_id", 2L, "analysis_status", 0, "cnt", 4));
        stats.add(Map.of("batch_id", 99L, "analysis_status", 1, "cnt", 1)); // 不在本页
        when(importItemMapper.countByBatchIds(anyList())).thenReturn(stats);

        Page<PostImportBatchVO> out = service.pageBatches(1, 10, 2);

        assertEquals(2, out.getRecords().size());
        PostImportBatchVO v1 = out.getRecords().stream()
                .filter(v -> v.getId().equals(1L)).findFirst().orElseThrow();
        assertEquals(5, v1.getSuccessAnalyzedCount());
        assertEquals(1, v1.getFailedAnalyzedCount());
    }

    // ==================== retryBatch ====================

    @Test
    @DisplayName("retryBatch：批次不存在 → 未找到")
    void retryBatch_notFound() {
        when(importBatchMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.retryBatch(1L));
    }

    @Test
    @DisplayName("retryBatch：状态不允许（已完成4）→ 抛参数异常")
    void retryBatch_notAllowed() {
        when(importBatchMapper.selectById(1L)).thenReturn(batch(1L, 4));
        assertThrows(BusinessException.class, () -> service.retryBatch(1L));
    }

    @Test
    @DisplayName("retryBatch：失败态(5) → 重置明细与批次后重新触发分析")
    void retryBatch_fromFailed() {
        PostImportBatch b = batch(1L, 5);
        when(importBatchMapper.selectById(1L)).thenReturn(b);
        when(importBatchMapper.selectById(1L)).thenReturn(b);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        service.retryBatch(1L);

        assertEquals(0, b.getImportStatus());
        verify(importItemMapper).update(any(), any());
        verify(importBatchMapper).updateById(b);
        verify(outboxDispatcher).enqueue(eq("EXCEL_IMPORT_ANALYZE"), any(), any(), eq(1L));
    }

    @Test
    @DisplayName("retryBatch：分析中(1)但已取消 → 允许重试")
    void retryBatch_cancelledAnalyzing() {
        PostImportBatch b = batch(1L, 1);
        b.setCancelFlag(1);
        when(importBatchMapper.selectById(1L)).thenReturn(b);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        service.retryBatch(1L);

        verify(importBatchMapper).updateById(b);
    }

    // ==================== deleteBatch ====================

    @Test
    @DisplayName("deleteBatch：批次不存在 → 未找到")
    void deleteBatch_notFound() {
        when(importBatchMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.deleteBatch(1L));
    }

    @Test
    @DisplayName("deleteBatch：分析中(1) → 先置取消标志再删除")
    void deleteBatch_analyzingCancelsFirst() {
        PostImportBatch b = batch(1L, 1);
        when(importBatchMapper.selectById(1L)).thenReturn(b);

        service.deleteBatch(1L);

        assertEquals(1, b.getCancelFlag());
        verify(importBatchMapper).updateById(b);
        verify(importItemMapper).deleteByBatchId(1L);
        verify(importBatchMapper).deleteById(1L);
    }

    @Test
    @DisplayName("deleteBatch：已完成(4) → 不置取消标志，直接删明细与批次")
    void deleteBatch_completed() {
        PostImportBatch b = batch(1L, 4);
        when(importBatchMapper.selectById(1L)).thenReturn(b);

        service.deleteBatch(1L);

        verify(importItemMapper).deleteByBatchId(1L);
        verify(importBatchMapper).deleteById(1L);
        verify(importBatchMapper, never()).updateById(any(PostImportBatch.class));
    }
}
