package com.example.matching.service.ability.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.common.enums.AbilitySourceType;
import com.example.matching.entity.ability.PersonAbilityClaim;
import com.example.matching.entity.ability.PersonAbilityProfile;
import com.example.matching.entity.interview.InterviewAbilityObservation;
import com.example.matching.mapper.ability.PersonAbilityClaimMapper;
import com.example.matching.mapper.ability.PersonAbilityProfileMapper;
import com.example.matching.service.ability.PersonAbilityProfileBuildService;
import com.example.matching.service.system.SourceWeightResolver;
import com.example.matching.application.agent.ReviewState;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 人员能力画像构建服务实现
 * <p>
 * 职责：转换、融合、冲突处理、画像写入。
 * 各来源的能力提取由 PersonAbilityExtractionAgent 负责。
 * AI面试观察由 AIInterviewAgent 负责。
 *
 * @author system
 */
@Slf4j
@Service
public class PersonAbilityProfileBuildServiceImpl implements PersonAbilityProfileBuildService {

    private final PersonAbilityClaimMapper claimMapper;
    private final PersonAbilityProfileMapper profileMapper;
    private final ObjectMapper objectMapper;
    private final SourceWeightResolver weightResolver;

    public PersonAbilityProfileBuildServiceImpl(
            PersonAbilityClaimMapper claimMapper,
            PersonAbilityProfileMapper profileMapper,
            ObjectMapper objectMapper,
            SourceWeightResolver weightResolver) {
        this.claimMapper = claimMapper;
        this.profileMapper = profileMapper;
        this.objectMapper = objectMapper;
        this.weightResolver = weightResolver;
    }

    // ==================== 来源类型常量 ====================
    private static final String SOURCE_AI_INTERVIEW = AbilitySourceType.AI_INTERVIEW;

    // ==================== 审核状态常量 ====================
    private static final String REVIEW_STATUS_AUTO = "AUTO";
    private static final String REVIEW_STATUS_PENDING = "PENDING_REVIEW";
    private static final String REVIEW_STATUS_REVIEWED = "REVIEWED";

    @Override
    @Transactional
    public List<PersonAbilityClaim> convertObservationsToClaims(List<InterviewAbilityObservation> observations) {
        List<PersonAbilityClaim> claims = new ArrayList<>();

        for (InterviewAbilityObservation observation : observations) {
            // 只处理Harness决策为PASS的观察
            if (!"PASS".equals(observation.getHarnessDecision())) {
                log.warn("跳过Harness决策非PASS的面试观察，observationId={}, decision={}",
                        observation.getId(), observation.getHarnessDecision());
                continue;
            }

            PersonAbilityClaim claim = new PersonAbilityClaim();
            claim.setEmpId(observation.getEmpId());
            claim.setTagId(observation.getTagId());
            claim.setAbilityName(observation.getAbilityName());
            claim.setClaimedLevel(observation.getObservedLevel());
            claim.setSourceType(SOURCE_AI_INTERVIEW);
            claim.setSourceRefId(observation.getSessionId());
            claim.setSourceWeight(weightResolver.resolveConfigWeight(SOURCE_AI_INTERVIEW));
            claim.setEvidenceText(observation.getEvidenceText());
            claim.setSourceRefsJson(observation.getSourceRefsJson());
            claim.setConfidenceScore(observation.getConfidenceScore());
            claim.setHarnessDecision(observation.getHarnessDecision());
            claim.setStatus("ACTIVE");

            claims.add(claim);
        }

        log.info("将面试观察转换为能力主张，observationCount={}, claimCount={}",
                observations.size(), claims.size());

        return claims;
    }

