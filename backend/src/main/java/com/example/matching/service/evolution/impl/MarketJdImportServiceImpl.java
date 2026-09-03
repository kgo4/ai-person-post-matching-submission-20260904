package com.example.matching.service.evolution.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.util.SimHash;
import com.example.matching.dto.post.JdAbilityItemDTO;
import com.example.matching.dto.post.PostCleaningResult;
import com.example.matching.dto.post.PostRawInput;
import com.example.matching.entity.evolution.MarketJdData;
import com.example.matching.entity.post.PostPost;
import com.example.matching.mapper.evolution.MarketJdDataMapper;
import com.example.matching.mapper.post.PostPostMapper;
import com.example.matching.config.MarketJdCapabilityAdmissionProperties;
import com.example.matching.service.evolution.MarketJdCapabilityAdmissionService;
import com.example.matching.service.evolution.MarketJdImportService;
import com.example.matching.service.evolution.RecruitmentDataGovernanceService;
import com.example.matching.service.post.PostCapabilityGenerationService;
import com.example.matching.service.post.PostDataCleaningService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 市场JD导入服务实现
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
                                     MarketJdCapabilityAdmissionProperties admissionProperties) {
        this(marketJdDataMapper, postPostMapper, postCapabilityGenerationService, postDataCleaningService,
                objectMapper, admissionService, admissionProperties,
                new RecruitmentDataGovernanceServiceImpl(marketJdDataMapper));
    }

    private final MarketJdDataMapper marketJdDataMapper;
    private final PostPostMapper postPostMapper;
    private final PostCapabilityGenerationService postCapabilityGenerationService;
    private final PostDataCleaningService postDataCleaningService;
    private final ObjectMapper objectMapper;
    private final MarketJdCapabilityAdmissionService admissionService;
    private final MarketJdCapabilityAdmissionProperties admissionProperties;
    private final RecruitmentDataGovernanceService recruitmentDataGovernanceService;

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

        for (String jdText : jdTexts) {
            if (jdText == null || jdText.isBlank()) {
                continue;
            }

            MarketJdData data = new MarketJdData();
            data.setBatchNo(batchNo);
            data.setJobDescription(jdText);
            data.setSourcePlatform(sourcePlatform);
            data.setTextHash(calculateHash(jdText));
            data.setCompanyDiversityKey(anonymousCompanyDiversityKey(data.getCompanyName()));
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

        for (MarketJdData data : dataList) {
            if (data.getJobDescription() == null || data.getJobDescription().isBlank()) {
                continue;
            }

            data.setBatchNo(batchNo);
            data.setTextHash(calculateHash(data.getJobDescription()));
            data.setCompanyDiversityKey(anonymousCompanyDiversityKey(data.getCompanyName()));
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
        for (VerifiedPostImportJd jd : jds) {
            if (jd == null || jd.jobDescription() == null || jd.jobDescription().isBlank()) {
                continue;
            }
            List<Long> tagIds = jd.verifiedTagIds() == null ? List.of() : jd.verifiedTagIds().stream()
                    .filter(Objects::nonNull).distinct().sorted().toList();
            // 岗位导入批次的纳入只由“已完成岗位 + 有效JD正文”决定。
            // 系统标签库/tagId 是辅助治理数据，不能阻塞市场JD样本进入；
            // 后续市场分析仍会按统一治理和Harness流程计算能力。
            MarketJdData data = new MarketJdData();
            data.setBatchNo(batchNo);
            data.setPostName(jd.postName());
            data.setJobDescription(jd.jobDescription());
            data.setSourcePlatform("POST_IMPORT");
            data.setTextHash(calculateHash(jd.jobDescription()));
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
        log.info("已将岗位导入批次纳入市场发现（复用已确认能力，无AI重分析）: postImportBatchId={}, imported={}",
                postImportBatchId, imported);
        return imported;
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

    private String calculateHash(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(normalizeTextForHash(text).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String normalizeTextForHash(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKC)
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    /**
     * Stable pseudonymous key used only by deterministic cross-employer validation.
     * The original employer name remains outside Agent, Harness and API payloads.
     */
    private String anonymousCompanyDiversityKey(String companyName) {
        if (companyName == null || companyName.isBlank()) {
            return "";
        }
        return calculateHash("market-jd-employer-v1:" + normalizeTextForHash(companyName));
    }

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
        if (jdText == null || jdText.isBlank()) {
            return BigDecimal.ZERO;
        }

        double score = 50.0; // 基础分

        // 长度检查
        if (jdText.length() > 200) score += 10;
        if (jdText.length() > 500) score += 10;

        // 关键词检查
        String lowerText = jdText.toLowerCase();
        if (lowerText.contains("要求") || lowerText.contains("职责")) score += 10;
        if (lowerText.contains("经验") || lowerText.contains("技能")) score += 5;
        if (lowerText.contains("学历") || lowerText.contains("本科")) score += 5;

        return BigDecimal.valueOf(Math.min(100, score));
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
                String companyDiversityKey = anonymousCompanyDiversityKey(jd.getCompanyName());
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
        if (plan != null) {
            for (Map.Entry<Long, MarketJdData> entry : jdById.entrySet()) {
                MarketJdData jd = entry.getValue();
                LinkedHashSet<Long> accepted = plan.acceptedTagIdsByJd()
                        .getOrDefault(jd.getId(), new LinkedHashSet<>());
                LinkedHashSet<Long> recommended = plan.recommendedTagIdsByJd()
                        .getOrDefault(jd.getId(), new LinkedHashSet<>());
                jd.setSkillTags(serializeAcceptedTagIds(accepted));
                jd.setRecommendedSkillTags(serializeAcceptedTagIds(recommended));
                jd.setAnalysisStatus(plan.infraFailedJdIds().contains(jd.getId()) ? 0 : 1);
                marketJdDataMapper.updateById(jd);
            }
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

    /**
     * 将准入后的标签ID集合序列化为排序去重 JSON；空集合输出 {@code []}。
     * 重跑同一批时会整体替换 skillTags，不会追加重复 ID（幂等）。
     */
    private String serializeAcceptedTagIds(LinkedHashSet<Long> accepted) {
        try {
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
     * 拼接完整的JD文本（jobDescription + requirements）
     */
    private String buildFullJdText(MarketJdData jd) {
        StringBuilder sb = new StringBuilder();
        if (jd.getJobDescription() != null && !jd.getJobDescription().isBlank()) {
            sb.append(jd.getJobDescription());
        }
        if (jd.getRequirements() != null && !jd.getRequirements().isBlank()) {
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append("任职要求：\n").append(jd.getRequirements());
        }
        return sb.toString();
    }
}
