package com.example.matching.controller.assessment;

import com.example.matching.common.result.R;
import com.example.matching.entity.assessment.report.EmpCapabilityAnalysisReport;
import com.example.matching.service.assessment.report.CapabilityAnalysisReportService;
import com.example.matching.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 员工全面能力分析报告接口（HR 匹配闭环 P2）。
 *
 * <p>权限由 SecurityConfig 的 /api/employee/** 规则统一覆盖：
 * EMPLOYEE:READ（HR 看全员）或 ASSESSMENT:SELF/ASSESSMENT:MANAGE（员工看本人）；
 * 「本人 vs 全员」的归属判定下沉在 Service 层。JSON 区块直接返回原始字符串，由前端解析渲染。</p>
 *
 * @author system
 */
@Tag(name = "全面能力分析报告", description = "员工能力审核完成后自动生成的分析报告快照")
@RestController
@RequestMapping("/api/employee/{empId}/capability-analysis-report")
@RequiredArgsConstructor
public class CapabilityAnalysisReportController {

    private final CapabilityAnalysisReportService reportService;

    @Operation(summary = "查询报告全部版本", description = "按版本号倒序；员工本人只能看到自己的报告")
    @GetMapping
    public R<List<EmpCapabilityAnalysisReport>> listAll(@PathVariable Long empId) {
        if (!reportService.canAccess(empId, SecurityUtils.getCurrentUserId(),
            SecurityUtils.hasAuthority("EMPLOYEE:READ"))) {
            return R.fail("无权查看该员工的报告");
        }
        return R.ok(reportService.listByEmp(empId));
    }

    @Operation(summary = "查询最新版报告")
    @GetMapping("/latest")
    public R<EmpCapabilityAnalysisReport> latest(@PathVariable Long empId) {
        if (!reportService.canAccess(empId, SecurityUtils.getCurrentUserId(),
            SecurityUtils.hasAuthority("EMPLOYEE:READ"))) {
            return R.fail("无权查看该员工的报告");
        }
        return R.ok(reportService.latestByEmp(empId));
    }

    @Operation(summary = "手动生成/补生成分析报告（HR 兜底）",
            description = "自动生成依赖审核链路的 AFTER_COMMIT 事件。若最后一步审核没有触发事件，"
                    + "报告不会落库（表现为「能力都审核通过了却仍显示暂无报告」），HR 可用本接口手动补生成。"
                    + "仍有待审内容时抛出带具体数量的错误信息，而不是静默失败。")
    @PostMapping("/generate")
    public R<Long> generate(@PathVariable Long empId) {
        if (!SecurityUtils.hasAuthority("EMPLOYEE:READ")
            && !SecurityUtils.hasAuthority("ASSESSMENT:MANAGE")) {
            return R.fail("无权生成该员工的报告");
        }
        return R.ok(reportService.generateManually(empId));
    }
}
