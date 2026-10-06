package com.example.matching.service.evolution.crawler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.example.matching.dto.evolution.api.CrawlerMarketJdItem;
import com.example.matching.entity.evolution.MarketJdData;
import com.example.matching.entity.evolution.MarketJdDataVersion;
import com.example.matching.mapper.evolution.MarketJdDataMapper;
import com.example.matching.mapper.evolution.MarketJdDataVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 爬虫单条 JD 的落库执行者。
 * <p>
 * 单独成 Bean 的唯一目的：让每条数据拥有**独立事务**，从而满足对接文档 §4.2.4
 * 「单条数据异常：跳过该条，计入 failed，不能回滚整批有效数据」。
 * 若把这段逻辑留在编排方法里，方法级事务会让一条异常带走整批。
 * <p>
 * 幂等三态（文档 §4.2）：
 * <ul>
 *   <li>唯一键不存在 → 新增，返回 {@link Outcome#IMPORTED}；</li>
 *   <li>存在且 {@code content_hash} 相同 → 只刷新 {@code last_seen_time}，返回 {@link Outcome#DUPLICATE}；</li>
 *   <li>存在且 {@code content_hash} 不同 → 先快照旧正文，再更新并重置为待分析，返回 {@link Outcome#UPDATED}。</li>
 * </ul>
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlerJdItemWriter {

    /** 单条落库结果。 */
    public enum Outcome {
        /** 首次到达的新岗位 */
        IMPORTED,
        /** 已存在且正文有变 */
        UPDATED,
        /** 已存在且正文未变 */
        DUPLICATE
    }

    private final MarketJdDataMapper marketJdDataMapper;
    private final MarketJdDataVersionMapper marketJdDataVersionMapper;

    /**
     * 落库一条爬虫推送的 JD。独立事务（{@code REQUIRES_NEW}），异常向外抛出由编排层计 failed。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Outcome ingestOne(String sourcePlatform, String batchNo, CrawlerMarketJdItem item) {
        String externalId = blankToNull(item.getExternalId());
        String contentHash = MarketJdTextMetrics.contentHash(item.getJobDescription(), item.getRequirements());
        MarketJdData existing = findExisting(sourcePlatform, externalId, item);
        if (existing == null) {
            try {
                insertNew(sourcePlatform, batchNo, item, externalId, contentHash);
                return Outcome.IMPORTED;
            } catch (DuplicateKeyException duplicateKey) {
                // 并发重推：唯一索引兜住了重复插入，按「已存在」重新判定，不计 failed。
                log.info("爬虫 JD 并发重复插入，转为存在性判定: platform={}, externalId={}, batchNo={}",
                        sourcePlatform, externalId, batchNo);
                existing = findExisting(sourcePlatform, externalId, item);
                if (existing == null) {
                    throw duplicateKey;
                }
            }
        }
        return refreshExisting(existing, sourcePlatform, batchNo, item, externalId, contentHash);
    }

    // ===== 新增 =====

    private void insertNew(String sourcePlatform, String batchNo, CrawlerMarketJdItem item,
                           String externalId, String contentHash) {
        LocalDateTime now = LocalDateTime.now();
        MarketJdData data = new MarketJdData();
        data.setSourcePlatform(sourcePlatform);
        applyItemFields(data, item, batchNo, externalId);
        data.setContentHash(contentHash);
        data.setContentCategory(MarketJdTextMetrics.CONTENT_CATEGORY_RECRUITMENT_JD);
        data.setCompanyDiversityKey(MarketJdTextMetrics.anonymousCompanyDiversityKey(data.getCompanyName()));
        // text_hash 维持既有口径（含岗位名与公司名），服务跨批次精确去重与近似去重。
        // 口径定义收敛在 MarketJdTextMetrics.dedupeKey，不要在别处再拼一遍。
        data.setTextHash(MarketJdTextMetrics.dedupeKey(
                data.getPostName(), data.getCompanyName(), data.getJobDescription(), data.getRequirements()));
        data.setQualityScore(MarketJdTextMetrics.qualityScore(
                MarketJdTextMetrics.buildFullJdText(data.getJobDescription(), data.getRequirements())));
        data.setFirstSeenTime(now);
        data.setLastSeenTime(now);
        data.setLastUpdatedTime(now);
        data.setIsDuplicate(0);
        // 新增即待分析：由推送后的自动解析链路（或人工 analyze-batch）消费。
        data.setAnalysisStatus(0);
        marketJdDataMapper.insert(data);
    }

    // ===== 已存在：重复 / 更新 =====

    private Outcome refreshExisting(MarketJdData existing, String sourcePlatform, String batchNo,
                                    CrawlerMarketJdItem item, String externalId, String contentHash) {
        LocalDateTime now = LocalDateTime.now();
        if (contentHash.equals(existing.getContentHash())) {
            // 正文一字未变：只刷新「最近看到时间」，不动正文、不重置分析状态，避免无谓重解析。
            existing.setLastSeenTime(now);
            existing.setBatchNo(batchNo);
            // 归属批次变了，通道必须同步（否则批次列表里通道与批次对不上）
            existing.setIngestChannel(MarketJdData.CHANNEL_CRAWLER);
            marketJdDataMapper.updateById(existing);
            return Outcome.DUPLICATE;
        }
        snapshotVersion(existing, batchNo);
        applyItemFields(existing, item, batchNo, externalId);
        existing.setContentHash(contentHash);
        existing.setContentCategory(MarketJdTextMetrics.CONTENT_CATEGORY_RECRUITMENT_JD);
        existing.setLastSeenTime(now);
        existing.setLastUpdatedTime(now);
        existing.setQualityScore(MarketJdTextMetrics.qualityScore(
                MarketJdTextMetrics.buildFullJdText(existing.getJobDescription(), existing.getRequirements())));
        // 正文变了，去重键必须跟着重算：否则 text_hash 仍指向上一个版本的正文，
        // 后续跨批次去重会拿着过期指纹做比较（既可能漏判，也可能把新正文误并进旧分组）。
        existing.setTextHash(MarketJdTextMetrics.dedupeKey(
                existing.getPostName(), existing.getCompanyName(),
                existing.getJobDescription(), existing.getRequirements()));
        // 旧判重结论同样失效：它是按旧正文得出的。曾出现过「一条数据被标过一次重复后，
        // 之后每次重推进来都继续显示重复跳过」的死结 —— 正文已经改过，但 is_duplicate 从不清零。
        existing.setIsDuplicate(0);
        existing.setCanonicalDocumentId(null);
        existing.setSimilarityGroupId(null);
        // 正文变化意味着此前的提取/准入结论已过期，重置为待分析交给既有链路重新消费。
        existing.setAnalysisStatus(0);
        marketJdDataMapper.updateById(existing);
        return Outcome.UPDATED;
    }

    // ===== 内部工具 =====

    /**
     * 定位同源岗位。
     * <p>
     * 优先按契约 {@code source_platform + external_id}（与唯一索引 {@code uk_market_jd_source_external}
     * 一致）；{@code externalId} 缺失时按文档 §4.1 退化为
     * {@code source_platform + jobTitle + companyName + city}。
     */
    private MarketJdData findExisting(String sourcePlatform, String externalId, CrawlerMarketJdItem item) {
        LambdaQueryWrapper<MarketJdData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MarketJdData::getSourcePlatform, sourcePlatform);
        if (externalId != null) {
            wrapper.eq(MarketJdData::getExternalId, externalId);
        } else {
            wrapper.eq(MarketJdData::getPostName, item.getPostName().trim());
            appendNullableEq(wrapper, MarketJdData::getCompanyName, item.getCompanyName());
            appendNullableEq(wrapper, MarketJdData::getCity, item.getCity());
        }
        wrapper.orderByAsc(MarketJdData::getId);
        return marketJdDataMapper.selectOne(wrapper.last("LIMIT 1"));
    }

    /** 可空列的等值条件：null 必须写成 {@code IS NULL}，否则 SQL 里 {@code = NULL} 永不成立。 */
    private static void appendNullableEq(LambdaQueryWrapper<MarketJdData> wrapper,
                                        SFunction<MarketJdData, ?> column, String value) {
        if (value == null) {
            wrapper.isNull(column);
        } else {
            wrapper.eq(column, value);
        }
    }

    /** 将爬虫条目字段写入目标实体（新增与更新共用，保证两条路径字段口径一致）。 */
    private void applyItemFields(MarketJdData data, CrawlerMarketJdItem item, String batchNo, String externalId) {
        data.setBatchNo(batchNo);
        // 通道跟着批次走：一条 JD 若被爬虫重新推送并归入爬虫批次，它就应当被算作爬虫来源，
        // 否则批次列表会出现「批次是爬虫的、通道却写着人工上传」的矛盾。
        data.setIngestChannel(MarketJdData.CHANNEL_CRAWLER);
        data.setExternalId(externalId);
        data.setPostName(item.getPostName().trim());
        data.setCompanyName(item.getCompanyName());
        data.setCity(item.getCity());
        data.setSalaryRange(item.getSalaryRange());
        data.setJobDescription(item.getJobDescription());
        data.setRequirements(item.getRequirements());
        data.setSourceUrl(item.getSourceUrl());
        data.setPublishedTime(item.getPublishedTime());
        data.setCompanyDiversityKey(MarketJdTextMetrics.anonymousCompanyDiversityKey(data.getCompanyName()));
    }

    /** 将实体当前正文存为历史版本（版本号自增），失败不阻断主流程。 */
    private void snapshotVersion(MarketJdData existing, String batchNo) {
        try {
            Long count = marketJdDataVersionMapper.selectCount(
                    new LambdaQueryWrapper<MarketJdDataVersion>()
                            .eq(MarketJdDataVersion::getMarketJdId, existing.getId()));
            MarketJdDataVersion version = new MarketJdDataVersion();
            version.setMarketJdId(existing.getId());
            version.setVersionNo((count == null ? 0 : count.intValue()) + 1);
            version.setBatchNo(batchNo);
            version.setPostName(existing.getPostName());
            version.setCompanyName(existing.getCompanyName());
            version.setCity(existing.getCity());
            version.setSalaryRange(existing.getSalaryRange());
            version.setJobDescription(existing.getJobDescription());
            version.setRequirements(existing.getRequirements());
            version.setSourceUrl(existing.getSourceUrl());
            version.setTextHash(existing.getTextHash());
            version.setChangeReason("CRAWLER_UPDATE");
            marketJdDataVersionMapper.insert(version);
        } catch (Exception exception) {
            log.warn("市场JD历史版本快照写入失败，已跳过: jdId={}", existing.getId(), exception);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}


