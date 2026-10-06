package com.example.matching.controller.interview;

import com.example.matching.application.common.SelfScopeSupport;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.interview.CommunicationInterviewResponse;
import com.example.matching.dto.interview.CreateInterviewRequest;
import com.example.matching.dto.interview.InterviewBriefingResponse;
import com.example.matching.dto.interview.InterviewCandidateResponse;
import com.example.matching.dto.interview.InterviewResponseRequest;
import com.example.matching.dto.interview.InterviewResultRequest;
import com.example.matching.service.interview.CommunicationInterviewService;
import com.example.matching.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * HR-员工视频终面接口（HR 匹配闭环设计 P5）。
 *
 * <p>媒体走讯飞会议，平台不做媒体，只管「发起邀请 → 通知 → 留痕 → 定论」。</p>
 *
 * <p>权限：读放行靠 SecurityConfig 的 {@code /api/communication-interviews/**} 规则
 * （{@code EMPLOYEE:READ} 或 {@code ASSESSMENT:SELF}）。HR 专属动作统一用
 * {@link SelfScopeSupport#assertManagementOnly} 收口。</p>
 *
 * <p>员工侧的写入口只有一个：{@code PUT /{id}/respond}（接受 / 放弃邀请）。
 * 它不做 {@code assertManagementOnly}，而是把归属校验下沉到服务层 ——
 * 员工只能响应对应**本人**的终面，越权枚举 ID 会被拒；其余接口对员工只读。</p>
 */
@Tag(name = "视频终面", description = "HR-员工视频终面（讯飞会议）记录与会话（闭环 P5）")
@RestController
@RequestMapping("/api/communication-interviews")
@RequiredArgsConstructor
public class CommunicationInterviewController {

    private final CommunicationInterviewService interviewService;
    private final SelfScopeSupport selfScopeSupport;

    @Operation(summary = "可邀约候选池（HR）",
            description = "全部匹配记录（不限匹配状态与推送状态）；匹配通过（强适配/适配）的排在前面并标记 recommended=true。"
                    + "平台不自动发起，由 HR 手选。")
    @GetMapping("/candidates")
    public R<List<InterviewCandidateResponse>> candidates(
            @Parameter(description = "最大条数") @RequestParam(defaultValue = "100") int limit) {
        selfScopeSupport.assertManagementOnly("视频终面");
        return R.ok(interviewService.listCandidates(limit));
    }

    @Operation(summary = "沟通要点文档（HR）",
            description = "按 empId + matchingRecordId 实时聚合四区要点；实时不落库，发起时以快照随记录留存。")
    @GetMapping("/briefing")
    public R<InterviewBriefingResponse> briefing(
            @Parameter(description = "员工档案ID") @RequestParam Long empId,
            @Parameter(description = "关联匹配记录ID（可空）") @RequestParam(required = false) Long matchingRecordId) {
        selfScopeSupport.assertManagementOnly("视频终面");
        return R.ok(interviewService.buildBriefing(empId, matchingRecordId));
    }

    @Operation(summary = "待沟通数量（HR）", description = "工作台角标")
    @GetMapping("/pending-count")
    public R<Long> pendingCount() {
        selfScopeSupport.assertManagementOnly("视频终面");
        return R.ok(interviewService.countPending());
    }

    @Operation(summary = "发起视频终面（HR）",
            description = "校验员工存在 + 会议域名白名单；关联的匹配记录只做存在性校验，**不要求匹配已通过**"
                    + "（匹配通过仅作推荐）。发起后给员工发 INVITE_MEETING 通知。")
    @PostMapping
    public R<Long> create(@Valid @RequestBody CreateInterviewRequest req) {
        selfScopeSupport.assertManagementOnly("视频终面");
        return R.ok(interviewService.create(req, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "终面记录分页（HR）", description = "HR 追踪视图数据源")
    @GetMapping
    public R<PageResponse<CommunicationInterviewResponse>> page(
            @Parameter(description = "当前页码") @RequestParam(defaultValue = "1") long current,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") long size,
            @Parameter(description = "员工档案ID") @RequestParam(required = false) Long empId,
            @Parameter(description = "状态：0待沟通 1已完成 2已取消")
            @RequestParam(required = false) Integer status) {
        selfScopeSupport.assertManagementOnly("视频终面");
        return R.ok(interviewService.page(current, size, empId, status));
    }

    @Operation(summary = "我的视频终面记录（员工）",
            description = "只读：含状态、结论与 HR 评价原文（需求方明确要求不脱敏）。")
    @GetMapping("/my")
    public R<PageResponse<CommunicationInterviewResponse>> my(
            @Parameter(description = "当前页码") @RequestParam(defaultValue = "1") long current,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") long size) {
        return R.ok(interviewService.pageMy(selfScopeSupport.currentEmpId(), current, size));
    }

    @Operation(summary = "响应终面邀请（员工）",
            description = "员工本人接受或放弃：1接受 2放弃。只能操作归属本人的终面（服务层强校验，不信任入参）。"
                    + "「接受」只记录意愿、状态仍为待沟通；「放弃」直接把该场终面置为已取消并通知发起人。"
                    + "响应一次后不可更改，需变更请由 HR 重新发起。")
    @PutMapping("/{id}/respond")
    public R<Void> respond(@PathVariable Long id, @Valid @RequestBody InterviewResponseRequest req) {
        interviewService.respond(id, selfScopeSupport.currentEmpId(), req.getResponse(), req.getComment());
        return R.ok();
    }

    @Operation(summary = "取消视频终面（HR）", description = "仅待沟通状态可取消")
    @PutMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) {
        selfScopeSupport.assertManagementOnly("视频终面");
        interviewService.cancel(id);
        return R.ok();
    }

    @Operation(summary = "录入终面结论（HR）",
            description = "结论 1通过/2不通过/3待定；待定保持待沟通可改期再谈。平台无法感知是否真的入会，以 HR 录入为准。")
    @PutMapping("/{id}/result")
    public R<Void> recordResult(@PathVariable Long id, @Valid @RequestBody InterviewResultRequest req) {
        selfScopeSupport.assertManagementOnly("视频终面");
        interviewService.recordResult(id, req.getResult(), req.getComment());
        return R.ok();
    }
}
