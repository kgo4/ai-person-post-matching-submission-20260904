package com.example.matching.controller.system;

import com.example.matching.application.system.AbilityTagApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.system.api.AbilityTagCreateRequest;
import com.example.matching.dto.system.api.AbilityTagResponse;
import com.example.matching.vo.system.AbilityTagTreeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "能力标签配置", description = "标签树查询、标签CRUD、分类过滤、状态管理")
@RestController
@RequestMapping("/api/system/ability-tag")
@RequiredArgsConstructor
public class AbilityTagController {

    private final AbilityTagApiFacade facade;

    @Operation(summary = "获取标签树", description = "获取所有已启用的能力标签，以树形结构返回（多层父子关系），用于前端树形控件展示")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功")
    })
    @GetMapping("/tree")
    public R<List<AbilityTagTreeVO>> tree() {
        return R.ok(facade.getTree());
    }

    @Operation(summary = "按分类获取标签树", description = "根据标签分类（TECHNICAL-技术能力、SOFT-软技能、BUSINESS-业务能力）获取对应分类的标签树")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功")
    })
    @GetMapping("/tree/{category}")
    public R<List<AbilityTagTreeVO>> treeByCategory(
            @Parameter(description = "标签分类：TECHNICAL-技术能力，SOFT-软技能，BUSINESS-业务能力", required = true) @PathVariable String category) {
        return R.ok(facade.getByCategory(category));
    }

    @Operation(summary = "分页查询标签", description = "按关键词模糊搜索标签名称、按分类筛选，以平铺列表形式分页返回标签数据")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功")
    })
    @GetMapping("/page")
    public R<PageResponse<AbilityTagResponse>> page(
            @Parameter(description = "当前页码，从1开始", example = "1") @RequestParam(defaultValue = "1") long current,
            @Parameter(description = "每页记录数，默认20条", example = "20") @RequestParam(defaultValue = "20") long size,
            @Parameter(description = "搜索关键词，匹配标签名称") @RequestParam(required = false) String keyword,
            @Parameter(description = "标签分类：TECHNICAL-技术能力，SOFT-软技能，BUSINESS-业务能力") @RequestParam(required = false) String category) {
        return R.ok(facade.page(current, size, keyword, category));
    }

    @Operation(summary = "获取标签详情", description = "根据标签ID查询单个能力标签的完整信息")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "404", description = "标签不存在")
    })
    @GetMapping("/{id}")
    public R<AbilityTagResponse> getById(
            @Parameter(description = "标签ID", required = true, example = "1") @PathVariable Long id) {
        return R.ok(facade.get(id));
    }

    @Operation(summary = "新增标签", description = "创建新的能力标签，需填写标签编码、名称、分类、层级、父标签ID等信息")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "创建成功"),
            @ApiResponse(responseCode = "400", description = "标签编码已存在或必填字段为空")
    })
    @PostMapping
    public R<AbilityTagResponse> save(
            @Parameter(description = "能力标签创建请求") @Valid @RequestBody AbilityTagCreateRequest request) {
        return R.ok(facade.create(request));
    }

    @Operation(summary = "更新标签", description = "根据标签ID修改能力标签的编码、名称、分类、层级、描述等信息")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "更新成功"),
            @ApiResponse(responseCode = "404", description = "标签不存在")
    })
    @PutMapping("/{id}")
    public R<Void> update(
            @Parameter(description = "标签ID", required = true, example = "1") @PathVariable Long id,
            @Parameter(description = "能力标签创建请求") @Valid @RequestBody AbilityTagCreateRequest request) {
        facade.update(id, request);
        return R.ok();
    }

    @Operation(summary = "修改标签状态", description = "启用或停用指定标签，停用后该标签将在树形查询中不显示")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "状态修改成功"),
            @ApiResponse(responseCode = "404", description = "标签不存在")
    })
    @PutMapping("/{id}/status")
    public R<Void> updateStatus(
            @Parameter(description = "标签ID", required = true, example = "1") @PathVariable Long id,
            @Parameter(description = "目标状态：0-停用，1-启用", required = true, example = "1") @RequestParam Integer status) {
        facade.updateStatus(id, status);
        return R.ok();
    }

    @Operation(summary = "批量生成标签向量", description = "为所有缺少向量嵌入的启用标签批量生成向量，用于语义匹配")
    @PostMapping("/batch-generate-vectors")
    public R<Integer> batchGenerateVectors() {
        int count = facade.batchGenerateVectors();
        return R.ok(count);
    }

    @Operation(summary = "删除标签", description = "根据标签ID删除指定标签，删除后不可恢复")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "删除成功"),
            @ApiResponse(responseCode = "404", description = "标签不存在")
    })
    @DeleteMapping("/{id}")
    public R<Void> delete(
            @Parameter(description = "标签ID", required = true, example = "1") @PathVariable Long id) {
        facade.delete(id);
        return R.ok();
    }
}
