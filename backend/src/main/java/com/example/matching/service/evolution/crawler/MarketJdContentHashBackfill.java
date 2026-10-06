package com.example.matching.service.evolution.crawler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.matching.config.MarketJdCrawlerProperties;
import com.example.matching.entity.evolution.MarketJdData;
import com.example.matching.mapper.evolution.MarketJdDataMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 存量数据 {@code content_hash} 启动回填。
 * <p>
 * 为什么要回填：{@code content_hash} 是本次新增的列，历史行全为 {@code NULL}。若直接让爬虫重推，
 * 同一条已存在岗位会因为「哈希不同」被误判为 {@code updated}，进而无谓地快照 + 重置分析状态，
 * 甚至消耗 LLM 配额。启动时把存量行按同一口径补齐，可让第一次重推就正确落在 {@code duplicate}。
 * <p>
 * 约束：
 * <ul>
 *   <li>只在 {@link ApplicationReadyEvent} 之后异步执行，**不阻塞应用就绪**；</li>
 *   <li>分批 + 行数上限（{@code content-hash-backfill-max-rows}），避免大表启动后长时间占用 IO；</li>
 *   <li>任一批失败只告警并停止，不影响服务对外提供能力；下次启动会从剩余空值行继续（天然可续跑）。</li>
 * </ul>
 *
 * @author system
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketJdContentHashBackfill {

    private final MarketJdDataMapper marketJdDataMapper;
    private final MarketJdCrawlerProperties properties;

    @Async("postImportAiExecutor")
    @EventListener(ApplicationReadyEvent.class)
    public void backfillOnStartup() {
        if (!properties.isContentHashBackfillEnabled()) {
            log.info("content_hash 启动回填已关闭（market-jd.crawler.content-hash-backfill-enabled=false）");
            return;
        }
        int batchSize = properties.getContentHashBackfillBatchSize();
        int maxRows = properties.getContentHashBackfillMaxRows();
        int processed = 0;
        try {
            while (processed < maxRows) {
                int limit = Math.min(batchSize, maxRows - processed);
                List<MarketJdData> rows = marketJdDataMapper.selectList(new LambdaQueryWrapper<MarketJdData>()
                        .isNull(MarketJdData::getContentHash)
                        .orderByAsc(MarketJdData::getId)
                        .last("LIMIT " + limit));
                if (rows.isEmpty()) {
                    break;
                }
                for (MarketJdData row : rows) {
                    backfillOne(row);
                }
                processed += rows.size();
                if (rows.size() < limit) {
                    break;
                }
            }
            if (processed > 0) {
                log.info("content_hash 回填完成: 处理 {} 行（上限 {} 行）", processed, maxRows);
            }
        } catch (Exception exception) {
            log.warn("content_hash 回填中断（已处理 {} 行），下次启动将续跑: error={}",
                    processed, exception.getMessage(), exception);
        }
    }

    private void backfillOne(MarketJdData row) {
        String contentHash = MarketJdTextMetrics.contentHash(row.getJobDescription(), row.getRequirements());
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<MarketJdData> update = new LambdaUpdateWrapper<MarketJdData>()
                .eq(MarketJdData::getId, row.getId())
                .isNull(MarketJdData::getContentHash)
                .set(MarketJdData::getContentHash, contentHash)
                .set(MarketJdData::getContentCategory, MarketJdTextMetrics.CONTENT_CATEGORY_RECRUITMENT_JD);
        if (row.getLastUpdatedTime() == null) {
            // 历史行没有「正文最近变更时间」，用创建时间兜底，避免前端展示空值。
            update.set(MarketJdData::getLastUpdatedTime,
                    row.getCreatedTime() == null ? now : row.getCreatedTime());
        }
        marketJdDataMapper.update(null, update);
    }
}


