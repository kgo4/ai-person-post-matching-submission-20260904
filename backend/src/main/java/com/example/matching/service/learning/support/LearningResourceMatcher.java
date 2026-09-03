package com.example.matching.service.learning.support;

import com.example.matching.common.util.AbilityNameNormalizer;
import com.example.matching.entity.learning.LearningResource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 学习资源统一召回规则（abilityName 主关联，tagId 仅作辅助）。
 * <p>
 * 匹配优先级：
 * <ol>
 *   <li>能力名称归一化精确匹配</li>
 *   <li>能力名称归一化包含匹配（双向 contains）</li>
 *   <li>资源标题归一化包含匹配</li>
 *   <li>tagId 精确匹配（仅前序均未命中时兜底，tagId 为空不阻断查询）</li>
 * </ol>
 * 排序：启用状态（调用方已过滤）→ 能力名称精确度 → 难度是否接近目标区间 → sortOrder → 资源类型。
 * <p>
 * 该组件同时被学习路径计划生成、轻量学习路径推荐复用，保证资源关联口径一致。
 */
public final class LearningResourceMatcher {

    private static final Map<String, Integer> TYPE_PRIORITY = Map.of(
            "PRACTICE", 1,
            "PROJECT", 2,
            "COURSE", 3,
            "DOC", 4,
            "VIDEO", 5,
            "BOOK", 6
    );

    private LearningResourceMatcher() {
    }

    /**
     * 在给定资源列表中召回与能力匹配的资源，并按规则排序。
     *
     * @param allResources 已过滤启用状态的资源列表（可为空）
     * @param abilityName  能力名称（主关联键）
     * @param tagId        能力标签ID（仅辅助兜底，可为 null）
     * @param currentLevel 当前能力等级
     * @param targetLevel  目标能力等级
     * @return 按优先级排序后的匹配资源列表；无匹配时为空列表
     */
    public static List<LearningResource> matchAndSort(List<LearningResource> allResources,
                                                      String abilityName, Long tagId,
                                                      int currentLevel, int targetLevel) {
        if (allResources == null || allResources.isEmpty()) {
            return List.of();
        }
        String normalized = AbilityNameNormalizer.normalize(abilityName);
        if (normalized.isBlank() && tagId == null) {
            return List.of();
        }

        List<LearningResource> matched = new ArrayList<>();
        if (!normalized.isBlank()) {
            // 1. 能力名称归一化精确 / 包含匹配
            for (LearningResource r : allResources) {
                String rNorm = AbilityNameNormalizer.normalize(r.getAbilityName());
                if (rNorm.equals(normalized) || rNorm.contains(normalized) || normalized.contains(rNorm)) {
                    matched.add(r);
                }
            }
        }
        if (matched.isEmpty() && !normalized.isBlank()) {
            // 2. 资源标题归一化包含匹配
            for (LearningResource r : allResources) {
                if (r.getTitle() != null
                        && AbilityNameNormalizer.normalize(r.getTitle()).contains(normalized)) {
                    matched.add(r);
                }
            }
        }
        if (matched.isEmpty() && tagId != null) {
            // 3. tagId 辅助匹配（不阻断查询）
            for (LearningResource r : allResources) {
                if (tagId.equals(r.getTagId())) {
                    matched.add(r);
                }
            }
        }

        // 排序：精确度 > 难度适配 > sortOrder > 资源类型
        matched.sort(Comparator
                .comparingInt((LearningResource r) -> isExactAbilityMatch(r, normalized) ? 0 : 1)
                .thenComparingInt(r -> difficultyFit(r, currentLevel, targetLevel))
                .thenComparingInt(r -> r.getSortOrder() != null ? r.getSortOrder() : 999)
                .thenComparingInt(r -> TYPE_PRIORITY.getOrDefault(r.getResourceType(), 99)));
        return matched;
    }

    private static boolean isExactAbilityMatch(LearningResource r, String normalizedName) {
        if (normalizedName.isBlank()) {
            return false;
        }
        return AbilityNameNormalizer.normalize(r.getAbilityName()).equals(normalizedName);
    }

    /**
     * 难度适配度（越小越好）：资源难度落在 [currentLevel, targetLevel] 区间最合适。
     */
    private static int difficultyFit(LearningResource r, int currentLevel, int targetLevel) {
        int resourceLevel = r.getDifficultyLevel() != null ? r.getDifficultyLevel() : 3;
        if (resourceLevel >= currentLevel && resourceLevel <= targetLevel) {
            return 0;
        }
        if (resourceLevel == currentLevel - 1) {
            return 1;
        }
        if (resourceLevel == targetLevel + 1) {
            return 2;
        }
        return Math.abs(resourceLevel - (currentLevel + targetLevel) / 2) + 3;
    }
}
