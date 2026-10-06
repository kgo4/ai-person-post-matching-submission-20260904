package com.example.matching.controller.system;

import com.example.matching.application.system.AbilityTagCandidateApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.system.api.AbilityTagCandidateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "候选标签管理", description = "AI发现的新能力标签审核管理")
@RestController
@RequestMapping("/api/system/tag-candidate")
@RequiredArgsConstructor
public class AbilityTagCandidateController {

    /** Compatibility overload for pre-taxonomy clients. */
    public R<Long> approve(Long id, String comment) {
        return R.ok(facade.approve(id, 0L, comment));
    }

    private final AbilityTagCandidateApiFacade facade;

    @Operation(summary = "分页查询候选标签")
    @GetMapping("/page")
    public R<PageResponse<AbilityTagCandidateResponse>> pageCandidates(
            @Parameter(description = "当前页码") @RequestParam(defaultValue = "1") long current,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") long size,
            @Parameter(description = "状态筛选") @RequestParam(required = false) String status,
            @Parameter(description = "来源筛选") @RequestParam(required = false) String sourceType,
            @Parameter(description = "关键词搜索") @RequestParam(required = false) String keyword) {
        return R.ok(facade.page(current, size, status, sourceType, keyword));
    }

    @Operation(summary = "获取高频候选标签")
    @GetMapping("/high-frequency")
    public R<List<AbilityTagCandidateResponse>> getHighFrequency(
            @Parameter(description = "最低出现次数") @RequestParam(defaultValue = "3") int threshold) {
        return R.ok(facade.getHighFrequency(threshold));
    }

    @Operation(summary = "统计各状态数量")
    @GetMapping("/count-by-status")
    public R<Map<String, Long>> countByStatus() {
        return R.ok(facade.countByStatus());
    }

    @Operation(summary = "审核通过（升级为正式标签）")
    @PostMapping("/{id}/approve")
    public R<Long> approve(
            @RequestParam Long parentDomainId,
            @Parameter(description = "候选标签ID", required = true) @PathVariable Long id,
            @Parameter(description = "审核意见") @RequestParam(required = false) String comment) {
        Long newTagId = facade.approve(id, parentDomainId, comment);
        return R.ok(newTagId);
    }

    @Operation(summary = "审核拒绝")
    @PostMapping("/{id}/reject")
    public R<Void> reject(
            @Parameter(description = "候选标签ID", required = true) @PathVariable Long id,
            @Parameter(description = "审核意见") @RequestParam(required = false) String comment) {
        facade.reject(id, comment);
        return R.ok();
    }

    @Operation(summary = "合并到已有正式标签")
    @PostMapping("/{id}/merge")
    public R<Void> merge(
            @Parameter(description = "候选标签ID", required = true) @PathVariable Long id,
            @Parameter(description = "目标正式标签ID", required = true) @RequestParam Long targetTagId,
            @Parameter(description = "审核意见") @RequestParam(required = false) String comment) {
        facade.merge(id, targetTagId, comment);
        return R.ok();
    }

    @Operation(summary = "删除候选标签")
    @DeleteMapping("/{id}")
    public R<Void> delete(@Parameter(description = "候选标签ID", required = true) @PathVariable Long id) {
        facade.delete(id);
        return R.ok();
    }
}
