package com.example.matching.service.assessment;

import com.example.matching.agent.dto.ComprehensiveAssessmentReportResult;
import com.example.matching.agent.service.ComprehensiveAssessmentReportService;
import com.example.matching.common.enums.DecisionStatusEnum;
import com.example.matching.common.enums.EvidenceStatusEnum;
import com.example.matching.common.enums.WorkflowStatusEnum;
import com.example.matching.dto.assessment.report.ComprehensiveAssessmentContext;
import com.example.matching.dto.assessment.report.ComprehensiveAssessmentReportDetail;
import com.example.matching.dto.interview.CompetencyReport;
import com.example.matching.entity.workflow.PersonAbilityLevelDecision;
import com.example.matching.entity.workflow.PersonCapabilityWorkflow;
import com.example.matching.port.assessment.AssessmentReportPort;
import com.example.matching.service.assessment.impl.AssessmentReportServiceImpl;
import com.example.matching.service.assessment.report.ComprehensiveAssessmentContextAssembler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

class AssessmentReportServiceImplTest {

    private final AssessmentReportPort port = mock(AssessmentReportPort.class);
    private final CapabilityAssessmentWorkflowService workflowService = mock(CapabilityAssessmentWorkflowService.class);
    private final AggregateAbilityHarnessService harnessService = mock(AggregateAbilityHarnessService.class);
    private final AbilityLevelConfirmationService levelService = mock(AbilityLevelConfirmationService.class);
    private final ComprehensiveAssessmentContextAssembler assembler =
            mock(ComprehensiveAssessmentContextAssembler.class);
    private final ComprehensiveAssessmentReportService insightService =
            mock(ComprehensiveAssessmentReportService.class);
    private final AssessmentReportService service = new AssessmentReportServiceImpl(
            port, workflowService, harnessService, levelService, new ObjectMapper(), assembler, insightService);

    private CompetencyReport sampleReport() {
        return new CompetencyReport(
                1L, 100L, 200L, 78, 70,
                List.of(), List.of(), List.of("强项"), List.of("弱项"), List.of(),
                List.of("建议"), List.of(), "结论", "建议文案", false, null);
    }

    /** 一条能力等级决策（综合评分的唯一计分来源） */
    private PersonAbilityLevelDecision decision(Long workflowId, Integer finalLevel, DecisionStatusEnum status) {
        PersonAbilityLevelDecision d = new PersonAbilityLevelDecision();
        d.setWorkflowId(workflowId);
        d.setEmpId(100L);
        d.setFinalLevel(finalLevel);
        d.setDecisionStatus(status.getCode());
        return d;
    }

    private PersonCapabilityWorkflow completedWorkflow() {        PersonCapabilityWorkflow w = new PersonCapabilityWorkflow();
        w.setId(1L);
        w.setEmpId(100L);
        w.setPostId(200L);
        w.setStatus(WorkflowStatusEnum.COMPLETED.getCode());
        w.setCompletedAt(LocalDateTime.now());
        return w;
    }

    /** 一份「报告主体已生成」的库中洞察行（洞察尚未生成） */
    private AssessmentReportPort.InsightDTO emptyInsightRow() {
        return new AssessmentReportPort.InsightDTO(null, null, null, null, null, null,
                null, null, null, null, null);
    }

    /** 一份已落库的洞察行，指纹固定 */
    private AssessmentReportPort.InsightDTO insightRow(String fingerprint) {
        return new AssessmentReportPort.InsightDTO("[]", "[]", "[]", "[]", "[]", "结论",
                ComprehensiveAssessmentReportService.SOURCE_AI, null, 80,
                LocalDateTime.now(), fingerprint);
    }

    private ComprehensiveAssessmentReportResult aiResult() {
        ComprehensiveAssessmentReportResult r = new ComprehensiveAssessmentReportResult();
        r.setInsightSource(ComprehensiveAssessmentReportService.SOURCE_AI);
        r.setConclusion("综合结论");
        r.setStrengths(List.of("强项 A"));
        r.setOverallConfidence(new BigDecimal("88"));
        return r;
    }

    /** 内容齐全的报告行（聚合审核结论 / 最终能力等级 / AI 面试结论都在，洞察另查） */
    private AssessmentReportPort.ReportDTO completeReport(Long workflowId) {
        return new AssessmentReportPort.ReportDTO(
                workflowId, 100L, 200L, 9L, "READY", null, null,
                "[]", "[]", "{}", "[]", "[]", "结论", "建议");
    }

