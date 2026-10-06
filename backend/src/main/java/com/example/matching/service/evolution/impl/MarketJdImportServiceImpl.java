package com.example.matching.service.evolution.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.common.util.SimHash;
import com.example.matching.dto.post.JdAbilityItemDTO;
import com.example.matching.dto.post.PostCleaningResult;
import com.example.matching.dto.post.PostRawInput;
import com.example.matching.entity.evolution.MarketJdCrawlerBatchLog;
import com.example.matching.entity.evolution.MarketJdData;
import com.example.matching.entity.evolution.MarketJdDataVersion;
import com.example.matching.entity.post.PostPost;
import com.example.matching.mapper.evolution.MarketJdDataMapper;
import com.example.matching.mapper.evolution.MarketJdDataVersionMapper;
import com.example.matching.mapper.post.PostPostMapper;
import com.example.matching.config.MarketJdCapabilityAdmissionProperties;
import com.example.matching.service.evolution.MarketJdCapabilityAdmissionService;
import com.example.matching.service.evolution.MarketJdImportService;
import com.example.matching.service.evolution.RecruitmentDataGovernanceService;
import com.example.matching.service.evolution.crawler.CrawlerBatchLogService;
import com.example.matching.service.evolution.crawler.MarketJdAiSkillTagCodec;
import com.example.matching.service.evolution.crawler.MarketJdTextMetrics;
import com.example.matching.service.post.PostCapabilityGenerationService;
import com.example.matching.service.post.PostDataCleaningService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 市场JD导入服务实现
 * <p>
 * 文本 / Excel / 已确认岗位导入，以及批次去重、治理与分析链路。
 * 爬虫推送入口已抽到
 * {@code com.example.matching.service.evolution.crawler.CrawlerMarketJdIngestService}
 * ——它需要「逐条独立事务 + 批次日志 + 自动触发解析」，与这里的职责不同。
 * 所有正文派生字段（哈希 / 质量分 / 匿名主体键）统一走 {@link MarketJdTextMetrics}，
 * 保证两条链路对同一段正文算出完全相同的值。
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketJdImportServiceImpl implements MarketJdImportService {

    /**
     * 兼容旧测试及少量手工装配场景；Spring 生产环境使用 Lombok 生成的完整构造函数。
     */
    @Autowired
    public MarketJdImportServiceImpl(MarketJdDataMapper marketJdDataMapper,
                                     PostPostMapper postPostMapper,
                                     PostCapabilityGenerationService postCapabilityGenerationService,
                                     PostDataCleaningService postDataCleaningService,
                                     ObjectMapper objectMapper,
                                     MarketJdCapabilityAdmissionService admissionService,
                                     MarketJdCapabilityAdmissionProperties admissionProperties,
                                     CrawlerBatchLogService batchLogService,
                                     MarketJdDataVersionMapper marketJdDataVersionMapper) {
        this(marketJdDataMapper, postPostMapper, postCapabilityGenerationService, postDataCleaningService,
                objectMapper, admissionService, admissionProperties,
                new RecruitmentDataGovernanceServiceImpl(marketJdDataMapper),
                batchLogService, marketJdDataVersionMapper);
    }

    private final MarketJdDataMapper marketJdDataMapper;
    private final PostPostMapper postPostMapper;
    private final PostCapabilityGenerationService postCapabilityGenerationService;
    private final PostDataCleaningService postDataCleaningService;
    private final ObjectMapper objectMapper;
    private final MarketJdCapabilityAdmissionService admissionService;
    private final MarketJdCapabilityAdmissionProperties admissionProperties;
    private final RecruitmentDataGovernanceService recruitmentDataGovernanceService;
    /**
     * 批次登记表读写。人工导入也在这里登记一行，使
     * {@code market_jd_crawler_batch_log} 成为**统一批次台账**——
     * 「重新解析」「删除」对爬虫批次与人工批次走同一条路径。
     */
    private final CrawlerBatchLogService batchLogService;
    /** 删除批次时同步清理历史版本快照，否则会留下没有归属的版本行。 */
    private final MarketJdDataVersionMapper marketJdDataVersionMapper;

    @Override
    @Transactional
    public int importFromTextList(List<String> jdTexts, String sourcePlatform) {
        return importFromTextListWithBatch(jdTexts, sourcePlatform).imported();
    }

    @Override
    @Transactional
    public MarketJdImportService.ImportBatchResult importFromTextListWithBatch(List<String> jdTexts,
                                                                                String sourcePlatform) {
        if (jdTexts == null || jdTexts.isEmpty()) {
            return new MarketJdImportService.ImportBatchResult(null, 0);
        }

        String batchNo = "BATCH_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        int imported = 0;
        int skipped = 0;

        for (String jdText : jdTexts) {
            if (jdText == null || jdText.isBlank()) {
                skipped++;
                continue;
            }

            MarketJdData data = new MarketJdData();
            data.setBatchNo(batchNo);
            data.setIngestChannel(MarketJdData.CHANNEL_MANUAL_UPLOAD);
            data.setJobDescription(jdText);
            data.setSourcePlatform(sourcePlatform);
            data.setTextHash(MarketJdTextMetrics.textHash(jdText));
            data.setCompanyDiversityKey(MarketJdTextMetrics.anonymousCompanyDiversityKey(data.getCompanyName()));
            data.setAnalysisStatus(0);
            data.setIsDuplicate(0);

            // 尝试从文本中提取岗位名称
            String postName = extractPostName(jdText);
            data.setPostName(postName);

            // 尝试匹配系统岗位
            Long matchedPostId = matchSystemPost(postName);
            data.setMatchedPostId(matchedPostId);

            // 计算质量分
            data.setQualityScore(calculateQualityScore(jdText));

            marketJdDataMapper.insert(data);
            imported++;
        }

        registerManualBatch(batchNo, MarketJdData.CHANNEL_MANUAL_UPLOAD, sourcePlatform,
                jdTexts.size(), imported, skipped,
                MarketJdCrawlerBatchLog.ANALYSIS_NOT_TRIGGERED,
                "人工上传批次：未自动触发解析，可在「市场 JD 池」按批次手动触发");
        log.info("批量导入市场JD完成: batchNo={}, total={}, imported={}", batchNo, jdTexts.size(), imported);
        return new MarketJdImportService.ImportBatchResult(batchNo, imported);
    }

    @Override
    @Transactional
    public int importFromExcelData(List<MarketJdData> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            return 0;
        }

        String batchNo = "BATCH_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        int imported = 0;
        int skipped = 0;

        for (MarketJdData data : dataList) {
            if (data.getJobDescription() == null || data.getJobDescription().isBlank()) {
                skipped++;
                continue;
            }

            data.setBatchNo(batchNo);
            data.setIngestChannel(MarketJdData.CHANNEL_MANUAL_UPLOAD);
            data.setTextHash(MarketJdTextMetrics.textHash(data.getJobDescription()));
            data.setCompanyDiversityKey(MarketJdTextMetrics.anonymousCompanyDiversityKey(data.getCompanyName()));
            data.setAnalysisStatus(0);
            data.setIsDuplicate(0);

            // 尝试匹配系统岗位
            if (data.getPostName() != null && !data.getPostName().isBlank()) {
                Long matchedPostId = matchSystemPost(data.getPostName());
                data.setMatchedPostId(matchedPostId);
            }

            // 计算质量分
            data.setQualityScore(calculateQualityScore(data.getJobDescription()));

            marketJdDataMapper.insert(data);
            imported++;
        }

        registerManualBatch(batchNo, MarketJdData.CHANNEL_MANUAL_UPLOAD, deriveBatchPlatform(dataList),
                dataList.size(), imported, skipped,
                MarketJdCrawlerBatchLog.ANALYSIS_NOT_TRIGGERED,
                "人工上传批次：未自动触发解析，可在「市场 JD 池」按批次手动触发");
        log.info("从Excel导入市场JD完成: batchNo={}, total={}, imported={}", batchNo, dataList.size(), imported);
        return imported;
    }

    @Override
    @Transactional
    public int importVerifiedPostBatch(Long postImportBatchId, List<VerifiedPostImportJd> jds) {
        if (postImportBatchId == null || jds == null || jds.isEmpty()) {
            return 0;
        }
        String batchNo = "POST_IMPORT_" + postImportBatchId;
        Long existing = marketJdDataMapper.selectCount(new LambdaQueryWrapper<MarketJdData>()
                .eq(MarketJdData::getBatchNo, batchNo));
        if (existing != null && existing > 0) {
            return existing.intValue();
        }

        int imported = 0;
        int skipped = 0;
        for (VerifiedPostImportJd jd : jds) {
            if (jd == null || jd.jobDescription() == null || jd.jobDescription().isBlank()) {
                skipped++;
                continue;
            }
            List<Long> tagIds = jd.verifiedTagIds() == null ? List.of() : jd.verifiedTagIds().stream()
                    .filter(Objects::nonNull).distinct().sorted().toList();
            // 岗位导入批次的纳入只由“已完成岗位 + 有效JD正文”决定。
            // 系统标签库/tagId 是辅助治理数据，不能阻塞市场JD样本进入；
            // 后续市场分析仍会按统一治理和Harness流程计算能力。
            MarketJdData data = new MarketJdData();
            data.setBatchNo(batchNo);
            data.setIngestChannel(MarketJdData.CHANNEL_POST_IMPORT);
            data.setPostName(jd.postName());
            data.setJobDescription(jd.jobDescription());
            data.setSourcePlatform("POST_IMPORT");
            data.setTextHash(MarketJdTextMetrics.textHash(jd.jobDescription()));
            data.setCompanyDiversityKey("");
            data.setMatchedPostId(jd.matchedPostId());
            data.setQualityScore(BigDecimal.valueOf(90));
            data.setIsDuplicate(0);
            data.setAnalysisStatus(1);
            data.setPublishedTime(LocalDateTime.now());
            try {
                data.setSkillTags(objectMapper.writeValueAsString(tagIds));
            } catch (Exception e) {
                throw new IllegalStateException("序列化已确认岗位能力失败", e);
            }
            marketJdDataMapper.insert(data);
            imported++;
        }
        registerManualBatch(batchNo, MarketJdData.CHANNEL_POST_IMPORT, "POST_IMPORT",
                jds.size(), imported, skipped,
                MarketJdCrawlerBatchLog.ANALYSIS_SKIPPED,
                "已复用岗位导入批次的人工确认能力，无需重新解析");
        log.info("已将岗位导入批次纳入市场发现（复用已确认能力，无AI重分析）: postImportBatchId={}, imported={}",
                postImportBatchId, imported);
        return imported;
    }

    /**
     * 人工导入也写一行批次登记。
     * <p>
     * 目的不是"留痕"而是"可管理"：改造前 {@code market_jd_crawler_batch_log} 只登记爬虫批次，
     * 人工上传的批次在批次列表里不可见 —— 既看不到，也就既无法分类、也无法删除。
     * <p>
     * {@link CrawlerBatchLogService#record} 内部吞异常，登记失败不会回滚已经导入的数据。
     */
    private void registerManualBatch(String batchNo, String ingestChannel, String sourcePlatform,
                                     int itemCount, int imported, int failed,
                                     String analysisState, String analysisNote) {
        MarketJdCrawlerBatchLog batchLog = new MarketJdCrawlerBatchLog();
        batchLog.setBatchNo(batchNo);
        batchLog.setIngestChannel(ingestChannel);
        batchLog.setSourcePlatform(sourcePlatform == null || sourcePlatform.isBlank() ? "-" : sourcePlatform);
        batchLog.setItemCount(Math.max(itemCount, 0));
        batchLog.setImported(Math.max(imported, 0));
        batchLog.setUpdated(0);
        batchLog.setDuplicate(0);
        batchLog.setFailed(Math.max(failed, 0));
        batchLog.setHttpStatus(200);
        batchLog.setResultStatus(failed > 0
                ? MarketJdCrawlerBatchLog.RESULT_PARTIAL_FAILED
                : MarketJdCrawlerBatchLog.RESULT_OK);
        batchLog.setAnalysisState(analysisState);
        batchLog.setAnalysisNote(analysisNote);
        batchLogService.record(batchLog);
    }

    /** Excel 批次的来源平台标签：能唯一定位就用它，否则用「EXCEL_MULTI」/「EXCEL」兜底，绝不写空串。 */
    private static String deriveBatchPlatform(List<MarketJdData> dataList) {
        Set<String> platforms = dataList.stream()
                .map(MarketJdData::getSourcePlatform)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (platforms.size() == 1) {
            return platforms.iterator().next();
        }
        return platforms.isEmpty() ? "EXCEL" : "EXCEL_MULTI";
    }

    @Override
    @Transactional
    public MarketJdImportService.BatchDeleteResult deleteBatch(String batchNo) {
        if (batchNo == null || batchNo.isBlank()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "批次号不能为空");
        }

        List<Long> jdIds = marketJdDataMapper.selectList(new LambdaQueryWrapper<MarketJdData>()
                        .select(MarketJdData::getId)
                        .eq(MarketJdData::getBatchNo, batchNo))
                .stream()
                .map(MarketJdData::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (jdIds.isEmpty() && batchLogService.countByBatchNo(batchNo) == 0) {
            // 先判定再删除：批次不存在时不应产生任何副作用
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "未找到该批次：" + batchNo);
        }

        // 级联清理与单条删除共用同一条路径，避免两套逻辑各自漏表
        int deletedVersions = purgeJdDependents(jdIds);

        int deletedRows = marketJdDataMapper.delete(new LambdaQueryWrapper<MarketJdData>()
                .eq(MarketJdData::getBatchNo, batchNo));
        int deletedLogs = batchLogService.deleteByBatchNo(batchNo);

        log.info("删除市场JD批次完成: batchNo={}, rows={}, versions={}, logs={}",
                batchNo, deletedRows, deletedVersions, deletedLogs);
        return new MarketJdImportService.BatchDeleteResult(batchNo, deletedRows, deletedVersions, deletedLogs);
    }

    @Override
    @Transactional
    public MarketJdImportService.SingleDeleteResult deleteJd(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "JD id 不能为空");
        }
        MarketJdData existing = marketJdDataMapper.selectById(id);
        if (existing == null) {
            // 先判定再删除：不存在时不产生任何副作用（也不会顺手清掉别处的引用）
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "未找到该 JD：" + id);
        }

        int deletedVersions = purgeJdDependents(List.of(id));
        int deletedRows = marketJdDataMapper.deleteById(id);

        log.info("删除市场JD完成: id={}, postName={}, rows={}, versions={}",
                id, existing.getPostName(), deletedRows, deletedVersions);
        return new MarketJdImportService.SingleDeleteResult(id, deletedRows, deletedVersions);
    }

    @Override
    @Transactional
    public MarketJdImportService.BatchDeleteByIdsResult deleteJds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "请至少选择一条 JD");
        }
        // 去重：前端「全选当前页」时可能混入重复行，重复 id 只删一次
        List<Long> distinctIds = ids.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (distinctIds.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "请至少选择一条 JD");
        }

        // 先算出真实存在的 id，再据此清理与删除 —— 这样「不存在」与「已删除」是可区分的，
        // 返回给前端后可以提示「有 N 条已被他人删除」，而不是假装全部成功。
        List<Long> existingIds = marketJdDataMapper.selectList(new LambdaQueryWrapper<MarketJdData>()
                        .select(MarketJdData::getId)
                        .in(MarketJdData::getId, distinctIds))
                .stream()
                .map(MarketJdData::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        List<Long> missingIds = distinctIds.stream()
                .filter(id -> !existingIds.contains(id))
                .collect(Collectors.toList());
        if (existingIds.isEmpty()) {
            return new MarketJdImportService.BatchDeleteByIdsResult(distinctIds.size(), 0, 0, missingIds);
        }

        int deletedVersions = purgeJdDependents(existingIds);
        int deletedRows = marketJdDataMapper.delete(new LambdaQueryWrapper<MarketJdData>()
                .in(MarketJdData::getId, existingIds));

        log.info("批量删除市场JD完成: requested={}, rows={}, versions={}, missing={}",
                distinctIds.size(), deletedRows, deletedVersions, missingIds.size());
        return new MarketJdImportService.BatchDeleteByIdsResult(
                distinctIds.size(), deletedRows, deletedVersions, missingIds);
    }

    /**
     * 清理被删 JD 的从属数据，返回删除的历史版本快照行数。
     * <p>
     * 批次删除与单条/批量删除的选择范围不同，但**清理动作必须是同一套**，否则会出现
     * 「按批次删干净了、按单条删留下了悬空分组」这类只在高频操作下才暴露的脏数据：
     * <ol>
     *   <li>删掉这些 JD 的历史版本快照；</li>
     *   <li>去重会把「模板抄袭」的行指向同批次的规范文档，规范文档被删后，其它批次里
     *       指向它的 canonical_document_id / similarity_group_id 就成了悬空引用，
     *       必须一并清空，否则前端会展示一个永远点不开的分组。</li>
     * </ol>
     *
     * @param ids 被删 JD 的主键
     * @return {@code market_jd_data_version} 删除行数
     */
    private int purgeJdDependents(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        int deletedVersions = marketJdDataVersionMapper.delete(new LambdaQueryWrapper<MarketJdDataVersion>()
                .in(MarketJdDataVersion::getMarketJdId, ids));
        marketJdDataMapper.update(null, new LambdaUpdateWrapper<MarketJdData>()
                .set(MarketJdData::getCanonicalDocumentId, null)
                .set(MarketJdData::getSimilarityGroupId, null)
                .in(MarketJdData::getCanonicalDocumentId, ids));
        return deletedVersions;
    }

    @Override
    public IPage<MarketJdData> pageMarketJds(Page<MarketJdData> page, String postName, String batchNo) {
        LambdaQueryWrapper<MarketJdData> wrapper = new LambdaQueryWrapper<>();
        if (postName != null && !postName.isBlank()) {
            wrapper.like(MarketJdData::getPostName, postName);
        }
        if (batchNo != null && !batchNo.isBlank()) {
            wrapper.eq(MarketJdData::getBatchNo, batchNo);
        }
        wrapper.orderByDesc(MarketJdData::getCreatedTime);
        return marketJdDataMapper.selectPage(page, wrapper);
    }

    @Override
    public List<MarketJdData> getMarketJdsByPostId(Long postId, int limit) {
        LambdaQueryWrapper<MarketJdData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MarketJdData::getMatchedPostId, postId);
        wrapper.eq(MarketJdData::getIsDuplicate, 0);
        wrapper.orderByDesc(MarketJdData::getPublishedTime);
        wrapper.last("LIMIT " + limit);
        return marketJdDataMapper.selectList(wrapper);
    }

    @Override
    @Transactional
    public int deduplicateByBatch(String batchNo) {
        List<MarketJdData> allData = marketJdDataMapper.selectList(
                new LambdaQueryWrapper<MarketJdData>()
                        .eq(MarketJdData::getBatchNo, batchNo)
                        .eq(MarketJdData::getIsDuplicate, 0));

        Set<String> seenHashes = new HashSet<>();
        Map<String, Boolean> historicalDuplicateCache = new HashMap<>();
        Map<String, MarketJdData> canonicalByHash = new HashMap<>();
        int duplicateCount = 0;

        // 本批首次出现、且未命中精确去重的记录，作为近似去重的候选基准
        List<MarketJdData> uniqueForSimHash = new ArrayList<>();

        // ===== 第一遍：SHA-256 精确去重（含跨批次，完全一致才命中）=====
        for (MarketJdData data : allData) {
            String textHash = data.getTextHash();
            boolean seenInEarlierBatch = textHash != null && historicalDuplicateCache.computeIfAbsent(
                    textHash, hash -> existsInAnotherBatch(hash, batchNo));
            boolean seenInCurrentBatch = textHash != null && !seenHashes.add(textHash);

            if (seenInEarlierBatch || seenInCurrentBatch) {
                data.setIsDuplicate(1);
                MarketJdData canonical = canonicalByHash.get(textHash);
                if (canonical != null) {
                    data.setCanonicalDocumentId(canonical.getId());
                    data.setSimilarityGroupId(similarityGroupId(canonical));
                }
                marketJdDataMapper.updateById(data);
                duplicateCount++;
            } else {
                if (textHash != null) {
                    canonicalByHash.put(textHash, data);
                }
                uniqueForSimHash.add(data);
            }
        }

        // ===== 第二遍：SimHash 近似去重（模板抄袭：措辞略不同但本质相同）=====
        // 仅对上述「唯一」记录做两两近似比对，命中即归并到先出现者（canonical）
        List<Long> canonicalSimHashes = new ArrayList<>();
        List<MarketJdData> canonicalJds = new ArrayList<>();
        for (MarketJdData data : uniqueForSimHash) {
            long simHash = SimHash.compute(buildFullJdText(data));
            MarketJdData nearDuplicateCanonical = null;
            for (int i = 0; i < canonicalSimHashes.size(); i++) {
                if (SimHash.isNearDuplicate(simHash, canonicalSimHashes.get(i))) {
                    nearDuplicateCanonical = canonicalJds.get(i);
                    break;
                }
            }

            if (nearDuplicateCanonical != null) {
                data.setIsDuplicate(1);
                data.setCanonicalDocumentId(nearDuplicateCanonical.getId());
                data.setSimilarityGroupId(similarityGroupId(nearDuplicateCanonical));
                marketJdDataMapper.updateById(data);
                duplicateCount++;
            } else {
                canonicalSimHashes.add(simHash);
                canonicalJds.add(data);
            }
        }

        log.info("去重处理完成: batchNo={}, duplicates={} (精确+近似)", batchNo, duplicateCount);
        return duplicateCount;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 实现要点：**清判定与改去重键必须在重新去重之前一次性落库**，否则
     * {@link #deduplicateByBatch(String)} 读到的仍是旧值（它只捞 {@code isDuplicate=0} 的行）。
     */
    @Override
    @Transactional
    public DedupeResetResult resetDedupeByBatch(String batchNo) {
        if (batchNo == null || batchNo.isBlank()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "批次号不能为空");
        }
        List<MarketJdData> rows = marketJdDataMapper.selectList(
                new LambdaQueryWrapper<MarketJdData>().eq(MarketJdData::getBatchNo, batchNo));
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "该批次没有市场 JD 数据：" + batchNo);
        }

        int recomputed = 0;
        for (MarketJdData row : rows) {
            LambdaUpdateWrapper<MarketJdData> update = new LambdaUpdateWrapper<MarketJdData>()
                    .eq(MarketJdData::getId, row.getId())
                    .set(MarketJdData::getIsDuplicate, 0)
                    .set(MarketJdData::getCanonicalDocumentId, null)
                    .set(MarketJdData::getSimilarityGroupId, null);
            if (MarketJdData.CHANNEL_CRAWLER.equals(row.getIngestChannel())) {
                // 爬虫行的去重键可由结构化字段完整复原（与 CrawlerJdItemWriter 同一口径）
                update.set(MarketJdData::getTextHash, MarketJdTextMetrics.dedupeKey(
                        row.getPostName(), row.getCompanyName(),
                        row.getJobDescription(), row.getRequirements()));
                recomputed++;
            }
            // 清 null 必须走 update + set(...)，MyBatis-Plus 的 updateById 会忽略 null 字段
            marketJdDataMapper.update(null, update);
        }

        int duplicateHits = deduplicateByBatch(batchNo);
        log.info("批次去重重算完成: batchNo={}, scanned={}, hashRecomputed={}, duplicateHits={}",
                batchNo, rows.size(), recomputed, duplicateHits);
        return new DedupeResetResult(batchNo, rows.size(), recomputed, duplicateHits);
    }

    /**
     * 生成相似分组 ID：以规范文档（canonical）ID 为锚点，
     * 供前端按「原始条数 → 去重条数」折叠展示同模板 JD。
     */
    private String similarityGroupId(MarketJdData canonical) {
        return "GROUP_" + canonical.getId();
    }

    private boolean existsInAnotherBatch(String textHash, String currentBatchNo) {
        Long existingCount = marketJdDataMapper.selectCount(
                new LambdaQueryWrapper<MarketJdData>()
                        .eq(MarketJdData::getTextHash, textHash)
                        .eq(MarketJdData::getIsDuplicate, 0)
                        .ne(MarketJdData::getBatchNo, currentBatchNo));
        return existingCount != null && existingCount > 0;
    }

    @Override
    public BatchStatistics getBatchStatistics(String batchNo) {
        BatchStatistics stats = new BatchStatistics();
        stats.setBatchNo(batchNo);

        List<MarketJdData> allData = marketJdDataMapper.selectList(
                new LambdaQueryWrapper<MarketJdData>()
                        .eq(MarketJdData::getBatchNo, batchNo));

        stats.setTotalCount(allData.size());
        stats.setDuplicateCount((int) allData.stream().filter(d -> d.getIsDuplicate() != null && d.getIsDuplicate() == 1).count());
        stats.setAnalyzedCount((int) allData.stream().filter(d -> d.getAnalysisStatus() != null && d.getAnalysisStatus() == 1).count());
        stats.setMatchedCount((int) allData.stream().filter(d -> d.getMatchedPostId() != null).count());

        return stats;
    }

    // ===== 内部方法 =====
    // 文本派生字段（哈希 / 匿名主体键 / 质量分 / 拼正文）一律委托 MarketJdTextMetrics，
    // 与爬虫推送链路 CrawlerJdItemWriter 共用同一口径，避免两条链路算出不同的 text_hash。

    private String extractPostName(String jdText) {
        // 跳过序号、列表编号等无效首行，避免把“1/01/1.”写成岗位名称。
        String[] lines = jdText.split("\n");
        for (String line : lines) {
            String candidate = line == null ? "" : line.trim();
            if (candidate.isBlank() || candidate.matches("^[0-9]+(?:[.、)）：:]?)$")) {
                continue;
            }
            candidate = candidate.replaceFirst("^[0-9]+[.、)）：:]\\s*", "").trim();
            if (candidate.isBlank() || candidate.matches("^[0-9]+$")) {
                continue;
            }
            return candidate.length() > 50 ? candidate.substring(0, 50) : candidate;
        }
        return "";
    }

    private Long matchSystemPost(String postName) {
        if (postName == null || postName.isBlank()) {
            return null;
        }

        // 精确匹配
        PostPost exactMatch = postPostMapper.selectOne(
                new LambdaQueryWrapper<PostPost>()
                        .eq(PostPost::getPostName, postName)
                        .last("LIMIT 1"));
        if (exactMatch != null) {
            return exactMatch.getId();
        }

        // 模糊匹配
        PostPost fuzzyMatch = postPostMapper.selectOne(
                new LambdaQueryWrapper<PostPost>()
                        .like(PostPost::getPostName, postName)
                        .last("LIMIT 1"));
        if (fuzzyMatch != null) {
            return fuzzyMatch.getId();
        }

        return null;
    }

    private BigDecimal calculateQualityScore(String jdText) {
        return MarketJdTextMetrics.qualityScore(jdText);
    }

    @Override
    public BatchAnalysisResult analyzeBatch(String batchNo) {
        // 治理是市场 JD 分析的统一前置步骤，保证去重、噪声和时效统计与实际分析口径一致。
        // 方法本身幂等：已处理或已跳过的数据不会再次被治理。
        deduplicateByBatch(batchNo);
        RecruitmentDataGovernanceService.GovernanceResult governanceResult =
                recruitmentDataGovernanceService.governBatch(batchNo);
        if (governanceResult == null) {
            governanceResult = new RecruitmentDataGovernanceService.GovernanceResult(0, 0, 0, 0, List.of());
        }

        // 特性开关：enabled=false 时保持改造前的行为不变（feature-flag 部署/回滚用）
        if (!admissionProperties.isEnabled()) {
            log.info("市场JD能力自动准入已关闭(enabled=false)，使用传统分析路径: batchNo={}", batchNo);
            return analyzeBatchLegacy(batchNo, governanceResult);
        }
        log.info("开始批量分析市场JD: batchNo={}", batchNo);

        BatchAnalysisResult result = new BatchAnalysisResult();
        result.setBatchNo(batchNo);
        result.setErrors(new ArrayList<>());

        // ===== ① 查询批次中待分析的JD（跳过已标记重复的）=====
        List<MarketJdData> candidateJds = marketJdDataMapper.selectList(
                new LambdaQueryWrapper<MarketJdData>()
                        .eq(MarketJdData::getBatchNo, batchNo)
                        .eq(MarketJdData::getIsDuplicate, 0)
                        .eq(MarketJdData::getAnalysisStatus, 0));

        // 统计跳过数
        Long duplicateCountLong = marketJdDataMapper.selectCount(
                new LambdaQueryWrapper<MarketJdData>()
                        .eq(MarketJdData::getBatchNo, batchNo)
                        .eq(MarketJdData::getIsDuplicate, 1));
        int duplicateCount = duplicateCountLong != null ? duplicateCountLong.intValue() : 0;

        Long totalInBatchLong = marketJdDataMapper.selectCount(
                new LambdaQueryWrapper<MarketJdData>()
                        .eq(MarketJdData::getBatchNo, batchNo));
        int totalInBatch = totalInBatchLong != null ? totalInBatchLong.intValue() : 0;

        result.setTotalCount(totalInBatch);
        result.setSkippedDuplicate(duplicateCount);
        result.setSkippedNoise(governanceResult.noiseFiltered());
        result.setGovernedCount(candidateJds.size());

        if (candidateJds.isEmpty()) {
            log.info("批次无待分析JD: batchNo={}", batchNo);
            return result;
        }

        // ===== ② 逐条清洗+提取（延迟准入：不在提取阶段立即写 skillTags / 不调 Harness）=====
        //      PostDataCleaningService.cleanAndDetect() → 清洗去噪去重+质量评分+阻断判定
        //      → PostCapabilityGenerationService.analyzeMarketJdText() → Agent提取能力（不调 checkAbilities）
        //      清洗仅在 MarketJdImportServiceImpl 执行一次，analyzeMarketJdText 接收已清洗文本
        log.info("开始Agent提取岗位能力: batchNo={}, count={}", batchNo, candidateJds.size());
        int successCount = 0;
        int blockedCount = 0;
        int failedCount = 0;

        List<MarketJdCapabilityAdmissionService.JdExtraction> extractions = new ArrayList<>();
        Map<Long, MarketJdData> jdById = new LinkedHashMap<>();

        for (MarketJdData jd : candidateJds) {
            try {
                String postName = jd.getPostName() != null ? jd.getPostName() : "未命名岗位";
                String jdText = buildFullJdText(jd);
                Long jdId = jd.getId();
                String sourceType = "MARKET_JD";

                // 招聘主体仅保留匿名稳定键用于跨主体门禁，绝不下传给 Agent、Harness 或前端。
                String companyDiversityKey = MarketJdTextMetrics.anonymousCompanyDiversityKey(jd.getCompanyName());
                jd.setCompanyDiversityKey(companyDiversityKey);
                // 清洗去噪+质量评分+去重检测（仅此一次）
                PostRawInput rawInput = PostRawInput.builder()
                        .postName(postName)
                        .rawText(jdText)
                        .sourceType(sourceType)
                        .sourceRefId(jdId)
                        .build();
                PostCleaningResult cleaningResult = postDataCleaningService.cleanAndDetect(rawInput);

                if (cleaningResult.isBlocked()) {
                    blockedCount++;
                    jd.setQualityScore(cleaningResult.getQualityScore());
                    jd.setAnalysisStatus(2);
                    marketJdDataMapper.updateById(jd);
                    log.info("JD被清洗阻断: jdId={}, postName={}, cleaningRecordId={}, reason={}",
                            jd.getId(), jd.getPostName(), cleaningResult.getCleaningRecordId(),
                            cleaningResult.getBlockReason());
                    continue;
                }

                String cleanedPostName = cleaningResult.getCleanedPostName() != null
                        ? cleaningResult.getCleanedPostName() : postName;
                String cleanedJdText = cleaningResult.getCleanedText() != null
                        ? cleaningResult.getCleanedText() : jdText;

                // 服务端生成的可信来源引用：仅 source:MARKET_JD:<jdId>。
                // platform:/cleaning: 属于内部准入元数据，绝不进入 Harness（Task 5a）。
                List<String> serverGeneratedRefs = List.of("source:MARKET_JD:" + jdId);

                List<JdAbilityItemDTO> abilities = postCapabilityGenerationService.analyzeMarketJdText(
                        cleanedPostName, cleanedJdText, jdId, serverGeneratedRefs);

                // AI 原始提取结果当场记下来。下游「标签匹配 → 准入门禁 → Harness」会层层过滤，
                // 若只保留通过准入的标签ID，则「提取到了但没准入」在库里没有任何痕迹，
                // 用户看到的就是「解析完成了，但一条结果都没有」。
                jd.setAiSkillTags(MarketJdAiSkillTagCodec.encode(abilities, objectMapper));

                extractions.add(new MarketJdCapabilityAdmissionService.JdExtraction(
                        jdId, cleanedJdText, companyDiversityKey, serverGeneratedRefs, abilities));
                jdById.put(jdId, jd);

                successCount++;
                log.info("JD提取成功: jdId={}, postName={}, abilities={}, cleaningRecordId={}",
                        jdId, postName, abilities != null ? abilities.size() : 0,
                        cleaningResult.getCleaningRecordId());
            } catch (com.example.matching.common.exception.BusinessException be) {
                blockedCount++;
                jd.setAnalysisStatus(2);
                marketJdDataMapper.updateById(jd);
                log.info("JD分析失败(业务异常): jdId={}, postName={}, reason={}",
                        jd.getId(), jd.getPostName(), be.getMessage());
            } catch (Exception e) {
                failedCount++;
                String errorMsg = "JD提取失败: jdId=" + jd.getId()
                        + ", postName=" + jd.getPostName()
                        + ", error=" + e.getMessage();
                log.error(errorMsg, e);
                result.getErrors().add(errorMsg);
            }
        }

        // ===== ③ 准入决策（事务外，恰好一次）：确定性门禁 + 批量 Harness + 新标签准入 =====
        MarketJdCapabilityAdmissionService.AdmissionPlan plan = null;
        if (!extractions.isEmpty()) {
            try {
                plan = admissionService.admitBatch(
                        new MarketJdCapabilityAdmissionService.AdmissionBatchRequest(batchNo, extractions));
            } catch (Exception e) {
                // Harness 基础设施失败：受影响 JD 保持 analysisStatus=0，可重试；绝不准入
                failedCount += jdById.size();
                String errorMsg = "批次准入失败（基础设施）: batchNo=" + batchNo + ", error=" + e.getMessage();
                log.error(errorMsg, e);
                result.getErrors().add(errorMsg);
            }
        }

        // ===== ④ 按决策持久化（每条 JD 短事务/update，全部决策完成后）=====
        // 无论准入是否成功都要落库：`ai_skill_tags` 记录的是 AI 提取的原始结果，
        // 准入整体失败（plan == null）时它同样应当可见 —— 否则用户连「AI 到底读出了什么」都看不到。
        for (Map.Entry<Long, MarketJdData> entry : jdById.entrySet()) {
            MarketJdData jd = entry.getValue();
            if (plan != null) {
                LinkedHashSet<Long> accepted = plan.acceptedTagIdsByJd()
                        .getOrDefault(jd.getId(), new LinkedHashSet<>());
                LinkedHashSet<Long> recommended = plan.recommendedTagIdsByJd()
                        .getOrDefault(jd.getId(), new LinkedHashSet<>());
                jd.setSkillTags(serializeAcceptedTagIds(accepted));
                jd.setRecommendedSkillTags(serializeAcceptedTagIds(recommended));
                jd.setAnalysisStatus(plan.infraFailedJdIds().contains(jd.getId()) ? 0 : 1);
            }
            marketJdDataMapper.updateById(jd);
        }

        if (plan != null) {
            result.setAutoAdmittedCount(plan.autoAcceptedCount());
            result.setHarnessPassCount(plan.harnessPassCount());
            result.setHarnessBlockedCount(plan.harnessBlockedCount());
            result.setReviewCandidateGroupCount(plan.reviewCandidateGroupCount());
            result.setRejectedClaimCount(plan.rejectedClaimCount());
            for (Long failedJdId : plan.infraFailedJdIds()) {
                result.getErrors().add("Harness基础设施失败，JD保持可重试: jdId=" + failedJdId);
            }
            log.info("批量准入完成: batchNo={}, autoAdmitted={}, harnessPass={}, harnessBlocked={}, "
                            + "reviewGroups={}, rejected={}, infraFailedJds={}",
                    batchNo, plan.autoAcceptedCount(), plan.harnessPassCount(), plan.harnessBlockedCount(),
                    plan.reviewCandidateGroupCount(), plan.rejectedClaimCount(), plan.infraFailedJdIds().size());
        }

        result.setSkippedNoise(governanceResult.noiseFiltered() + blockedCount);
        result.setExtractedSuccess(successCount);
        result.setExtractedFailed(failedCount);

        log.info("批量分析完成: batchNo={}, total={}, candidates={}, success={}, blocked={}, failed={}",
                batchNo, totalInBatch, candidateJds.size(), successCount, blockedCount, failedCount);
        return result;
    }

    // ==================== 单条解析 ====================

    /**
     * 单条解析：链路与 {@link #analyzeBatch(String)} 完全一致，只把范围收窄到一条。
     * <p>
     * 与批次解析刻意不同的三处，都源于「这是用户的显式动作」这个语义：
     * <ol>
     *   <li>不要求 {@code analysisStatus=0}：允许对已分析 / 已跳过的 JD 重跑
     *       （批次路径只捞 0，是为了避免重复劳动，不是业务限制）；</li>
     *   <li>基础设施失败时**不覆盖 skill_tags**：批次路径会把失败 JD 的 skillTags 写成 {@code []}，
     *       单条重跑时那等于把上一次的成功结果擦掉；</li>
     *   <li>成功时补写 {@code qualityScore}（批次路径只在「清洗阻断」分支写质量分）。</li>
     * </ol>
     */
    @Override
    public MarketJdImportService.SingleAnalysisResult analyzeOne(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "请指定要解析的市场 JD");
        }
        MarketJdData jd = marketJdDataMapper.selectById(id);
        if (jd == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "该市场 JD 不存在或已被删除");
        }
        if (jd.getIsDuplicate() != null && jd.getIsDuplicate() == 1) {
            // 去重命中的 JD 在批次解析里也被刻意排除。单条解析必须给出一致结论，
            // 否则用户会以为「手点一下就能绕过去重」，而结果里永远不会有标签。
            throw new BusinessException(ErrorCodeEnum.STATE_CONFLICT,
                    "该 JD 已被判定为重复项（同批次内存在等价的规范正文），解析会跳过它；"
                            + "确需解析请先删除该条重复数据。");
        }

        String postName = jd.getPostName() != null ? jd.getPostName() : "未命名岗位";
        String jdText = buildFullJdText(jd);
        // 招聘主体只保留匿名稳定键（与批次路径同口径），绝不下传给 Agent / Harness / 前端
        String companyDiversityKey = MarketJdTextMetrics.anonymousCompanyDiversityKey(jd.getCompanyName());
        jd.setCompanyDiversityKey(companyDiversityKey);

        // ===== ① 清洗（与批次路径同一个服务、同一次判定）=====
        PostRawInput rawInput = PostRawInput.builder()
                .postName(postName)
                .rawText(jdText)
                .sourceType("MARKET_JD")
                .sourceRefId(id)
                .build();
        PostCleaningResult cleaningResult = postDataCleaningService.cleanAndDetect(rawInput);
        jd.setQualityScore(cleaningResult.getQualityScore());

        if (cleaningResult.isBlocked()) {
            jd.setAnalysisStatus(2);
            marketJdDataMapper.updateById(jd);
            log.info("单条解析：JD被清洗阻断: jdId={}, cleaningRecordId={}, reason={}",
                    id, cleaningResult.getCleaningRecordId(), cleaningResult.getBlockReason());
            return new MarketJdImportService.SingleAnalysisResult(id, 2, 0, 0, 0, false,
                    "正文被清洗判定为噪声或无效内容，已跳过（不参与岗位演化与市场统计）。");
        }

        String cleanedPostName = cleaningResult.getCleanedPostName() != null
                ? cleaningResult.getCleanedPostName() : postName;
        String cleanedJdText = cleaningResult.getCleanedText() != null
                ? cleaningResult.getCleanedText() : jdText;
        // 服务端生成的可信来源引用（Task 5a：platform:/cleaning: 属内部准入元数据，绝不进 Harness）
        List<String> serverGeneratedRefs = List.of("source:MARKET_JD:" + id);

        // ===== ② 能力提取 =====
        List<JdAbilityItemDTO> abilities;
        try {
            abilities = postCapabilityGenerationService.analyzeMarketJdText(
                    cleanedPostName, cleanedJdText, id, serverGeneratedRefs);
        } catch (BusinessException be) {
            // 业务异常 = 「这条 JD 不适用于能力提取」，与清洗阻断同为「跳过」，不是系统故障
            jd.setAnalysisStatus(2);
            marketJdDataMapper.updateById(jd);
            log.info("单条解析失败(业务异常): jdId={}, reason={}", id, be.getMessage());
            return new MarketJdImportService.SingleAnalysisResult(id, 2, 0, 0, 0, false,
                    "能力提取判定该 JD 不适用（" + be.getMessage() + "），已跳过。");
        } catch (Exception e) {
            // 非业务异常 = 基础设施抖动：状态保持 0 以便重试，绝不写 1（写了就再也捞不回来）
            jd.setAnalysisStatus(0);
            marketJdDataMapper.updateById(jd);
            log.error("单条解析失败(基础设施): jdId=" + id, e);
            return new MarketJdImportService.SingleAnalysisResult(id, 0, 0, 0, 0, true,
                    "能力提取环节出现服务异常，本次未产生结果；该 JD 状态保持「待分析」，可再次解析。");
        }

        // AI 原始提取结果立刻落库（与批次路径同口径）。放在这里而不是各分支里：
        // 后面每个返回分支都会 updateById，字段挂在实体上就能一起落库，
        // 包括「准入基础设施失败」——那时 AI 提取其实已经成功了，不该连痕迹都不留。
        jd.setAiSkillTags(MarketJdAiSkillTagCodec.encode(abilities, objectMapper));

        // 特性开关关闭时与批次路径落到同一分支语义（enabled=false 时不做准入）
        if (!admissionProperties.isEnabled()) {
            return persistLegacySingleResult(jd, abilities);
        }

        // ===== ③ 准入（单元素批次）=====
        MarketJdCapabilityAdmissionService.AdmissionPlan plan;
        try {
            plan = admissionService.admitBatch(new MarketJdCapabilityAdmissionService.AdmissionBatchRequest(
                    jd.getBatchNo(),
                    List.of(new MarketJdCapabilityAdmissionService.JdExtraction(
                            id, cleanedJdText, companyDiversityKey, serverGeneratedRefs, abilities))));
        } catch (Exception e) {
            jd.setAnalysisStatus(0);
            marketJdDataMapper.updateById(jd);
            log.error("单条解析准入失败(基础设施): jdId=" + id, e);
            return new MarketJdImportService.SingleAnalysisResult(id, 0, 0, 0, 0, true,
                    "准入（Harness）环节出现服务异常，本次未产生结果；该 JD 状态保持「待分析」，可再次解析。");
        }

        if (plan.infraFailedJdIds().contains(id)) {
            // 同上：基础设施失败不覆盖既有 skill_tags，避免把上次的成功结果擦成 []
            jd.setAnalysisStatus(0);
            marketJdDataMapper.updateById(jd);
            log.warn("单条解析准入失败(Harness基础设施): jdId={}", id);
            return new MarketJdImportService.SingleAnalysisResult(id, 0, 0, 0,
                    plan.rejectedClaimCount(), true,
                    "准入（Harness）基础设施失败，本次未产生结果；该 JD 状态保持「待分析」，可再次解析。");
        }

        // ===== ④ 持久化 =====
        LinkedHashSet<Long> accepted = plan.acceptedTagIdsByJd()
                .getOrDefault(id, new LinkedHashSet<>());
        LinkedHashSet<Long> recommended = plan.recommendedTagIdsByJd()
                .getOrDefault(id, new LinkedHashSet<>());
        jd.setSkillTags(serializeAcceptedTagIds(accepted));
        jd.setRecommendedSkillTags(serializeAcceptedTagIds(recommended));
        jd.setAnalysisStatus(1);
        marketJdDataMapper.updateById(jd);

        log.info("单条解析完成: jdId={}, postName={}, accepted={}, recommended={}, rejected={}",
                id, postName, accepted.size(), recommended.size(), plan.rejectedClaimCount());

        return new MarketJdImportService.SingleAnalysisResult(id, 1, accepted.size(),
                recommended.size(), plan.rejectedClaimCount(), false,
                buildSingleAnalysisMessage(accepted.size(), recommended.size(), plan.rejectedClaimCount()));
    }

    /**
     * 单条解析结论的中文说明。
     * <p>
     * 必须把「为什么新能力没有结果」讲清楚：新能力准入需 ≥{@code newAbilityMinJdCount} 条 JD
     * 且 ≥{@code newAbilityMinCompanyCount} 家公司互相印证，单条 JD 天然达不到这个阈值。
     * 不说明的话，用户会把「设计使然」读成「解析坏了」。
     */
    private String buildSingleAnalysisMessage(int acceptedCount, int recommendedCount, int rejectedCount) {
        StringBuilder message = new StringBuilder();
        message.append(acceptedCount > 0
                ? "已准入 " + acceptedCount + " 个既有能力标签"
                : "未准入既有能力标签（本 JD 的能力主张未命中任何正式标签）");
        if (recommendedCount > 0) {
            message.append("；另有 ").append(recommendedCount)
                    .append(" 个高置信候选进入推荐集（待审核，不计入正式能力）");
        }
        if (rejectedCount > 0) {
            message.append("；").append(rejectedCount).append(" 个能力主张未通过门禁被拒");
        }
        message.append("。说明：新能力需不少于 ")
                .append(admissionProperties.getNewAbilityMinJdCount())
                .append(" 条 JD 且不少于 ")
                .append(admissionProperties.getNewAbilityMinCompanyCount())
                .append(" 家公司互相印证才会自动建标签，单条 JD 解析不会新建标签。");
        return message.toString();
    }

    /**
     * 传统路径（{@code market-jd.capability-admission.enabled=false}）的单条解析，
     * 与 {@link #analyzeBatchLegacy(String, RecruitmentDataGovernanceService.GovernanceResult)}
     * 的逐条逻辑同口径：直接写 {@code MATCHED} 的标签，不做候选/准入区分。
     */
    private MarketJdImportService.SingleAnalysisResult persistLegacySingleResult(
            MarketJdData jd, List<JdAbilityItemDTO> abilities) {
        jd.setSkillTags(writeMatchedTagIds(abilities));
        jd.setAnalysisStatus(1);
        marketJdDataMapper.updateById(jd);
        long matched = abilities == null ? 0L : abilities.stream()
                .filter(item -> "MATCHED".equals(item.getMatchStatus()))
                .map(JdAbilityItemDTO::getMatchedTagId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        return new MarketJdImportService.SingleAnalysisResult(jd.getId(), 1, (int) matched, 0, 0, false,
                "已按传统路径解析：命中 " + matched + " 个正式能力标签（该路径不区分候选与准入）。");
    }

    /**
     * 将准入后的标签ID集合序列化为排序去重 JSON；空集合输出 {@code []}。
     * 重跑同一批时会整体替换 skillTags，不会追加重复 ID（幂等）。
     */
    private String serializeAcceptedTagIds(LinkedHashSet<Long> accepted) {        try {
            List<Long> sorted = new ArrayList<>(accepted);
            Collections.sort(sorted);
            return objectMapper.writeValueAsString(sorted);
        } catch (Exception exception) {
            log.warn("Unable to persist normalized market JD skill tags", exception);
            return "[]";
        }
    }

    /**
     * 传统分析路径（market-jd.capability-admission.enabled=false 时使用）：
     * 保持改造前的行为——逐 JD 调用 5 参 analyzePostText（含 Harness 防护）、立即写 skillTags。
     */
    private BatchAnalysisResult analyzeBatchLegacy(String batchNo,
                                                   RecruitmentDataGovernanceService.GovernanceResult governanceResult) {
        BatchAnalysisResult result = new BatchAnalysisResult();
        result.setBatchNo(batchNo);
        result.setErrors(new ArrayList<>());

        List<MarketJdData> candidateJds = marketJdDataMapper.selectList(
                new LambdaQueryWrapper<MarketJdData>()
                        .eq(MarketJdData::getBatchNo, batchNo)
                        .eq(MarketJdData::getIsDuplicate, 0)
                        .eq(MarketJdData::getAnalysisStatus, 0));

        Long duplicateCountLong = marketJdDataMapper.selectCount(
                new LambdaQueryWrapper<MarketJdData>()
                        .eq(MarketJdData::getBatchNo, batchNo)
                        .eq(MarketJdData::getIsDuplicate, 1));
        int duplicateCount = duplicateCountLong != null ? duplicateCountLong.intValue() : 0;

        Long totalInBatchLong = marketJdDataMapper.selectCount(
                new LambdaQueryWrapper<MarketJdData>()
                        .eq(MarketJdData::getBatchNo, batchNo));
        int totalInBatch = totalInBatchLong != null ? totalInBatchLong.intValue() : 0;

        result.setTotalCount(totalInBatch);
        result.setSkippedDuplicate(duplicateCount);
        result.setSkippedNoise(governanceResult.noiseFiltered());
        result.setGovernedCount(candidateJds.size());

        if (candidateJds.isEmpty()) {
            log.info("批次无待分析JD: batchNo={}", batchNo);
            return result;
        }

        int successCount = 0;
        int blockedCount = 0;
        int failedCount = 0;

        for (MarketJdData jd : candidateJds) {
            try {
                String postName = jd.getPostName() != null ? jd.getPostName() : "未命名岗位";
                String jdText = buildFullJdText(jd);
                Long jdId = jd.getId();
                String sourceType = "MARKET_JD";

                PostRawInput rawInput = PostRawInput.builder()
                        .postName(postName)
                        .rawText(jdText)
                        .sourceType(sourceType)
                        .sourceRefId(jdId)
                        .build();
                PostCleaningResult cleaningResult = postDataCleaningService.cleanAndDetect(rawInput);

                if (cleaningResult.isBlocked()) {
                    blockedCount++;
                    jd.setQualityScore(cleaningResult.getQualityScore());
                    jd.setAnalysisStatus(2);
                    marketJdDataMapper.updateById(jd);
                    continue;
                }

                String cleanedPostName = cleaningResult.getCleanedPostName() != null
                        ? cleaningResult.getCleanedPostName() : postName;
                String cleanedJdText = cleaningResult.getCleanedText() != null
                        ? cleaningResult.getCleanedText() : jdText;

                List<String> sourceRefs = List.of(
                        "source:MARKET_JD:" + jdId,
                        "platform:" + (jd.getSourcePlatform() != null ? jd.getSourcePlatform() : "UNKNOWN"),
                        "cleaning:" + cleaningResult.getCleaningRecordId());

                List<JdAbilityItemDTO> abilities = postCapabilityGenerationService.analyzePostText(
                        cleanedPostName, cleanedJdText, sourceType, jdId, sourceRefs);

                // 传统路径也要留痕：本路径不区分候选与准入，`skill_tags` 只写 MATCHED，
                // 未命中的 AI 提取结果同样只能靠 `ai_skill_tags` 才看得到。
                jd.setAiSkillTags(MarketJdAiSkillTagCodec.encode(abilities, objectMapper));
                jd.setSkillTags(writeMatchedTagIds(abilities));
                jd.setQualityScore(cleaningResult.getQualityScore());
                jd.setAnalysisStatus(1);
                marketJdDataMapper.updateById(jd);
                successCount++;
            } catch (com.example.matching.common.exception.BusinessException be) {
                blockedCount++;
                jd.setAnalysisStatus(2);
                marketJdDataMapper.updateById(jd);
                log.info("JD分析失败(业务异常): jdId={}, postName={}, reason={}",
                        jd.getId(), jd.getPostName(), be.getMessage());
            } catch (Exception e) {
                failedCount++;
                String errorMsg = "JD分析失败: jdId=" + jd.getId()
                        + ", postName=" + jd.getPostName()
                        + ", error=" + e.getMessage();
                log.error(errorMsg, e);
                result.getErrors().add(errorMsg);
            }
        }

        result.setSkippedNoise(governanceResult.noiseFiltered() + blockedCount);
        result.setExtractedSuccess(successCount);
        result.setExtractedFailed(failedCount);

        log.info("批量分析完成(传统路径): batchNo={}, total={}, candidates={}, success={}, blocked={}, failed={}",
                batchNo, totalInBatch, candidateJds.size(), successCount, blockedCount, failedCount);
        return result;
    }

    private String writeMatchedTagIds(List<JdAbilityItemDTO> abilities) {
        try {
            List<Long> tagIds = abilities == null ? List.of() : abilities.stream()
                    .filter(ability -> "MATCHED".equals(ability.getMatchStatus()))
                    .map(JdAbilityItemDTO::getMatchedTagId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted()
                    .toList();
            return objectMapper.writeValueAsString(tagIds);
        } catch (Exception exception) {
            log.warn("Unable to persist normalized market JD skill tags", exception);
            return "[]";
        }
    }

    /**
     * 拼接完整的JD文本（jobDescription + requirements），口径见 {@link MarketJdTextMetrics#buildFullJdText}。
     */
    private String buildFullJdText(MarketJdData jd) {
        return MarketJdTextMetrics.buildFullJdText(jd.getJobDescription(), jd.getRequirements());
    }
}