    @Override
    @Transactional
    public List<PersonAbilityProfile> buildProfile(Long empId, List<PersonAbilityClaim> claims) {
        log.info("开始构建人员能力画像，empId={}, claimCount={}", empId, claims.size());

        // 0. 排除待确立（未通过 Harness 审核）的 Claim，不参与正式画像计算与等级融合
        List<PersonAbilityClaim> confirmedClaims = claims.stream()
                .filter(c -> !"PENDING_HARNESS_REVIEW".equals(c.getStatus()))
                .toList();
        if (confirmedClaims.isEmpty()) {
            log.info("无正式可融合主张，跳过画像构建 empId={}", empId);
            return new ArrayList<>();
        }

        // 1. 按能力标签分组
        Map<Long, List<PersonAbilityClaim>> claimsByTag = confirmedClaims.stream()
                .filter(c -> c.getTagId() != null)
                .collect(Collectors.groupingBy(PersonAbilityClaim::getTagId));

        List<PersonAbilityProfile> profiles = new ArrayList<>();

        // 2. 对每个能力标签进行融合
        for (Map.Entry<Long, List<PersonAbilityClaim>> entry : claimsByTag.entrySet()) {
            Long tagId = entry.getKey();
            List<PersonAbilityClaim> tagClaims = entry.getValue();

            // 检查是否存在冲突
            ConflictResolution conflict = resolveConflicts(empId, tagClaims);

            // 检查是否仅有AI面试来源
            boolean onlyInterviewSource = tagClaims.stream()
                    .allMatch(c -> SOURCE_AI_INTERVIEW.equals(c.getSourceType()));
            // 计算融合后的等级
            int finalLevel = calculateFinalLevel(tagClaims);
            BigDecimal confidence = calculateConfidence(tagClaims, conflict.hasConflict());
            String sourceBreakdown = buildSourceBreakdown(tagClaims);
            List<String> riskSignals = buildRiskSignals(tagClaims, conflict);

            // 如果仅有AI面试来源，添加风险信号并标记为待复核
            if (onlyInterviewSource) {
                riskSignals.add("仅有AI面试来源，作为初步画像，建议补充简历、项目或人工验证");
            }

            // 确定审核状态
            String reviewStatus = REVIEW_STATUS_AUTO;
            if (conflict.hasConflict() || onlyInterviewSource) {
                reviewStatus = REVIEW_STATUS_PENDING;
            }

            // 构建或更新画像
            PersonAbilityProfile profile = profileMapper.selectOne(
                    Wrappers.<PersonAbilityProfile>lambdaQuery()
                            .eq(PersonAbilityProfile::getEmpId, empId)
                            .eq(PersonAbilityProfile::getTagId, tagId)
            );

            if (profile == null) {
                profile = new PersonAbilityProfile();
                profile.setEmpId(empId);
                profile.setTagId(tagId);
                profile.setAbilityName(tagClaims.get(0).getAbilityName());
                profile.setFinalLevel(finalLevel);
                profile.setConfidenceScore(confidence);
                profile.setSourceBreakdownJson(sourceBreakdown);
                profile.setEvidenceCount(tagClaims.size());
                profile.setLastEvidenceTime(LocalDateTime.now());
                profile.setRiskSignalsJson(toJson(riskSignals));
                profile.setReviewStatus(reviewStatus);
                profile.setReviewState(toReviewState(reviewStatus).name());
                profileMapper.insert(profile);
            } else {
                // 已审核的画像：只追加风险和证据，不直接改 finalLevel
                if (ReviewState.APPROVED.name().equals(profile.getReviewState())) {
                    log.info("画像已审核，仅追加风险信号和证据，不覆盖等级，empId={}, tagId={}", empId, tagId);
                    // 合并风险信号
                    List<String> existingRisks = parseRiskSignals(profile.getRiskSignalsJson());
                    List<String> mergedRisks = new ArrayList<>(existingRisks);
                    mergedRisks.addAll(riskSignals);
                    profile.setRiskSignalsJson(toJson(mergedRisks));
                    profile.setEvidenceCount(tagClaims.size());
                    profile.setLastEvidenceTime(LocalDateTime.now());
                    // 添加新来源到明细（不改变最终等级）
                    profile.setSourceBreakdownJson(sourceBreakdown);
                } else {
                    // 未审核的画像：正常更新
                    profile.setFinalLevel(finalLevel);
                    profile.setConfidenceScore(confidence);
                    profile.setSourceBreakdownJson(sourceBreakdown);
                    profile.setEvidenceCount(tagClaims.size());
                    profile.setLastEvidenceTime(LocalDateTime.now());
                    profile.setRiskSignalsJson(toJson(riskSignals));
                    // 只有需要复核时才更新状态，避免将已审核的降级
                    if (REVIEW_STATUS_PENDING.equals(reviewStatus)) {
                        profile.setReviewStatus(REVIEW_STATUS_PENDING);
                        profile.setReviewState(ReviewState.PENDING.name());
                    } else if (ReviewState.PENDING.name().equals(profile.getReviewState())
                            || REVIEW_STATUS_PENDING.equals(profile.getReviewStatus())) {
                        profile.setReviewStatus(REVIEW_STATUS_AUTO);
                        profile.setReviewState(ReviewState.AUTO.name());
                    }
                }
                profileMapper.updateById(profile);
            }

            profiles.add(profile);
        }

        log.info("人员能力画像构建完成，empId={}, profileCount={}", empId, profiles.size());
        return profiles;
    }

