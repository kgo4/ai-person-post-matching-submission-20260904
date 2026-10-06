package com.example.matching.controller.post;

import com.example.matching.application.post.EmergingPostApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.post.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "岗位趋势发现", description = "基于政策资料、行业报告或市场JD发现候选岗位趋势，人工审核后进入岗位演化或岗位创建")
@RestController
@RequestMapping("/api/post/emerging")
@RequiredArgsConstructor
public class PostEmergingPostController {

    private final EmergingPostApiFacade emergingPostApiFacade;

    @Operation(summary = "发现岗位趋势", description = "基于已索引官方资料、行业报告或已治理市场JD发现候选岗位趋势，任一来源即可运行")
    @GetMapping("/discover")
    public R<List<EmergingPostDiscoveryDTO>> discover(
            @RequestParam(defaultValue = "10") int limit) {
        return R.ok(emergingPostApiFacade.discover(limit));
    }

    @Operation(summary = "获取市场洞察", description = "获取当前市场上的热门能力标签、新兴技术趋势等信息")
    @GetMapping("/market-insight")
    public R<EmergingPostDiscoveryDTO.MarketInsight> getMarketInsight() {
        return R.ok(emergingPostApiFacade.getMarketInsight());
    }

    @Operation(summary = "JD质量检测", description = "检测JD中的时滞、噪音、抄袭等问题")
    @PostMapping("/quality-check")
    public R<JdQualityReport> checkJdQuality(@RequestBody JdQualityCheckRequest request) {
        return R.ok(emergingPostApiFacade.checkJdQuality(request));
    }

    @Operation(summary = "兼容分析接口", description = "保留旧接口兼容；趋势发现页面不要求人工填写岗位名称")
    @PostMapping("/analyze")
    public R<Map<String, String>> analyze(@Valid @RequestBody EmergingPostRequestDTO request) {
        return R.ok(Map.of("taskId", emergingPostApiFacade.submitAnalyze(request), "status", "PENDING"));
    }

    @Operation(summary = "查询新兴岗位分析任务")
    @GetMapping("/analyze/tasks/{taskId}")
    public R<Map<String, Object>> analyzeTask(@PathVariable String taskId) {
        return R.ok(emergingPostApiFacade.getAnalyzeTask(taskId));
    }

    @Operation(summary = "人工确认并创建正式岗位")
    @PostMapping("/confirm")
    public R<Long> confirm(@Valid @RequestBody EmergingPostConfirmDTO request) {
        return R.ok(emergingPostApiFacade.confirm(request));
    }
}
