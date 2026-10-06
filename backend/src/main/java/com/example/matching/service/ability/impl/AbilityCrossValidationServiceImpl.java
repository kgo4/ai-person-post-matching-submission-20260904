package com.example.matching.service.ability.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.common.enums.AbilitySourceCredibility;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.service.ability.AbilityCrossValidationService;
import com.example.matching.service.rag.RagRetrievalService;
import com.example.matching.service.rag.RagScenarioEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 能力多源交叉验证服务实现
 * <p>
 * 通过RAG检索历史证据，对比新旧数据一致性，实现多源交叉验证。
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AbilityCrossValidationServiceImpl implements AbilityCrossValidationService {

    private final EmpAbilityMapper empAbilityMapper;
    private final AbilityTagMapper abilityTagMapper;
    private final RagRetrievalService ragRetrievalService;

    /**
     * 等级差异容忍度：相同能力在不同来源下允许的最大等级差
     */
    private static final int LEVEL_TOLERANCE = 1;

    /**
     * 一致性分数阈值：高于此值认为一致
     */
    private static final int CONSISTENCY_THRESHOLD = 70;

    @Override
    public ValidationResult validateAbility(Long empId, Long tagId, Integer newLevel,
                                             String newSource, Long excludeSourceId) {
        try {
            // 1. 查询该员工+该能力标签的历史记录
            List<EmpAbility> historyList = empAbilityMapper.selectList(
                    Wrappers.<EmpAbility>lambdaQuery()
                            .eq(EmpAbility::getEmpId, empId)
                            .eq(EmpAbility::getTagId, tagId)
                            .eq(EmpAbility::getIsDeleted, 0)
            );

            // 排除当前记录
            if (excludeSourceId != null) {
                historyList = historyList.stream()
                        .filter(a -> !a.getId().equals(excludeSourceId))
                        .collect(Collectors.toList());
            }

            // 2. 无历史记录
            if (historyList.isEmpty()) {
                return new ValidationResult(
                        100, "NO_HISTORY", 0,
                        "无历史记录，首次录入该能力",
                        "ACCEPT"
                );
            }

            // 3. 计算一致性
            AbilityTag tag = abilityTagMapper.selectById(tagId);
            String tagName = tag != null ? tag.getTagName() : "未知能力";

            // 按来源分组统计
            Map<String, List<EmpAbility>> bySource = historyList.stream()
                    .collect(Collectors.groupingBy(EmpAbility::getEvaluationSource));

            int totalScore = 0;
            int matchCount = 0;
            StringBuilder detailBuilder = new StringBuilder();
            detailBuilder.append("历史来源：");

            for (Map.Entry<String, List<EmpAbility>> entry : bySource.entrySet()) {
                String source = entry.getKey();
                List<EmpAbility> abilities = entry.getValue();

                for (EmpAbility history : abilities) {
                    int levelDiff = Math.abs(history.getMasteryLevel() - newLevel);
                    double sourceWeight = AbilitySourceCredibility.getWeightBySource(source);

                    // 等级差异越小，一致性越高
                    int levelScore;
                    if (levelDiff == 0) {
                        levelScore = 100;
                    } else if (levelDiff <= LEVEL_TOLERANCE) {
                        levelScore = 70;
                    } else if (levelDiff <= 2) {
                        levelScore = 40;
                    } else {
                        levelScore = 10;
                    }

                    // 按来源可信度加权
                    int weightedScore = (int) (levelScore * sourceWeight);
                    totalScore += weightedScore;
                    matchCount++;

                    detailBuilder.append(String.format("\n- %s: 等级%d (差异%d级, 权重%.2f, 得分%d)",
                            source, history.getMasteryLevel(), levelDiff, sourceWeight, weightedScore));
                }
            }

            // 计算平均一致性分数
            int avgScore = matchCount > 0 ? totalScore / matchCount : 0;

            // 4. RAG增强验证：检索相关知识作为参考
            try {
                String ragQuery = "能力 " + tagName + " 等级 " + newLevel + " 评价标准";
                String ragContext = ragRetrievalService.retrieveContext(ragQuery, RagScenarioEnum.MATCHING_ANALYSIS, 2);
                if (ragContext != null && !ragContext.isBlank()) {
                    detailBuilder.append("\n\nRAG参考：").append(ragContext, 0, Math.min(200, ragContext.length()));
                }
            } catch (Exception e) {
                log.warn("RAG检索失败: {}", e.getMessage());
            }

            // 5. 确定验证状态和建议
            String status;
            String recommendation;
            if (avgScore >= CONSISTENCY_THRESHOLD) {
                status = "CONSISTENT";
                recommendation = "ACCEPT";
            } else if (avgScore >= 40) {
                status = "INCONSISTENT";
                recommendation = "REVIEW";
            } else {
                status = "INCONSISTENT";
                recommendation = "REJECT";
            }

            return new ValidationResult(
                    avgScore, status, matchCount,
                    detailBuilder.toString(), recommendation
            );

        } catch (Exception e) {
            log.error("交叉验证失败: empId={}, tagId={}, error={}", empId, tagId, e.getMessage(), e);
            return new ValidationResult(
                    50, "ERROR", 0,
                    "验证过程异常: " + e.getMessage(),
                    "REVIEW"
            );
        }
    }

    @Override
    public List<ValidationResult> validateAllAbilities(Long empId) {
        List<EmpAbility> abilities = empAbilityMapper.selectList(
                Wrappers.<EmpAbility>lambdaQuery()
                        .eq(EmpAbility::getEmpId, empId)
                        .eq(EmpAbility::getIsDeleted, 0)
        );

        List<ValidationResult> results = new ArrayList<>();
        for (EmpAbility ability : abilities) {
            ValidationResult result = validateAbility(
                    empId, ability.getTagId(), ability.getMasteryLevel(),
                    ability.getEvaluationSource(), ability.getId()
            );
            results.add(result);
        }
        return results;
    }

    @Override
    public double getSuggestedWeightAdjustment(Long empId, Long tagId, String source) {
        // 查询历史记录，不传入 newLevel 避免 NPE
        List<EmpAbility> historyList = empAbilityMapper.selectList(
                Wrappers.<EmpAbility>lambdaQuery()
                        .eq(EmpAbility::getEmpId, empId)
                        .eq(EmpAbility::getTagId, tagId)
                        .eq(EmpAbility::getIsDeleted, 0));

        if (historyList.isEmpty()) {
            return 0.0; // 无历史则不调整
        }

        // 使用最高等级作为参考
        int maxLevel = historyList.stream()
                .mapToInt(a -> a.getMasteryLevel() != null ? a.getMasteryLevel() : 0)
                .max().orElse(0);

        ValidationResult result = validateAbility(empId, tagId, maxLevel, source, null);

        return switch (result.status()) {
            case "CONSISTENT" -> 0.05;
            case "NO_HISTORY" -> 0.0;
            case "INCONSISTENT" -> result.consistencyScore() >= 40 ? -0.05 : -0.15;
            default -> 0.0;
        };
    }
}