    @Override
    public ConflictResolution resolveConflicts(Long empId, List<PersonAbilityClaim> claims) {
        if (claims == null || claims.size() <= 1) {
            return new ConflictResolution(false, null, null, List.of());
        }

        // 检查同一能力标签的不同来源是否存在等级差异
        Set<String> sourceTypes = claims.stream()
                .map(PersonAbilityClaim::getSourceType)
                .collect(Collectors.toSet());

        if (sourceTypes.size() <= 1) {
            return new ConflictResolution(false, null, null, List.of());
        }

        // 计算等级差异
        int minLevel = claims.stream().mapToInt(PersonAbilityClaim::getClaimedLevel).min().orElse(0);
        int maxLevel = claims.stream().mapToInt(PersonAbilityClaim::getClaimedLevel).max().orElse(0);
        int levelDiff = maxLevel - minLevel;

        // 如果等级差异大于等于2，视为冲突
        if (levelDiff >= 2) {
            String description = String.format(
                    "能力等级冲突：%s 在不同来源中等级差异为 %d 级（最低 %d，最高 %d）",
                    claims.get(0).getAbilityName(), levelDiff, minLevel, maxLevel
            );

            List<Long> tagsNeedingReview = claims.stream()
                    .map(PersonAbilityClaim::getTagId)
                    .distinct()
                    .collect(Collectors.toList());

            return new ConflictResolution(
                    true,
                    description,
                    "建议人工审核，确认最终能力等级",
                    tagsNeedingReview
            );
        }

        return new ConflictResolution(false, null, null, List.of());
    }

    @Override
    public List<PersonAbilityProfile> getLatestProfile(Long empId) {
        return profileMapper.selectList(
                Wrappers.<PersonAbilityProfile>lambdaQuery()
                        .eq(PersonAbilityProfile::getEmpId, empId)
                        .eq(PersonAbilityProfile::getIsDeleted, 0)
                        .orderByDesc(PersonAbilityProfile::getUpdatedTime)
        );
    }

