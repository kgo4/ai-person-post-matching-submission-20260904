package com.example.matching.controller.system;

import com.example.matching.application.system.AbilityTagGovernanceApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.utils.SecurityUtils;
import com.example.matching.vo.system.AbilityTagRelationVO;
import com.example.matching.schedule.PostAbilityTagGovernanceBackfillScheduler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.time.LocalDateTime;
import java.util.*;

@Tag(name = "能力标签治理")
@RestController
@RequestMapping("/api/system/tag-governance")
@RequiredArgsConstructor
public class AbilityTagGovernanceController {

    /** Compatibility overload retained for pre-taxonomy controller tests/clients. */
    public R<Long> approveCandidate(Long id, String tagCategory, Long parentDomainId) {
        return R.ok(facade.approveCandidate(id, tagCategory, parentDomainId));
    }

    public R<Long> approveCandidate(Long id, String tagCategory) {
        return R.ok(facade.approveCandidate(id, tagCategory, 0L));
    }

    private final AbilityTagGovernanceApiFacade facade;
    private final PostAbilityTagGovernanceBackfillScheduler backfillScheduler;

    @Operation(summary = "回填岗位能力到系统标签库")
    @PostMapping("/backfill-post-abilities")
    public R<Integer> backfillPostAbilities() {
        return R.ok(backfillScheduler.runOnce());
    }

    @Operation(summary = "分页查询候选标签")
    @GetMapping("/candidates")
    public R<?> pageCandidates(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") Integer pageSize,
            @Parameter(description = "状态") @RequestParam(required = false) String status,
            @Parameter(description = "来源类型") @RequestParam(required = false) String sourceType) {
        return R.ok(facade.pageCandidates(pageNum, pageSize, status, sourceType));
    }

    @Operation(summary = "批准候选标签")
    @PostMapping("/candidates/{id}/approve")
    public R<Long> approveCandidate(
            @PathVariable Long id,
            @Parameter(description = "目标一级能力域（可选；不传则由系统自动归层，归层失败降级为平铺标签）") @RequestParam(required = false) Long parentDomainId,
            @Parameter(description = "标签分类") @RequestParam(required = false) String tagCategory,
            @Parameter(description = "审核时修订后的正式标签名称") @RequestParam(required = false) String candidateName,
            @Parameter(description = "审核意见") @RequestParam(required = false) String comment) {
        // 审核人由服务端从安全上下文推导，禁止客户端传入（防冒名审核）
        return R.ok(facade.approveCandidate(id, tagCategory, parentDomainId, SecurityUtils.getCurrentUserId(), candidateName, comment));
    }

    @Operation(summary = "拒绝候选标签")
    @PostMapping("/candidates/{id}/reject")
    public R<Void> rejectCandidate(
            @PathVariable Long id,
            @Parameter(description = "拒绝原因") @RequestParam(required = false) String reason) {
        facade.rejectCandidate(id, SecurityUtils.getCurrentUserId(), reason);
        return R.ok();
    }

    @Operation(summary = "合并候选标签到已有标签")
    @PostMapping("/candidates/{id}/merge")
    public R<Void> mergeCandidate(
            @PathVariable Long id,
            @Parameter(description = "目标标签ID") @RequestParam Long targetTagId) {
        facade.mergeCandidate(id, targetTagId, SecurityUtils.getCurrentUserId());
        return R.ok();
    }

    @Operation(summary = "计算标签使用统计")
    @PostMapping("/stats/compute")
    public R<Void> computeStats() {
        facade.computeStats();
        return R.ok();
    }

    @Operation(summary = "获取标签使用统计")
    @GetMapping("/stats")
    public R<?> getUsageStats(
            @Parameter(description = "返回数量") @RequestParam(defaultValue = "50") int topN) {
        return R.ok(facade.getUsageStats(topN));
    }

    @Operation(summary = "分页查询标签关系")
    @GetMapping("/relations")
    public R<IPage<AbilityTagRelationVO>> pageRelations(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "100") Integer pageSize,
            @Parameter(description = "源标签ID") @RequestParam(required = false) Long sourceTagId,
            @Parameter(description = "目标标签ID") @RequestParam(required = false) Long targetTagId,
            @Parameter(description = "关系类型") @RequestParam(required = false) String relationType,
            @Parameter(description = "状态") @RequestParam(required = false) String status) {
        return R.ok(facade.pageRelations(pageNum, pageSize, sourceTagId, targetTagId, relationType, status));
    }

    @Operation(summary = "手动创建标签关系")
    @PostMapping("/relations")
    public R<?> createRelation(
            @Parameter(description = "源标签ID") @RequestParam Long sourceTagId,
            @Parameter(description = "目标标签ID") @RequestParam Long targetTagId,
            @Parameter(description = "关系类型") @RequestParam String relationType,
            @Parameter(description = "相似度分数") @RequestParam(required = false) Double similarityScore,
            @Parameter(description = "备注") @RequestParam(required = false) String remark) {
        return R.ok(facade.createRelation(sourceTagId, targetTagId, relationType, similarityScore, remark));
    }

    @Operation(summary = "审核通过标签关系")
    @PostMapping("/relations/{id}/approve")
    public R<Void> approveRelation(@PathVariable Long id) {
        facade.approveRelation(id, SecurityUtils.getCurrentUserId());
        return R.ok();
    }

    @Operation(summary = "审核拒绝标签关系")
    @PostMapping("/relations/{id}/reject")
    public R<Void> rejectRelation(@PathVariable Long id) {
        facade.rejectRelation(id, SecurityUtils.getCurrentUserId());
        return R.ok();
    }

    @Operation(summary = "自动发现标签关系（向量相似度）")
    @PostMapping("/relations/discover")
    public R<Integer> discoverRelations(
            @Parameter(description = "相似度阈值") @RequestParam(defaultValue = "0.7") double threshold) {
        return R.ok(facade.discoverRelations(threshold));
    }

    @Operation(summary = "立即执行标签自动归并")
    @PostMapping("/merge/execute")
    public R<Map<String, Object>> executeMerge(
            @Parameter(description = "相似度阈值（0~1）") @RequestParam(defaultValue = "0.9") double threshold) {
        return R.ok(facade.executeMerge(threshold));
    }

    @Operation(summary = "设置定时归并任务")
    @PostMapping("/merge/schedule")
    public R<Map<String, Object>> scheduleMerge(
            @Parameter(description = "相似度阈值（0~1）") @RequestParam(defaultValue = "0.9") double threshold,
            @Parameter(description = "执行时间（ISO格式）")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime scheduledTime) {
        if (!scheduledTime.isAfter(LocalDateTime.now())) {
            return R.fail("执行时间必须晚于当前时间");
        }
        return R.ok(facade.scheduleMerge(threshold, scheduledTime, com.example.matching.utils.SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "取消等待中的定时归并任务")
    @PostMapping("/merge/cancel")
    public R<Void> cancelMerge(@Parameter(description = "任务ID") @RequestParam String taskId) {
        boolean cancelled = facade.cancelMerge(taskId);
        return cancelled ? R.ok() : R.fail("任务不存在或已执行");
    }

    @Operation(summary = "查询等待中的定时归并任务")
    @GetMapping("/merge/pending")
    public R<List<Map<String, Object>>> listPendingMerges() {
        return R.ok(facade.listPendingMerges());
    }

    @Operation(summary = "查询当前用户的定时归并结果通知")
    @GetMapping("/merge/notifications")
    public R<List<Map<String, Object>>> listMergeNotifications() {
        return R.ok(facade.listRecentMergeNotifications(com.example.matching.utils.SecurityUtils.getCurrentUserId()));
    }
}
