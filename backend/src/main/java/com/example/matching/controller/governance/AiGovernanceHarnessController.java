package com.example.matching.controller.governance;

import com.example.matching.application.governance.AiGovernanceHarnessApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.governance.api.AiHarnessCheckLogResponse;
import com.example.matching.dto.governance.api.AssessmentHarnessPersonGroupResponse;
import com.example.matching.dto.governance.api.AssessmentHarnessReviewView;
import com.example.matching.dto.governance.api.BatchHarnessReviewRequest;
import com.example.matching.dto.governance.api.BatchHarnessReviewResult;
import com.example.matching.dto.governance.api.HarnessCheckRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;

@Tag(name = "AI 治理", description = "AI 生成内容的可信审核与治理记录。")
@RestController
@RequestMapping("/api/ai-governance/harness")
@RequiredArgsConstructor
public class AiGovernanceHarnessController {

    private final AiGovernanceHarnessApiFacade facade;

    @Operation(summary = "分页查询治理记录")
    @GetMapping("/checks/page")
    public R<PageResponse<AiHarnessCheckLogResponse>> pageChecks(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String scenario,
            @RequestParam(required = false) String decision,
            @RequestParam(required = false) String reviewStatus,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String claimType,
            @RequestParam(required = false) Integer isSelfEvidence,
            @RequestParam(required = false) Boolean assessmentOnly) {
        return R.ok(facade.pageChecks(current, size, scenario, decision,
                reviewStatus, riskLevel, claimType, isSelfEvidence, assessmentOnly));
    }

    @Operation(summary = "按人员查询最终能力审核队列")
    @GetMapping("/assessment/person-groups")
    public R<List<AssessmentHarnessPersonGroupResponse>> assessmentPersonGroups(
            @RequestParam(defaultValue = "PENDING") AssessmentHarnessReviewView view) {
        return R.ok(facade.listAssessmentPersonGroups(view));
    }

    @Operation(summary = "获取治理摘要统计")
    @GetMapping("/checks/summary")
    public R<Map<String, Object>> summary(@RequestParam(required = false) Boolean assessmentOnly) {
        return R.ok(facade.summary(assessmentOnly));
    }

    @Operation(summary = "更新审核状态（采纳/驳回/标记已处理）")
    @PostMapping("/checks/{id}/review")
    public R<Void> updateReviewStatus(@PathVariable Long id,
                                      @RequestBody HarnessCheckRequest request) {
        facade.updateReviewStatus(id, request);
        return R.ok();
    }

    @Operation(summary = "批量审核 Harness 记录")
    @PostMapping("/checks/batch-review")
    public R<BatchHarnessReviewResult> batchReview(@RequestBody BatchHarnessReviewRequest request) {
        return R.ok(facade.batchReview(request));
    }

    @Operation(summary = "采纳并应用到业务数据")
    @PostMapping("/checks/{id}/accept")
    public R<Boolean> acceptReview(@PathVariable Long id,
                                   @RequestBody(required = false) HarnessCheckRequest request) {
        boolean success = facade.acceptReview(id,
                request != null ? request.reviewComment() : null);
        return success ? R.ok(Boolean.TRUE) : R.fail("应用失败，请检查记录状态");
    }

    /**
     * @deprecated 使用 POST /checks/{id}/accept 代替；待前端迁移完成后删除。
     */
    @Deprecated
    @Operation(summary = "采纳并应用到业务数据（已废弃，请使用 /accept）")
    @PostMapping("/checks/{id}/apply")
    public R<Boolean> applyToBusiness(@PathVariable Long id,
                                      @RequestBody(required = false) HarnessCheckRequest request) {
        boolean success = facade.applyToBusiness(id,
                request != null ? request.reviewComment() : null);
        return success ? R.ok(Boolean.TRUE) : R.fail("应用失败，请检查记录状态");
    }
}
