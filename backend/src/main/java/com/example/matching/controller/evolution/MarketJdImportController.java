package com.example.matching.controller.evolution;

import com.example.matching.application.evolution.MarketJdImportApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.evolution.api.MarketJdDetailResponse;
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

    @Operation(summary = "解析单条市场 JD",
            description = "对池中任意一条 JD 单独执行「清洗 → 能力提取 → 准入」。"
                    + "批次解析用于整批一起跑；单条解析用于「就这一条结果不对」或「只想先跑几条看看」。"
                    + "重复项（isDuplicate=1）会被拒绝（409）——它在批次解析里同样被跳过。"
                    + "注意：新能力需跨 JD/跨公司互相印证，单条解析不会新建标签，结论见 message。")
    @PostMapping("/{id}/analyze")
    public R<MarketJdImportService.SingleAnalysisResult> analyzeMarketJd(
            @Parameter(description = "市场 JD 主键") @PathVariable Long id) {
        return R.ok(facade.analyzeMarketJd(id));
    }

    @Operation(summary = "查看单条市场 JD 的解析结果",
            description = "返回该条 JD 的解析状态、质量分、匹配岗位，以及 skill_tags / recommended_skill_tags "
                    + "反解出的可读能力标签（同时给出原始 JSON，便于核对标签被删除等异常）。")
    @GetMapping("/{id}/detail")
    public R<MarketJdDetailResponse> getMarketJdDetail(
            @Parameter(description = "市场 JD 主键") @PathVariable Long id) {
        return R.ok(facade.getMarketJdDetail(id));
    }

    @Operation(summary = "删除单条市场 JD",
            description = "数据治理入口：删除一条误入池子的 JD，并级联清理其历史版本快照与悬空去重引用。"
                    + "整批删除请用 DELETE /api/post/evolution/crawler/batches/{batchNo}。")
    @DeleteMapping("/{id}")
    public R<MarketJdImportService.SingleDeleteResult> deleteMarketJd(
            @Parameter(description = "市场 JD 主键") @PathVariable Long id) {
        return R.ok(facade.deleteMarketJd(id));
    }

    @Operation(summary = "批量删除市场 JD",
            description = "按主键列表删除（前端勾选删除）。后端会先去重再级联清理，"
                    + "返回 missingIds 用于提示「有 N 条已被他人删除」。")
    @PostMapping("/batch-delete")
    public R<MarketJdImportService.BatchDeleteByIdsResult> deleteMarketJds(
            @RequestBody List<Long> ids) {
        return R.ok(facade.deleteMarketJds(ids));
    }
}