    /** 内容仍在生成的报告行：报告主体已落库（READY），但最终能力等级还没回填 */
    private AssessmentReportPort.ReportDTO generatingReport(Long workflowId) {
        return new AssessmentReportPort.ReportDTO(
                workflowId, 100L, 200L, 9L, "READY", null, null,
                "[]", "[]", "{}", "[]", null, "结论", "建议");
    }

    private void stubReadyToGenerate() {
        when(workflowService.getWorkflow(1L)).thenReturn(completedWorkflow());
        when(port.listClaimGroups(1L)).thenReturn(List.of());
        when(assembler.assemble(100L)).thenReturn(new ComprehensiveAssessmentContext());
        when(port.findInsights(1L)).thenReturn(emptyInsightRow());
        when(insightService.generateInsights(any())).thenReturn(aiResult());
        when(port.updateInsights(eq(1L), any())).thenReturn(1);
    }

    /* ===================== 既有行为 ===================== */

    @Test
    void generateAndPersist_savesReadyReport() {
        PersonCapabilityWorkflow w = new PersonCapabilityWorkflow();
        w.setId(1L);
        w.setEmpId(100L);
        w.setPostId(200L);
        when(workflowService.getWorkflow(1L)).thenReturn(w);
        when(port.listClaims(eq(1L), any())).thenReturn(List.of());

        service.generateAndPersist(1L, 1L, sampleReport());

        // overallScore 现在是「综合评分」，不再是面试分（sampleReport 里是 78）。
        // 报告在 AI 面试结束时落库，此刻还没有任何已确认能力等级 → 写 null，
        // 真正的数值由读时按最新决策重算（getByWorkflowId / listByEmpId）。
        verify(port).saveReport(argThat(dto ->
                dto != null && "READY".equals(dto.status())
                        && dto.overallScore() == null
                        && dto.interviewSummaryJson() != null));
    }

    @Test
    void generateAndPersist_writesCompositeScoreFromConfirmedLevels_notInterviewScore() {
        PersonCapabilityWorkflow w = new PersonCapabilityWorkflow();
        w.setId(1L);
        w.setEmpId(100L);
        w.setPostId(200L);
        when(workflowService.getWorkflow(1L)).thenReturn(w);
        when(port.listClaims(eq(1L), any())).thenReturn(List.of());
        // 3 项已确认（4/5/3 → 均值 4 → 80）；待复核与已拒绝都不计入
        when(levelService.listDecisions(1L)).thenReturn(List.of(
                decision(1L, 4, DecisionStatusEnum.HUMAN_CONFIRMED),
                decision(1L, 5, DecisionStatusEnum.AUTO_CONFIRMED),
                decision(1L, 3, DecisionStatusEnum.HUMAN_CONFIRMED),
                decision(1L, 5, DecisionStatusEnum.PENDING_MANUAL_REVIEW),
                decision(1L, 1, DecisionStatusEnum.REJECTED)));

        service.generateAndPersist(1L, 1L, sampleReport());

        // 面试综合分是 78；综合评分必须是能力等级均值×20 = 80
        verify(port).saveReport(argThat(dto -> dto != null && dto.overallScore() == 80));
    }

    /* ===================== 综合评分口径（报告分数 ≠ 面试分数） ===================== */

    @Test
    void getByWorkflowId_replacesStoredInterviewScoreWithCompositeScore() {
        when(port.findReport(1L)).thenReturn(new AssessmentReportPort.ReportDTO(
                1L, 100L, 200L, 9L, "READY", 78, 70,
                "[]", "[]", "{}", "[]", "[]", "面试结论", "面试建议"));
        when(levelService.listDecisions(1L)).thenReturn(List.of(
                decision(1L, 5, DecisionStatusEnum.HUMAN_CONFIRMED),
                decision(1L, 4, DecisionStatusEnum.HUMAN_CONFIRMED)));

        AssessmentReportPort.ReportDTO dto = service.getByWorkflowId(1L);

        // avg(5,4)=4.5 → 90；库里存的 78（面试分）必须被替换掉
        assertThat(dto.overallScore()).isEqualTo(90);
        // 岗位匹配度按原口径保留
        assertThat(dto.postMatchScore()).isEqualTo(70);
        // withScores 不能丢掉其余字段
        assertThat(dto.conclusion()).isEqualTo("面试结论");
        assertThat(dto.interviewSummaryJson()).isEqualTo("{}");
    }

