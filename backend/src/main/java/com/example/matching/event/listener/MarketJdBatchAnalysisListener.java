package com.example.matching.event.listener;

import com.example.matching.entity.evolution.MarketJdCrawlerBatchLog;
import com.example.matching.event.MarketJdBatchImportedEvent;
import com.example.matching.service.evolution.MarketJdImportService;
import com.example.matching.service.evolution.crawler.CrawlerBatchLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 爬虫批次入库后自动进入解析链路。
 * <p>
 * 这是「爬虫推来的数据如何被主系统使用」的接合点：爬虫只负责把 JD 交到
 * {@code market_jd_data}，本监听器负责把该批次送进既有
 * {@link MarketJdImportService#analyzeBatch}（去重 → 治理 → Agent 提取能力 → Harness 准入），
 * 于是下游消费方（市场 JD 统计、岗位演化证据、趋势发现）无需任何改动即可读到数据。
 * <p>
 * 异步范式与项目既有做法一致（{@code @Async} + {@code AFTER_COMMIT} + {@code fallbackExecution}）：
 * <ul>
 *   <li>接收接口立刻返回，不会被 LLM 往返拖过爬虫的 30 秒超时；</li>
 *   <li>异步任务读到的一定是已提交的 JD；</li>
 *   <li>任何异常只留痕 + 告警，绝不回抛给爬虫，也不会把 HTTP 200 变成失败。</li>
 * </ul>
 * <p>
 * 幂等三重保障：①调用方只在「有新增或正文变更」时发事件；②同批次在途互斥（本类的
 * {@code inFlightBatches}）；③{@code analyzeBatch} 本身只捞 {@code analysisStatus=0} 的行。
 *
 * @author system
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketJdBatchAnalysisListener {

    private final MarketJdImportService marketJdImportService;
    private final CrawlerBatchLogService batchLogService;

    /** 在途批次：同一 batchNo 并发触发时只保留第一次，避免重复消耗 LLM 配额。 */
    private final Set<String> inFlightBatches = ConcurrentHashMap.newKeySet();

    @Async("postImportAiExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onBatchImported(MarketJdBatchImportedEvent event) {
        String batchNo = event.batchNo();
        if (batchNo == null || batchNo.isBlank()) {
            return;
        }
        if (!inFlightBatches.add(batchNo)) {
            log.info("批次已在解析中，本次触发合并: batchNo={}", batchNo);
            batchLogService.markAnalysis(event.logId(), MarketJdCrawlerBatchLog.ANALYSIS_SKIPPED,
                    "该批次正在解析中，本次触发已合并");
            return;
        }
        try {
            batchLogService.markAnalysis(event.logId(), MarketJdCrawlerBatchLog.ANALYSIS_RUNNING, "解析中");
            MarketJdImportService.BatchAnalysisResult result = marketJdImportService.analyzeBatch(batchNo);
            batchLogService.markAnalysis(event.logId(), MarketJdCrawlerBatchLog.ANALYSIS_SUCCEEDED,
                    summarize(result));
            log.info("爬虫批次自动解析完成: batchNo={}, sourcePlatform={}, imported={}, updated={}, summary={}",
                    batchNo, event.sourcePlatform(), event.imported(), event.updated(), summarize(result));
        } catch (Exception exception) {
            // 解析失败不回滚任何已入库的 JD：它们保持 analysis_status=0，可人工或定时重试。
            batchLogService.markAnalysis(event.logId(), MarketJdCrawlerBatchLog.ANALYSIS_FAILED,
                    "解析失败：" + exception.getMessage() + "（JD 保持待分析，可重试）");
            log.warn("爬虫批次自动解析失败: batchNo={}, error={}", batchNo, exception.getMessage(), exception);
        } finally {
            inFlightBatches.remove(batchNo);
        }
    }

    /** 解析结果摘要，写入批次日志便于前端与排障直接阅读。 */
    private static String summarize(MarketJdImportService.BatchAnalysisResult result) {
        if (result == null) {
            return null;
        }
        return "批次共 " + result.getTotalCount() + " 条；进入治理 " + result.getGovernedCount()
                + " 条，跳过重复 " + result.getSkippedDuplicate() + " 条，跳过噪声 " + result.getSkippedNoise()
                + " 条；能力提取成功 " + result.getExtractedSuccess() + " 条，失败 " + result.getExtractedFailed()
                + " 条；自动准入 " + result.getAutoAdmittedCount() + " 条";
    }
}
