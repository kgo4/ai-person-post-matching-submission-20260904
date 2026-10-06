package com.example.matching.service.evolution.crawler;

import com.example.matching.common.exception.CrawlerIngressException;
import com.example.matching.config.MarketJdCrawlerProperties;
import com.example.matching.dto.evolution.api.CrawlerMarketJdImportRequest;
import com.example.matching.dto.evolution.api.CrawlerMarketJdImportResponse;
import com.example.matching.dto.evolution.api.CrawlerMarketJdItem;
import com.example.matching.entity.evolution.MarketJdCrawlerBatchLog;
import com.example.matching.event.MarketJdBatchImportedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 爬虫推送的编排入口：校验 → 逐条独立事务落库 → 批次日志 → 触发解析。
 * <p>
 * 关键设计（对应爬虫对接文档 §4.2、§8 与「设计数据如何被使用」）：
 * <ol>
 *   <li><b>不回滚整批</b>：本方法**不加**方法级事务，逐条交给
 *       {@link CrawlerJdItemWriter#ingestOne}（{@code REQUIRES_NEW}）单独提交，
 *       单条异常只计 {@code failed}；</li>
 *   <li><b>幂等且允许重推</b>：重复批次只产生 {@code duplicate}，不报错；</li>
 *   <li><b>是否立刻解析由开关决定</b>：只有本批有新增或正文变更、<b>且</b>
 *       {@link MarketJdAutoAnalyzeSwitch} 处于开启状态时，才发布
 *       {@link MarketJdBatchImportedEvent}，由监听器异步驱动既有
 *       {@code analyzeBatch}（治理 → 去重 → 能力提取 → 准入）。
 *       <b>默认是「只入库、不解析」</b>（{@code market-jd.crawler.auto-analyze-enabled=false}）：
 *       爬虫自行推送，自动解析会在无人值守时消耗 LLM 配额并把结论直接写库，运营没有先审后跑的机会。
 *       关闭时批次台账会写清「已入库 N 条待分析 + 去哪里手动解析」；</li>
 *   <li><b>不阻塞爬虫</b>：解析永远异步，接收接口在 30 秒内必定返回。</li>
 * </ol>
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlerMarketJdIngestService {

    private final CrawlerJdItemWriter itemWriter;
    private final CrawlerBatchLogService batchLogService;
    private final MarketJdCrawlerProperties properties;
    /**
     * 自动解析开关（配置默认 + 运行期覆盖）。
     * <p>
     * 刻意不复用 {@link #properties} 的取值：{@code properties} 只表达部署默认值，
     * 而「本批要不要立刻解析」还要看管理端页面上的临时开关。
     */
    private final MarketJdAutoAnalyzeSwitch autoAnalyzeSwitch;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 一次推送的处理结论。
     *
     * @param response    返回给爬虫的 {@code data}
     * @param itemCount   请求 items 条数
     * @param errors      失败原因明细（用于日志表 error_sample）
     * @param errorSample 失败原因摘要
     * @param logId       批次日志行 id（可能为 null：日志写入失败）
     */
    public record IngestReport(CrawlerMarketJdImportResponse response, int itemCount,
                              List<String> errors, String errorSample, Long logId) {
    }

    /**
     * 处理一次爬虫推送。
     *
     * @param requestIp 调用方 IP（文档 §7.5 留痕要求）
     * @throws CrawlerIngressException 整批级校验失败（HTTP 400），此时已写入一条 REJECTED 批次日志
     */
    public IngestReport ingest(CrawlerMarketJdImportRequest request, String requestIp) {
        long startedAt = System.currentTimeMillis();
        String batchError = CrawlerImportValidator.validateBatch(request, properties.getMaxItems());
        if (batchError != null) {
            recordRejectedIngress(request == null ? null : request.getBatchNo(),
                    request == null ? null : request.getSourcePlatform(),
                    requestIp,
                    request == null || request.getItems() == null ? 0 : request.getItems().size(),
                    batchError,
                    java.net.HttpURLConnection.HTTP_BAD_REQUEST,
                    elapsed(startedAt));
            throw CrawlerIngressException.badRequest(batchError);
        }

        String batchNo = request.getBatchNo();
        String sourcePlatform = request.getSourcePlatform();
        List<CrawlerMarketJdItem> items = request.getItems();
        int imported = 0;
        int updated = 0;
        int duplicate = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        for (int index = 0; index < items.size(); index++) {
            CrawlerMarketJdItem item = items.get(index);
            String itemError = CrawlerImportValidator.validateItem(index, item);
            if (itemError != null) {
                failed++;
                errors.add(itemError);
                continue;
            }
            try {
                CrawlerJdItemWriter.Outcome outcome = itemWriter.ingestOne(sourcePlatform, batchNo, item);
                switch (outcome) {
                    case IMPORTED -> imported++;
                    case UPDATED -> updated++;
                    case DUPLICATE -> duplicate++;
                }
            } catch (Exception exception) {
                failed++;
                errors.add("items[" + index + "] 落库失败：" + rootMessage(exception));
                log.warn("爬虫 JD 落库失败: batchNo={}, sourcePlatform={}, externalId={}, error={}",
                        batchNo, sourcePlatform, item == null ? null : item.getExternalId(),
                        exception.getMessage(), exception);
            }
        }

        boolean progress = imported + updated > 0;
        boolean autoTriggered = progress && autoAnalyzeSwitch.isEnabled();
        CrawlerMarketJdImportResponse response = CrawlerMarketJdImportResponse.of(
                imported, updated, duplicate, failed, batchNo, autoTriggered);

        // 先落日志再发事件：异步监听器需要 logId 回写解析状态。
        Long logId = batchLogService.record(buildBatchLog(batchNo, sourcePlatform, requestIp, items.size(),
                imported, updated, duplicate, failed, errors, response.getStatus(), elapsed(startedAt)));

        if (autoTriggered) {
            batchLogService.markAnalysis(logId, MarketJdCrawlerBatchLog.ANALYSIS_QUEUED,
                    "已排队进入解析链路（治理 → 去重 → 能力提取 → 准入）");
            eventPublisher.publishEvent(
                    new MarketJdBatchImportedEvent(batchNo, sourcePlatform, imported, updated, logId));
        } else {
            batchLogService.markAnalysis(logId, MarketJdCrawlerBatchLog.ANALYSIS_SKIPPED,
                    skipReason(progress, imported + updated));
        }

        log.info("爬虫市场JD接收完成: batchNo={}, sourcePlatform={}, itemCount={}, imported={}, updated={}, "
                        + "duplicate={}, failed={}, status={}, autoAnalysisTriggered={}, costMillis={}",
                batchNo, sourcePlatform, items.size(), imported, updated, duplicate, failed,
                response.getStatus(), autoTriggered, elapsed(startedAt));
        return new IngestReport(response, items.size(), errors,
                CrawlerBatchLogService.buildErrorSample(errors, failed), logId);
    }

    /**
     * 记录一条被拒/未授权的入站请求（鉴权失败、请求体超限等）。
     * <p>
     * 由 Controller 在抛错前调用，保证「任何入站尝试都留痕」（文档 §7.5）。
     * 只记批次号与来源，**绝不记录请求头或密钥**。
     */
    public void recordRejectedIngress(String batchNo, String sourcePlatform, String requestIp,
                                     int itemCount, String message, int httpStatus, int costMillis) {
        MarketJdCrawlerBatchLog batchLog = new MarketJdCrawlerBatchLog();
        batchLog.setBatchNo(batchNo == null ? "-" : batchNo);
        batchLog.setIngestChannel(MarketJdCrawlerBatchLog.CHANNEL_CRAWLER);
        batchLog.setSourcePlatform(sourcePlatform == null ? "-" : sourcePlatform);
        batchLog.setRequestIp(requestIp);
        batchLog.setItemCount(Math.max(itemCount, 0));
        batchLog.setImported(0);
        batchLog.setUpdated(0);
        batchLog.setDuplicate(0);
        batchLog.setFailed(0);
        batchLog.setHttpStatus(httpStatus);
        batchLog.setResultStatus(httpStatus == 401
                ? MarketJdCrawlerBatchLog.RESULT_UNAUTHORIZED
                : MarketJdCrawlerBatchLog.RESULT_REJECTED);
        batchLog.setErrorSample(CrawlerBatchLogService.truncate(message, 2000));
        batchLog.setAnalysisState(MarketJdCrawlerBatchLog.ANALYSIS_NOT_TRIGGERED);
        batchLog.setCostMillis(costMillis);
        batchLogService.record(batchLog);
    }

    /**
     * 未自动解析时写入批次台账的中文结论。
     * <p>
     * 「自动解析已关闭」必须顺带告诉用户**下一步该做什么**：默认手动模式下，看到这句的是运营本人，
     * 只说「已关闭」会被读成「系统不会解析了」，而实际上批次里还有 {@code pendingCount} 条在等着。
     * 同理，`progress=false` 的批次要说明「没有需要解析的新内容」，否则与前者无法区分。
     *
     * @param progress     本批是否有新增或正文变更
     * @param pendingCount 本批新增 + 更新的条数（即真正待解析的条数）
     */
    private static String skipReason(boolean progress, int pendingCount) {
        if (!progress) {
            return "本批无新增或正文变更，无需重复解析";
        }
        return "已入库 " + pendingCount + " 条待分析；自动解析未开启，"
                + "请在批次行点「重新解析」，或到市场 JD 池按批次解析";
    }

    private MarketJdCrawlerBatchLog buildBatchLog(String batchNo, String sourcePlatform, String requestIp,
                                                 int itemCount, int imported, int updated, int duplicate,
                                                 int failed, List<String> errors, String status, int costMillis) {
        MarketJdCrawlerBatchLog batchLog = new MarketJdCrawlerBatchLog();
        batchLog.setBatchNo(batchNo);
        batchLog.setIngestChannel(MarketJdCrawlerBatchLog.CHANNEL_CRAWLER);
        batchLog.setSourcePlatform(sourcePlatform);
        batchLog.setRequestIp(requestIp);
        batchLog.setItemCount(itemCount);
        batchLog.setImported(imported);
        batchLog.setUpdated(updated);
        batchLog.setDuplicate(duplicate);
        batchLog.setFailed(failed);
        batchLog.setHttpStatus(200);
        batchLog.setResultStatus(resolveResultStatus(imported, updated, duplicate, failed));
        batchLog.setErrorSample(CrawlerBatchLogService.buildErrorSample(errors, failed));
        batchLog.setAnalysisState(MarketJdCrawlerBatchLog.ANALYSIS_NOT_TRIGGERED);
        batchLog.setCostMillis(costMillis);
        return batchLog;
    }

    /** 批次结论：全部成功 → OK；部分成功 → PARTIAL_FAILED；全部失败 → REJECTED。 */
    private static String resolveResultStatus(int imported, int updated, int duplicate, int failed) {
        boolean hasSuccess = imported + updated + duplicate > 0;
        if (failed == 0) {
            return MarketJdCrawlerBatchLog.RESULT_OK;
        }
        return hasSuccess ? MarketJdCrawlerBatchLog.RESULT_PARTIAL_FAILED
                : MarketJdCrawlerBatchLog.RESULT_REJECTED;
    }

    private static int elapsed(long startedAt) {
        return (int) Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - startedAt);
    }

    /** 取最内层异常消息作为失败原因，避免长链条堆栈摘要污染日志列。 */
    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
