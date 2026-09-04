package com.example.matching.service.evolution;

import com.example.matching.config.MarketJdCapabilityAdmissionProperties;
import com.example.matching.mapper.evolution.MarketJdDataMapper;
import com.example.matching.mapper.post.PostPostMapper;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class MarketJdImportServiceImplTest {

    private final MarketJdDataMapper marketJdDataMapper = mock(MarketJdDataMapper.class);
    private final PostPostMapper postPostMapper = mock(PostPostMapper.class);
    private final PostCapabilityGenerationService postCapabilityGenerationService =
            mock(PostCapabilityGenerationService.class);
    private final PostDataCleaningService postDataCleaningService = mock(PostDataCleaningService.class);
    private final MarketJdCapabilityAdmissionService admissionService =
            mock(MarketJdCapabilityAdmissionService.class);

    private final MarketJdImportServiceImpl service = new MarketJdImportServiceImpl(
            marketJdDataMapper,
            postPostMapper,
            postCapabilityGenerationService,
            postDataCleaningService,
            new ObjectMapper(),
            admissionService,
            new MarketJdCapabilityAdmissionProperties());

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
}
