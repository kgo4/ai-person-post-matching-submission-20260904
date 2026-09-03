package com.example.matching.controller.assessment;

import com.example.matching.application.assessment.CapabilityAssessmentFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.assessment.EligibilityPrecheckResult;
import com.example.matching.dto.assessment.ProvisionalAbilitySnapshotDTO;
import com.example.matching.dto.assessment.ResumeAbilityClaimDTO;
import com.example.matching.entity.workflow.PersonCapabilityStageRun;
import com.example.matching.utils.SecurityUtils;
import com.example.matching.vo.assessment.CapabilityAssessmentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 人员能力评估流程控制器
 * <p>
 * 候选人主流程唯一入口：简历证据 -> AI 测试 -> AI 面试 -> 聚合审核 -> 最终确认。
 * 前端不直接调用"导入正式能力"接口。
 *
 * @author system
 */
@Tag(name = "能力评估流程", description = "人员能力评估工作流：简历证据、AI测试、AI面试、聚合审核、等级确认")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class CapabilityAssessmentController {

    private final CapabilityAssessmentFacade capabilityAssessmentFacade;

    @Operation(summary = "获取或创建员工活跃评估工作流")
    @PostMapping("/employees/{empId}/capability-assessments/active")
    public R<CapabilityAssessmentVO.WorkflowView> getOrCreateActive(
            @PathVariable Long empId) {
        return R.ok(capabilityAssessmentFacade.getOrCreateWorkflow(empId, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "查询员工活跃评估工作流")
    @GetMapping("/employees/{empId}/capability-assessments/active")
    public R<CapabilityAssessmentVO.WorkflowView> getActive(
            @PathVariable Long empId) {
        return R.ok(capabilityAssessmentFacade.getActiveWorkflow(empId));
    }

    @Operation(summary = "查询工作流详情")
    @GetMapping("/capability-assessments/{workflowId}")
    public R<CapabilityAssessmentVO.WorkflowView> getWorkflow(
            @PathVariable Long workflowId) {
        return R.ok(capabilityAssessmentFacade.getWorkflow(workflowId));
    }

    @Operation(summary = "查询评估范围", description = "简历声明 ∩ 岗位要求的交集 + 未覆盖岗位能力（岗位未绑定返回空）")
    @GetMapping("/capability-assessments/{workflowId}/scope")
    public R<com.example.matching.dto.assessment.AssessmentScopeDTO> getAssessmentScope(
            @PathVariable Long workflowId) {
        return R.ok(capabilityAssessmentFacade.getAssessmentScope(workflowId));
    }

    @Operation(summary = "保存简历能力证据", description = "简历解析完成后保存为待验证能力证据，不直接正式入库")
    @PostMapping("/employees/{empId}/capability-assessments/resume")
    public R<Integer> submitResumeEvidence(
            @PathVariable Long empId,
            @RequestParam Long resumeParseId,
            @RequestBody List<ResumeAbilityClaimDTO> claims) {
        int saved = capabilityAssessmentFacade.submitResumeEvidence(
                empId, resumeParseId, claims, SecurityUtils.getCurrentUserId());
        return R.ok("已保存 " + saved + " 条待验证能力，下一步：生成验证测试", saved);
    }

    @Operation(summary = "生成验证测试", description = "根据简历能力与目标岗位生成验证测试（AI测试阶段）")
    @PostMapping("/capability-assessments/{workflowId}/test/generate")
    public R<com.example.matching.dto.assessment.GenerateVerificationTestResponse> generateTest(
            @PathVariable Long workflowId,
            @RequestParam(required = false) Long postId) {
        return R.ok(capabilityAssessmentFacade.generateTest(
                workflowId, postId, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "提交测试答案")
    @PostMapping("/capability-assessments/{workflowId}/test/{testId}/submit")
    public R<com.example.matching.vo.assessment.CapabilityAssessmentVO.StageRunView> submitTest(
            @PathVariable Long workflowId,
            @PathVariable Long testId,
            @RequestBody @jakarta.validation.Valid com.example.matching.dto.assessment.SubmitTestRequest request) {
        return R.ok(capabilityAssessmentFacade.toStageRunView(capabilityAssessmentFacade.submitTest(
                workflowId, testId, request.getAnswers(), SecurityUtils.getCurrentUserId())));
    }

    @Operation(summary = "创建 AI 面试")
    @PostMapping("/capability-assessments/{workflowId}/interview/create")
    public R<com.example.matching.dto.assessment.CreateAssessmentInterviewResponse> createInterview(
            @PathVariable Long workflowId) {
        return R.ok(capabilityAssessmentFacade.createInterview(
                workflowId, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "结束 AI 面试并推进聚合审核")
    @PostMapping("/capability-assessments/{workflowId}/interview/{sessionId}/finish")
    public R<com.example.matching.vo.assessment.CapabilityAssessmentVO.StageRunView> finishInterview(
            @PathVariable Long workflowId,
            @PathVariable Long sessionId) {
        return R.ok(capabilityAssessmentFacade.toStageRunView(
                capabilityAssessmentFacade.finishInterview(
                        workflowId, sessionId, SecurityUtils.getCurrentUserId())));
    }

    @Operation(summary = "查询聚合 Harness 审核结果", description = "面试完成后逐能力审核的决策、等级上限、风险与证据引用")
    @GetMapping("/capability-assessments/{workflowId}/harness")
    public R<List<com.example.matching.dto.assessment.HarnessBatchItemResultDTO>> getHarnessResults(
            @PathVariable Long workflowId) {
        return R.ok(capabilityAssessmentFacade.getHarnessResults(workflowId));
    }

    @Operation(summary = "查询工作流等级决策记录", description = "最终能力等级确认中心的决策、策略快照、有效权重与原因")
    @GetMapping("/capability-assessments/{workflowId}/decisions")
    public R<List<com.example.matching.vo.assessment.PersonAbilityLevelDecisionVO>> listDecisions(
            @PathVariable Long workflowId) {
        return R.ok(capabilityAssessmentFacade.listDecisions(workflowId).stream()
                .map(com.example.matching.vo.assessment.PersonAbilityLevelDecisionVO::from)
                .toList());
    }

    @Operation(summary = "人工确认等级", description = "人工复核后确认能力等级（HUMAN_CONFIRMED），记录审核人")
    @PostMapping("/capability-assessments/decisions/{decisionId}/confirm")
    public R<com.example.matching.vo.assessment.PersonAbilityLevelDecisionVO> confirmDecision(
            @PathVariable Long decisionId,
            @RequestParam Integer finalLevel,
            @RequestParam(required = false) Integer finalConfidence,
            @RequestParam(required = false) String reason) {
        return R.ok(com.example.matching.vo.assessment.PersonAbilityLevelDecisionVO.from(
                capabilityAssessmentFacade.humanConfirmDecision(
                        decisionId, finalLevel, finalConfidence, reason, SecurityUtils.getCurrentUserId())));
    }

    @Operation(summary = "人工拒绝等级", description = "人工复核后拒绝能力等级（REJECTED），记录审核人")
    @PostMapping("/capability-assessments/decisions/{decisionId}/reject")
    public R<com.example.matching.vo.assessment.PersonAbilityLevelDecisionVO> rejectDecision(
            @PathVariable Long decisionId,
            @RequestParam(required = false) String reason) {
        return R.ok(com.example.matching.vo.assessment.PersonAbilityLevelDecisionVO.from(
                capabilityAssessmentFacade.humanRejectDecision(
                        decisionId, reason, SecurityUtils.getCurrentUserId())));
    }

    @Operation(summary = "按新策略重算等级结论", description = "AUTO_CONFIRMED 标记待重算；PENDING_MANUAL_REVIEW 立即重算；HUMAN_CONFIRMED 不静默改写")
    @PostMapping("/capability-assessments/{workflowId}/recalculate")
    public R<Void> recalculateDecisions(
            @PathVariable Long workflowId,
            @RequestParam String policyVersion) {
        capabilityAssessmentFacade.recalculateDecisions(workflowId, policyVersion);
        return R.ok();
    }

    @Operation(summary = "重试失败阶段", description = "从失败阶段恢复，不重跑已完成阶段")
    @PostMapping("/capability-assessments/{workflowId}/retry-stage")
    public R<Void> retryStage(
            @PathVariable Long workflowId,
            @RequestParam String stageType) {
        capabilityAssessmentFacade.retryStage(workflowId, stageType, SecurityUtils.getCurrentUserId());
        return R.ok();
    }

    @Operation(summary = "员工能力画像视图", description = "正式画像 + 待确立画像")
    @GetMapping("/employees/{empId}/capability-assessments/profile")
    public R<Map<String, Object>> getProfile(
            @PathVariable Long empId) {
        return R.ok(capabilityAssessmentFacade.getProfile(empId));
    }

    @Operation(summary = "查询员工全部评估报告列表", description = "该人员每一次评估（workflow）及报告状态，倒序")
    @GetMapping("/employees/{empId}/capability-assessments/reports")
    public R<List<com.example.matching.port.assessment.AssessmentReportPort.WorkflowReportDTO>> listReports(
            @PathVariable Long empId) {
        return R.ok(capabilityAssessmentFacade.listAssessmentReports(empId));
    }

    @Operation(summary = "查询单次评估报告", description = "按评估工作流ID查询综合报告")
    @GetMapping("/capability-assessments/{workflowId}/report")
    public R<com.example.matching.port.assessment.AssessmentReportPort.ReportDTO> getReport(
            @PathVariable Long workflowId) {
        return R.ok(capabilityAssessmentFacade.getAssessmentReport(workflowId));
    }

    @Operation(summary = "匹配资格预检", description = "检查人员是否具备匹配资格及待确立能力情况")
    @PostMapping("/matching/precheck-capability-eligibility")
    public R<List<EligibilityPrecheckResult>> precheckEligibility(
            @RequestBody @jakarta.validation.Valid com.example.matching.dto.assessment.EligibilityPrecheckRequest request) {
        return R.ok(capabilityAssessmentFacade.precheckEligibility(
                request.getEmpIds(), request.getPostIds()));
    }

    @Operation(summary = "构建强制匹配临时能力快照", description = "确认风险后构建一次性临时能力快照（仅软评分）")
    @PostMapping("/employees/{empId}/capability-assessments/provisional-snapshot")
    public R<ProvisionalAbilitySnapshotDTO> buildProvisionalSnapshot(
            @PathVariable Long empId,
            @RequestParam boolean acknowledged) {
        return R.ok(capabilityAssessmentFacade.buildProvisionalSnapshot(
                empId, acknowledged, SecurityUtils.getCurrentUserId()));
    }
}
