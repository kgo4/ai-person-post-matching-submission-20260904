package com.example.matching.controller.learning;

import com.example.matching.application.common.SelfScopeSupport;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.learning.LearningOutcomeContextResponse;
import com.example.matching.dto.learning.LearningOutcomeReviewDTO;
import com.example.matching.dto.learning.LearningOutcomeSubmissionResponse;
import com.example.matching.dto.learning.LearningOutcomeSubmitDTO;
import com.example.matching.entity.learning.EmpLearningOutcomeSubmission;
import com.example.matching.service.learning.EmpLearningOutcomeReviewService;
import com.example.matching.service.learning.LearningOutcomeContextQueryService;
import com.example.matching.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学习成果 HR 复核闭环接口（HR 匹配闭环设计 P4）。
 *
 * <p>权限：读放行靠 SecurityConfig 的 {@code /api/learning-outcomes/**} 规则
 * （{@code ASSESSMENT:SELF} 或 {@code ASSESSMENT:MANAGE}）；HR 专属动作再用
 * {@link SelfScopeSupport#assertManagementOnly} 收口，员工侧不能自评自批。</p>
 *
 * <p>员工侧只有「提交」与「查本人记录」两个入口，**不提供任何修改复核结论的接口**。</p>
 */
@Tag(name = "学习成果复核", description = "员工提交学习成果 → HR 复核通过后回写能力证据（闭环 P4）")
@RestController
@RequestMapping("/api/learning-outcomes")
@RequiredArgsConstructor
public class LearningOutcomeReviewController {

    private final EmpLearningOutcomeReviewService reviewService;
    private final LearningOutcomeContextQueryService contextQueryService;
    private final SelfScopeSupport selfScopeSupport;

    @Operation(summary = "提交学习成果（员工）",
            description = "提交后进入「待复核」，不立即回写能力证据；人员范围由登录身份固定为本人。")
    @PostMapping
    public R<Long> submit(@Valid @RequestBody LearningOutcomeSubmitDTO dto) {
        return R.ok(reviewService.submit(selfScopeSupport.currentEmpId(), dto));
    }

    @Operation(summary = "我的学习成果提交记录（员工）",
            description = "含复核状态与驳回理由，用于员工侧展示进度。")
    @GetMapping("/my")
    public R<PageResponse<LearningOutcomeSubmissionResponse>> mySubmissions(
            @Parameter(description = "当前页码") @RequestParam(defaultValue = "1") long current,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") long size) {
        return R.ok(reviewService.pageMy(selfScopeSupport.currentEmpId(), current, size));
    }

    @Operation(summary = "待复核数量（HR）", description = "工作台角标 / 复核页待办数")
    @GetMapping("/pending-count")
    public R<Long> pendingCount() {
        return R.ok(reviewService.countByReviewStatus(EmpLearningOutcomeSubmission.STATUS_PENDING));
    }

    @Operation(summary = "学习成果复核列表（HR）", description = "待复核优先排序")
    @GetMapping
    public R<PageResponse<LearningOutcomeSubmissionResponse>> pageForReview(
            @Parameter(description = "当前页码") @RequestParam(defaultValue = "1") long current,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") long size,
            @Parameter(description = "复核状态：0待复核 1通过 2驳回")
            @RequestParam(required = false) Integer reviewStatus) {
        selfScopeSupport.assertManagementOnly("学习成果复核");
        return R.ok(reviewService.pageForReview(current, size, reviewStatus));
    }

    @Operation(summary = "查看提交单对应的学习路径与学习情况（HR）",
            description = "🔴 可见性闸门：HR 默认看不到员工的学习路径，**提交单是唯一入口** —— "
                    + "员工发起能力提升申请后，HR 才能看到该员工在这条匹配下的学习路径进展与该项能力的学习记录。"
                    + "因此本接口只接受提交单ID，不提供按员工/按匹配记录直查学习路径的入口。")
    @GetMapping("/{id}/learning-context")
    public R<LearningOutcomeContextResponse> getLearningContext(@PathVariable Long id) {
        selfScopeSupport.assertManagementOnly("学习成果复核");
        return R.ok(contextQueryService.getContext(id));
    }

    @Operation(summary = "复核通过（HR）",
            description = "通过后调用既有回写链路，能力证据升级、能力画像自动更新；回写失败则整体回滚。")
    @PostMapping("/{id}/approve")
    public R<Void> approve(@PathVariable Long id,
                           @RequestBody(required = false) LearningOutcomeReviewDTO dto) {
        selfScopeSupport.assertManagementOnly("学习成果复核");
        reviewService.approve(id, dto == null ? null : dto.getComment(), SecurityUtils.getCurrentUserId());
        return R.ok();
    }

    @Operation(summary = "复核驳回（HR）", description = "驳回必填理由，员工可修改后重新提交（新单据，旧单留痕）")
    @PostMapping("/{id}/reject")
    public R<Void> reject(@PathVariable Long id,
                          @RequestBody(required = false) LearningOutcomeReviewDTO dto) {
        selfScopeSupport.assertManagementOnly("学习成果复核");
        reviewService.reject(id, dto == null ? null : dto.getComment(), SecurityUtils.getCurrentUserId());
        return R.ok();
    }
}
