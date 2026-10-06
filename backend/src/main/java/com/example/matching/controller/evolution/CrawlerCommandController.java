package com.example.matching.controller.evolution;

import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.common.result.R;
import com.example.matching.dto.evolution.api.CrawlerAgentView;
import com.example.matching.dto.evolution.api.CrawlerAutoAnalyzeUpdateRequest;
import com.example.matching.dto.evolution.api.CrawlerAutoAnalyzeView;
import com.example.matching.dto.evolution.api.CrawlerAvailabilityView;
import com.example.matching.dto.evolution.api.CrawlerBatchLogView;
import com.example.matching.dto.evolution.api.CrawlerCommandCreateRequest;
import com.example.matching.dto.evolution.api.CrawlerCommandView;
import com.example.matching.entity.evolution.MarketJdCrawlerBatchLog;
import com.example.matching.service.evolution.MarketJdImportService;
import com.example.matching.service.evolution.crawler.CrawlerBatchLogService;
import com.example.matching.service.evolution.crawler.MarketJdAutoAnalyzeSwitch;
import com.example.matching.service.evolution.crawler.CrawlerCommandService;
import com.example.matching.service.evolution.crawler.CrawlerAgentService;
import com.example.matching.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 采集命令管理端接口（前端「市场 JD 采集」页用）。
 * <p>
 * 权限沿用 {@code /api/post/evolution/**} → {@code POST:EVOLUTION}，无需改前端权限三层载体。
 * <p>
 * 与旧反向代理的区别（对齐爬虫对接文档 §1.1、§9）：这里只**创建命令并读取状态**，
 * 绝不主动连本地爬虫；命令由本地爬虫每 10–30 秒轮询领取。
 *
 * @author system
 */
@Slf4j
@Tag(name = "爬虫采集命令", description = "下发采集命令、查看命令/爬虫/批次接收状态。")
@RestController
@RequestMapping("/api/post/evolution/crawler")
@RequiredArgsConstructor
public class CrawlerCommandController {

    private final CrawlerCommandService commandService;
    private final CrawlerAgentService agentService;
    private final CrawlerBatchLogService batchLogService;
    private final MarketJdImportService marketJdImportService;
    /**
     * 自动解析开关。注入的是具体组件而非 Facade：它本身没有任何领域依赖，
     * 只回答「现在要不要自动解析」，与 {@link CrawlerBatchLogService} 属同一类无状态组件。
     */
    private final MarketJdAutoAnalyzeSwitch autoAnalyzeSwitch;

    @Operation(summary = "创建采集命令", description = "创建 PENDING 命令，等待本地爬虫轮询领取；返回 commandId 供前端轮询。")
    @PostMapping("/commands")
    public R<CrawlerCommandView> createCommand(@RequestBody CrawlerCommandCreateRequest request) {
        return R.ok(commandService.create(request, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "最近采集命令", description = "按创建时间倒序返回命令及状态，供前端轮询刷新。")
    @GetMapping("/commands")
    public R<List<CrawlerCommandView>> listCommands(
            @Parameter(description = "返回条数，默认 20，最大 100") @RequestParam(defaultValue = "20") int limit) {
        return R.ok(commandService.list(limit));
    }

    @Operation(summary = "查询单条命令状态")
    @GetMapping("/commands/{commandId}")
    public R<CrawlerCommandView> getCommand(
            @Parameter(description = "命令ID，如 cmd-20260904-120000-07") @PathVariable String commandId) {
        return R.ok(commandService.get(commandId));
    }

    @Operation(summary = "取消待领取命令", description = "仅 PENDING 可取消；已被领取返回 409。")
    @PostMapping("/commands/{commandId}/cancel")
    public R<CrawlerCommandView> cancelCommand(
            @Parameter(description = "命令ID") @PathVariable String commandId) {
        return R.ok(commandService.cancel(commandId));
    }

    @Operation(summary = "爬虫实例在线状态", description = "按最近心跳倒序返回本地爬虫实例，含 online 标记。")
    @GetMapping("/agents")
    public R<List<CrawlerAgentView>> listAgents() {
        return R.ok(agentService.listAgents());
    }

    @Operation(summary = "最近接收批次",
            description = "统一批次台账：爬虫推送与人工上传的批次都在这里，"
                    + "按 ingestChannel 区分来源，并给出入库统计与解析状态。")
    @GetMapping("/batches")
    public R<List<CrawlerBatchLogView>> listBatches(
            @Parameter(description = "返回条数，默认 20，最大 200") @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "入库通道筛选：CRAWLER / MANUAL_UPLOAD / POST_IMPORT，留空为全部")
            @RequestParam(required = false) String channel) {
        return R.ok(batchLogService.recent(limit, channel));
    }

    @Operation(summary = "删除某批次",
            description = "删除该批次的市场 JD 数据、历史版本快照与批次登记行；"
                    + "爬虫批次与人工上传批次走同一条路径。")
    @DeleteMapping("/batches/{batchNo}")
    public R<MarketJdImportService.BatchDeleteResult> deleteBatch(
            @Parameter(description = "批次号") @PathVariable String batchNo) {
        return R.ok(marketJdImportService.deleteBatch(batchNo));
    }

    @Operation(summary = "重新解析某批次",
            description = "解析失败或跳过后的补偿入口：重置该批次待分析行并重新进入解析链路。")
    @PostMapping("/batches/{batchNo}/reanalyze")
    public R<CrawlerBatchLogView> reanalyzeBatch(
            @Parameter(description = "批次号") @PathVariable String batchNo) {
        MarketJdCrawlerBatchLog latest = batchLogService.latestByBatchNo(batchNo);
        if (latest == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "未找到该批次的接收记录：" + batchNo);
        }
        Long logId = latest.getId();
        batchLogService.markAnalysis(logId, MarketJdCrawlerBatchLog.ANALYSIS_RUNNING,
                "管理端手动触发重新解析");
        try {
            MarketJdImportService.BatchAnalysisResult result = marketJdImportService.analyzeBatch(batchNo);
            batchLogService.markAnalysis(logId, MarketJdCrawlerBatchLog.ANALYSIS_SUCCEEDED,
                    summarize(result));
        } catch (Exception exception) {
            log.warn("手动重解析批次失败: batchNo={}", batchNo, exception);
            batchLogService.markAnalysis(logId, MarketJdCrawlerBatchLog.ANALYSIS_FAILED,
                    "解析失败：" + rootMessage(exception) + "（JD 保持待分析，可重试）");
            throw new BusinessException(ErrorCodeEnum.INTERNAL_ERROR,
                    "重新解析失败：" + rootMessage(exception));
        }
        return R.ok(batchLogService.latestView(batchNo));
    }

    @Operation(summary = "重算某批次的去重",
            description = "排查「推来的数据全显示重复跳过、但业务上并不重复」时使用："
                    + "按统一口径重算该批次的去重键、清空重复判定，再重新判定一轮。"
                    + "真重复仍会被重新标出（不是一次性豁免）。")
    @PostMapping("/batches/{batchNo}/reset-dedupe")
    public R<MarketJdImportService.DedupeResetResult> resetBatchDedupe(
            @Parameter(description = "批次号") @PathVariable String batchNo) {
        return R.ok(marketJdImportService.resetDedupeByBatch(batchNo));
    }

    @Operation(summary = "采集通道可用性",
            description = "命令队列是否接入、在线爬虫数、最近心跳时间，以及过渡期反向代理开关。")
    @GetMapping("/availability")
    public R<CrawlerAvailabilityView> availability() {
        return R.ok(commandService.availability());
    }

    @Operation(summary = "查询批次自动解析开关",
            description = "默认关闭：爬虫推来的批次只入库，等人工在批次行或市场 JD 池触发解析。"
                    + "开启后推送的批次会在入库后异步自动解析。")
    @GetMapping("/auto-analyze")
    public R<CrawlerAutoAnalyzeView> getAutoAnalyze() {
        return R.ok(autoAnalyzeSwitch.toView());
    }

    @Operation(summary = "切换批次自动解析开关",
            description = "运行期开关，**不落库**：服务重启后回到配置默认值"
                    + "（环境变量 MARKET_JD_CRAWLER_AUTO_ANALYZE_ENABLED）。"
                    + "要长期生效请改环境变量。")
    @PutMapping("/auto-analyze")
    public R<CrawlerAutoAnalyzeView> updateAutoAnalyze(
            @RequestBody @Valid CrawlerAutoAnalyzeUpdateRequest request) {
        autoAnalyzeSwitch.apply(Boolean.TRUE.equals(request.getEnabled()));
        return R.ok(autoAnalyzeSwitch.toView());
    }

    private static String summarize(MarketJdImportService.BatchAnalysisResult result) {
        if (result == null) {
            return "解析完成（无返回统计）";
        }
        return "解析完成：本批 " + result.getTotalCount() + " 条，去重跳过 " + result.getSkippedDuplicate()
                + " 条，成功提取 " + result.getExtractedSuccess() + " 条，失败 " + result.getExtractedFailed() + " 条";
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