    @Override
    public PersonAbilityProfile getProfileByTag(Long empId, Long tagId) {
        return profileMapper.selectOne(
                Wrappers.<PersonAbilityProfile>lambdaQuery()
                        .eq(PersonAbilityProfile::getEmpId, empId)
                        .eq(PersonAbilityProfile::getTagId, tagId)
                        .eq(PersonAbilityProfile::getIsDeleted, 0)
        );
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 计算融合后的等级
     * <p>
     * 使用加权平均算法，考虑来源权重和置信度。
     */
    private int calculateFinalLevel(List<PersonAbilityClaim> claims) {
        if (claims == null || claims.isEmpty()) {
            return 0;
        }

        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal weightedSum = BigDecimal.ZERO;

        for (PersonAbilityClaim claim : claims) {
            BigDecimal weight = getSourceWeight(claim.getSourceType());
            BigDecimal confidence = claim.getConfidenceScore() != null
                    ? claim.getConfidenceScore().divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                    : BigDecimal.valueOf(0.5);

            // 有效权重 = 来源权重 * 置信度
            BigDecimal effectiveWeight = weight.multiply(confidence);
            totalWeight = totalWeight.add(effectiveWeight);
            weightedSum = weightedSum.add(BigDecimal.valueOf(claim.getClaimedLevel()).multiply(effectiveWeight));
        }

        if (totalWeight.compareTo(BigDecimal.ZERO) == 0) {
            return claims.stream().mapToInt(PersonAbilityClaim::getClaimedLevel).max().orElse(0);
        }

        return weightedSum.divide(totalWeight, 0, RoundingMode.HALF_UP).intValue();
    }

    /**
     * 计算置信度
     */
    private BigDecimal calculateConfidence(List<PersonAbilityClaim> claims, boolean hasConflict) {
        if (claims == null || claims.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // 基础置信度：各来源置信度的加权平均
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal weightedConfidence = BigDecimal.ZERO;

        for (PersonAbilityClaim claim : claims) {
            BigDecimal weight = getSourceWeight(claim.getSourceType());
            BigDecimal confidence = claim.getConfidenceScore() != null
                    ? claim.getConfidenceScore()
                    : BigDecimal.valueOf(50);

            totalWeight = totalWeight.add(weight);
            weightedConfidence = weightedConfidence.add(confidence.multiply(weight));
        }

        BigDecimal baseConfidence = totalWeight.compareTo(BigDecimal.ZERO) > 0
                ? weightedConfidence.divide(totalWeight, 2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(50);

        // 多来源增强：每增加一个独立来源，置信度提升5%
        long independentSources = claims.stream()
                .map(PersonAbilityClaim::getSourceType)
                .distinct()
                .count();
        BigDecimal multiSourceBonus = BigDecimal.valueOf(Math.min((independentSources - 1) * 5, 20));

        // 冲突惩罚：存在冲突时置信度降低20%
        BigDecimal conflictPenalty = hasConflict ? BigDecimal.valueOf(20) : BigDecimal.ZERO;

        BigDecimal finalConfidence = baseConfidence.add(multiSourceBonus).subtract(conflictPenalty);
        return finalConfidence.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 获取来源权重（从数据库配置读取，带缓存）
     */
    private BigDecimal getSourceWeight(String sourceType) {
        return weightResolver.resolveConfigWeight(sourceType);
    }

    /**
     * 构建来源明细JSON
     */
    private String buildSourceBreakdown(List<PersonAbilityClaim> claims) {
        try {
            List<Map<String, Object>> breakdown = new ArrayList<>();
            for (PersonAbilityClaim claim : claims) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("sourceType", claim.getSourceType());
                item.put("claimedLevel", claim.getClaimedLevel());
                item.put("confidence", claim.getConfidenceScore());
                item.put("weight", getSourceWeight(claim.getSourceType()));
                item.put("evidenceText", claim.getEvidenceText());
                breakdown.add(item);
            }
            return objectMapper.writeValueAsString(breakdown);
        } catch (Exception e) {
            log.warn("构建来源明细JSON失败: {}", e.getMessage());
            return "[]";
        }
    }

    /**
     * 构建风险信号（去重，上限50条）
     */
    private List<String> buildRiskSignals(List<PersonAbilityClaim> claims, ConflictResolution conflict) {
        LinkedHashSet<String> riskSet = new LinkedHashSet<>();

        // 冲突风险
        if (conflict.hasConflict()) {
            riskSet.add(conflict.conflictDescription());
        }

        // 低置信度风险
        long lowConfidenceCount = claims.stream()
                .filter(c -> c.getConfidenceScore() != null && c.getConfidenceScore().compareTo(BigDecimal.valueOf(50)) < 0)
                .count();
        if (lowConfidenceCount > 0) {
            riskSet.add("存在 " + lowConfidenceCount + " 条低置信度证据");
        }

        // 单一来源风险
        long independentSources = claims.stream()
                .map(PersonAbilityClaim::getSourceType)
                .distinct()
                .count();
        if (independentSources == 1) {
            riskSet.add("仅有一个来源支撑，建议补充验证");
        }

        List<String> risks = new ArrayList<>(riskSet);
        // 截断至50条
        if (risks.size() > 50) {
            return risks.subList(0, 50);
        }
        return risks;
    }

    /**
     * 解析风险信号JSON
     */
    @SuppressWarnings("unchecked")
    private List<String> parseRiskSignals(String riskSignalsJson) {
        if (riskSignalsJson == null || riskSignalsJson.isBlank() || "[]".equals(riskSignalsJson)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(riskSignalsJson, List.class);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * 序列化为JSON
     */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }

    private ReviewState toReviewState(String reviewStatus) {
        if (REVIEW_STATUS_PENDING.equals(reviewStatus)) {
            return ReviewState.PENDING;
        }
        if (REVIEW_STATUS_REVIEWED.equals(reviewStatus)) {
            return ReviewState.APPROVED;
        }
        return ReviewState.AUTO;
    }
}
