package com.example.matching.controller.post;

import com.example.matching.application.post.PostTrendApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.post.api.PostTrendCandidateDetailResponse;
import com.example.matching.dto.post.api.PostTrendCandidateResponse;
import com.example.matching.dto.post.api.PostTrendCandidateUpdateRequest;
import com.example.matching.dto.post.api.PostTrendLandResult;
import com.example.matching.dto.post.api.PostTrendReviewRequest;
import com.example.matching.dto.post.api.TrendBatchConfirmRequest;
import com.example.matching.dto.post.api.TrendBatchConfirmResult;
import com.example.matching.dto.post.api.TrendNewTagCandidateResponse;
import com.example.matching.dto.post.api.TrendTagAdoptRequest;
import com.example.matching.dto.post.api.TrendTaskCreateRequest;
import com.example.matching.service.post.PostTrendConfirmService;
import com.example.matching.service.post.PostTrendQueryService;
import com.example.matching.service.post.PostTrendTaskService;
import com.example.matching.utils.SecurityUtils;
import com.example.matching.vo.post.PostTrendProgressVO;
import com.example.matching.vo.post.PostTrendTaskVO;
import com.example.matching.vo.post.TrendPendingSummaryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 岗位趋势发现。
 * <p>
 * 全链路只有一次人工输入：把权威材料（政府红头文件、政策文件、市场职业报告）上传到
 * {@code /api/post/evolution/sources/**} 拿到 documentId，然后发起解析。解析产出两类候选
 * ——「趋势岗位」与「既有岗位能力变更」——都必须由岗位管理员审核后才会落地，
 * 不存在任何自动创建岗位的路径。
 * <p>
 * 权限：整族含 GET 一律 {@code POST:MANAGE}（见 SecurityConfig）。
 */
@Tag(name = "岗位趋势发现", description = "上传权威材料 → AI 解析趋势岗位与能力变更 → 岗位管理员审核确认后落地")
@RestController
@RequestMapping("/api/post/trend")
@RequiredArgsConstructor
public class PostTrendController {

    private final PostTrendTaskService trendTaskService;
    private final PostTrendQueryService trendQueryService;
    private final PostTrendConfirmService trendConfirmService;
    private final PostTrendApiFacade trendApiFacade;

    @Operation(summary = "发起趋势解析", description = "引用已上传并索引的权威材料，异步解析出趋势岗位与能力变更候选")
    @PostMapping("/tasks")
    public R<PostTrendTaskVO> createTask(@Valid @RequestBody TrendTaskCreateRequest request) {
        var task = trendTaskService.createTask(request.sourceDocumentIds(), request.sourceCategories(),
                request.taskName(), currentUserId());
        return R.ok(trendTaskService.getTask(task.getId()));
    }

    @Operation(summary = "解析任务分页", description = "按发起时间倒序返回趋势解析任务")
    @GetMapping("/tasks/page")
    public R<PageResponse<PostTrendTaskVO>> pageTasks(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return R.ok(trendTaskService.pageTasks(current, size));
    }

    @Operation(summary = "解析任务详情")
    @GetMapping("/tasks/{taskId}")
    public R<PostTrendTaskVO> getTask(@PathVariable Long taskId) {
        return R.ok(trendTaskService.getTask(taskId));
    }

    @Operation(summary = "解析进度", description = "含分步进度与诊断信息；无候选时会说明原因")
    @GetMapping("/tasks/{taskId}/progress")
    public R<PostTrendProgressVO> getProgress(@PathVariable Long taskId) {
        return R.ok(trendTaskService.getProgress(taskId));
    }

    @Operation(summary = "取消解析", description = "任务已进入终态时返回 false，不覆盖已有结论")
    @PostMapping("/tasks/{taskId}/cancel")
    public R<Boolean> cancel(@PathVariable Long taskId) {
        return R.ok(trendTaskService.cancel(taskId));
    }

    @Operation(summary = "待人工审核汇总",
            description = "跨任务统计待确认候选数与待审任务数；供工作台待办使用，避免工作台拉全部任务逐个查候选")
    @GetMapping("/pending-summary")
    public R<TrendPendingSummaryVO> pendingSummary() {
        return R.ok(trendQueryService.pendingSummary());
    }

    @Operation(summary = "候选列表", description = "卡片字段：岗位名、相似度、治理徽标、前几个能力名")
    @GetMapping("/tasks/{taskId}/candidates")
    public R<PageResponse<PostTrendCandidateResponse>> listCandidates(
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @Parameter(description = "NEW_POST / ABILITY_CHANGE") @RequestParam(required = false) String candidateType,
            @Parameter(description = "PENDING / APPROVED / REJECTED") @RequestParam(required = false) String confirmStatus,
            @Parameter(description = "PASS / REVIEW / BLOCK") @RequestParam(required = false) String harnessDecision) {
        return R.ok(trendApiFacade.pageCandidates(current, size, taskId, candidateType, confirmStatus, harnessDecision));
    }

    @Operation(summary = "候选详情", description = "完整能力清单、证据原文、来源材料标题、待归位能力；供抽屉按需展示")
    @GetMapping("/candidates/{candidateId}")
    public R<PostTrendCandidateDetailResponse> getCandidateDetail(@PathVariable Long candidateId) {
        return R.ok(trendQueryService.getCandidateDetail(candidateId));
    }

    @Operation(summary = "审核候选（不落地）", description = "仅改状态：APPROVED 标记通过但不建岗位；REJECTED 驳回。正常落地请用 confirm 接口")
    @PostMapping("/candidates/{candidateId}/review")
    public R<Boolean> review(@PathVariable Long candidateId,
                             @Valid @RequestBody PostTrendReviewRequest request) {
        return R.ok(trendConfirmService.review(candidateId, request.confirmStatus(),
                request.reviewComment(), currentUserId()));
    }

    @Operation(summary = "展开调整", description = "保存人工修改的岗位名、描述与能力清单；仅待确认候选可改，落库后会重算变更类型")
    @PutMapping("/candidates/{candidateId}/payload")
    public R<Void> updatePayload(@PathVariable Long candidateId,
                                 @Valid @RequestBody PostTrendCandidateUpdateRequest request) {
        trendConfirmService.updatePayload(candidateId, request);
        return R.ok();
    }

    @Operation(summary = "确认落地",
            description = "新岗位候选 → 同事务内建岗位并写入能力画像；能力变更候选 → 改写既有岗位能力画像。重复确认不会二次建岗")
    @PostMapping("/candidates/{candidateId}/confirm")
    public R<PostTrendLandResult> confirm(@PathVariable Long candidateId,
                                          @RequestBody(required = false) PostTrendReviewRequest request) {
        String comment = request == null ? null : request.reviewComment();
        return R.ok(trendConfirmService.land(candidateId, comment, currentUserId()));
    }

    @Operation(summary = "批量确认落地",
            description = "只对治理判定 PASS 且待确认的候选生效，其余逐条跳过并返回中文原因")
    @PostMapping("/candidates/batch-confirm")
    public R<TrendBatchConfirmResult> batchConfirm(@Valid @RequestBody TrendBatchConfirmRequest request) {
        return R.ok(trendApiFacade.batchConfirm(request, currentUserId()));
    }

    @Operation(summary = "新能力标签候选", description = "本次解析提出但未归位到既有标签的能力；可「采用现有标签」或「忽略」")
    @GetMapping("/tags/new-candidates")
    public R<List<TrendNewTagCandidateResponse>> listNewTagCandidates(
            @Parameter(description = "趋势任务ID；不传则返回最近的待审候选") @RequestParam(required = false) Long taskId) {
        return R.ok(trendQueryService.listNewTagCandidates(taskId));
    }

    @Operation(summary = "采用现有标签",
            description = "把该能力归位到既有 L2 能力标签，并回填到本任务下待确认候选的能力清单里")
    @PostMapping("/tags/new-candidates/{candidateId}/adopt")
    public R<Void> adoptNewTag(@PathVariable Long candidateId,
                               @Valid @RequestBody TrendTagAdoptRequest request) {
        trendConfirmService.adoptNewTag(candidateId, request.tagId(), request.comment(), currentUserId());
        return R.ok();
    }

    @Operation(summary = "忽略新能力", description = "认为该能力名不该进入标签体系，候选置为已驳回")
    @PostMapping("/tags/new-candidates/{candidateId}/ignore")
    public R<Void> ignoreNewTag(@PathVariable Long candidateId,
                                @RequestBody(required = false) TrendTagAdoptRequest request) {
        trendConfirmService.ignoreNewTag(candidateId, request == null ? null : request.comment(), currentUserId());
        return R.ok();
    }

    /** 当前操作人ID；未登录（如内部调用）时为 null，由各 Service 按需兜底。 */
    private Long currentUserId() {
        return SecurityUtils.getCurrentUserId();
    }
}
