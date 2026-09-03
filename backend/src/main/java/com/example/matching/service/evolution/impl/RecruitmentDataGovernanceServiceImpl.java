package com.example.matching.service.evolution.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.entity.evolution.MarketJdData;
import com.example.matching.mapper.evolution.MarketJdDataMapper;
import com.example.matching.service.evolution.RecruitmentDataGovernanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 招聘数据治理服务实现
 * <p>
 * 处理招聘JD的时滞、噪声、重复等数据质量问题。
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecruitmentDataGovernanceServiceImpl implements RecruitmentDataGovernanceService {

    private final MarketJdDataMapper marketJdDataMapper;

    // 时效性评分配置：连续指数衰减 score = 100 * exp(-daysSincePublished / DECAY_TAU_DAYS)
    // 时效系数 λ = score / 100（0-1），可作为岗位—技能边权重 w(r,s,t) 的时效因子
    private static final int FRESH_DAYS = 30;
    private static final int MEDIUM_DAYS = 90;
    private static final int DECAY_TAU_DAYS = 90;

    // 噪声关键词
    private static final List<String> NOISE_KEYWORDS = List.of(
            "公司介绍", "福利待遇", "宣传话术", "企业文化", "员工活动",
            "团建", "下午茶", "加班补贴", "五险一金", "带薪年假"
    );

    @Override
    @Transactional
    public GovernanceResult governBatch(String batchNo) {
        log.info("治理招聘数据批次: batchNo={}", batchNo);

        int totalProcessed = 0;
        int duplicatesRemoved = 0;
        int noiseFiltered = 0;
        int qualityPassed = 0;
        List<String> warnings = new ArrayList<>();

        try {
            // 1. 获取批次数据
            List<MarketJdData> jdList = marketJdDataMapper.selectList(
                    Wrappers.<MarketJdData>lambdaQuery()
                            .eq(MarketJdData::getBatchNo, batchNo)
                            .eq(MarketJdData::getAnalysisStatus, 0)
                            .eq(MarketJdData::getIsDuplicate, 0)
            );

            totalProcessed = jdList.size();

            // 2. 计算内容哈希
            for (MarketJdData jd : jdList) {
                try {
                    String contentHash = calculateContentHash(jd.getJobDescription(), jd.getRequirements());
                    jd.setTextHash(contentHash);
                    String normalizedCompanyName = normalizeCompanyName(jd.getCompanyName());
                    jd.setCompanyDiversityKey(normalizedCompanyName.isEmpty() ? ""
                            : calculateContentHash("market-jd-employer-v1:" + normalizedCompanyName, ""));
                } catch (Exception e) {
                    warnings.add("计算哈希失败: jdId=" + jd.getId() + ", error=" + e.getMessage());
                }
            }

            // 3. 检测重复
            Map<String, List<MarketJdData>> hashGroups = jdList.stream()
                    .filter(jd -> jd.getTextHash() != null)
                    .collect(Collectors.groupingBy(MarketJdData::getTextHash));

            for (Map.Entry<String, List<MarketJdData>> entry : hashGroups.entrySet()) {
                List<MarketJdData> group = entry.getValue();
                if (group.size() > 1) {
                    // 标记重复
                    MarketJdData canonical = group.get(0);
                    for (int i = 1; i < group.size(); i++) {
                        MarketJdData duplicate = group.get(i);
                        duplicate.setIsDuplicate(1);
                        duplicate.setCanonicalDocumentId(canonical.getId());
                        duplicatesRemoved++;
                    }
                }
            }

            // 4. 计算时效性评分
            for (MarketJdData jd : jdList) {
                if (jd.getIsDuplicate() != null && jd.getIsDuplicate() == 1) {
                    continue;
                }

                FreshnessScore freshness = calculateFreshnessScoreInternal(jd);
                jd.setFreshnessScore(BigDecimal.valueOf(freshness.score()));
                jd.setLastSeenTime(LocalDateTime.now());
            }

            // 5. 过滤噪声
            for (MarketJdData jd : jdList) {
                if (jd.getIsDuplicate() != null && jd.getIsDuplicate() == 1) {
                    continue;
                }

                double noiseScore = calculateNoiseScore(jd);
                jd.setNoiseScore(BigDecimal.valueOf(noiseScore));

                // 噪声评分过高则跳过
                if (noiseScore > 70) {
                    jd.setAnalysisStatus(2); // 跳过
                    noiseFiltered++;
                } else {
                    qualityPassed++;
                }
            }

            // 6. 批量更新
            for (MarketJdData jd : jdList) {
                marketJdDataMapper.updateById(jd);
            }

            log.info("招聘数据治理完成: totalProcessed={}, duplicatesRemoved={}, noiseFiltered={}, qualityPassed={}",
                    totalProcessed, duplicatesRemoved, noiseFiltered, qualityPassed);

        } catch (Exception e) {
            log.error("招聘数据治理失败: {}", e.getMessage(), e);
            warnings.add("治理失败: " + e.getMessage());
        }

        return new GovernanceResult(totalProcessed, duplicatesRemoved, noiseFiltered, qualityPassed, warnings);
    }

    @Override
    public FreshnessScore calculateFreshnessScore(Long jdId) {
        MarketJdData jd = marketJdDataMapper.selectById(jdId);
        if (jd == null) {
            return new FreshnessScore(jdId, 0.0, 0, "UNKNOWN");
        }
        return calculateFreshnessScoreInternal(jd);
    }

    @Override
    public List<FreshnessScore> batchCalculateFreshnessScore(List<Long> jdIds) {
        List<FreshnessScore> results = new ArrayList<>();
        for (Long jdId : jdIds) {
            results.add(calculateFreshnessScore(jdId));
        }
        return results;
    }

    @Override
    public DuplicateResult detectDuplicate(Long jdId) {
        MarketJdData jd = marketJdDataMapper.selectById(jdId);
        if (jd == null) {
            return new DuplicateResult(jdId, false, null, null);
        }

        // 计算内容哈希
        String contentHash = calculateContentHash(jd.getJobDescription(), jd.getRequirements());

        // 查找相同哈希的记录
        List<MarketJdData> duplicates = marketJdDataMapper.selectList(
                Wrappers.<MarketJdData>lambdaQuery()
                        .eq(MarketJdData::getTextHash, contentHash)
                        .ne(MarketJdData::getId, jdId)
        );

        if (duplicates.isEmpty()) {
            return new DuplicateResult(jdId, false, null, contentHash);
        }

        // 返回第一条作为规范记录
        return new DuplicateResult(jdId, true, duplicates.get(0).getId(), contentHash);
    }

    @Override
    public List<DuplicateResult> batchDetectDuplicate(List<Long> jdIds) {
        List<DuplicateResult> results = new ArrayList<>();
        for (Long jdId : jdIds) {
            results.add(detectDuplicate(jdId));
        }
        return results;
    }

    @Override
    public int calculateSourceDiversity(String postName) {
        // 统计不同来源平台的数量
        List<MarketJdData> jdList = marketJdDataMapper.selectList(
                Wrappers.<MarketJdData>lambdaQuery()
                        .like(MarketJdData::getPostName, postName)
                        .eq(MarketJdData::getIsDuplicate, 0)
        );

        if (jdList.isEmpty()) {
            return 0;
        }

        long platformCount = jdList.stream()
                .map(MarketJdData::getSourcePlatform)
                .distinct()
                .count();

        // 计算多样性评分
        return Math.min(100, (int) (platformCount * 25));
    }

    @Override
    public int calculateCompanyDiversity(String postName) {
        // 统计不同公司的数量
        List<MarketJdData> jdList = marketJdDataMapper.selectList(
                Wrappers.<MarketJdData>lambdaQuery()
                        .like(MarketJdData::getPostName, postName)
                        .eq(MarketJdData::getIsDuplicate, 0)
        );

        if (jdList.isEmpty()) {
            return 0;
        }

        long companyCount = jdList.stream()
                .map(MarketJdData::getCompanyDiversityKey)
                .distinct()
                .count();

        // 计算多样性评分
        return Math.min(100, (int) (companyCount * 10));
    }

    /**
     * 计算内容哈希
     */
    private String calculateContentHash(String jobDescription, String requirements) {
        try {
            String content = (jobDescription != null ? jobDescription : "") +
                    (requirements != null ? requirements : "");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.warn("计算内容哈希失败: {}", e.getMessage());
            return null;
        }
    }

    private String normalizeCompanyName(String companyName) {
        return companyName == null ? "" : companyName.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    /**
     * 计算时效性评分（内部方法）
     * <p>
     * 采用连续指数衰减：score = 100 * exp(-daysSincePublished / DECAY_TAU_DAYS)。
     * 相比固定三档（FRESH/MEDIUM/OLD 离散分值），连续衰减能更精细地反映旧 JD 的
     * 价值流失，避免短期热点与长期陈旧的 JD 被等权处理；时效系数 λ = score / 100
     * 可进一步乘进岗位—技能边权重。freshnessLevel 仍按天数分档，仅用于展示。
     */
    private FreshnessScore calculateFreshnessScoreInternal(MarketJdData jd) {
        LocalDateTime publishedTime = jd.getPublishedTime();
        if (publishedTime == null) {
            return new FreshnessScore(jd.getId(), 50.0, 0, "UNKNOWN");
        }

        int daysSincePublished = (int) Duration.between(publishedTime, LocalDateTime.now()).toDays();

        double score = 100.0 * Math.exp(-(double) daysSincePublished / DECAY_TAU_DAYS);
        score = Math.max(0.0, Math.min(100.0, score));

        String freshnessLevel;
        if (daysSincePublished <= FRESH_DAYS) {
            freshnessLevel = "FRESH";
        } else if (daysSincePublished <= MEDIUM_DAYS) {
            freshnessLevel = "MEDIUM";
        } else {
            freshnessLevel = "OLD";
        }

        return new FreshnessScore(jd.getId(), score, daysSincePublished, freshnessLevel);
    }

    /**
     * 计算噪声评分
     */
    private double calculateNoiseScore(MarketJdData jd) {
        double noiseScore = 0.0;

        String content = (jd.getJobDescription() != null ? jd.getJobDescription() : "") +
                (jd.getRequirements() != null ? jd.getRequirements() : "");

        // 检查噪声关键词
        long noiseCount = NOISE_KEYWORDS.stream()
                .filter(keyword -> content.contains(keyword))
                .count();

        noiseScore += noiseCount * 15;

        // 检查内容长度（过短可能是噪声）
        if (content.length() < 100) {
            noiseScore += 30;
        }

        // 检查是否有实际岗位职责
        if (!content.contains("岗位职责") && !content.contains("工作内容") && !content.contains("职责描述")) {
            noiseScore += 20;
        }

        return Math.min(100, noiseScore);
    }
}
