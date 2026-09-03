package com.example.matching.controller.evolution;

import com.example.matching.application.evolution.MarketJdImportApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.evolution.api.MarketJdImportRequest;
import com.example.matching.dto.evolution.api.MarketJdResponse;
import com.example.matching.service.evolution.MarketJdImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "市场JD导入", description = "批量导入市场招聘JD数据，用于岗位演化分析。")
@RestController
@RequestMapping("/api/post/evolution/market-jd")
@RequiredArgsConstructor
public class MarketJdImportController {

    private final MarketJdImportApiFacade facade;

    @Operation(summary = "批量导入JD文本", description = "批量导入多条JD文本数据。")
    @PostMapping("/import-texts")
    public R<Map<String, Object>> importTexts(
            @RequestBody List<String> jdTexts,
            @Parameter(description = "来源平台") @RequestParam(defaultValue = "OTHER") String sourcePlatform) {
        return R.ok(facade.importTexts(jdTexts, sourcePlatform));
    }

    @Operation(summary = "从Excel导入", description = "从上传的Excel文件导入市场JD数据。")
    @PostMapping("/import-excel")
    public R<Map<String, Object>> importExcel(
            @RequestBody List<MarketJdImportRequest> requests) {
        return R.ok(facade.importExcel(requests));
    }

    @Operation(summary = "分页查询市场JD", description = "分页查询已导入的市场JD数据。")
    @GetMapping("/page")
    public R<PageResponse<MarketJdResponse>> pageMarketJds(
            @Parameter(description = "当前页码") @RequestParam(defaultValue = "1") long current,
            @Parameter(description = "每页记录数") @RequestParam(defaultValue = "20") long size,
            @Parameter(description = "岗位名称") @RequestParam(required = false) String postName,
            @Parameter(description = "批次号") @RequestParam(required = false) String batchNo) {
        return R.ok(facade.pageMarketJds(current, size, postName, batchNo));
    }

    @Operation(summary = "获取岗位相关JD", description = "获取指定岗位相关的市场JD数据。")
    @GetMapping("/by-post/{postId}")
    public R<List<MarketJdResponse>> getMarketJdsByPostId(
            @PathVariable Long postId,
            @Parameter(description = "限制数量") @RequestParam(defaultValue = "50") int limit) {
        return R.ok(facade.getMarketJdsByPostId(postId, limit));
    }

    @Operation(summary = "去重处理", description = "对指定批次的数据进行去重处理。")
    @PostMapping("/deduplicate")
    public R<Map<String, Object>> deduplicate(@RequestParam String batchNo) {
        return R.ok(facade.deduplicate(batchNo));
    }

    @Operation(summary = "获取批次统计", description = "获取指定批次的统计信息。")
    @GetMapping("/statistics")
    public R<MarketJdImportService.BatchStatistics> getBatchStatistics(@RequestParam String batchNo) {
        return R.ok(facade.getBatchStatistics(batchNo));
    }

    @Operation(summary = "批量分析JD", description = "对指定批次的JD执行完整分析链路。")
    @PostMapping("/analyze-batch")
    public R<MarketJdImportService.BatchAnalysisResult> analyzeBatch(
            @Parameter(description = "批次号") @RequestParam String batchNo) {
        return R.ok(facade.analyzeBatch(batchNo));
    }
}
