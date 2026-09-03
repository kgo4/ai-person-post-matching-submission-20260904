package com.example.matching.controller.system;

import com.example.matching.application.system.SkillTaxonomyMapApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.system.api.SkillTaxonomyMapRequest;
import com.example.matching.dto.system.api.SkillTaxonomyMapResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 技能→能力规则映射管理接口。
 * <p>
 * 供标签管理员维护「技能词（如 Vue3/SpringBoot）→ 能力层标签」的高置信映射规则，
 * 支撑技能归层分类器（AbilityTagTaxonomyClassifier）的规则匹配通道。
 */
@Tag(name = "技能归层规则", description = "技能词→能力标签映射规则的增删改查")
@RestController
@RequestMapping("/api/system/skill-taxonomy")
@RequiredArgsConstructor
public class SkillTaxonomyMapController {

    private final SkillTaxonomyMapApiFacade facade;

    @Operation(summary = "分页查询规则", description = "按技能词模糊匹配、按归属能力标签过滤，分页返回映射规则")
    @GetMapping("/page")
    public R<PageResponse<SkillTaxonomyMapResponse>> page(
            @Parameter(description = "当前页码，从1开始") @RequestParam(defaultValue = "1") long current,
            @Parameter(description = "每页记录数，默认20") @RequestParam(defaultValue = "20") long size,
            @Parameter(description = "技能词模糊匹配") @RequestParam(required = false) String keyword,
            @Parameter(description = "归属能力标签ID") @RequestParam(required = false) Long abilityTagId) {
        return R.ok(PageResponse.from(facade.pageRules(current, size, keyword, abilityTagId), e -> e));
    }

    @Operation(summary = "新增规则", description = "新增「技能词→能力标签」映射规则，技能词不可重复")
    @PostMapping
    public R<SkillTaxonomyMapResponse> create(@RequestBody SkillTaxonomyMapRequest request) {
        return R.ok(facade.createRule(request));
    }

    @Operation(summary = "更新规则", description = "按 id 更新映射规则的归属能力标签/置信度/来源等")
    @PutMapping("/{id}")
    public R<SkillTaxonomyMapResponse> update(
            @Parameter(description = "规则ID") @PathVariable Long id,
            @RequestBody SkillTaxonomyMapRequest request) {
        return R.ok(facade.updateRule(id, request));
    }

    @Operation(summary = "启用/停用规则", description = "修改规则状态：0停用，1启用")
    @PutMapping("/{id}/status")
    public R<Void> updateStatus(
            @Parameter(description = "规则ID") @PathVariable Long id,
            @Parameter(description = "目标状态：0停用，1启用") @RequestParam Integer status) {
        facade.updateStatus(id, status);
        return R.ok();
    }

    @Operation(summary = "删除规则", description = "按 id 删除映射规则")
    @DeleteMapping("/{id}")
    public R<Void> delete(@Parameter(description = "规则ID") @PathVariable Long id) {
        facade.deleteRule(id);
        return R.ok();
    }
}
