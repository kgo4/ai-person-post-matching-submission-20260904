package com.example.matching.event;

/**
 * 爬虫批次入库完成事件。
 * <p>
 * 由 {@code CrawlerMarketJdIngestService} 在数据落库后发布；监听方
 * （{@code MarketJdBatchAnalysisListener}）在事务提交后异步把该批次送进既有分析链路，
 * 使爬虫推来的 JD「入池即可用」，无需运维手工点「解析该批次」。
 *
 * @param batchNo        爬虫推送批次号
 * @param sourcePlatform 来源平台
 * @param imported       本批新增条数
 * @param updated        本批更新条数
 * @param logId          本次推送对应的批次日志 id（用于回写解析状态；可能为 null）
 * @author system
 */
public record MarketJdBatchImportedEvent(String batchNo, String sourcePlatform,
                                         int imported, int updated, Long logId) {
}