    @Test
    void getByWorkflowId_returnsNull_whenReportMissing() {
        when(port.findReport(1L)).thenReturn(null);
        assertThat(service.getByWorkflowId(1L)).isNull();
    }

    @Test
    void listByEmpId_scoresEachWorkflowSeparately() {
        when(port.listWorkflowReports(100L)).thenReturn(List.of(
                new AssessmentReportPort.WorkflowReportDTO(1L, "COMPLETED", null, null, "READY", 78, 70),
                new AssessmentReportPort.WorkflowReportDTO(2L, "COMPLETED", null, null, "READY", 66, 60)));
        when(levelService.listDecisions(1L)).thenReturn(List.of(
                decision(1L, 5, DecisionStatusEnum.HUMAN_CONFIRMED),
                decision(1L, 4, DecisionStatusEnum.HUMAN_CONFIRMED)));
        when(levelService.listDecisions(2L)).thenReturn(List.of(
                decision(2L, 2, DecisionStatusEnum.AUTO_CONFIRMED)));
        // 报告内容齐全 → 列表仍以 READY 示人
        when(port.findReport(any())).thenReturn(completeReport(1L));
        when(port.findInsights(any())).thenReturn(insightRow("fp"));

        List<AssessmentReportPort.WorkflowReportDTO> list = service.listByEmpId(100L);

        // 每次评估各有各的综合分（90 / 40），不共用同一个数
        assertThat(list).extracting(AssessmentReportPort.WorkflowReportDTO::overallScore)
                .containsExactly(90, 40);
        assertThat(list).extracting(AssessmentReportPort.WorkflowReportDTO::reportStatus)
                .containsExactly("READY", "READY");
    }

    /**
     * 【2026-09-04 需求回归】报告行虽然已是 READY（AI 面试结束即落库），
     * 但内容可能还没生成完 —— 此时列表必须显示「生成中」，
     * 否则员工看到「已生成」却点不开（详情闸门 3 会拒绝），比看不到更糟。
     */
    @Test
    void listByEmpId_marksGenerating_whenContentStillNotComplete() {
        when(port.listWorkflowReports(100L)).thenReturn(List.of(
                new AssessmentReportPort.WorkflowReportDTO(1L, "COMPLETED", null, null, "READY", 78, 70),
                new AssessmentReportPort.WorkflowReportDTO(2L, "COMPLETED", null, null, "READY", 66, 60)));
        when(port.findReport(1L)).thenReturn(completeReport(1L));
        when(port.findInsights(1L)).thenReturn(insightRow("fp"));
        when(port.findReport(2L)).thenReturn(generatingReport(2L));
        when(port.findInsights(2L)).thenReturn(null);
        when(levelService.listDecisions(any())).thenReturn(List.of());

        List<AssessmentReportPort.WorkflowReportDTO> list = service.listByEmpId(100L);

        assertThat(list.get(0).reportStatus()).isEqualTo("READY");
        assertThat(list.get(1).reportStatus()).isEqualTo("GENERATING");
    }

    @Test
    void listByEmpId_reportsNoScore_whenNoConfirmedLevels() {
        when(port.listWorkflowReports(100L)).thenReturn(List.of(
                new AssessmentReportPort.WorkflowReportDTO(1L, "IN_PROGRESS", null, null, null, 78, 70)));
        when(levelService.listDecisions(1L)).thenReturn(List.of(
                decision(1L, 5, DecisionStatusEnum.PENDING_MANUAL_REVIEW),
                decision(1L, 4, DecisionStatusEnum.REJECTED)));

        List<AssessmentReportPort.WorkflowReportDTO> list = service.listByEmpId(100L);

        // 没有已确认能力 → 不下发任何分数（含面试分 78）
        assertThat(list.get(0).overallScore()).isNull();
    }

    @Test
    void generateAndPersist_includesInterviewObservationsAndDegradedStateInSummary() {
        PersonCapabilityWorkflow w = new PersonCapabilityWorkflow();
        w.setId(1L);
        w.setEmpId(100L);
        w.setPostId(200L);
        when(workflowService.getWorkflow(1L)).thenReturn(w);
        when(port.listClaims(eq(1L), any())).thenReturn(List.of());

        service.generateAndPersist(1L, 1L, sampleReport());

        verify(port).saveReport(argThat(dto -> {
            assertThat(dto.interviewSummaryJson()).contains("observations");
            assertThat(dto.interviewSummaryJson()).contains("degraded");
            assertThat(dto.interviewSummaryJson()).contains("sessionId");
            assertThat(dto.interviewSummaryJson()).contains("questionAnswers");
            return true;
        }));
    }

