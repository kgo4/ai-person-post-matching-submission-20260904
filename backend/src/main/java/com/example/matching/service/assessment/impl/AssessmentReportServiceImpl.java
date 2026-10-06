package com.example.matching.service.assessment.impl;

import com.example.matching.agent.dto.ComprehensiveAssessmentReportResult;
import com.example.matching.agent.service.ComprehensiveAssessmentReportService;
import com.example.matching.common.enums.DecisionStatusEnum;
import com.example.matching.common.enums.EvidenceStatusEnum;
import com.example.matching.common.enums.WorkflowStatusEnum;
import com.example.matching.dto.assessment.report.ComprehensiveAssessmentContext;
import com.example.matching.dto.assessment.report.ComprehensiveAssessmentReportDetail;
import com.example.matching.entity.workflow.PersonAbilityLevelDecision;
import com.example.matching.entity.workflow.PersonCapabilityWorkflow;
import com.example.matching.port.assessment.AssessmentReportPort;
import com.example.matching.service.assessment.AbilityLevelConfirmationService;
import com.example.matching.service.assessment.AggregateAbilityHarnessService;
import com.example.matching.service.assessment.AssessmentReportService;
import com.example.matching.service.assessment.CapabilityAssessmentWorkflowService;
import com.example.matching.service.assessment.report.ComprehensiveAssessmentContextAssembler;
import com.example.matching.dto.interview.CompetencyReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;

@Slf4j
@Service
public class AssessmentReportServiceImpl implements AssessmentReportService {

    private static final String STATUS_READY = "READY";
    private static final String STATUS_FAILED = "FAILED";

    /**
     * 报告行已落库、但内容尚未全部生成的中间态（列表展示「报告生成中」）。
     *
     * <p>报告主体在 AI 面试结束时就是 {@code READY}，而「最终能力等级」「AI 综合洞察」等
     * 区块是后置写入的 —— 若列表直接透出 {@code READY}，员工会看到一个写着「已生成」
     * 却打不开的按钮。可见性一律以内容完整性为准（与 {@link #getReportDetail} 闸门 3 同源）。
     */
    private static final String STATUS_GENERATING = "GENERATING";

    /**
     * 允许生成 AI 洞察的流程状态：聚合审核及其之后。
     *
     * <p>为什么不能要求 {@code COMPLETED}：洞察由 {@code USER_ACTION_COMPLETED} 事件触发，
     * 而该事件正是「把流程推进到 COMPLETED 的那个动作」发出的 —— 事件处理时状态尚未变成
     * COMPLETED，若在此要求 COMPLETED，洞察会被永久挡住（详见 {@link #generateInsights}）。
     */
    private static final Set<String> INSIGHT_ELIGIBLE_FLOW_STATUSES = Set.of(
            WorkflowStatusEnum.AGGREGATE_HARNESS_RUNNING.getCode(),
            WorkflowStatusEnum.LEVEL_CONFIRMING.getCode(),
            WorkflowStatusEnum.COMPLETED.getCode());

    private final AssessmentReportPort reportPort;
    private final CapabilityAssessmentWorkflowService workflowService;
    private final AggregateAbilityHarnessService aggregateHarnessService;
    private final AbilityLevelConfirmationService levelConfirmationService;
    private final ObjectMapper objectMapper;
    /** 四部分事实聚合器（只读既有数据，零 AI 调用） */
    private final ComprehensiveAssessmentContextAssembler contextAssembler;
    /** AI 洞察服务（只写文字，不打分、不改分） */
    private final ComprehensiveAssessmentReportService insightService;

    public AssessmentReportServiceImpl(AssessmentReportPort reportPort,
                                       CapabilityAssessmentWorkflowService workflowService,
                                       AggregateAbilityHarnessService aggregateHarnessService,
                                       AbilityLevelConfirmationService levelConfirmationService,
                                       ObjectMapper objectMapper,
                                       ComprehensiveAssessmentContextAssembler contextAssembler,
                                       ComprehensiveAssessmentReportService insightService) {
        this.reportPort = reportPort;
        this.workflowService = workflowService;
        this.aggregateHarnessService = aggregateHarnessService;
        this.levelConfirmationService = levelConfirmationService;
        this.objectMapper = objectMapper;
        this.contextAssembler = contextAssembler;
        this.insightService = insightService;
    }

