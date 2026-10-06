package com.example.matching.controller.post;

import com.example.matching.application.post.PostPanoramaApiFacade;
import com.example.matching.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "岗位全景图谱")
@RestController
@RequestMapping("/api/post/panorama")
@RequiredArgsConstructor
public class PostPanoramaController {

    private final PostPanoramaApiFacade panoramaApiFacade;

    @Operation(summary = "获取岗位全景图谱概览")
    @GetMapping("/overview")
    public R<Map<String, Object>> overview(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String techStack,
            @RequestParam(required = false) String keyword) {
        return R.ok(panoramaApiFacade.getOverview(level, techStack, keyword));
    }

    @Operation(summary = "获取筛选项")
    @GetMapping("/filters")
    public R<Map<String, Object>> filters() {
        return R.ok(panoramaApiFacade.getFilters());
    }

    @Operation(summary = "获取岗位能力详情")
    @GetMapping("/post/{postId}")
    public R<Map<String, Object>> postDetail(@PathVariable Long postId) {
        Map<String, Object> result = panoramaApiFacade.getPostDetail(postId);
        if (result == null) return R.fail("岗位不存在");
        return R.ok(result);
    }

    @Operation(summary = "获取能力全景详情")
    @GetMapping("/ability/{abilityId}")
    public R<Map<String, Object>> abilityDetail(@PathVariable Long abilityId) {
        Map<String, Object> result = panoramaApiFacade.getAbilityDetail(abilityId);
        if (result == null) return R.fail("能力标签不存在");
        return R.ok(result);
    }

    @Operation(summary = "获取岗位全景图谱（标准图结构）")
    @GetMapping("/graph")
    public R<Map<String, Object>> graph(
            @RequestParam(required = false) Long postId,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String techStack,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer requiredLevel,
            @RequestParam(required = false) Boolean coreOnly) {
        return R.ok(panoramaApiFacade.getGraph(postId, level, techStack, keyword, limit, requiredLevel, coreOnly));
    }

    public R<Map<String, Object>> graph(Long postId, String level, String techStack, String keyword, Integer limit) {
        return R.ok(panoramaApiFacade.getGraph(postId, level, techStack, keyword, limit));
    }

    @Operation(summary = "获取岗位能力事实图谱（含未归一能力）")
    @GetMapping("/fact-graph")
    public R<Map<String, Object>> factGraph(
            @RequestParam(required = false) Long postId,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer limit) {
        return R.ok(panoramaApiFacade.getAbilityFactGraph(postId, level, keyword, limit));
    }
}