    @Test
    void generateAndPersist_marksFailed_onWorkflowMissing() {
        when(workflowService.getWorkflow(1L)).thenReturn(null);
        service.generateAndPersist(1L, 1L, sampleReport());
        verify(port, never()).saveReport(argThat(dto -> dto != null && "READY".equals(dto.status())));
    }

    @Test
    void refreshLevelConclusion_callsUpdate() {
        when(levelService.listDecisions(1L)).thenReturn(List.of());
        service.refreshLevelConclusion(1L);
        verify(port).updateLevelSummary(eq(1L), any());
    }

    /* ===================== 洞察生成：闸门与幂等 ===================== */

    @Test
    void generateInsights_skipsWithoutCallingModel_whenFlowNotYetInAggregateStage() {
        PersonCapabilityWorkflow w = completedWorkflow();
        w.setStatus("AI_INTERVIEW");
        when(workflowService.getWorkflow(1L)).thenReturn(w);

        assertThat(service.generateInsights(1L, false)).isFalse();
        // 关键：流程还没进聚合审核时**根本不调模型**，避免每次 USER_ACTION_COMPLETED 都烧一次
        verifyNoInteractions(insightService);
        verify(port, never()).updateInsights(any(), any());
    }

    /**
     * 【2026-09-04 修复回归】洞察的触发事件（USER_ACTION_COMPLETED）本身就是「把流程推进到
     * COMPLETED 的那次动作」发出的 —— 事件处理时状态还停在 LEVEL_CONFIRMING。
     * 若此处仍要求 COMPLETED，「AI 洞察」列永远不会被写入（用户反馈的「ai洞察没有数据」）。
     */
    @Test
    void generateInsights_runsInLevelConfirming_becauseTriggerEventFiresBeforeCompleted() {
        PersonCapabilityWorkflow w = completedWorkflow();
        w.setStatus(WorkflowStatusEnum.LEVEL_CONFIRMING.getCode());
        when(workflowService.getWorkflow(1L)).thenReturn(w);
        when(port.listClaimGroups(1L)).thenReturn(List.of());
        when(assembler.assemble(100L)).thenReturn(new ComprehensiveAssessmentContext());
        when(port.findInsights(1L)).thenReturn(emptyInsightRow());
        when(insightService.generateInsights(any())).thenReturn(aiResult());
        when(port.updateInsights(eq(1L), any())).thenReturn(1);

        assertThat(service.generateInsights(1L, false)).isTrue();
        verify(insightService).generateInsights(any());
        verify(port).updateInsights(eq(1L), any());
    }

    @Test
    void generateInsights_skipsWithoutCallingModel_whenReviewsStillPending() {
        when(workflowService.getWorkflow(1L)).thenReturn(completedWorkflow());
        when(port.listClaimGroups(1L)).thenReturn(List.of(
                new AssessmentReportPort.ClaimGroupDTO(1L, 10L, "Java",
                        EvidenceStatusEnum.PENDING_MANUAL_REVIEW.getCode())));

        assertThat(service.generateInsights(1L, false)).isFalse();
        verifyNoInteractions(insightService);
    }

    @Test
    void generateInsights_skipsWithoutCallingModel_whenReportBodyMissing() {
        when(workflowService.getWorkflow(1L)).thenReturn(completedWorkflow());
        when(port.listClaimGroups(1L)).thenReturn(List.of());
        when(port.findInsights(1L)).thenReturn(null);

        assertThat(service.generateInsights(1L, false)).isFalse();
        verifyNoInteractions(insightService);
    }

    @Test
    void generateInsights_writesInsightsWithFingerprint() {
        stubReadyToGenerate();

        assertThat(service.generateInsights(1L, false)).isTrue();

        ArgumentCaptor<AssessmentReportPort.InsightDTO> captor =
                ArgumentCaptor.forClass(AssessmentReportPort.InsightDTO.class);
        verify(port).updateInsights(eq(1L), captor.capture());
        AssessmentReportPort.InsightDTO written = captor.getValue();
        assertThat(written.insightSource()).isEqualTo(ComprehensiveAssessmentReportService.SOURCE_AI);
        assertThat(written.aiConclusion()).isEqualTo("综合结论");
        assertThat(written.sourceFingerprint()).isNotBlank();
        assertThat(written.insightGeneratedAt()).isNotNull();
    }

