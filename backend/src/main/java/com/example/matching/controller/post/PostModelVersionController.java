package com.example.matching.controller.post;

import com.example.matching.application.post.PostModelVersionApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.post.api.PostModelVersionItemRequest;
import com.example.matching.dto.post.api.PostModelVersionItemResponse;
import com.example.matching.dto.post.api.PostModelVersionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "岗位能力模型版本管理", description = "版本的草稿、发布、回滚管理")
@RestController
@RequestMapping("/api/post/model-version")
@RequiredArgsConstructor
public class PostModelVersionController {

    private final PostModelVersionApiFacade modelVersionApiFacade;

    @Operation(summary = "创建草稿版本", description = "为指定岗位创建一个新的草稿版本")
    @PostMapping("/draft")
    public R<PostModelVersionResponse> createDraft(
            @Parameter(description = "岗位ID", required = true) @RequestParam Long postId,
            @Parameter(description = "来源类型：TEMPLATE/JD_AI/EXCEL/COPY/MANUAL/FEEDBACK", required = true) @RequestParam String sourceType,
            @Parameter(description = "版本说明") @RequestParam(required = false) String description) {
        return R.ok(modelVersionApiFacade.createDraft(postId, sourceType, description));
    }

    @Operation(summary = "保存版本明细", description = "保存草稿版本的能力项配置")
    @PostMapping("/{versionId}/items")
    public R<Void> saveVersionItems(
            @Parameter(description = "版本ID", required = true) @PathVariable Long versionId,
            @RequestBody List<PostModelVersionItemRequest> items) {
        modelVersionApiFacade.saveVersionItems(versionId, items);
        return R.ok();
    }

    @Operation(summary = "发布版本", description = "将草稿版本发布为正式生效的岗位能力模型")
    @PostMapping("/{versionId}/publish")
    public R<Void> publishVersion(
            @Parameter(description = "版本ID", required = true) @PathVariable Long versionId) {
        modelVersionApiFacade.publishVersion(versionId);
        return R.ok();
    }

    @Operation(summary = "回滚到指定版本", description = "将岗位能力模型回滚到指定的历史版本")
    @PostMapping("/{versionId}/rollback")
    public R<Void> rollbackToVersion(
            @Parameter(description = "版本ID", required = true) @PathVariable Long versionId) {
        modelVersionApiFacade.rollbackToVersion(versionId);
        return R.ok();
    }

    @Operation(summary = "获取岗位的版本列表", description = "获取指定岗位的所有版本记录")
    @GetMapping("/list/{postId}")
    public R<List<PostModelVersionResponse>> listVersions(
            @Parameter(description = "岗位ID", required = true) @PathVariable Long postId) {
        return R.ok(modelVersionApiFacade.listVersions(postId));
    }

    @Operation(summary = "获取版本详情", description = "获取版本的基本信息")
    @GetMapping("/{versionId}")
    public R<PostModelVersionResponse> getVersionDetail(
            @Parameter(description = "版本ID", required = true) @PathVariable Long versionId) {
        return R.ok(modelVersionApiFacade.getVersionDetail(versionId));
    }

    @Operation(summary = "获取版本明细", description = "获取版本的能力项配置列表")
    @GetMapping("/{versionId}/items")
    public R<List<PostModelVersionItemResponse>> getVersionItems(
            @Parameter(description = "版本ID", required = true) @PathVariable Long versionId) {
        return R.ok(modelVersionApiFacade.getVersionItems(versionId));
    }

    @Operation(summary = "删除草稿版本", description = "删除草稿状态的版本（已发布版本不能删除）")
    @DeleteMapping("/{versionId}")
    public R<Void> deleteDraft(
            @Parameter(description = "版本ID", required = true) @PathVariable Long versionId) {
        modelVersionApiFacade.deleteDraft(versionId);
        return R.ok();
    }
}
