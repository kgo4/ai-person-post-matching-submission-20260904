package com.example.matching.service.evolution.crawler;

import com.example.matching.config.MarketJdCrawlerProperties;
import com.example.matching.dto.evolution.api.CrawlerMarketJdImportRequest;
import com.example.matching.dto.evolution.api.CrawlerMarketJdImportResponse;
import com.example.matching.dto.evolution.api.CrawlerMarketJdItem;
import com.example.matching.entity.evolution.MarketJdCrawlerBatchLog;
import com.example.matching.entity.evolution.MarketJdData;
import com.example.matching.entity.evolution.MarketJdDataVersion;
import com.example.matching.event.MarketJdBatchImportedEvent;
import com.example.matching.mapper.evolution.MarketJdDataMapper;
import com.example.matching.mapper.evolution.MarketJdDataVersionMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 爬虫推送链路的落库幂等与自动消费触发测试。
 * <p>
 * 覆盖对接文档 §4.2 的幂等三态、§4.2.4「单条失败不回滚整批」，以及本系统扩展的
 * §6.2「有新增/变更才触发解析、纯重复批次跳过」。
 *
 * @author system
 */
class CrawlerJdIngestTest {

    // ===== CrawlerJdItemWriter：幂等三态 =====

    private final MarketJdDataMapper dataMapper = mock(MarketJdDataMapper.class);
    private final MarketJdDataVersionMapper versionMapper = mock(MarketJdDataVersionMapper.class);
    private final CrawlerJdItemWriter writer = new CrawlerJdItemWriter(dataMapper, versionMapper);

    @Test
    void insertsNewItemWithContractFields() {
        when(dataMapper.selectOne(any())).thenReturn(null);

        CrawlerJdItemWriter.Outcome outcome = writer.ingestOne("jd", "batch-1",
                item("jd-1", "Java 工程师", "负责 Java 服务开发", "3 年经验"));

        assertThat(outcome).isEqualTo(CrawlerJdItemWriter.Outcome.IMPORTED);
        ArgumentCaptor<MarketJdData> captor = ArgumentCaptor.forClass(MarketJdData.class);
        verify(dataMapper).insert(captor.capture());
        MarketJdData saved = captor.getValue();
        assertThat(saved.getSourcePlatform()).isEqualTo("jd");
        assertThat(saved.getExternalId()).isEqualTo("jd-1");
        assertThat(saved.getPostName()).isEqualTo("Java 工程师");
        assertThat(saved.getContentHash()).isNotBlank();
        assertThat(saved.getContentCategory()).isEqualTo(MarketJdTextMetrics.CONTENT_CATEGORY_RECRUITMENT_JD);
        assertThat(saved.getTextHash()).isNotBlank();
        assertThat(saved.getFirstSeenTime()).isNotNull();
        assertThat(saved.getLastUpdatedTime()).isNotNull();
        assertThat(saved.getLastSeenTime()).isNotNull();
        assertThat(saved.getAnalysisStatus()).isZero();
        assertThat(saved.getIsDuplicate()).isZero();
        verify(versionMapper, never()).insert(any(MarketJdDataVersion.class));
    }

    @Test
    void unchangedContentIsDuplicateAndOnlyRefreshesLastSeen() {
        MarketJdData existing = new MarketJdData();
        existing.setId(7L);
        existing.setExternalId("jd-7");
        existing.setContentHash(MarketJdTextMetrics.contentHash("职责正文", "要求正文"));
        existing.setAnalysisStatus(1);
        when(dataMapper.selectOne(any())).thenReturn(existing);

        CrawlerJdItemWriter.Outcome outcome = writer.ingestOne("jd", "batch-2",
                item("jd-7", "岗位A", "职责正文", "要求正文"));

        assertThat(outcome).isEqualTo(CrawlerJdItemWriter.Outcome.DUPLICATE);
        // 正文未变：不重写正文、不重置分析状态、不产生历史版本
        assertThat(existing.getPostName()).isNull();
        assertThat(existing.getAnalysisStatus()).isEqualTo(1);
        assertThat(existing.getLastSeenTime()).isNotNull();
        verify(dataMapper).updateById(existing);
        verify(versionMapper, never()).insert(any(MarketJdDataVersion.class));
    }