    @Test
    void generateInsights_isIdempotent_whenFingerprintUnchanged() {
        stubReadyToGenerate();
        // 先落一次，拿到真实指纹
        assertThat(service.generateInsights(1L, false)).isTrue();
        ArgumentCaptor<AssessmentReportPort.InsightDTO> captor =
                ArgumentCaptor.forClass(AssessmentReportPort.InsightDTO.class);
        verify(port).updateInsights(eq(1L), captor.capture());
        String fingerprint = captor.getValue().sourceFingerprint();

        // 同一份事实、库中已有同指纹洞察 → 跳过，不再调模型
        reset(insightService, port);
        when(workflowService.getWorkflow(1L)).thenReturn(completedWorkflow());
        when(port.listClaimGroups(1L)).thenReturn(List.of());
        when(assembler.assemble(100L)).thenReturn(new ComprehensiveAssessmentContext());
        when(port.findInsights(1L)).thenReturn(insightRow(fingerprint));

        assertThat(service.generateInsights(1L, false)).isFalse();
        verifyNoInteractions(insightService);
        verify(port, never()).updateInsights(any(), any());
    }

    @Test
    void generateInsights_forceRecomputes_evenWhenFingerprintUnchanged() {
        stubReadyToGenerate();
        assertThat(service.generateInsights(1L, false)).isTrue();
        ArgumentCaptor<AssessmentReportPort.InsightDTO> captor =
                ArgumentCaptor.forClass(AssessmentReportPort.InsightDTO.class);
        verify(port).updateInsights(eq(1L), captor.capture());
        String fingerprint = captor.getValue().sourceFingerprint();

        reset(insightService, port);
        when(workflowService.getWorkflow(1L)).thenReturn(completedWorkflow());
        when(port.listClaimGroups(1L)).thenReturn(List.of());
        when(assembler.assemble(100L)).thenReturn(new ComprehensiveAssessmentContext());
        when(port.findInsights(1L)).thenReturn(insightRow(fingerprint));
        when(insightService.generateInsights(any())).thenReturn(aiResult());
        when(port.updateInsights(eq(1L), any())).thenReturn(1);

        assertThat(service.generateInsights(1L, true)).isTrue();
        verify(insightService).generateInsights(any());
    }

    @Test
    void generateInsights_doesNotThrow_whenModelFails() {
        stubReadyToGenerate();
        when(insightService.generateInsights(any())).thenAnswer(inv -> {
            throw new IllegalStateException("model down");
        });

        // 洞察失败绝不能向上抛（数值部分不依赖它，主链路必须继续）
        assertThat(service.generateInsights(1L, false)).isFalse();
    }

    /* ===================== 报告详情：两级闸门 ===================== */

    @Test
    void getReportDetail_marksUnavailable_whenWorkflowNotCompleted() {
        PersonCapabilityWorkflow w = completedWorkflow();
        w.setStatus("AI_TEST");
        when(workflowService.getWorkflow(1L)).thenReturn(w);
        when(assembler.assemble(100L)).thenReturn(new ComprehensiveAssessmentContext());

        ComprehensiveAssessmentReportDetail detail = service.getReportDetail(1L);

        assertThat(detail).isNotNull();
        assertThat(detail.isAvailable()).isFalse();
        assertThat(detail.getUnavailableReason()).contains("尚未全部完成");
        // 闸门命中时不读取任何报告内容
        verify(port, never()).findReport(any());
    }

    @Test
    void getReportDetail_marksUnavailable_whenReviewsStillPending() {
        when(workflowService.getWorkflow(1L)).thenReturn(completedWorkflow());
        when(assembler.assemble(100L)).thenReturn(new ComprehensiveAssessmentContext());
        when(port.listClaimGroups(1L)).thenReturn(List.of(
                new AssessmentReportPort.ClaimGroupDTO(1L, 10L, "Java",
                        EvidenceStatusEnum.PENDING_MANUAL_REVIEW.getCode()),
                new AssessmentReportPort.ClaimGroupDTO(2L, 11L, "Redis",
                        EvidenceStatusEnum.PENDING_MANUAL_REVIEW.getCode())));

        ComprehensiveAssessmentReportDetail detail = service.getReportDetail(1L);

        assertThat(detail.isAvailable()).isFalse();
        assertThat(detail.getUnavailableReason()).contains("2 项待人工确认");
        verify(port, never()).findReport(any());
    }

