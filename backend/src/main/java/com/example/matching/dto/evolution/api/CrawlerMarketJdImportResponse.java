package com.example.matching.dto.evolution.api;

import lombok.Data;

/**
 * 爬虫接收接口的成功响应体（{@code data} 部分）。
 * <p>
 * 对齐爬虫对接文档 §3.3：
 * <ul>
 *   <li>{@code imported / duplicate / updated} **必须**是整数——爬虫据此计算
 *       {@code acceptedCount = imported + duplicate + updated}，缺任一项会被判推送失败并重试；</li>
 *   <li>{@code failed / batchNo} 为文档建议的排障字段；</li>
 *   <li>{@code status / autoAnalysisTriggered} 为主系统扩展字段，爬虫可忽略。</li>
 * </ul>
 *
 * @author system
 */
@Data
public class CrawlerMarketJdImportResponse {

    /** 本批新增条数 */
    private int imported;

    /** 本批正文变化被更新的条数 */
    private int updated;

    /** 本批正文未变被计为重复的条数 */
    private int duplicate;

    /** 本批被跳过的条数 */
    private int failed;

    /** 请求批次号，便于爬虫对账 */
    private String batchNo;

    /**
     * 批次结论：
     * {@code READY_FOR_ANALYSIS}（有新增或更新）/ {@code NO_NEW_DATA}（全为重复）/
     * {@code PARTIAL_FAILED}（有失败且有成功项）。
     */
    private String status;

    /** 是否已自动排队解析（主系统扩展字段） */
    private boolean autoAnalysisTriggered;

    public CrawlerMarketJdImportResponse(int imported, int updated, int duplicate, int failed,
                                        String batchNo, String status, boolean autoAnalysisTriggered) {
        this.imported = imported;
        this.updated = updated;
        this.duplicate = duplicate;
        this.failed = failed;
        this.batchNo = batchNo;
        this.status = status;
        this.autoAnalysisTriggered = autoAnalysisTriggered;
    }

    public static CrawlerMarketJdImportResponse of(int imported, int updated, int duplicate, int failed,
                                                  String batchNo, boolean autoAnalysisTriggered) {
        return new CrawlerMarketJdImportResponse(imported, updated, duplicate, failed, batchNo,
                resolveStatus(imported, updated, duplicate, failed), autoAnalysisTriggered);
    }

    /** 批次结论：有成功入库项即视为可用于后续分析。 */
    public static String resolveStatus(int imported, int updated, int duplicate, int failed) {
        boolean hasProgress = imported + updated > 0;
        if (!hasProgress && duplicate > 0) {
            return "NO_NEW_DATA";
        }
        if (failed > 0 && (hasProgress || duplicate > 0)) {
            return "PARTIAL_FAILED";
        }
        return hasProgress ? "READY_FOR_ANALYSIS" : "REJECTED";
    }
}
