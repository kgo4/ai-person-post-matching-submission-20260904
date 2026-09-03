package com.example.matching.controller.contest;

import com.example.matching.application.contest.ContestEvidenceApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.contest.EvidenceCreateDTO;
import com.example.matching.dto.contest.EvidenceReviewDTO;
import com.example.matching.dto.contest.api.ContestEvidenceResponse;
import com.example.matching.dto.contest.api.EvidenceQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "证据治理", description = "模型质量证据治理模块，用于记录、审核和统计各类证据。")
@RestController
@RequestMapping("/api/contest/evidence")
@RequiredArgsConstructor
public class ContestEvidenceController {

    private final ContestEvidenceApiFacade facade;

    @Operation(summary = "创建证据", description = "手动或通过服务集成创建一条证据记录。")
    @PostMapping
    public R<ContestEvidenceResponse> create(@RequestBody EvidenceCreateDTO dto) {
        return R.ok(facade.create(dto));
    }

    @Operation(summary = "分页查询证据", description = "按来源类型、目标类型、状态、能力名称分页查询证据。")
    @GetMapping("/page")
    public R<PageResponse<ContestEvidenceResponse>> page(
            @Parameter(description = "当前页码") @RequestParam(defaultValue = "1") long current,
            @Parameter(description = "每页记录数") @RequestParam(defaultValue = "10") long size,
            @Parameter(description = "来源类型") @RequestParam(required = false) String sourceType,
            @Parameter(description = "目标类型") @RequestParam(required = false) String targetType,
            @Parameter(description = "证据状态") @RequestParam(required = false) String evidenceStatus,
            @Parameter(description = "能力名称") @RequestParam(required = false) String abilityName) {
        EvidenceQuery query = new EvidenceQuery(sourceType, targetType, evidenceStatus, abilityName);
        return R.ok(facade.page(current, size, query));
    }

    @Operation(summary = "获取证据详情", description = "根据ID获取证据详情。")
    @GetMapping("/{id}")
    public R<ContestEvidenceResponse> detail(@PathVariable Long id) {
        return R.ok(facade.detail(id));
    }

    @Operation(summary = "审核证据", description = "对证据进行人工审核，设置为VERIFIED或REJECTED。")
    @PostMapping("/{id}/review")
    public R<Void> review(
            @PathVariable Long id,
            @RequestBody EvidenceReviewDTO dto,
            @Parameter(description = "审核人ID") @RequestParam(required = false, defaultValue = "0") Long userId) {
        facade.review(id, dto, userId);
        return R.ok();
    }

    @Operation(summary = "获取证据统计摘要", description = "获取证据来源分布、状态分布、平均可信度。")
    @GetMapping("/summary")
    public R<Map<String, Object>> summary() {
        return R.ok(facade.summary());
    }

    @Operation(summary = "获取人员能力证据链", description = "按人员聚合能力项、来源证据、置信度和可信度。")
    @GetMapping("/employee/{empId}/chain")
    public R<Map<String, Object>> employeeChain(@PathVariable Long empId) {
        return R.ok(facade.employeeChain(empId));
    }

    @Operation(summary = "获取岗位能力需求证据链", description = "按岗位聚合能力需求、来源证据、置信度和可信度。")
    @GetMapping("/post/{postId}/chain")
    public R<Map<String, Object>> postChain(@PathVariable Long postId) {
        return R.ok(facade.postChain(postId));
    }

    @Operation(summary = "回填证据", description = "从现有记录（JD导入、简历解析、匹配反馈）回填证据。")
    @PostMapping("/backfill")
    public R<Map<String, Object>> backfill(
            @Parameter(description = "来源类型：JD_IMPORT/RESUME_PARSE/MATCHING_FEEDBACK") @RequestParam String sourceType,
            @Parameter(description = "最大回填数量") @RequestParam(defaultValue = "100") int limit) {
        return R.ok(facade.backfill(sourceType, limit));
    }
}
