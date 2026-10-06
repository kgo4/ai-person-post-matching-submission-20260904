package com.example.matching.controller.post;

import com.example.matching.application.post.PostModelTemplateApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.post.PostTemplateSaveDTO;
import com.example.matching.dto.post.api.PostModelTemplateResponse;
import com.example.matching.dto.post.api.TemplateAbilityItemRequest;
import com.example.matching.dto.post.api.TemplateAbilityResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "岗位模型模板", description = "岗位模型模板的管理接口，支持模板的增删改查与分页查询，模板可应用于快速配置岗位能力模型")
@RestController
@RequestMapping("/api/post/model-template")
@RequiredArgsConstructor
public class PostModelTemplateController {

    private final PostModelTemplateApiFacade postModelTemplateApiFacade;

    @Operation(summary = "分页查询模板", description = "根据关键词进行分页查询，按模板名称模糊匹配，返回模板列表及其分页信息")
    @GetMapping("/page")
    public R<PageResponse<PostModelTemplateResponse>> page(
            @Parameter(description = "当前页码，默认第1页") @RequestParam(defaultValue = "1") long current,
            @Parameter(description = "每页显示条数，默认10条") @RequestParam(defaultValue = "10") long size,
            @Parameter(description = "搜索关键词，支持按模板名称模糊匹配") @RequestParam(required = false) String keyword) {
        return R.ok(postModelTemplateApiFacade.page(current, size, keyword));
    }

    @Operation(summary = "获取模板详情", description = "根据模板ID获取单个模板的完整信息，包括模板名称、描述和关联的能力项配置")
    @GetMapping("/{id}")
    public R<PostModelTemplateResponse> getById(
            @Parameter(description = "模板ID", required = true) @PathVariable Long id) {
        return R.ok(postModelTemplateApiFacade.get(id));
    }

    @Operation(summary = "新增模板", description = "新增一个岗位模型模板，包含模板名称、描述和关联的能力项配置")
    @PostMapping
    public R<Void> save(
            @Parameter(description = "模板保存DTO，包含模板名称、描述和关联的能力项配置列表") @Valid @RequestBody PostTemplateSaveDTO dto) {
        postModelTemplateApiFacade.save(dto);
        return R.ok();
    }

    @Operation(summary = "更新模板", description = "根据模板ID更新已有模板的名称、描述或关联的能力项配置")
    @PutMapping("/{id}")
    public R<Void> update(
            @Parameter(description = "模板ID", required = true) @PathVariable Long id,
            @Parameter(description = "模板保存DTO，包含需要更新的模板名称、描述和能力项配置") @Valid @RequestBody PostTemplateSaveDTO dto) {
        postModelTemplateApiFacade.update(id, dto);
        return R.ok();
    }

    @Operation(summary = "删除模板", description = "根据模板ID删除指定岗位模型模板，删除后该模板及其关联配置不可恢复")
    @DeleteMapping("/{id}")
    public R<Void> delete(
            @Parameter(description = "模板ID", required = true) @PathVariable Long id) {
        postModelTemplateApiFacade.delete(id);
        return R.ok();
    }

    @Operation(summary = "获取模板能力要求", description = "根据模板ID获取该模板下所有能力要求配置")
    @GetMapping("/{templateId}/ability-models")
    public R<List<TemplateAbilityResponse>> getAbilityModels(
            @Parameter(description = "模板ID", required = true) @PathVariable Long templateId) {
        return R.ok(postModelTemplateApiFacade.getAbilityModels(templateId));
    }

    @Operation(summary = "保存模板能力要求", description = "批量保存模板的能力要求配置")
    @PostMapping("/{templateId}/ability-models")
    public R<Void> saveAbilityModels(
            @Parameter(description = "模板ID", required = true) @PathVariable Long templateId,
            @Parameter(description = "能力要求列表") @RequestBody List<TemplateAbilityItemRequest> items) {
        postModelTemplateApiFacade.saveAbilityModels(templateId, items);
        return R.ok();
    }

    @Operation(summary = "应用模板到岗位", description = "将模板的能力要求配置应用到指定岗位，会覆盖岗位原有的能力模型配置")
    @PostMapping("/{templateId}/apply/{postId}")
    public R<Void> applyTemplateToPost(
            @Parameter(description = "模板ID", required = true) @PathVariable Long templateId,
            @Parameter(description = "岗位ID", required = true) @PathVariable Long postId) {
        postModelTemplateApiFacade.applyTemplateToPost(templateId, postId);
        return R.ok();
    }
}