    @Override
    public void generateAndPersist(Long workflowId, Long sessionId, CompetencyReport report) {
        PersonCapabilityWorkflow workflow = null;
        try {
            workflow = workflowService.getWorkflow(workflowId);
            if (workflow == null) {
                log.warn("评估工作流不存在，跳过报告生成: workflowId={}", workflowId);
                return;
            }
            reportPort.saveReport(new AssessmentReportPort.ReportDTO(
                    workflowId,
                    workflow.getEmpId(),
                    workflow.getPostId(),
                    sessionId,
                    STATUS_READY,
                    // 【2026-09-04 口径变更】这里原先把 CompetencyReport.overallScore（**面试环节**综合分）
                    // 直接写进 assessment_report.overall_score，于是「我的评估记录」里那道「综合评分」
                    // 实际显示的是面试得分。现改为写「综合评分」= 本次评估已确认能力等级均值 × 20。
                    // 报告主体在 AI 面试结束时即落库，此刻 HR 的能力等级确认多半还没完成，
                    // 因此这里通常写入 null（前端显示「审核中」），真正的数值由读取口按最新决策重算。
                    resolveCompositeScore(workflowId),
                    report.postMatchScore(),
                    buildClaimSummaryJson(workflowId, "RESUME_PARSE"),
                    buildClaimSummaryJson(workflowId, "AI_TEST"),
                    buildInterviewSummaryJson(report),
                    null,
                    null,
                    report.conclusion(),
                    report.recommendation()));
            log.info("评估报告主体已生成: workflowId={}, sessionId={}", workflowId, sessionId);
        } catch (Exception e) {
            log.error("评估报告主体生成失败: workflowId={}, error={}", workflowId, e.getMessage(), e);
            markFailed(workflowId, sessionId, workflow);
        }
    }

