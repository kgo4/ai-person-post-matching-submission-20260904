package com.example.matching.controller.ability;

import com.example.matching.application.ability.AgentMemoryGovernanceApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.ability.api.AgentMemoryResponse;
import com.example.matching.dto.ability.api.AgentMemoryUpdateRequest;
import com.example.matching.dto.ability.api.GovernanceEventQuery;
import com.example.matching.dto.ability.api.GovernanceEventResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Agent记忆治理中心", description = "Agent记忆的管理接口，包括启用/禁用/编辑/过期等操作")
@RestController
@RequestMapping("/api/governance/agent-memory")
@RequiredArgsConstructor
public class AgentMemoryGovernanceController {

    private final AgentMemoryGovernanceApiFacade facade;

    @Operation(summary = "分页查询Agent记忆", description = "分页查询Agent记忆列表，支持按状态、类型、范围、标签等筛选")
    @GetMapping("/page")
    public R<PageResponse<AgentMemoryResponse>> page(
            @Parameter(description = "页码", example = "1") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页大小", example = "10") @RequestParam(defaultValue = "10") Integer pageSize,
            @Parameter(description = "状态筛选") @RequestParam(required = false) String status,
            @Parameter(description = "记忆类型筛选") @RequestParam(required = false) String memoryType,
            @Parameter(description = "适用范围筛选") @RequestParam(required = false) String scope,
            @Parameter(description = "关键词搜索") @RequestParam(required = false) String keyword) {
        return R.ok(facade.pageMemories(pageNum, pageSize, status, memoryType, scope, keyword));
    }

    @Operation(summary = "获取记忆详情", description = "根据ID获取Agent记忆详情")
    @GetMapping("/{id}")
    public R<AgentMemoryResponse> getById(
            @Parameter(description = "记忆ID", required = true) @PathVariable Long id) {
        return R.ok(facade.getById(id));
    }

    @Operation(summary = "更新记忆", description = "更新Agent记忆的内容、优先级等信息")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public R<Void> update(
            @Parameter(description = "记忆ID", required = true) @PathVariable Long id,
            @Valid @RequestBody AgentMemoryUpdateRequest request) {
        facade.update(id, request);
        return R.ok();
    }

    @Operation(summary = "启用记忆", description = "将Agent记忆状态设置为ACTIVE")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/enable")
    public R<Void> enable(
            @Parameter(description = "记忆ID", required = true) @PathVariable Long id) {
        facade.enable(id);
        return R.ok();
    }

    @Operation(summary = "禁用记忆", description = "将Agent记忆状态设置为DISABLED")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/disable")
    public R<Void> disable(
            @Parameter(description = "记忆ID", required = true) @PathVariable Long id) {
        facade.disable(id);
        return R.ok();
    }

    @Operation(summary = "过期记忆", description = "将Agent记忆状态设置为EXPIRED")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/expire")
    public R<Void> expire(
            @Parameter(description = "记忆ID", required = true) @PathVariable Long id) {
        facade.expire(id);
        return R.ok();
    }

    @Operation(summary = "获取来源事件", description = "获取记忆来源的治理事件详情")
    @GetMapping("/{id}/source-event")
    public R<GovernanceEventResponse> getSourceEvent(
            @Parameter(description = "记忆ID", required = true) @PathVariable Long id) {
        return R.ok(facade.getSourceEvent(id));
    }

    @Operation(summary = "分页查询治理事件", description = "分页查询治理事件列表")
    @GetMapping("/events/page")
    public R<PageResponse<GovernanceEventResponse>> pageEvents(
            @Parameter(description = "页码", example = "1") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页大小", example = "10") @RequestParam(defaultValue = "10") Integer pageSize,
            @Parameter(description = "修改类型筛选") @RequestParam(required = false) String modifyType,
            @Parameter(description = "员工ID筛选") @RequestParam(required = false) Long empId,
            @Parameter(description = "标签ID筛选") @RequestParam(required = false) Long tagId) {
        GovernanceEventQuery query = new GovernanceEventQuery(pageNum, pageSize, modifyType, empId, tagId);
        return R.ok(facade.pageEvents(query));
    }

    @Operation(summary = "获取治理事件详情", description = "根据ID获取治理事件详情")
    @GetMapping("/events/{id}")
    public R<GovernanceEventResponse> getEventById(
            @Parameter(description = "事件ID", required = true) @PathVariable Long id) {
        return R.ok(facade.getEventById(id));
    }
}
