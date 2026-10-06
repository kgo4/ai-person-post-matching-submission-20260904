package com.example.matching.controller.post;

import com.example.matching.application.post.PostPrototypeApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.post.PostPrototypeSaveDTO;
import com.example.matching.dto.post.PostPrototypeVO;
import com.example.matching.dto.post.api.PostPrototypeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "岗位原型管理", description = "管理岗位族/原型模板，支撑新兴岗位快速建模")
@RestController
@RequestMapping("/api/post/prototype")
@RequiredArgsConstructor
public class PostPrototypeController {

    private final PostPrototypeApiFacade postPrototypeApiFacade;

    @Operation(summary = "分页查询岗位原型")
    @GetMapping("/page")
    public R<PageResponse<PostPrototypeResponse>> page(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") Integer pageSize,
            @Parameter(description = "关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "行业") @RequestParam(required = false) String industry,
            @Parameter(description = "分类") @RequestParam(required = false) String category) {
        return R.ok(postPrototypeApiFacade.page(pageNum, pageSize, keyword, industry, category));
    }

    @Operation(summary = "查询所有启用的原型")
    @GetMapping("/enabled")
    public R<List<PostPrototypeResponse>> listEnabled() {
        return R.ok(postPrototypeApiFacade.listEnabled());
    }

    @Operation(summary = "获取原型详情（含标签）")
    @GetMapping("/{id}")
    public R<PostPrototypeVO> getDetail(@PathVariable Long id) {
        return R.ok(postPrototypeApiFacade.getDetail(id));
    }

    @Operation(summary = "保存原型（新增或更新）")
    @PostMapping
    public R<Void> save(@Valid @RequestBody PostPrototypeSaveDTO dto) {
        postPrototypeApiFacade.save(dto);
        return R.ok();
    }

    @Operation(summary = "删除原型")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        postPrototypeApiFacade.delete(id);
        return R.ok();
    }

    @Operation(summary = "向量召回相似原型")
    @GetMapping("/recall")
    public R<List<PostPrototypeVO>> recall(
            @Parameter(description = "岗位描述文本") @RequestParam String description,
            @Parameter(description = "返回数量") @RequestParam(defaultValue = "5") int topN) {
        return R.ok(postPrototypeApiFacade.recall(description, topN));
    }

    @Operation(summary = "应用原型到岗位")
    @PostMapping("/{prototypeId}/apply/{postId}")
    public R<Void> applyToPost(@PathVariable Long prototypeId, @PathVariable Long postId) {
        postPrototypeApiFacade.applyToPost(prototypeId, postId);
        return R.ok();
    }
}
