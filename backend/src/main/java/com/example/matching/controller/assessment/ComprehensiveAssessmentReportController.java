package com.example.matching.controller.assessment;

import com.example.matching.common.result.R;
import com.example.matching.dto.assessment.report.ComprehensiveAssessmentReportDetail;
import com.example.matching.service.assessment.AssessmentReportService;
import com.example.matching.service.assessment.CapabilityAssessmentWorkflowService;
import com.example.matching.service.assessment.report.CapabilityAnalysisReportService;
import com.example.matching.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 员工全方位评估报告接口。
 *
 * <p>能力评估域**只有一个报告**：简历证据 → AI 测试 → AI 面试（完整报告）→ 最终等级 +
 * AI 综合洞察。原并列的「面试报告」入口已并入本报告的第三部分。
 *
 * <p>权限沿用 {@code /api/employee/**} 的统一规则（EMPLOYEE:READ 看全员 /
 * ASSESSMENT:SELF|MANAGE 看本人），「本人 vs 全员」的归属判定复用
 * {@link CapabilityAnalysisReportService#canAccess}，与「能力分析报告」保持同一口径。
 *
 * <p><b>报告闸门在 Service 层</b>：评估流程未完成或仍有待人工审核的能力项时，
 * 接口正常返回 200，但 {@code available=false} 且带原因 —— 前端据此展示说明，
 * 不用错误码表达「还没到时候」这种正常业务状态。
 *
 * @author system
 */
@Tag(name = "全方位评估报告", description = "四部分（简历/AI测试/AI面试/最终等级）+ AI 综合洞察")
@RestController
@RequestMapping("/api/employee/{empId}/comprehensive-assessment-report")
@RequiredArgsConstructor
public class ComprehensiveAssessmentReportController {

    private final AssessmentReportService assessmentReportService;
    private final CapabilityAssessmentWorkflowService workflowService;
    private final CapabilityAnalysisReportService analysisReportService;

    @Operation(summary = "查询最近一次已完成评估的完整报告",
            description = "员工本人或 HR 查看；评估未完成 / 审核未清空时返回 available=false + 原因")
    @GetMapping("/latest")
    public R<ComprehensiveAssessmentReportDetail> latest(@PathVariable Long empId) {
        if (!canRead(empId)) {
            return R.fail("无权查看该员工的评估报告");
        }
        Long workflowId = workflowService.getLatestCompletedWorkflowId(empId);
        if (workflowId == null) {
            return R.ok(null);
        }
        return R.ok(assessmentReportService.getReportDetail(workflowId));
    }

    @Operation(summary = "按评估流程查询完整报告")
    @GetMapping("/workflow/{workflowId}")
    public R<ComprehensiveAssessmentReportDetail> byWorkflow(@PathVariable Long empId,
                                                             @PathVariable Long workflowId) {
        if (!canRead(empId)) {
            return R.fail("无权查看该员工的评估报告");
        }
        ComprehensiveAssessmentReportDetail detail = assessmentReportService.getReportDetail(workflowId);
        if (detail == null) {
            return R.ok(null);
        }
        // 防止用他人 workflowId 越权读取：报告内的 empId 必须与路径一致
        if (detail.getEmpId() != null && !detail.getEmpId().equals(empId)) {
            return R.fail("该评估流程不属于当前员工");
        }
        return R.ok(detail);
    }

    @Operation(summary = "重新生成 AI 综合洞察（HR 手动重算）",
            description = "忽略事实指纹强制重算。只重写洞察文字，数值与结论均来自既有链路不受影响。"
                    + "若报告尚不可读（流程未完成 / 审核未清空 / 报告主体未生成）则说明原因，不静默成功。")
    @PostMapping("/regenerate")
    public R<Boolean> regenerate(@PathVariable Long empId) {
        if (!SecurityUtils.hasAuthority("EMPLOYEE:READ")
                && !SecurityUtils.hasAuthority("ASSESSMENT:MANAGE")) {
            return R.fail("无权重新生成该员工的评估报告");
        }
        Long workflowId = workflowService.getLatestCompletedWorkflowId(empId);
        if (workflowId == null) {
            return R.fail("该员工暂无已完成的评估流程，无法生成报告");
        }
        ComprehensiveAssessmentReportDetail detail = assessmentReportService.getReportDetail(workflowId);
        if (detail == null || !detail.isAvailable()) {
            return R.fail(detail == null
                    ? "评估流程不存在"
                    : detail.getUnavailableReason());
        }
        return R.ok(assessmentReportService.generateInsights(workflowId, true));
    }

    private boolean canRead(Long empId) {
        return analysisReportService.canAccess(empId, SecurityUtils.getCurrentUserId(),
                SecurityUtils.hasAuthority("EMPLOYEE:READ"));
    }
}
