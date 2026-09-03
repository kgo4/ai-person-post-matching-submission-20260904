package com.example.matching.controller.post;

import com.example.matching.application.post.PostAbilityModelApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.post.PostAbilityModelConfigDTO;
import com.example.matching.dto.post.api.PostAbilityModelResponse;
import com.example.matching.vo.post.PostAbilityModelVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@Tag(name = "岗位能力模型配置", description = "岗位能力模型的配置管理接口，包括能力要求的增删改查、批量配置与模型视图查询")
@RestController
@RequestMapping("/api/post/ability-model")
@RequiredArgsConstructor
public class PostAbilityModelController {

    private final PostAbilityModelApiFacade postAbilityModelApiFacade;

    @Operation(summary = "获取岗位能力模型（含标签名称）", description = "根据岗位ID获取该岗位的完整能力模型视图，包含各项能力标签的名称、等级要求和权重等详细信息")
    @GetMapping("/{postId}")
    public R<PostAbilityModelVO> getModel(
            @Parameter(description = "岗位ID", required = true) @PathVariable Long postId) {
        return R.ok(postAbilityModelApiFacade.getModel(postId));
    }

    @Operation(summary = "按岗位ID查询能力要求列表", description = "根据岗位ID查询该岗位所有能力要求的明细列表，每条记录包含能力标签ID和对应的等级要求")
    @GetMapping("/list/{postId}")
    public R<List<PostAbilityModelResponse>> listByPostId(
            @Parameter(description = "岗位ID", required = true) @PathVariable Long postId) {
        return R.ok(postAbilityModelApiFacade.listByPostId(postId));
    }

    @Operation(summary = "查询已配置能力模型的岗位", description = "仅返回传入岗位 ID 中至少有一项有效能力模型的岗位 ID")
    @PostMapping("/configured-post-ids")
    public R<Set<Long>> listConfiguredPostIds(@RequestBody List<Long> postIds) {
        return R.ok(postAbilityModelApiFacade.listConfiguredPostIds(postIds));
    }

    @Operation(summary = "新增能力配置", description = "为指定岗位新增一条能力要求配置，包含能力标签ID、等级要求和权重")
    @PostMapping
    public R<Void> save(
            @Parameter(description = "能力配置DTO，包含岗位ID、能力标签ID、等级要求和权重等字段") @Valid @RequestBody PostAbilityModelConfigDTO dto) {
        postAbilityModelApiFacade.save(dto);
        return R.ok();
    }

    @Operation(summary = "更新能力配置", description = "根据配置记录ID更新已有能力配置的等级要求或权重信息")
    @PutMapping("/{id}")
    public R<Void> update(
            @Parameter(description = "能力配置记录ID", required = true) @PathVariable Long id,
            @Parameter(description = "能力配置DTO，包含需要更新的能力标签ID、等级要求和权重") @Valid @RequestBody PostAbilityModelConfigDTO dto) {
        postAbilityModelApiFacade.update(id, dto);
        return R.ok();
    }

    @Operation(summary = "批量配置能力要求", description = "批量配置岗位的多项能力要求，可用于一次性设置或更新某个岗位的全部能力项")
    @PostMapping("/batch")
    public R<Void> batchConfig(
            @Parameter(description = "能力配置DTO列表，每条记录包含岗位ID、能力标签ID、等级要求和权重") @Valid @RequestBody List<PostAbilityModelConfigDTO> list) {
        postAbilityModelApiFacade.batchConfig(list);
        return R.ok();
    }

    @Operation(summary = "删除能力配置", description = "根据配置记录ID删除指定能力要求配置，删除后该记录不可恢复")
    @DeleteMapping("/{id}")
    public R<Void> delete(
            @Parameter(description = "能力配置记录ID", required = true) @PathVariable Long id) {
        postAbilityModelApiFacade.delete(id);
        return R.ok();
    }
}