    @Test
    void getReportDetail_returnsFullView_whenReady() {
        when(workflowService.getWorkflow(1L)).thenReturn(completedWorkflow());
        ComprehensiveAssessmentContext context = new ComprehensiveAssessmentContext();
        context.setEmpName("张三");
        context.setSourceWeights(List.of(new ComprehensiveAssessmentContext.SourceWeightFact(
                "AI_TEST", "AI 测试", new BigDecimal("30"))));
        when(assembler.assemble(100L)).thenReturn(context);
        when(port.listClaimGroups(1L)).thenReturn(List.of(
                new AssessmentReportPort.ClaimGroupDTO(1L, 10L, "Java", "CONFIRMED")));
        when(port.findReport(1L)).thenReturn(new AssessmentReportPort.ReportDTO(
                1L, 100L, 200L, 9L, "READY", null, null,
                "[{\"abilityName\":\"Java\"}]", "[]", "{\"radarItems\":[]}", "[]", "[]",
                "面试结论", "面试建议"));
        when(port.findInsights(1L)).thenReturn(insightRow("fp-1"));

        ComprehensiveAssessmentReportDetail detail = service.getReportDetail(1L);

        assertThat(detail.isAvailable()).isTrue();
        assertThat(detail.getEmpName()).isEqualTo("张三");
        assertThat(detail.getInterviewSessionId()).isEqualTo(9L);
        assertThat(detail.getResumeSummaryJson()).contains("Java");
        assertThat(detail.getSourceWeights()).hasSize(1);
        assertThat(detail.isInsightAvailable()).isTrue();
        assertThat(detail.isInsightFallbackUsed()).isFalse();
        // 汇总分不下发（产品口径：报告顶部不展示汇总分）
        assertThat(detail.toString()).doesNotContain("overallScore");
    }

    @Test
    void getReportDetail_marksInsightFallback_whenTemplateSource() {
        when(workflowService.getWorkflow(1L)).thenReturn(completedWorkflow());
        when(assembler.assemble(100L)).thenReturn(new ComprehensiveAssessmentContext());
        when(port.listClaimGroups(1L)).thenReturn(List.of());
        when(port.findReport(1L)).thenReturn(new AssessmentReportPort.ReportDTO(
                1L, 100L, 200L, 9L, "READY", null, null,
                "[]", "[]", "{}", "[]", "[]", null, null));
        when(port.findInsights(1L)).thenReturn(new AssessmentReportPort.InsightDTO(
                "[]", "[]", "[]", "[]", "[]", "模板结论",
                ComprehensiveAssessmentReportService.SOURCE_TEMPLATE, null, 50,
                LocalDateTime.now(), "fp"));

        ComprehensiveAssessmentReportDetail detail = service.getReportDetail(1L);

        assertThat(detail.isAvailable()).isTrue();
        assertThat(detail.isInsightFallbackUsed()).isTrue();
        assertThat(detail.getInsightSource()).isEqualTo(ComprehensiveAssessmentReportService.SOURCE_TEMPLATE);
    }

    /**
     * 【2026-09-04 需求】「评估报告的内容一定是全部生成好了之后才能被员工看见」。
     *
     * <p>报告主体在 AI 面试结束就落库了，而「最终能力等级」「AI 综合洞察」是后置写入的。
     * 若不拦这一层，员工会打开一份结构完整、内容半空的报告 —— 比看不到更糟。
     * 文案必须点名缺哪一块，既是给员工的解释，也是 HR 排查的依据。
     */
    @Test
    void getReportDetail_marksUnavailable_whenContentStillGenerating() {
        when(workflowService.getWorkflow(1L)).thenReturn(completedWorkflow());
        when(assembler.assemble(100L)).thenReturn(new ComprehensiveAssessmentContext());
        when(port.listClaimGroups(1L)).thenReturn(List.of());
        when(port.findReport(1L)).thenReturn(generatingReport(1L));
        when(port.findInsights(1L)).thenReturn(null);

        ComprehensiveAssessmentReportDetail detail = service.getReportDetail(1L);

        assertThat(detail.isAvailable()).isFalse();
        assertThat(detail.getUnavailableReason()).contains("仍在生成中");
        assertThat(detail.getUnavailableReason()).contains("最终能力等级");
        assertThat(detail.getUnavailableReason()).contains("AI 综合洞察");
    }

    @Test
    void getReportDetail_returnsNull_whenWorkflowMissing() {
        when(workflowService.getWorkflow(1L)).thenReturn(null);
        assertThat(service.getReportDetail(1L)).isNull();
        verifyNoInteractions(assembler);
    }
}
