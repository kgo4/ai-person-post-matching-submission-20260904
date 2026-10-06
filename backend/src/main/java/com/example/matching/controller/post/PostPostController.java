package com.example.matching.controller.post;

import com.example.matching.application.post.PostApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.post.api.PostCreateRequest;
import com.example.matching.dto.post.api.PostResponse;
import com.example.matching.dto.post.api.PostUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "岗位信息", description = "匹配用岗位信息维护")
@RestController
@RequestMapping("/api/post")
@RequiredArgsConstructor
public class PostPostController {

    private final PostApiFacade postApiFacade;

    @Operation(summary = "分页查询岗位")
    @GetMapping("/page")
    public R<PageResponse<PostResponse>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return R.ok(postApiFacade.page(current, size, keyword, status));
    }

    @Operation(summary = "全部启用岗位列表")
    @GetMapping("/enabled")
    public R<List<PostResponse>> listEnabled() {
        return R.ok(postApiFacade.listEnabled());
    }

    @Operation(summary = "获取岗位详情")
    @GetMapping("/{id}")
    public R<PostResponse> getById(@PathVariable Long id) {
        return R.ok(postApiFacade.get(id));
    }

    @Operation(summary = "新增岗位")
    @PostMapping
    public R<Void> save(@RequestBody PostCreateRequest request) {
        postApiFacade.create(request);
        return R.ok();
    }

    @Operation(summary = "更新岗位")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody PostUpdateRequest request) {
        postApiFacade.update(id, request);
        return R.ok();
    }

    @Operation(summary = "删除岗位")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        postApiFacade.delete(id);
        return R.ok();
    }
}