    @Test
    void changedContentSnapshotsOldVersionAndResetsAnalysisStatus() {
        MarketJdData existing = new MarketJdData();
        existing.setId(9L);
        existing.setExternalId("jd-9");
        existing.setJobDescription("旧版职责");
        existing.setContentHash("stale-hash");
        existing.setAnalysisStatus(1);
        when(dataMapper.selectOne(any())).thenReturn(existing);
        when(versionMapper.selectCount(any())).thenReturn(0L);

        CrawlerJdItemWriter.Outcome outcome = writer.ingestOne("jd", "batch-3",
                item("jd-9", "后端工程师", "新版职责，新增 Go 要求", "5 年经验"));

        assertThat(outcome).isEqualTo(CrawlerJdItemWriter.Outcome.UPDATED);
        assertThat(existing.getAnalysisStatus()).isZero();
        assertThat(existing.getJobDescription()).isEqualTo("新版职责，新增 Go 要求");
        assertThat(existing.getLastUpdatedTime()).isNotNull();
        verify(dataMapper).updateById(existing);
        verify(dataMapper, never()).insert(any(MarketJdData.class));

        ArgumentCaptor<MarketJdDataVersion> versionCaptor =
                ArgumentCaptor.forClass(MarketJdDataVersion.class);
        verify(versionMapper).insert(versionCaptor.capture());
        assertThat(versionCaptor.getValue().getMarketJdId()).isEqualTo(9L);
        assertThat(versionCaptor.getValue().getVersionNo()).isEqualTo(1);
        assertThat(versionCaptor.getValue().getJobDescription()).isEqualTo("旧版职责");
        assertThat(versionCaptor.getValue().getChangeReason()).isEqualTo("CRAWLER_UPDATE");
    }

    @Test
    void snapshotFailureDoesNotBreakUpdate() {
        when(versionMapper.selectCount(any())).thenThrow(new IllegalStateException("db down"));
        MarketJdData existing = new MarketJdData();
        existing.setId(11L);
        existing.setContentHash("stale");
        when(dataMapper.selectOne(any())).thenReturn(existing);

        CrawlerJdItemWriter.Outcome outcome = writer.ingestOne("jd", "batch-4",
                item("jd-11", "数据分析师", "新的职责", null));

        assertThat(outcome).isEqualTo(CrawlerJdItemWriter.Outcome.UPDATED);
        verify(dataMapper).updateById(existing);
    }

    @Test
    void fallsBackToIdentityTupleWhenExternalIdMissing() {
        when(dataMapper.selectOne(any())).thenReturn(null);

        CrawlerJdItemWriter.Outcome outcome = writer.ingestOne("jd", "batch-5",
                item(null, "无外部ID岗位", "职责", "要求"));

        assertThat(outcome).isEqualTo(CrawlerJdItemWriter.Outcome.IMPORTED);
        ArgumentCaptor<MarketJdData> captor = ArgumentCaptor.forClass(MarketJdData.class);
        verify(dataMapper).insert(captor.capture());
        assertThat(captor.getValue().getExternalId()).isNull();
        assertThat(captor.getValue().getPostName()).isEqualTo("无外部ID岗位");
    }

    // ===== CrawlerMarketJdIngestService：批次编排与自动消费触发 =====

