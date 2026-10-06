package com.example.matching.service.closure.impl;

import com.example.matching.dto.closure.ComprehensiveDiagnosisFactDTO;
import com.example.matching.dto.learning.LearningPathItemDTO;
import com.example.matching.dto.matching.MatchingReportDTO;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.port.post.PostQueryPort;
import com.example.matching.port.tag.TagQueryPort;
import com.example.matching.port.talent.TalentQueryPort;
import com.example.matching.service.learning.LearningPathService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * {@link ComprehensiveDiagnosisFactBuilder} 单元测试。
 *
 * <p>覆盖 7 个维度的构建分支：正常解析、空/非法 JSON 降级、缺依赖时的直接比对降级、
 * 证据风险分类（弱来源/单一来源/低可信度）以及学习资源回填与异常吞没。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ComprehensiveDiagnosisFactBuilderTest {

    @Mock private TalentQueryPort talentQueryPort;
    @Mock private PostQueryPort postQueryPort;
    @Mock private TagQueryPort tagQueryPort;
    @Mock private LearningPathService learningPathService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ComprehensiveDiagnosisFactBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new ComprehensiveDiagnosisFactBuilder(
                talentQueryPort, postQueryPort, tagQueryPort, learningPathService, objectMapper);
    }

    private MatchingRecord baseRecord() {
        MatchingRecord r = new MatchingRecord();
        r.setId(1L);
        r.setEmpId(100L);
        r.setPostId(200L);
        r.setFinalMatchScore(new BigDecimal("88.00"));
        r.setAiMatchScore(new BigDecimal("80.00"));
        r.setL2Score(new BigDecimal("70.00"));
        r.setVectorScore(new BigDecimal("0.90"));
        r.setEvidenceScore(new BigDecimal("60.00"));
        r.setLlmScore(new BigDecimal("75.00"));
        r.setModelQualityCoefficient(new BigDecimal("0.95"));
        r.setFeedbackCalibration(new BigDecimal("2.00"));
        r.setScreeningLevel(1);
        r.setMatchStatus(1);
        r.setProfileSemanticScore(new BigDecimal("0.85"));
        r.setApprovalStatus(1);
        r.setManualRemark("ok");
        return r;
    }

    private MatchingReportDTO.AbilityDetail gapDetail(String name, boolean weak, List<MatchingReportDTO.EvidenceItem> evs) {
        MatchingReportDTO.AbilityDetail d = new MatchingReportDTO.AbilityDetail();
        d.setTagId(5L);
        d.setTagName(name);
        d.setRequiredLevel(4);
        d.setActualLevel(new BigDecimal("2"));
        d.setMatchCoefficient(new BigDecimal("0.5"));
        d.setSimilarityScore(new BigDecimal("0.6"));
        d.setPassed(false);
        d.setWeakEvidence(weak);
        d.setIsCore(1);
        d.setIsRequired(1);
        d.setEvidences(evs);
        return d;
    }

    private MatchingReportDTO.EvidenceItem evidence(String source, String cred) {
        MatchingReportDTO.EvidenceItem e = new MatchingReportDTO.EvidenceItem();
        e.setSource(source);
        e.setLevel(2);
        e.setCredibility(new BigDecimal(cred));
        e.setTimeFactor(new BigDecimal("0.9"));
        return e;
    }

    // ==================== buildFactPackage 主链路 ====================

    @Test
    @DisplayName("buildFactPackage：员工/岗位存在 + 完整报告 → 组装全部维度")
    void buildFactPackage_full() throws Exception {
        MatchingRecord r = baseRecord();
        when(talentQueryPort.getEmployeeById(100L))
                .thenReturn(new TalentQueryPort.EmployeeDTO(100L, "张三", "E1", 1, "P5", 1L, 200L, 1));
        when(postQueryPort.getPostById(200L))
                .thenReturn(new PostQueryPort.PostDTO(200L, "Java工程师", "JC", "P5", 1L, 1, "岗位描述"));

        MatchingReportDTO report = new MatchingReportDTO();
        report.setFeedbackAdjustment(new BigDecimal("3.00"));
        report.setAbilityDetails(List.of(gapDetail("Kafka", false, List.of(evidence("PMS", "0.4")))));
        r.setQuantitativeReport(objectMapper.writeValueAsString(report));

        r.setHardConditionResult("{\"details\":[{\"field\":\"degree\",\"label\":\"学历\",\"operator\":\"gte\"," +
                "\"expectedValue\":\"本科\",\"actualValue\":\"硕士\",\"passed\":true,\"source\":\"resume\"}]}");
        r.setFeedbackReasons("[\"薪资不符\",\"地点偏远\"]");

        when(talentQueryPort.listAbilitiesByEmpId(100L)).thenReturn(List.of(
                new TalentQueryPort.EmployeeAbilityDTO(1L, 100L, 5L, 3, "PMS", BigDecimal.ONE, null, null, "Kafka")));
        when(tagQueryPort.batchGetTags(anyList())).thenReturn(List.of(
                new TagQueryPort.TagDTO(5L, "Kafka", "kafka", "SKILL", null, 3, null, null, "SYS", null, null, null)));
        when(learningPathService.generateLearningPath(any())).thenReturn(List.of(learningItem("Kafka")));

        ComprehensiveDiagnosisFactDTO fact = builder.buildFactPackage(r);

        assertEquals(1L, fact.getRecordId());
        assertEquals("张三", fact.getEmpName());
        assertEquals("Java工程师", fact.getPostName());
        assertEquals("P5", fact.getPostLevel());
        // 报告里的 feedbackAdjustment 覆盖记录上的
        assertEquals(new BigDecimal("3.00"), fact.getScores().getFeedbackAdjustment());
        assertEquals(1, fact.getHardConditions().size());
        assertTrue(fact.getHardConditions().get(0).isPassed());
        assertEquals(1, fact.getAbilityGaps().size());
        assertFalse(fact.getAbilityGaps().get(0).getEvidenceSources().isEmpty());
        // 单来源证据 → SINGLE_SOURCE 风险
        assertEquals("SINGLE_SOURCE", fact.getEvidenceRisks().get(0).getRiskType());
        assertEquals(2, fact.getFeedbackSignals().getFeedbackReasons().size());
        assertEquals(1, fact.getAvailableLearningResources().size());
        assertNotNull(fact.getSemanticSignals().getEmployeeProfileSummary());
        assertEquals("岗位描述", fact.getSemanticSignals().getPostDescriptionSummary());
    }

    private LearningPathItemDTO learningItem(String ability) {
        LearningPathItemDTO item = new LearningPathItemDTO();
        item.setAbilityName(ability);
        item.setTitle("Kafka 入门");
        item.setResourceType("COURSE");
        item.setDifficultyLevel(3);
        item.setUrl("http://x");
        return item;
    }

    @Test
    @DisplayName("buildFactPackage：empId/postId 为空 → 跳过关联查询，相关摘要为 null")
    void buildFactPackage_nullIds() {
        MatchingRecord r = new MatchingRecord();
        r.setId(2L);
        r.setVectorScore(null);

        ComprehensiveDiagnosisFactDTO fact = builder.buildFactPackage(r);

        assertNull(fact.getEmpName());
        assertNull(fact.getPostName());
        assertFalse(fact.getSemanticSignals().isVectorAvailable());
        assertTrue(fact.getAbilityGaps().isEmpty());
        assertTrue(fact.getEvidenceRisks().isEmpty());
    }

    @Test
    @DisplayName("buildFactPackage：quantitativeReport 非法 JSON → 降级不抛异常")
    void buildFactPackage_badJson() {
        MatchingRecord r = baseRecord();
        r.setQuantitativeReport("{not-json");
        r.setHardConditionResult("also-bad");
        r.setFeedbackReasons("nope");
        when(talentQueryPort.listAbilitiesByEmpId(100L)).thenReturn(List.of());
        when(postQueryPort.listRequirementsByPostId(200L)).thenReturn(List.of());

        ComprehensiveDiagnosisFactDTO fact = builder.buildFactPackage(r);

        assertNotNull(fact);
        assertTrue(fact.getAbilityGaps().isEmpty());
        assertTrue(fact.getHardConditions().isEmpty());
        assertTrue(fact.getFeedbackSignals().getFeedbackReasons().isEmpty());
    }

    @Test
    @DisplayName("buildFactPackage：无报告 → 降级为直接比对岗位要求与员工能力")
    void buildFactPackage_directCompareFallback() {
        MatchingRecord r = baseRecord();
        when(talentQueryPort.listAbilitiesByEmpId(100L)).thenReturn(List.of(
                new TalentQueryPort.EmployeeAbilityDTO(1L, 100L, 5L, 1, "PMS", BigDecimal.ONE, null, null, "Kafka")));
        when(postQueryPort.listRequirementsByPostId(200L)).thenReturn(List.of(
                new PostQueryPort.PostAbilityDTO(1L, 200L, 5L, 4, BigDecimal.ONE, 1, 1, "v1", null, "Kafka"),
                new PostQueryPort.PostAbilityDTO(2L, 200L, 6L, 2, BigDecimal.ONE, 0, 0, "v1", null, "Redis")));
        when(tagQueryPort.batchGetTags(anyList())).thenReturn(List.of());

        ComprehensiveDiagnosisFactDTO fact = builder.buildFactPackage(r);

        // Kafka 有差距（1 < 4）；Redis 无员工能力（0 < 2，弱证据）
        assertEquals(2, fact.getAbilityGaps().size());
        assertTrue(fact.getAbilityGaps().stream().anyMatch(g -> g.isWeakEvidence()));
        // 无证据来源的弱证据能力被标记 WEAK_SOURCE 风险
        assertTrue(fact.getEvidenceRisks().size() >= 1);
    }

    @Test
    @DisplayName("buildFactPackage：岗位无要求 → 能力差距为空")
    void buildFactPackage_emptyRequirements() {
        MatchingRecord r = baseRecord();
        when(talentQueryPort.listAbilitiesByEmpId(100L)).thenReturn(List.of());
        when(postQueryPort.listRequirementsByPostId(200L)).thenReturn(List.of());

        ComprehensiveDiagnosisFactDTO fact = builder.buildFactPackage(r);
        assertTrue(fact.getAbilityGaps().isEmpty());
    }

    @Test
    @DisplayName("buildFactPackage：学习资源服务抛异常 → 吞没返回空资源")
    void buildFactPackage_learningServiceThrows() {
        MatchingRecord r = baseRecord();
        when(talentQueryPort.listAbilitiesByEmpId(100L)).thenReturn(List.of());
        when(postQueryPort.listRequirementsByPostId(200L)).thenReturn(List.of(
                new PostQueryPort.PostAbilityDTO(1L, 200L, 5L, 4, BigDecimal.ONE, 1, 1, "v1", null, "Kafka")));
        when(tagQueryPort.batchGetTags(anyList())).thenReturn(List.of());
        when(learningPathService.generateLearningPath(any())).thenThrow(new RuntimeException("no resource"));

        ComprehensiveDiagnosisFactDTO fact = builder.buildFactPackage(r);

        assertEquals(1, fact.getAbilityGaps().size());
        assertTrue(fact.getAvailableLearningResources().isEmpty());
    }

    // ==================== 证据风险分类 ====================

    @Test
    @DisplayName("证据风险：有多来源且含低可信度(<0.5) → LOW_CREDIBILITY")
    void evidenceRisk_lowCredibility() throws Exception {
        MatchingRecord r = baseRecord();
        when(talentQueryPort.listAbilitiesByEmpId(anyLong())).thenReturn(List.of());
        when(tagQueryPort.batchGetTags(anyList())).thenReturn(List.of());
        MatchingReportDTO report = new MatchingReportDTO();
        report.setAbilityDetails(List.of(gapDetail("Kafka", false,
                List.of(evidence("PMS", "0.9"), evidence("SELF", "0.3")))));
        r.setQuantitativeReport(objectMapper.writeValueAsString(report));

        ComprehensiveDiagnosisFactDTO fact = builder.buildFactPackage(r);

        assertEquals("LOW_CREDIBILITY", fact.getEvidenceRisks().get(0).getRiskType());
    }

    @Test
    @DisplayName("证据风险：弱证据能力 → WEAK_SOURCE")
    void evidenceRisk_weakSource() throws Exception {
        MatchingRecord r = baseRecord();
        when(talentQueryPort.listAbilitiesByEmpId(anyLong())).thenReturn(List.of());
        when(tagQueryPort.batchGetTags(anyList())).thenReturn(List.of());
        MatchingReportDTO report = new MatchingReportDTO();
        report.setAbilityDetails(List.of(gapDetail("Kafka", true, List.of())));
        r.setQuantitativeReport(objectMapper.writeValueAsString(report));

        ComprehensiveDiagnosisFactDTO fact = builder.buildFactPackage(r);

        assertEquals("WEAK_SOURCE", fact.getEvidenceRisks().get(0).getRiskType());
        assertEquals("Kafka 缺少可靠证据支撑", fact.getEvidenceRisks().get(0).getDescription());
    }

    @Test
    @DisplayName("证据风险：报告已覆盖的能力不重复添加风险")
    void evidenceRisk_noDuplicate() throws Exception {
        MatchingRecord r = baseRecord();
        when(talentQueryPort.listAbilitiesByEmpId(anyLong())).thenReturn(List.of());
        when(tagQueryPort.batchGetTags(anyList())).thenReturn(List.of());
        MatchingReportDTO report = new MatchingReportDTO();
        report.setAbilityDetails(List.of(gapDetail("Kafka", true, List.of())));
        r.setQuantitativeReport(objectMapper.writeValueAsString(report));

        ComprehensiveDiagnosisFactDTO fact = builder.buildFactPackage(r);

        assertEquals(1, fact.getEvidenceRisks().size());
    }

    // ==================== 摘要 / 截断 ====================

    @Test
    @DisplayName("语义信号：岗位描述超 500 字 → 截断加省略号")
    void semanticSignal_truncatesLongPostDesc() {
        MatchingRecord r = baseRecord();
        String longDesc = "x".repeat(600);
        when(postQueryPort.getPostById(200L))
                .thenReturn(new PostQueryPort.PostDTO(200L, "Java工程师", "JC", "P5", 1L, 1, longDesc));
        when(talentQueryPort.listAbilitiesByEmpId(100L)).thenReturn(List.of());
        when(postQueryPort.listRequirementsByPostId(200L)).thenReturn(List.of());

        ComprehensiveDiagnosisFactDTO fact = builder.buildFactPackage(r);

        String summary = fact.getSemanticSignals().getPostDescriptionSummary();
        assertTrue(summary.endsWith("..."));
        assertEquals(503, summary.length());
    }

    @Test
    @DisplayName("员工摘要：能力名为空时回退到标签名；标签名也为空则跳过")
    void employeeSummary_fallbackToTagName() {
        MatchingRecord r = baseRecord();
        when(talentQueryPort.getEmployeeById(100L))
                .thenReturn(new TalentQueryPort.EmployeeDTO(100L, "张三", "E1", 1, "P5", 1L, 200L, 1));
        when(talentQueryPort.listAbilitiesByEmpId(100L)).thenReturn(List.of(
                new TalentQueryPort.EmployeeAbilityDTO(1L, 100L, 5L, 3, "PMS", BigDecimal.ONE, null, null, null),
                new TalentQueryPort.EmployeeAbilityDTO(2L, 100L, 6L, 3, "PMS", BigDecimal.ONE, null, null, "  ")));
        when(tagQueryPort.batchGetTags(anyList())).thenReturn(List.of(
                new TagQueryPort.TagDTO(5L, "Kafka", "kafka", "SKILL", null, 3, null, null, "SYS", null, null, null),
                new TagQueryPort.TagDTO(6L, null, "x", "SKILL", null, 3, null, null, "SYS", null, null, null)));
        when(postQueryPort.listRequirementsByPostId(200L)).thenReturn(List.of());

        ComprehensiveDiagnosisFactDTO fact = builder.buildFactPackage(r);

        String summary = fact.getSemanticSignals().getEmployeeProfileSummary();
        assertTrue(summary.contains("Kafka"));
    }

    @Test
    @DisplayName("能力差距原因：弱证据 → 「证据薄弱」；否则「未达到岗位要求」")
    void gapReason_branches() throws Exception {
        MatchingRecord weak = baseRecord();
        when(talentQueryPort.listAbilitiesByEmpId(anyLong())).thenReturn(List.of());
        when(tagQueryPort.batchGetTags(anyList())).thenReturn(List.of());
        MatchingReportDTO report = new MatchingReportDTO();
        report.setAbilityDetails(List.of(gapDetail("Kafka", true, List.of())));
        weak.setQuantitativeReport(objectMapper.writeValueAsString(report));
        assertEquals("证据薄弱，需要补充确认", builder.buildFactPackage(weak).getAbilityGaps().get(0).getReason());

        MatchingRecord normal = baseRecord();
        MatchingReportDTO report2 = new MatchingReportDTO();
        MatchingReportDTO.AbilityDetail d = gapDetail("Kafka", false, List.of());
        d.setPassedDesc("等级偏低");
        report2.setAbilityDetails(List.of(d));
        normal.setQuantitativeReport(objectMapper.writeValueAsString(report2));
        assertEquals("等级偏低", builder.buildFactPackage(normal).getAbilityGaps().get(0).getReason());
    }

    // ==================== buildEmptyFactPackage ====================

    @Test
    @DisplayName("buildEmptyFactPackage：返回仅含 recordId 与空分数快照的包")
    void buildEmptyFactPackage_ok() {
        ComprehensiveDiagnosisFactDTO fact = builder.buildEmptyFactPackage(99L);

        assertEquals(99L, fact.getRecordId());
        assertNotNull(fact.getScores());
        assertNull(fact.getScores().getFinalMatchScore());
    }
}