    @Override
    public void refreshAggregateConclusion(Long workflowId) {
        try {
            Map<Long, String> groupNames = resolveGroupNames(workflowId);
            List<Map<String, Object>> items = aggregateHarnessService.getHarnessResults(workflowId).stream()
                    .map(d -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("claimGroupId", d.getClaimGroupId());
                        m.put("abilityName", groupNames.getOrDefault(d.getClaimGroupId(), "能力组" + d.getClaimGroupId()));
                        m.put("decision", d.getDecision());
                        m.put("riskLevel", d.getRiskLevel());
                        m.put("abilitySupported", d.getAbilitySupported());
                        m.put("supportedLevelCeiling", d.getSupportedLevelCeiling());
                        m.put("reasonCodes", d.getReasonCodes());
                        return m;
                    })
                    .toList();
            reportPort.updateAggregateSummary(workflowId, toJson(items));
            log.info("报告聚合审核结论已回填: workflowId={}", workflowId);
        } catch (Exception e) {
            log.warn("报告聚合审核结论回填失败: workflowId={}, error={}", workflowId, e.getMessage());
        }
    }

    @Override
    public void refreshLevelConclusion(Long workflowId) {
        try {
            Map<Long, String> groupNames = resolveGroupNames(workflowId);
            List<Map<String, Object>> items = levelConfirmationService.listDecisions(workflowId).stream()
                    .map(d -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("claimGroupId", d.getClaimGroupId());
                        m.put("abilityName", groupNames.getOrDefault(d.getClaimGroupId(), "能力组" + d.getClaimGroupId()));
                        m.put("tagId", d.getTagId());
                        m.put("finalLevel", d.getFinalLevel());
                        m.put("finalConfidence", d.getFinalConfidence());
                        m.put("decisionStatus", d.getDecisionStatus());
                        return m;
                    })
                    .toList();
            reportPort.updateLevelSummary(workflowId, toJson(items));
            log.info("报告等级确认结论已回填: workflowId={}", workflowId);
        } catch (Exception e) {
            log.warn("报告等级确认结论回填失败: workflowId={}, error={}", workflowId, e.getMessage());
        }
    }

    @Override
    public List<AssessmentReportPort.WorkflowReportDTO> listByEmpId(Long empId) {
        List<AssessmentReportPort.WorkflowReportDTO> reports = reportPort.listWorkflowReports(empId);
        if (reports.isEmpty()) {
            return reports;
        }
        // 综合评分是**每次评估各自**的结果，必须逐个 workflow 取决策重算 ——
        // 不能用 listDecisionsByEmp：它按 tagId 跨流程去重、只保留最新一条，
        // 会把更早几次评估的能力项吃掉，算出来的不是「本次评估」的分。
        return reports.stream()
                .map(r -> new AssessmentReportPort.WorkflowReportDTO(
                        r.workflowId(), r.workflowStatus(), r.startedAt(), r.completedAt(),
                        r.reportStatus(),
                        resolveCompositeScore(r.workflowId()),
                        r.postMatchScore()))
                // 内容还没生成完的报告不能以 READY 出现在列表里 —— 否则员工看到「已生成」，
                // 点进去却只得到一句「内容仍在生成中」。判据与详情闸门 3 同源（同一个私有方法）。
                .map(r -> STATUS_READY.equals(r.reportStatus()) && !isReportContentComplete(r.workflowId())
                        ? r.withReportStatus(STATUS_GENERATING)
                        : r)
                .toList();
    }

    /** 报告内容是否已全部生成（四大块齐全）—— 列表与详情共用的唯一判据 */
    private boolean isReportContentComplete(Long workflowId) {
        try {
            AssessmentReportPort.ReportDTO report = reportPort.findReport(workflowId);
            if (report == null) {
                return false;
            }
            return missingReportSections(report, reportPort.findInsights(workflowId)).isEmpty();
        } catch (Exception e) {
            log.warn("报告内容完整性判定失败，按未完成处理: workflowId={}, error={}", workflowId, e.getMessage());
            return false;
        }
    }

    /** 列出报告缺失的内容区块；返回空列表表示内容齐全。 */
    private List<String> missingReportSections(AssessmentReportPort.ReportDTO report,
                                               AssessmentReportPort.InsightDTO insights) {
        List<String> missing = new ArrayList<>();
        if (!StringUtils.hasText(report.aggregateSummaryJson())) {
            missing.add("聚合审核结论");
        }
        if (!StringUtils.hasText(report.levelSummaryJson())) {
            missing.add("最终能力等级");
        }
        if (!StringUtils.hasText(report.interviewSummaryJson())) {
            missing.add("AI 面试结论");
        }
        if (insights == null || insights.insightGeneratedAt() == null) {
            missing.add("AI 综合洞察");
        }
        return missing;
    }

    @Override
    public AssessmentReportPort.ReportDTO getByWorkflowId(Long workflowId) {
        AssessmentReportPort.ReportDTO report = reportPort.findReport(workflowId);
        if (report == null) {
            return null;
        }
        // 与列表同一口径：详情里的分数也换成综合分，
        // 避免出现「列表是综合分、详情是面试分」两套数并存。
        return report.withScores(resolveCompositeScore(workflowId), report.postMatchScore());
    }

    /**
     * 综合评分 = 本次评估「已确认能力」等级均值 × 20（0–100 整数）。
     *
     * <p><b>口径（2026-09-04 需求）</b>：「评估报告的分数不能是面试的分，而是综合的分数」。
     * 与本项目既有的综合能力评分同源 —— {@code EmpAbilityServiceImpl} 的能力画像是
     * 「各能力 masteryLevel 均值 × 20」，这里唯一的区别是按 workflow 收口：
     * 只统计**本次评估**已确认的能力等级，因此同一员工的多条历史评估各有各的分数。
     *
     * <p>只计 {@link DecisionStatusEnum#isProjectable()}（AUTO_CONFIRMED / HUMAN_CONFIRMED）
     * 的决策：待人工复核与已拒绝都不算已确立能力，与被投影进 {@code emp_ability} 的集合一致。
     *
     * <p><b>为什么读时算而不是只写库</b>：报告主体在 AI 面试结束时落库，HR 的能力等级确认
     * 发生在其后 —— 写时算出来的只会是过期的。读时重算同时免去了历史数据回填。
     *
     * @return 尚无可计分的已确认能力时返回 null（前端显示「审核中」而不是 0 分）
     */
    private Integer resolveCompositeScore(Long workflowId) {
        try {
            List<PersonAbilityLevelDecision> decisions = levelConfirmationService.listDecisions(workflowId);
            if (decisions == null || decisions.isEmpty()) {
                return null;
            }
            OptionalDouble average = decisions.stream()
                    .filter(d -> d != null && d.getFinalLevel() != null)
                    .filter(d -> {
                        DecisionStatusEnum status = DecisionStatusEnum.fromCode(d.getDecisionStatus());
                        return status != null && status.isProjectable();
                    })
                    .mapToInt(PersonAbilityLevelDecision::getFinalLevel)
                    .average();
            if (average.isEmpty()) {
                return null;
            }
            return (int) Math.round(average.getAsDouble() * 20);
        } catch (Exception e) {
            // 分数是展示信息，任何取值失败都不能把报告链路打挂
            log.warn("综合评分计算失败，按无分数处理: workflowId={}, error={}", workflowId, e.getMessage());
            return null;
        }
    }

    /* ==========================================================================
     *                      AI 综合洞察（只写文字，不打分、不改分）
     * ========================================================================== */

    /**
     * 生成（或重算）AI 综合洞察并落库。
     *
     * <p><b>幂等</b>：输入事实指纹与库中一致且非强制时直接跳过 —— 同一份审核结果不会
     * 反复烧模型；审核被重审后事实变化，指纹随之变化，自然允许重算。
     *
     * <p><b>降级</b>：AI 不可用时 {@code insightService} 内部落模板文字，
     * 本方法只负责写入，**不触碰任何既有数值列**（数值本就不经过 AI）。
     */
    @Override
    public boolean generateInsights(Long workflowId, boolean force) {
        if (workflowId == null) {
            return false;
        }
        try {
            PersonCapabilityWorkflow workflow = workflowService.getWorkflow(workflowId);
            if (workflow == null) {
                log.warn("评估工作流不存在，跳过洞察生成: workflowId={}", workflowId);
                return false;
            }
            // 【2026-09-04 修复】闸门 1 由「必须 COMPLETED」放宽为「已进入聚合审核及之后」。
            //
            // 原实现在这里要求 COMPLETED，但触发洞察的唯一事件（USER_ACTION_COMPLETED）
            // 正是**把流程推进到 COMPLETED 的那个动作**发出的 —— 事件被处理时状态还停在
            // LEVEL_CONFIRMING，于是每次都从这个分支 return false：洞察从来没有被真正生成过。
            // 这就是「评估报告里 AI 洞察没有数据」的根因 —— 不是没触发，是触发了但被闸门挡住。
            //
            // 放宽是安全的：**可见性**仍由 getReportDetail 控制（必须 COMPLETED 且四块内容齐全），
            // 这里只决定「什么时候把内容算好」。提前算好反而保证员工在放行那一刻看到的是完整报告。
            // 与 CapabilityAnalysisReportListener 的口径一致（能力分析报告也在聚合审核/等级确认
            // 阶段就生成，不等 COMPLETED）。
            if (!INSIGHT_ELIGIBLE_FLOW_STATUSES.contains(workflow.getStatus())) {
                log.debug("评估流程尚未进入聚合审核阶段，跳过洞察生成: workflowId={}, status={}",
                        workflowId, workflow.getStatus());
                return false;
            }
            long pendingGroups = countPendingReviewGroups(workflowId);
            if (pendingGroups > 0) {
                log.debug("仍有 {} 项能力待人工审核，跳过洞察生成: workflowId={}", pendingGroups, workflowId);
                return false;
            }
            AssessmentReportPort.InsightDTO existing = reportPort.findInsights(workflowId);
            if (existing == null) {
                // 报告主体由面试结束链路落库；主体都没有时洞察无处可写（不新建半成品行）
                log.info("报告主体尚未生成，跳过洞察写入: workflowId={}", workflowId);
                return false;
            }
            ComprehensiveAssessmentContext context = contextAssembler.assemble(workflow.getEmpId());
            String fingerprint = buildFingerprint(context);
            if (!force && fingerprint.equals(existing.sourceFingerprint())
                    && existing.insightGeneratedAt() != null) {
                log.debug("报告输入事实未变化，洞察幂等跳过: workflowId={}", workflowId);
                return false;
            }

            ComprehensiveAssessmentReportResult result = insightService.generateInsights(context);
            int updated = reportPort.updateInsights(workflowId, new AssessmentReportPort.InsightDTO(
                    toJson(result.getSectionInsights()),
                    toJson(result.getStrengths()),
                    toJson(result.getWeaknesses()),
                    toJson(result.getRiskSignals()),
                    toJson(result.getSuggestions()),
                    result.getConclusion(),
                    StringUtils.hasText(result.getInsightSource())
                            ? result.getInsightSource() : ComprehensiveAssessmentReportService.SOURCE_TEMPLATE,
                    null,
                    result.getOverallConfidence() == null
                            ? null : result.getOverallConfidence().intValue(),
                    LocalDateTime.now(),
                    fingerprint));
            if (updated == 0) {
                log.info("报告主体在洞察写入瞬间消失，已放弃: workflowId={}", workflowId);
                return false;
            }
            log.info("评估报告洞察已落库: workflowId={}, source={}, force={}",
                    workflowId, result.getInsightSource(), force);
            return true;
        } catch (Exception e) {
            // 洞察失败绝不能影响评估主链路（数值部分本就不依赖它）
            log.warn("评估报告洞察生成失败（不阻断主流程）: workflowId={}", workflowId, e);
            return false;
        }
    }

    @Override
    public void refreshReportSections(Long workflowId) {
        if (workflowId == null) {
            return;
        }
        // 两个子步骤各自 try/catch、互不影响：任一失败都不该让另一个不写。
        refreshAggregateConclusion(workflowId);
        refreshLevelConclusion(workflowId);
    }

    /**
     * 报告的完整视图。
     *
     * <p><b>三级闸门</b>（这是「能力项只有 HR 审核全部完成后才对外可见」的落地点）：
     * <ol>
     *   <li>评估流程未到 {@code COMPLETED}；</li>
     *   <li>仍有处于 {@code PENDING_MANUAL_REVIEW} 的能力声明组；</li>
     *   <li>报告内容尚未全部生成（聚合审核结论 / 最终能力等级 / AI 面试结论 / AI 综合洞察
     *       任缺其一）—— 员工拿不到一份结构完整、内容半空的报告。</li>
     * </ol>
     * 命中任一条即返回 {@code available=false} + 原因文案（并明说是哪一块没生成完）——
     * 前端据此展示说明，而不是报错或半成品数据。
     */
    @Override
    public ComprehensiveAssessmentReportDetail getReportDetail(Long workflowId) {
        PersonCapabilityWorkflow workflow = workflowService.getWorkflow(workflowId);
        if (workflow == null) {
            return null;
        }
        ComprehensiveAssessmentReportDetail detail = new ComprehensiveAssessmentReportDetail();
        detail.setWorkflowId(workflowId);
        detail.setEmpId(workflow.getEmpId());
        detail.setPostId(workflow.getPostId());
        detail.setCompletedAt(workflow.getCompletedAt() == null ? null : workflow.getCompletedAt().toString());

        ComprehensiveAssessmentContext context = contextAssembler.assemble(workflow.getEmpId());
        detail.setEmpName(context.getEmpName());
        detail.setSourceWeights(context.getSourceWeights());

        // 闸门 1：评估流程尚未完成
        if (!WorkflowStatusEnum.COMPLETED.getCode().equals(workflow.getStatus())) {
            detail.setAvailable(false);
            detail.setUnavailableReason("评估流程尚未全部完成，报告将在流程结束并由 HR 确认后生成。");
            return detail;
        }

        // 闸门 2：仍有待人工审核的能力项 —— 与「全面能力分析报告」的清空口径一致
        long pendingGroups = countPendingReviewGroups(workflowId);
        if (pendingGroups > 0) {
            detail.setAvailable(false);
            detail.setUnavailableReason("HR 正在完成能力项审核，仍有 " + pendingGroups
                    + " 项待人工确认。全部审核完成后报告才会生成。");
            return detail;
        }

        AssessmentReportPort.ReportDTO report = reportPort.findReport(workflowId);
        if (report == null || !STATUS_READY.equals(report.status())) {
            detail.setAvailable(false);
            detail.setUnavailableReason("报告主体尚未生成：需先完成 AI 面试并形成评估结论。");
            return detail;
        }

        AssessmentReportPort.InsightDTO insights = reportPort.findInsights(workflowId);

        // 闸门 3：报告内容必须**已全部生成**，缺任何一块都不对外可见。
        //
        // 【2026-09-04 需求】「评估报告内容包含什么 AI 洞察等等，这些内容要全部生成好了之后
        // 才能被员工看见」。此前只要流程完成 + 无待审项就放行，而「最终能力等级」
        // （level_summary_json 从未被生产代码回填）与「AI 综合洞察」这两块常常是空的 ——
        // 员工打开一份结构完整、内容半空的报告，比看不到更糟。
        //
        // 缺哪一块就明说是哪一块：这条文案既是给员工的解释，也是 HR 排查「为什么还看不到」的线索。
        // 与 listByEmpId 的报告状态判定共用同一份实现（missingReportSections），
        // 避免「列表说已生成、详情说还在生成」两套口径漂移。
        List<String> missingSections = missingReportSections(report, insights);
        if (!missingSections.isEmpty()) {
            detail.setAvailable(false);
            detail.setUnavailableReason("评估报告内容仍在生成中：" + String.join("、", missingSections)
                    + "。全部内容生成完成后报告才会开放查看。"
                    + "若长时间停留在该状态，可联系 HR 重新生成本次评估报告。");
            return detail;
        }

        detail.setAvailable(true);
        detail.setReportStatus(report.status());
        detail.setResumeSummaryJson(report.resumeSummaryJson());
        detail.setTestSummaryJson(report.testSummaryJson());
        detail.setInterviewSummaryJson(report.interviewSummaryJson());
        detail.setAggregateSummaryJson(report.aggregateSummaryJson());
        detail.setLevelSummaryJson(report.levelSummaryJson());
        detail.setConclusion(report.conclusion());
        detail.setRecommendation(report.recommendation());
        // 面试过程记录入口需要会话 ID（只做跳转，不把逐字稿投喂给 AI）
        detail.setInterviewSessionId(report.sessionId());

        if (insights != null) {
            detail.setSectionInsightsJson(insights.sectionInsightsJson());
            detail.setStrengthsJson(insights.strengthsJson());
            detail.setWeaknessesJson(insights.weaknessesJson());
            detail.setRiskSignalsJson(insights.riskSignalsJson());
            detail.setSuggestionsJson(insights.suggestionsJson());
            detail.setAiConclusion(insights.aiConclusion());
            detail.setInsightSource(insights.insightSource());
            detail.setInsightConfidence(insights.insightConfidence());
            detail.setInsightGeneratedAt(insights.insightGeneratedAt() == null
                    ? null : insights.insightGeneratedAt().toString());
            detail.setInsightAvailable(insights.insightGeneratedAt() != null);
            detail.setInsightFallbackUsed(
                    ComprehensiveAssessmentReportService.SOURCE_TEMPLATE.equals(insights.insightSource()));
        }
        return detail;
    }

    /* ===================== 洞察辅助 ===================== */

    /**
     * 仍处于「待人工审核」的能力声明组数量。
     *
     * <p>口径与 {@code CapabilityAnalysisReportService.isReviewQueueCleared} 保持一致：
     * 采纳聚合 Harness 后 claim group 会停在 {@code READY_FOR_AGGREGATE_HARNESS}，
     * 那是终态而非未完成态，因此这里**只认 {@code PENDING_MANUAL_REVIEW}**。
     */
    private long countPendingReviewGroups(Long workflowId) {
        return reportPort.listClaimGroups(workflowId).stream()
                .filter(g -> EvidenceStatusEnum.PENDING_MANUAL_REVIEW.getCode().equals(g.status()))
                .count();
    }

    /**
     * 输入事实指纹：把四部分事实按**稳定顺序**拼成字符串再取 SHA-256 前 16 字节。
     *
     * <p>刻意排序后再拼接：数据库查询不保证顺序，若直接按返回顺序拼接，
     * 同一份数据可能算出不同指纹，导致无谓地重复调用模型。
     */
    private String buildFingerprint(ComprehensiveAssessmentContext context) {
        StringBuilder raw = new StringBuilder("v1");
        if (context.getResume() != null) {
            ComprehensiveAssessmentContext.ResumeFact r = context.getResume();
            raw.append("|R:").append(r.degree()).append(',').append(r.yearsOfWork())
                    .append(',').append(r.currentTitle()).append(',').append(r.claimCount())
                    .append(',').append(r.status());
        }
        context.getTests().stream()
                .sorted(Comparator.comparing(t -> Objects.toString(t.tagId(), "")
                        + Objects.toString(t.abilityName(), "")))
                .forEach(t -> raw.append("|T:").append(t.tagId()).append(',').append(t.abilityName())
                        .append(',').append(t.score()).append(',').append(t.masteryLevel()));
        if (context.getInterview() != null) {
            ComprehensiveAssessmentContext.InterviewFact i = context.getInterview();
            raw.append("|I:").append(i.overallScore()).append(',').append(i.observationCount())
                    .append(',').append(i.riskSignals()).append(',').append(i.conclusions());
        }
        context.getDecisions().stream()
                .sorted(Comparator.comparing(d -> Objects.toString(d.tagId(), "")))
                .forEach(d -> raw.append("|D:").append(d.tagId()).append(',').append(d.finalLevel())
                        .append(',').append(d.finalConfidence()).append(',').append(d.decisionStatus()));
        context.getAbilities().stream()
                .sorted(Comparator.comparing(a -> Objects.toString(a.tagId(), "")))
                .forEach(a -> raw.append("|A:").append(a.tagId()).append(',').append(a.masteryLevel()));

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(raw.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                hex.append(String.format("%02x", bytes[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 必备算法，兜底仅用于极端环境；仍保证长度可控（VARCHAR(64)）
            return Integer.toHexString(raw.toString().hashCode());
        }
    }

    private String buildClaimSummaryJson(Long workflowId, String sourceType) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (AssessmentReportPort.ClaimDTO c : reportPort.listClaims(workflowId, sourceType)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tagId", c.tagId());
            m.put("abilityName", c.abilityName());
            m.put("claimedLevel", c.claimedLevel());
            m.put("confidenceScore", c.confidenceScore());
            m.put("evidenceText", c.evidenceText());
            m.put("harnessDecision", c.harnessDecision());
            items.add(m);
        }
        return toJson(items);
    }

    private String buildInterviewSummaryJson(CompetencyReport report) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sessionId", report.sessionId());
        m.put("overallScore", report.overallScore());
        m.put("postMatchScore", report.postMatchScore());
        m.put("radarItems", report.radarItems());
        m.put("observations", report.observations());
        m.put("questionAnswers", safeList(reportPort.listInterviewQuestionAnswers(report.sessionId())));
        m.put("strengths", report.strengths());
        m.put("weaknesses", report.weaknesses());
        m.put("riskSignals", report.riskSignals());
        m.put("improvementSuggestions", report.improvementSuggestions());
        m.put("learningPathSuggestions", report.learningPathSuggestions());
        m.put("degraded", report.degraded());
        m.put("degradedReason", report.degradedReason());
        return toJson(m);
    }

    private <T> List<T> safeList(List<T> values) {
        return values != null ? values : List.of();
    }

    private Map<Long, String> resolveGroupNames(Long workflowId) {
        return reportPort.listClaimGroups(workflowId).stream()
                .collect(java.util.stream.Collectors.toMap(
                        AssessmentReportPort.ClaimGroupDTO::claimGroupId,
                        g -> g.normalizedAbilityName() != null ? g.normalizedAbilityName() : "能力组" + g.claimGroupId(),
                        (a, b) -> a));
    }

    private void markFailed(Long workflowId, Long sessionId, PersonCapabilityWorkflow workflow) {
        try {
            reportPort.saveReport(new AssessmentReportPort.ReportDTO(
                    workflowId,
                    workflow != null ? workflow.getEmpId() : null,
                    workflow != null ? workflow.getPostId() : null,
                    sessionId, STATUS_FAILED,
                    null, null, null, null, null, null, null, null, null));
        } catch (Exception e) {
            log.warn("报告失败状态落库失败: workflowId={}", workflowId, e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }
}