    private final CrawlerJdItemWriter mockedWriter = mock(CrawlerJdItemWriter.class);
    private final CrawlerBatchLogService batchLogService = mock(CrawlerBatchLogService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final MarketJdCrawlerProperties properties = new MarketJdCrawlerProperties();
    private final MarketJdAutoAnalyzeSwitch autoAnalyzeSwitch = new MarketJdAutoAnalyzeSwitch(properties);
    private final CrawlerMarketJdIngestService ingestService =
            new CrawlerMarketJdIngestService(mockedWriter, batchLogService, properties,
                    autoAnalyzeSwitch, eventPublisher);

    {
        when(batchLogService.record(any(MarketJdCrawlerBatchLog.class))).thenReturn(1L);
    }

    @Test
    void singleItemFailureDoesNotAbortBatch() {
        // 本用例验证的是「单条失败不影响整批 + 有效数据仍触发解析」，
        // 自动解析默认已关闭，因此需要显式打开开关，否则断言的是另一个场景。
        properties.setAutoAnalyzeEnabled(true);
        when(mockedWriter.ingestOne(eq("jd"), anyString(), any()))
                .thenReturn(CrawlerJdItemWriter.Outcome.IMPORTED)
                .thenThrow(new RuntimeException("duplicate key"))
                .thenReturn(CrawlerJdItemWriter.Outcome.UPDATED);

        CrawlerMarketJdIngestService.IngestReport report = ingestService.ingest(
                request(item("jd-1", "A", "职责A", null),
                        item("jd-2", "B", "职责B", null),
                        item("jd-3", "C", "职责C", null)), "10.0.0.1");

        CrawlerMarketJdImportResponse response = report.response();
        assertThat(response.getImported()).isEqualTo(1);
        assertThat(response.getUpdated()).isEqualTo(1);
        assertThat(response.getFailed()).isEqualTo(1);
        assertThat(report.errors()).hasSize(1);
        assertThat(report.errors().get(0)).contains("items[1]");
        // 有效的两条仍触发解析
        assertThat(response.isAutoAnalysisTriggered()).isTrue();
        verify(eventPublisher).publishEvent(any(MarketJdBatchImportedEvent.class));
        verify(batchLogService).markAnalysis(eq(1L),
                eq(MarketJdCrawlerBatchLog.ANALYSIS_QUEUED), anyString());
    }

    @Test
    void duplicateOnlyBatchSkipsAnalysis() {
        when(mockedWriter.ingestOne(anyString(), anyString(), any()))
                .thenReturn(CrawlerJdItemWriter.Outcome.DUPLICATE);

        CrawlerMarketJdIngestService.IngestReport report = ingestService.ingest(
                request(item("jd-1", "A", "职责A", null), item("jd-2", "B", "职责B", null)), "10.0.0.2");

        assertThat(report.response().getDuplicate()).isEqualTo(2);
        assertThat(report.response().getStatus()).isEqualTo("NO_NEW_DATA");
        assertThat(report.response().isAutoAnalysisTriggered()).isFalse();
        verify(eventPublisher, never()).publishEvent(any());
        verify(batchLogService).markAnalysis(eq(1L),
                eq(MarketJdCrawlerBatchLog.ANALYSIS_SKIPPED), anyString());
    }

    @Test
    void autoAnalysisCanBeDisabledByConfiguration() {
        properties.setAutoAnalyzeEnabled(false);
        when(mockedWriter.ingestOne(anyString(), anyString(), any()))
                .thenReturn(CrawlerJdItemWriter.Outcome.IMPORTED);

        CrawlerMarketJdIngestService.IngestReport report = ingestService.ingest(
                request(item("jd-1", "A", "职责A", null)), "10.0.0.3");

        assertThat(report.response().getImported()).isEqualTo(1);
        assertThat(report.response().isAutoAnalysisTriggered()).isFalse();
        verify(eventPublisher, never()).publishEvent(any());
    }

    /**
     * 【2026-09-04 行为变更】默认必须是「只入库、不解析」。
     * <p>
     * 爬虫按自己的计划推送，如果开箱即自动解析，运维就没有「先看数据、再决定跑不跑」的机会，
     * 而解析会消耗 LLM 配额并把能力标签/准入结论直接写库。这条断言守住默认值。
     */
    @Test
    void autoAnalysisIsOffByDefault() {
        assertThat(properties.isAutoAnalyzeEnabled())
                .as("市场 JD 爬虫自动解析默认必须关闭（改为手动解析）")
                .isFalse();
        when(mockedWriter.ingestOne(anyString(), anyString(), any()))
                .thenReturn(CrawlerJdItemWriter.Outcome.IMPORTED);

        CrawlerMarketJdIngestService.IngestReport report = ingestService.ingest(
                request(item("jd-1", "A", "职责A", null)), "10.0.0.6");

        assertThat(report.response().getImported()).isEqualTo(1);
        assertThat(report.response().isAutoAnalysisTriggered()).isFalse();
        verify(eventPublisher, never()).publishEvent(any());

        // 关闭时不能只说「已跳过」：必须顺带告诉运营去哪里把这批解析掉
        ArgumentCaptor<String> noteCaptor = ArgumentCaptor.forClass(String.class);
        verify(batchLogService).markAnalysis(eq(1L),
                eq(MarketJdCrawlerBatchLog.ANALYSIS_SKIPPED), noteCaptor.capture());
        assertThat(noteCaptor.getValue()).contains("待分析").contains("重新解析");
    }

    /** 运行期开关应当压过配置默认值：配置关闭时仍能临时打开自动解析。 */
    @Test
    void runtimeSwitchOverridesConfiguredDefault() {
        assertThat(autoAnalyzeSwitch.isEnabled()).isFalse();
        autoAnalyzeSwitch.apply(true);
        assertThat(autoAnalyzeSwitch.isEnabled()).isTrue();
        assertThat(autoAnalyzeSwitch.isConfiguredDefault()).isFalse();
        assertThat(autoAnalyzeSwitch.isOverridden()).isTrue();

        when(mockedWriter.ingestOne(anyString(), anyString(), any()))
                .thenReturn(CrawlerJdItemWriter.Outcome.IMPORTED);
        CrawlerMarketJdIngestService.IngestReport report = ingestService.ingest(
                request(item("jd-1", "A", "职责A", null)), "10.0.0.7");

        assertThat(report.response().isAutoAnalysisTriggered()).isTrue();
        verify(eventPublisher).publishEvent(any(MarketJdBatchImportedEvent.class));
    }

    /**
     * 与配置默认值一致的取值不应留下「覆盖」状态：
     * 否则界面会长期显示「临时开关，重启回落」，运维会误以为线上配置被人改过。
     */
    @Test
    void applyingConfiguredDefaultClearsOverride() {
        autoAnalyzeSwitch.apply(true);
        assertThat(autoAnalyzeSwitch.isOverridden()).isTrue();

        autoAnalyzeSwitch.apply(false);
        assertThat(autoAnalyzeSwitch.isOverridden()).isFalse();
        assertThat(autoAnalyzeSwitch.isEnabled()).isFalse();
    }

    @Test
    void emptyItemsIsRejectedAndRecordedWithoutPublishing() {
        CrawlerMarketJdImportRequest request = new CrawlerMarketJdImportRequest();
        request.setBatchNo("batch-empty");
        request.setSourcePlatform("jd");
        request.setItems(List.of());

        assertThatThrownBy(() -> ingestService.ingest(request, "10.0.0.4"))
                .hasMessageContaining("items 不能为空");

        verify(batchLogService).record(any(MarketJdCrawlerBatchLog.class));
        verify(eventPublisher, never()).publishEvent(any());
        verifyNoInteractions(mockedWriter);
    }

    @Test
    void itemWithoutBodyIsFailedAndBatchStillAccepted() {
        when(mockedWriter.ingestOne(anyString(), anyString(), any()))
                .thenReturn(CrawlerJdItemWriter.Outcome.IMPORTED);

        CrawlerMarketJdIngestService.IngestReport report = ingestService.ingest(
                request(item("jd-1", "无正文", null, null), item("jd-2", "B", "职责B", null)), "10.0.0.5");

        assertThat(report.response().getFailed()).isEqualTo(1);
        assertThat(report.response().getImported()).isEqualTo(1);
        assertThat(report.errors().get(0)).contains("不能同时为空");
        verify(mockedWriter, times(1)).ingestOne(anyString(), anyString(), any());
    }

    // ===== helpers =====

    private static CrawlerMarketJdItem item(String externalId, String postName,
                                            String description, String requirements) {
        CrawlerMarketJdItem item = new CrawlerMarketJdItem();
        item.setExternalId(externalId);
        item.setPostName(postName);
        item.setJobDescription(description);
        item.setRequirements(requirements);
        return item;
    }

    private static CrawlerMarketJdImportRequest request(CrawlerMarketJdItem... items) {
        CrawlerMarketJdImportRequest request = new CrawlerMarketJdImportRequest();
        request.setBatchNo("crawler-jd-20260904-001");
        request.setSourcePlatform("jd");
        request.setItems(new ArrayList<>(List.of(items)));
        return request;
    }
}
