package com.example.matching.service.evolution;

import com.example.matching.config.MarketJdCapabilityAdmissionProperties;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.entity.evolution.MarketJdCrawlerBatchLog;
import com.example.matching.entity.evolution.MarketJdDataVersion;
import com.example.matching.mapper.evolution.MarketJdDataMapper;
import com.example.matching.mapper.evolution.MarketJdDataVersionMapper;
import com.example.matching.mapper.post.PostPostMapper;
import com.example.matching.service.evolution.crawler.CrawlerBatchLogService;
import com.example.matching.service.evolution.impl.MarketJdImportServiceImpl;
import com.example.matching.service.post.PostCapabilityGenerationService;
import com.example.matching.service.post.PostDataCleaningService;
import com.example.matching.entity.evolution.MarketJdData;
import com.example.matching.dto.post.JdAbilityItemDTO;
import com.example.matching.dto.post.PostCleaningResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class MarketJdImportServiceImplTest {

    /**
     * 单测环境没有 MyBatis 容器，LambdaQueryWrapper/LambdaUpdateWrapper 需要 TableInfo 缓存
     * 才能把 `MarketJdData::getBatchNo` 这类 lambda 解析成列名，否则会抛
     * "MybatisPlus can not find lambda cache for this entity"。
     */
    @org.junit.jupiter.api.BeforeAll
    static void initTableInfoCache() {
        com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant assistant =
                new com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, MarketJdData.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, MarketJdDataVersion.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, MarketJdCrawlerBatchLog.class);
    }

    private final MarketJdDataMapper marketJdDataMapper = mock(MarketJdDataMapper.class);
    private final PostPostMapper postPostMapper = mock(PostPostMapper.class);
    private final PostCapabilityGenerationService postCapabilityGenerationService =
            mock(PostCapabilityGenerationService.class);
    private final PostDataCleaningService postDataCleaningService = mock(PostDataCleaningService.class);
    private final MarketJdCapabilityAdmissionService admissionService =
            mock(MarketJdCapabilityAdmissionService.class);
    private final CrawlerBatchLogService batchLogService = mock(CrawlerBatchLogService.class);
    private final MarketJdDataVersionMapper marketJdDataVersionMapper = mock(MarketJdDataVersionMapper.class);

    private final MarketJdImportServiceImpl service = new MarketJdImportServiceImpl(
            marketJdDataMapper,
            postPostMapper,
            postCapabilityGenerationService,
            postDataCleaningService,
            new ObjectMapper(),
            admissionService,
            new MarketJdCapabilityAdmissionProperties(),
            batchLogService,
            marketJdDataVersionMapper);

    @Test
    void qualityScoreRecognizesSkillKeywordWithinJdText() {
        BigDecimal score = ReflectionTestUtils.invokeMethod(service, "calculateQualityScore", "\u5177\u5907\u6280\u80fd");

        assertEquals(new BigDecimal("55.0"), score);
    }

    @Test
    void importUsesTheSameHashForWhitespaceOnlyJdVariants() {
        service.importFromTextList(List.of("Java\n  Spring Boot", " java Spring   Boot "), "TEST");

        ArgumentCaptor<com.example.matching.entity.evolution.MarketJdData> captor =
                ArgumentCaptor.forClass(com.example.matching.entity.evolution.MarketJdData.class);
        verify(marketJdDataMapper, times(2)).insert(captor.capture());
        assertEquals(captor.getAllValues().get(0).getTextHash(), captor.getAllValues().get(1).getTextHash());
    }

    @Test
    void importWithBatchReturnsTheCreatedMarketBatchReference() {
        MarketJdImportService.ImportBatchResult result =
                service.importFromTextListWithBatch(List.of("Java Engineer\nSpring Boot"), "BOSS");

        assertEquals(1, result.imported());
        assertFalse(result.batchNo().isBlank());
        ArgumentCaptor<MarketJdData> captor = ArgumentCaptor.forClass(MarketJdData.class);
        verify(marketJdDataMapper).insert(captor.capture());
        assertEquals(result.batchNo(), captor.getValue().getBatchNo());
    }

    @Test
    void confirmedPostBatchReusesVerifiedTagsWithoutReanalyzingJd() {
        when(marketJdDataMapper.selectCount(any())).thenReturn(0L);

        int imported = service.importVerifiedPostBatch(88L, List.of(
                new MarketJdImportService.VerifiedPostImportJd(
                        "Java工程师", "负责Java服务开发", 9L, List.of(20L, 10L, 20L))));

        assertEquals(1, imported);
        ArgumentCaptor<MarketJdData> captor = ArgumentCaptor.forClass(MarketJdData.class);
        verify(marketJdDataMapper).insert(captor.capture());
        assertEquals("POST_IMPORT_88", captor.getValue().getBatchNo());
        assertEquals("[10,20]", captor.getValue().getSkillTags());
        assertEquals(1, captor.getValue().getAnalysisStatus());
        org.mockito.Mockito.verifyNoInteractions(postCapabilityGenerationService, admissionService);
    }

    @Test
    void confirmedPostBatchAcceptsPostAbilitySampleWithoutTagIds() {
        when(marketJdDataMapper.selectCount(any())).thenReturn(0L);

        int imported = service.importVerifiedPostBatch(89L, List.of(
                new MarketJdImportService.VerifiedPostImportJd(
                        "Java工程师", "负责Java服务开发", 9L, List.of())));

        assertEquals(1, imported);
        verify(marketJdDataMapper).insert(any(MarketJdData.class));
    }

    @Test
    void deduplicateByBatchMarksRowsAlreadySeenInEarlierBatches() {
        MarketJdData current = new MarketJdData();
        current.setId(2L);
        current.setBatchNo("CURRENT");
        current.setTextHash("same-hash");
        current.setIsDuplicate(0);
        when(marketJdDataMapper.selectList(any())).thenReturn(List.of(current));
        when(marketJdDataMapper.selectCount(any())).thenReturn(1L);

        int duplicates = service.deduplicateByBatch("CURRENT");

        assertEquals(1, duplicates);
        assertEquals(1, current.getIsDuplicate());
        verify(marketJdDataMapper).updateById(current);
    }

    @Test
    void deduplicateByBatchMarksNearDuplicateTemplateRowsWithGroupId() {
        MarketJdData canonical = new MarketJdData();
        canonical.setId(1L);
        canonical.setBatchNo("CURRENT");
        canonical.setTextHash("hash-a");
        canonical.setIsDuplicate(0);
        canonical.setJobDescription("负责Java后端开发，熟悉Spring Boot、MySQL，本科及以上学历，3年以上工作经验");

        MarketJdData nearDup = new MarketJdData();
        nearDup.setId(2L);
        nearDup.setBatchNo("CURRENT");
        nearDup.setTextHash("hash-b");
        nearDup.setIsDuplicate(0);
        nearDup.setJobDescription("负责Java后端研发，熟悉Spring Boot与MySQL，本科及以上学历，3年以上工作经验");

        when(marketJdDataMapper.selectList(any())).thenReturn(List.of(canonical, nearDup));
        when(marketJdDataMapper.selectCount(any())).thenReturn(0L);

        int duplicates = service.deduplicateByBatch("CURRENT");

        assertEquals(1, duplicates);
        assertEquals(0, canonical.getIsDuplicate(), "规范文档不应被标记为重复");
        assertEquals(1, nearDup.getIsDuplicate());
        assertEquals(1L, nearDup.getCanonicalDocumentId());
        assertEquals("GROUP_1", nearDup.getSimilarityGroupId());
        verify(marketJdDataMapper, times(1)).updateById(nearDup);
    }

    // ==================== Task 6: 自动准入集成 ====================

    private MarketJdData analyzableJd() {
        MarketJdData jd = new MarketJdData();
        jd.setId(1L);
        jd.setBatchNo("B1");
        jd.setPostName("高级Java工程师");
        jd.setJobDescription("负责订单系统开发，精通Java并发编程");
        jd.setRequirements("本科及以上");
        jd.setCompanyDiversityKey("A公司");
        jd.setSourcePlatform("BOSS直聘");
        jd.setAnalysisStatus(0);
        jd.setIsDuplicate(0);
        return jd;
    }

    private void stubAnalyzeBatchBase(MarketJdData jd) {
        when(marketJdDataMapper.selectList(any())).thenReturn(List.of(jd));
        when(marketJdDataMapper.selectCount(any())).thenReturn(1L);

        PostCleaningResult cleaningResult = new PostCleaningResult();
        cleaningResult.setCleanedPostName("高级Java工程师");
        cleaningResult.setCleanedText("负责订单系统开发，精通Java并发编程，本科及以上");
        cleaningResult.setCleaningRecordId(88L);
        cleaningResult.setQualityScore(new BigDecimal("70"));
        cleaningResult.setBlocked(false);
        when(postDataCleaningService.cleanAndDetect(any())).thenReturn(cleaningResult);
    }

    private JdAbilityItemDTO matchedItem(String name, Long tagId, String evidence) {
        JdAbilityItemDTO item = new JdAbilityItemDTO();
        item.setSuggestedName(name);
        item.setMatchStatus("MATCHED");
        item.setMatchedTagId(tagId);
        item.setEvidenceText(evidence);
        item.setSourceRefs(List.of("source:MARKET_JD:1"));
        return item;
    }

    private MarketJdCapabilityAdmissionService.AdmissionPlan planWith(
            java.util.Map<Long, java.util.LinkedHashSet<Long>> accepted,
            java.util.Set<Long> infraFailed) {
        int autoCount = accepted.values().stream().mapToInt(java.util.Set::size).sum();
        return new MarketJdCapabilityAdmissionService.AdmissionPlan(
                accepted, java.util.Map.of(), List.of(), autoCount, 0, 0, 0, 0, 0, 0, infraFailed);
    }

    @Test
    void directEvidenceKnownTagsPersistWithoutHarness() {
        MarketJdData jd = analyzableJd();
        stubAnalyzeBatchBase(jd);
        when(postCapabilityGenerationService.analyzeMarketJdText(any(), any(), any(), any()))
                .thenReturn(List.of(
                        matchedItem("Java", 10L, "精通Java并发编程"),
                        matchedItem("MySQL", 20L, "负责订单系统开发")));

        java.util.LinkedHashSet<Long> accepted = new java.util.LinkedHashSet<>(List.of(10L, 20L));
        when(admissionService.admitBatch(any())).thenReturn(planWith(
                java.util.Map.of(1L, accepted), java.util.Set.of()));

        MarketJdImportService.BatchAnalysisResult result = service.analyzeBatch("B1");

        // 使用市场专用提取 API，不再调用 5 参（带 Harness）路径
        verify(postCapabilityGenerationService).analyzeMarketJdText(any(), any(), any(), any());
        org.mockito.Mockito.verify(postCapabilityGenerationService,
                org.mockito.Mockito.never()).analyzePostText(any(), any(), any(), any(), any());
        // admitBatch 恰好一次（不 per-JD 调 Harness）
        verify(admissionService).admitBatch(any());

        ArgumentCaptor<MarketJdData> captured = ArgumentCaptor.forClass(MarketJdData.class);
        verify(marketJdDataMapper, atLeastOnce()).updateById(captured.capture());
        MarketJdData saved = captured.getAllValues().get(captured.getAllValues().size() - 1);
        assertEquals("[10,20]", saved.getSkillTags()); // 排序去重 JSON
        assertEquals(1, saved.getAnalysisStatus());
        assertEquals(2, result.getAutoAdmittedCount()); // 计数从计划透传
    }

    @Test
    void semanticTagPersistsOnlyAfterBatchPlan() {
        MarketJdData jd = analyzableJd();
        stubAnalyzeBatchBase(jd);
        when(postCapabilityGenerationService.analyzeMarketJdText(any(), any(), any(), any()))
                .thenReturn(List.of(matchedItem("Java", 10L, "负责订单系统开发")));

        // 语义 defer 由 Harness PASS 后写入 plan
        java.util.LinkedHashSet<Long> accepted = new java.util.LinkedHashSet<>(List.of(10L));
        when(admissionService.admitBatch(any())).thenReturn(planWith(
                java.util.Map.of(1L, accepted), java.util.Set.of()));

        service.analyzeBatch("B1");

        ArgumentCaptor<MarketJdData> captured = ArgumentCaptor.forClass(MarketJdData.class);
        verify(marketJdDataMapper, atLeastOnce()).updateById(captured.capture());
        assertEquals("[10]", captured.getAllValues().get(captured.getAllValues().size() - 1).getSkillTags());
    }

    @Test
    void blockedPlanPersistsEmptyJson() {
        MarketJdData jd = analyzableJd();
        stubAnalyzeBatchBase(jd);
        when(postCapabilityGenerationService.analyzeMarketJdText(any(), any(), any(), any()))
                .thenReturn(List.of(matchedItem("Java", 10L, "负责订单系统开发")));

        when(admissionService.admitBatch(any())).thenReturn(planWith(
                java.util.Map.of(), java.util.Set.of()));

        service.analyzeBatch("B1");

        ArgumentCaptor<MarketJdData> captured = ArgumentCaptor.forClass(MarketJdData.class);
        verify(marketJdDataMapper, atLeastOnce()).updateById(captured.capture());
        MarketJdData saved = captured.getAllValues().get(captured.getAllValues().size() - 1);
        assertEquals("[]", saved.getSkillTags());
        assertEquals(1, saved.getAnalysisStatus()); // 决策完成（含空集合）
    }

    @Test
    void infraFailureLeavesAnalysisStatusZeroForRetry() {
        MarketJdData jd = analyzableJd();
        stubAnalyzeBatchBase(jd);
        when(postCapabilityGenerationService.analyzeMarketJdText(any(), any(), any(), any()))
                .thenReturn(List.of(matchedItem("Java", 10L, "负责订单系统开发")));
        when(admissionService.admitBatch(any())).thenThrow(new RuntimeException("Harness timeout"));

        MarketJdImportService.BatchAnalysisResult result = service.analyzeBatch("B1");

        // 治理阶段可更新质量字段；AI 准入失败时最终分析状态仍保持 0，可重试。
        ArgumentCaptor<MarketJdData> captured = ArgumentCaptor.forClass(MarketJdData.class);
        verify(marketJdDataMapper, atLeastOnce()).updateById(captured.capture());
        assertEquals(0, captured.getAllValues().get(captured.getAllValues().size() - 1).getAnalysisStatus());
        assertEquals(1, result.getExtractedFailed());
        assertFalse(result.getErrors().isEmpty());
    }

    @Test
    void reprocessingSameBatchReplacesSkillTagsIdempotently() {
        // 幂等：同一 JD 已 status=1 时重跑不再被选中（不会追加重复 ID）
        when(marketJdDataMapper.selectList(any())).thenReturn(List.of());
        when(marketJdDataMapper.selectCount(any())).thenReturn(1L);

        MarketJdImportService.BatchAnalysisResult result = service.analyzeBatch("B1");

        assertEquals(0, result.getGovernedCount());
        org.mockito.Mockito.verify(admissionService, org.mockito.Mockito.never()).admitBatch(any());
    }

    // ==================== 批次来源通道与删除 ====================
    //
    // 用户诉求：批次列表要能分清「爬虫推来的」和「自己手动上传的」，且两类批次都能删除。
    // 这两个能力共同依赖同一件事——人工导入也写进 market_jd_crawler_batch_log，
    // 否则人工批次在列表里根本不可见，既无法分类也无法删除。

    @Test
    void manualTextImportMarksChannelAndRegistersBatch() {
        MarketJdImportService.ImportBatchResult result =
                service.importFromTextListWithBatch(List.of("Java工程师\nSpring Boot", "   "), "BOSS");

        ArgumentCaptor<MarketJdData> dataCaptor = ArgumentCaptor.forClass(MarketJdData.class);
        verify(marketJdDataMapper).insert(dataCaptor.capture());
        assertEquals(MarketJdData.CHANNEL_MANUAL_UPLOAD, dataCaptor.getValue().getIngestChannel());

        ArgumentCaptor<MarketJdCrawlerBatchLog> logCaptor =
                ArgumentCaptor.forClass(MarketJdCrawlerBatchLog.class);
        verify(batchLogService).record(logCaptor.capture());
        MarketJdCrawlerBatchLog batchLog = logCaptor.getValue();
        assertEquals(result.batchNo(), batchLog.getBatchNo());
        assertEquals(MarketJdData.CHANNEL_MANUAL_UPLOAD, batchLog.getIngestChannel());
        assertEquals("BOSS", batchLog.getSourcePlatform());
        assertEquals(2, batchLog.getItemCount());
        assertEquals(1, batchLog.getImported());
        // 空文本被跳过必须计入 failed，否则「入库 1 / 请求 2」会永远对不上
        assertEquals(1, batchLog.getFailed());
        assertEquals(MarketJdCrawlerBatchLog.RESULT_PARTIAL_FAILED, batchLog.getResultStatus());
        assertEquals(MarketJdCrawlerBatchLog.ANALYSIS_NOT_TRIGGERED, batchLog.getAnalysisState());
    }

    @Test
    void excelImportUsesDataPlatformAndManualChannel() {
        MarketJdData row = new MarketJdData();
        row.setJobDescription("负责Java服务开发");
        row.setSourcePlatform("ZHILIAN");

        service.importFromExcelData(List.of(row));

        ArgumentCaptor<MarketJdData> dataCaptor = ArgumentCaptor.forClass(MarketJdData.class);
        verify(marketJdDataMapper).insert(dataCaptor.capture());
        assertEquals(MarketJdData.CHANNEL_MANUAL_UPLOAD, dataCaptor.getValue().getIngestChannel());

        ArgumentCaptor<MarketJdCrawlerBatchLog> logCaptor =
                ArgumentCaptor.forClass(MarketJdCrawlerBatchLog.class);
        verify(batchLogService).record(logCaptor.capture());
        assertEquals("ZHILIAN", logCaptor.getValue().getSourcePlatform());
        assertEquals(0, logCaptor.getValue().getFailed());
        assertEquals(MarketJdCrawlerBatchLog.RESULT_OK, logCaptor.getValue().getResultStatus());
    }

    @Test
    void confirmedPostBatchRegistersPostImportChannelAndSkipsReanalysis() {
        when(marketJdDataMapper.selectCount(any())).thenReturn(0L);

        service.importVerifiedPostBatch(88L, List.of(
                new MarketJdImportService.VerifiedPostImportJd(
                        "Java工程师", "负责Java服务开发", 9L, List.of())));

        ArgumentCaptor<MarketJdData> dataCaptor = ArgumentCaptor.forClass(MarketJdData.class);
        verify(marketJdDataMapper).insert(dataCaptor.capture());
        assertEquals(MarketJdData.CHANNEL_POST_IMPORT, dataCaptor.getValue().getIngestChannel());

        ArgumentCaptor<MarketJdCrawlerBatchLog> logCaptor =
                ArgumentCaptor.forClass(MarketJdCrawlerBatchLog.class);
        verify(batchLogService).record(logCaptor.capture());
        assertEquals("POST_IMPORT_88", logCaptor.getValue().getBatchNo());
        assertEquals(MarketJdData.CHANNEL_POST_IMPORT, logCaptor.getValue().getIngestChannel());
        assertEquals(MarketJdCrawlerBatchLog.ANALYSIS_SKIPPED, logCaptor.getValue().getAnalysisState());
    }

    @Test
    void deleteBatchRemovesRowsVersionSnapshotsAndBatchLog() {
        MarketJdData row = new MarketJdData();
        row.setId(11L);
        row.setBatchNo("crawler-20260904-001");
        when(marketJdDataMapper.selectList(any())).thenReturn(List.of(row));
        when(marketJdDataVersionMapper.delete(any())).thenReturn(3);
        when(marketJdDataMapper.delete(any())).thenReturn(1);
        when(batchLogService.deleteByBatchNo("crawler-20260904-001")).thenReturn(1);

        MarketJdImportService.BatchDeleteResult result = service.deleteBatch("crawler-20260904-001");

        assertEquals(1, result.marketJdRows());
        assertEquals(3, result.versionSnapshots());
        assertEquals(1, result.batchLogs());
        // 悬空去重引用必须被清空，否则前端会展示一个点不开的分组
        verify(marketJdDataMapper).update(any(), any());
        verify(batchLogService).deleteByBatchNo("crawler-20260904-001");
    }

    @Test
    void deleteBatchRejectsBlankBatchNo() {
        assertThrows(BusinessException.class, () -> service.deleteBatch("   "));
        verify(marketJdDataMapper, never()).delete(any());
    }

    @Test
    void deleteBatchThrowsNotFoundWhenBatchDoesNotExist() {
        when(marketJdDataMapper.selectList(any())).thenReturn(List.of());
        when(batchLogService.countByBatchNo("missing-batch")).thenReturn(0L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.deleteBatch("missing-batch"));

        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("missing-batch"));
        verify(marketJdDataMapper, never()).delete(any());
        verify(marketJdDataVersionMapper, never()).delete(any());
    }

    // ---------- JD 级治理：单条 / 批量删除 ----------

    private MarketJdData jdRow(long id, String batchNo, String postName) {
        MarketJdData row = new MarketJdData();
        row.setId(id);
        row.setBatchNo(batchNo);
        row.setPostName(postName);
        return row;
    }

    @Test
    void deleteJdRemovesRowAndVersionSnapshotsAndClearsDanglingReferences() {
        when(marketJdDataMapper.selectById(11L)).thenReturn(jdRow(11L, "B1", "Java 工程师"));
        when(marketJdDataVersionMapper.delete(any())).thenReturn(2);
        when(marketJdDataMapper.deleteById(11L)).thenReturn(1);

        MarketJdImportService.SingleDeleteResult result = service.deleteJd(11L);

        assertEquals(11L, result.id());
        assertEquals(1, result.marketJdRows());
        assertEquals(2, result.versionSnapshots());
        // 单条删除必须与批次删除共用同一套级联清理，否则会留下点不开的去重分组
        verify(marketJdDataMapper).update(any(), any());
        verify(marketJdDataMapper).deleteById(11L);
    }

    @Test
    void deleteJdThrowsNotFoundAndTouchesNothingWhenRowMissing() {
        when(marketJdDataMapper.selectById(99L)).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class, () -> service.deleteJd(99L));

        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("99"));
        // 先判定再删除：不存在的 id 不能顺手清掉别处的引用
        verify(marketJdDataMapper, never()).deleteById(any());
        verify(marketJdDataVersionMapper, never()).delete(any());
    }

    @Test
    void deleteJdRejectsNullId() {
        assertThrows(BusinessException.class, () -> service.deleteJd(null));
        verify(marketJdDataMapper, never()).deleteById(any());
    }

    @Test
    void deleteJdsDeletesOnlyExistingRowsAndReportsMissingIds() {
        when(marketJdDataMapper.selectList(any())).thenReturn(List.of(jdRow(1L, "B1", "A"), jdRow(2L, "B1", "B")));
        when(marketJdDataVersionMapper.delete(any())).thenReturn(4);
        when(marketJdDataMapper.delete(any())).thenReturn(2);

        MarketJdImportService.BatchDeleteByIdsResult result = service.deleteJds(List.of(1L, 2L, 3L));

        assertEquals(3, result.requested());
        assertEquals(2, result.marketJdRows());
        assertEquals(4, result.versionSnapshots());
        // 3 号不存在，必须报给前端，不能假装全部成功
        assertEquals(List.of(3L), result.missingIds());
    }

    @Test
    void deleteJdsDeduplicatesRepeatedIds() {
        when(marketJdDataMapper.selectList(any())).thenReturn(List.of(jdRow(5L, "B1", "A")));
        when(marketJdDataVersionMapper.delete(any())).thenReturn(1);
        when(marketJdDataMapper.delete(any())).thenReturn(1);

        MarketJdImportService.BatchDeleteByIdsResult result = service.deleteJds(List.of(5L, 5L, 5L));

        // 「全选当前页」可能混入重复行，重复 id 只能算一条
        assertEquals(1, result.requested());
        assertEquals(1, result.marketJdRows());
        assertEquals(List.of(), result.missingIds());
    }

    @Test
    void deleteJdsReturnsZeroWithoutTouchingDbWhenNoneExist() {
        when(marketJdDataMapper.selectList(any())).thenReturn(List.of());

        MarketJdImportService.BatchDeleteByIdsResult result = service.deleteJds(List.of(7L, 8L));

        assertEquals(0, result.marketJdRows());
        assertEquals(List.of(7L, 8L), result.missingIds());
        verify(marketJdDataMapper, never()).delete(any());
        verify(marketJdDataVersionMapper, never()).delete(any());
    }

    @Test
    void deleteJdsRejectsEmptySelection() {
        assertThrows(BusinessException.class, () -> service.deleteJds(List.of()));
        assertThrows(BusinessException.class, () -> service.deleteJds(null));
        verify(marketJdDataMapper, never()).delete(any());
    }
}
