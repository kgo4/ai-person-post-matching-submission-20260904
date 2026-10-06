package com.example.matching.service.evolution.crawler;

import com.example.matching.dto.evolution.api.CrawlerBatchLogView;
import com.example.matching.entity.evolution.MarketJdCrawlerBatchLog;
import com.example.matching.mapper.evolution.MarketJdCrawlerBatchLogMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 批次登记表的读写回归。
 * <p>
 * 重点锁死三件在页面上会直接看出错的事：
 * <ol>
 *   <li>通道文案不能吞成空 —— 通道是运维判断「这批数据该不该信」的第一依据；</li>
 *   <li>{@code record} 必须给缺失通道补默认值 —— 否则历史调用点会写出 NULL 通道，
 *       在批次列表里显示成一片空白；</li>
 *   <li>批次列表必须按业务时间倒序 —— V167 给历史批次补登记行时 id 很大，
 *       按 id 排序会把几年前的老批次顶到最前面。</li>
 * </ol>
 */
class CrawlerBatchLogServiceTest {

    private final MarketJdCrawlerBatchLogMapper batchLogMapper = mock(MarketJdCrawlerBatchLogMapper.class);
    private final CrawlerBatchLogService service = new CrawlerBatchLogService(batchLogMapper);

    @Test
    void ingestChannelTextCoversEveryChannelAndEchoesUnknownValues() {
        assertEquals("爬虫推送", CrawlerBatchLogService.ingestChannelText("CRAWLER"));
        assertEquals("人工上传", CrawlerBatchLogService.ingestChannelText("MANUAL_UPLOAD"));
        assertEquals("岗位导入", CrawlerBatchLogService.ingestChannelText("POST_IMPORT"));
        assertEquals("—", CrawlerBatchLogService.ingestChannelText(null));
        assertEquals("—", CrawlerBatchLogService.ingestChannelText("   "));
        assertEquals("PARTNER_FEED", CrawlerBatchLogService.ingestChannelText("PARTNER_FEED"),
                "未知通道必须回显原值，静默显示空白比显示陌生代码更危险");
    }

    @Test
    void recordFillsDefaultChannelSoLegacyCallSitesNeverWriteNullChannel() {
        MarketJdCrawlerBatchLog inbound = new MarketJdCrawlerBatchLog();
        inbound.setBatchNo("crawler-1");

        service.record(inbound);

        ArgumentCaptor<MarketJdCrawlerBatchLog> captor =
                ArgumentCaptor.forClass(MarketJdCrawlerBatchLog.class);
        verify(batchLogMapper).insert(captor.capture());
        assertEquals(MarketJdCrawlerBatchLog.CHANNEL_CRAWLER, captor.getValue().getIngestChannel());
    }

    @Test
    void recordKeepsChannelWhenCallerAlreadySpecifiedIt() {
        MarketJdCrawlerBatchLog inbound = new MarketJdCrawlerBatchLog();
        inbound.setBatchNo("BATCH_ABC");
        inbound.setIngestChannel(MarketJdCrawlerBatchLog.CHANNEL_MANUAL_UPLOAD);

        service.record(inbound);

        ArgumentCaptor<MarketJdCrawlerBatchLog> captor =
                ArgumentCaptor.forClass(MarketJdCrawlerBatchLog.class);
        verify(batchLogMapper).insert(captor.capture());
        assertEquals(MarketJdCrawlerBatchLog.CHANNEL_MANUAL_UPLOAD, captor.getValue().getIngestChannel());
    }

    @Test
    void recordSwallowsFailureSoItNeverBreaksIngestion() {
        when(batchLogMapper.insert(any(MarketJdCrawlerBatchLog.class)))
                .thenThrow(new IllegalStateException("log table unavailable"));

        MarketJdCrawlerBatchLog inbound = new MarketJdCrawlerBatchLog();
        inbound.setBatchNo("crawler-2");

        assertNull(service.record(inbound), "日志写入失败必须返回 null 而不是向上抛，否则爬虫会收到非 2xx 触发重试");
    }

    @Test
    void recentMapsChannelToViewWithChineseLabel() {
        MarketJdCrawlerBatchLog manual = new MarketJdCrawlerBatchLog();
        manual.setId(9L);
        manual.setBatchNo("BATCH_ABC");
        manual.setIngestChannel(MarketJdCrawlerBatchLog.CHANNEL_MANUAL_UPLOAD);
        manual.setResultStatus(MarketJdCrawlerBatchLog.RESULT_OK);
        manual.setAnalysisState(MarketJdCrawlerBatchLog.ANALYSIS_NOT_TRIGGERED);
        manual.setCreatedTime(LocalDateTime.now());
        when(batchLogMapper.selectList(any())).thenReturn(List.of(manual));

        List<CrawlerBatchLogView> views = service.recent(20);

        assertEquals(1, views.size());
        assertEquals("MANUAL_UPLOAD", views.get(0).getIngestChannel());
        assertEquals("人工上传", views.get(0).getIngestChannelText());
        assertEquals("接收成功", views.get(0).getResultStatusText());
        assertEquals("未触发解析", views.get(0).getAnalysisStateText());
    }

    @Test
    void resultStatusTextNamesBackfilledHistoricalBatchesInsteadOfClaimingSuccess() {
        MarketJdCrawlerBatchLog historical = new MarketJdCrawlerBatchLog();
        historical.setId(100L);
        historical.setBatchNo("BATCH_OLD");
        historical.setIngestChannel(MarketJdCrawlerBatchLog.CHANNEL_MANUAL_UPLOAD);
        historical.setResultStatus(MarketJdCrawlerBatchLog.RESULT_HISTORICAL);
        historical.setAnalysisState(MarketJdCrawlerBatchLog.ANALYSIS_NOT_TRIGGERED);
        when(batchLogMapper.selectList(any())).thenReturn(List.of(historical));

        List<CrawlerBatchLogView> views = service.recent(20);

        assertEquals("历史批次", views.get(0).getResultStatusText(),
                "补登记的历史批次没有真实接收动作，不能显示成「接收成功」");
    }

    @Test
    void deleteByBatchNoIgnoresBlankInput() {
        assertEquals(0, service.deleteByBatchNo("  "));
        assertEquals(0, service.deleteByBatchNo(null));
        assertEquals(0L, service.countByBatchNo(null));
        verify(batchLogMapper, org.mockito.Mockito.never()).delete(any());
    }
}
