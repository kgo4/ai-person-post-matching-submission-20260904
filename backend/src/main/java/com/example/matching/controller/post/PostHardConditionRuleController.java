package com.example.matching.controller.post;

import com.example.matching.application.post.HardConditionRuleApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.post.PostHardConditionRuleDTO;
import com.example.matching.dto.post.api.HardConditionRuleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "岗位硬性条件规则", description = "维护岗位匹配前置筛选规则")
@RestController
@RequestMapping("/api/post/hard-condition-rule")
@RequiredArgsConstructor
public class PostHardConditionRuleController {

    private final HardConditionRuleApiFacade hardConditionRuleApiFacade;

    @Operation(summary = "按岗位查询硬性条件规则")
    @GetMapping("/list/{postId}")
    public R<List<HardConditionRuleResponse>> listByPostId(@PathVariable Long postId) {
        return R.ok(hardConditionRuleApiFacade.listByPostId(postId));
    }

    @Operation(summary = "新增硬性条件规则")
    @PostMapping
    public R<Void> save(@Valid @RequestBody PostHardConditionRuleDTO dto) {
        hardConditionRuleApiFacade.save(dto);
        return R.ok();
    }

    @Operation(summary = "更新硬性条件规则")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody PostHardConditionRuleDTO dto) {
        hardConditionRuleApiFacade.update(id, dto);
        return R.ok();
    }

    @Operation(summary = "批量保存岗位硬性条件规则")
    @PostMapping("/batch/{postId}")
    public R<Void> batchConfig(@PathVariable Long postId, @Valid @RequestBody List<PostHardConditionRuleDTO> list) {
        hardConditionRuleApiFacade.batchConfig(postId, list);
        return R.ok();
    }

    @Operation(summary = "删除硬性条件规则")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        hardConditionRuleApiFacade.delete(id);
        return R.ok();
    }
}
