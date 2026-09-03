package com.example.matching.service.system.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.ai.service.VectorEmbeddingService;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.entity.system.SkillTaxonomyMap;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.mapper.system.SkillTaxonomyMapMapper;
import com.example.matching.service.system.TaxonomyClassifyResult;
import com.example.matching.service.system.AbilityTagHierarchy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

/**
 * 技能归层分类器
 * <p>
 * 把 agent 提取的技能词（如 Vue3、SpringBoot、MySQL）归属到能力层（L1）标签，
 * 用于标签入库时正确挂载 parent_id / tag_level，解决「技能词与能力词粒度错配」问题。
 * <p>
 * 归类策略（优先级从高到低）：
 * <ol>
 *   <li>规则表精确匹配（{@link SkillTaxonomyMap}，人工维护的高置信快速通道）</li>
 *   <li>向量匹配（技能词 embedding 与 L1 能力标签 embedding 的余弦相似度）</li>
 *   <li>均未命中 → 返回 null，由调用方走候选池人工治理</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AbilityTagTaxonomyClassifier {

    /** 技能→能力跨粒度匹配的相似度阈值（低于同粒度匹配的 0.55，因跨粒度相似度天然偏低） */
    private static final float VECTOR_THRESHOLD = 0.50f;

    private final SkillTaxonomyMapMapper taxonomyMapMapper;
    private final AbilityTagMapper abilityTagMapper;
    private final VectorEmbeddingService vectorEmbeddingService;

    /**
     * 将技能词归属到能力层标签。
     *
     * @param skillName agent 提取的技能词
     * @return 归属结果；无法归属时返回 null
     */
    public TaxonomyClassifyResult classify(String skillName) {
        if (!StringUtils.hasText(skillName)) {
            return null;
        }
        String normalized = skillName.trim();

        // 1. 规则表精确匹配
        TaxonomyClassifyResult ruleResult = matchByRule(normalized);
        if (ruleResult != null) {
            return ruleResult;
        }

        // 2. 向量匹配 L1 能力层
        return matchByVector(normalized);
    }

    private TaxonomyClassifyResult matchByRule(String skillName) {
        SkillTaxonomyMap rule = taxonomyMapMapper.selectOne(
                Wrappers.<SkillTaxonomyMap>lambdaQuery()
                        .eq(SkillTaxonomyMap::getSkillName, skillName)
                        .eq(SkillTaxonomyMap::getStatus, 1)
                        .last("LIMIT 1"));
        if (rule == null || rule.getAbilityTagId() == null) {
            return null;
        }
        AbilityTag ability = abilityTagMapper.selectById(rule.getAbilityTagId());
        if (!isValidCapabilityPlacement(ability)) {
            return null;
        }
        return TaxonomyClassifyResult.of(ability, "RULE", rule.getConfidence());
    }

    private TaxonomyClassifyResult matchByVector(String skillName) {
        try {
            List<Float> queryVector = vectorEmbeddingService.embed(skillName);
            if (queryVector == null || queryVector.isEmpty()) {
                return null;
            }

            // 仅对能力层（L1）标签做归属匹配，避免技能词匹配到其它技能词
            List<AbilityTag> competencies = abilityTagMapper.selectList(
                    Wrappers.<AbilityTag>lambdaQuery()
                            .eq(AbilityTag::getTagLevel, AbilityTagHierarchy.ASSESSABLE_LEVEL)
                            .eq(AbilityTag::getStatus, 1));

            AbilityTag best = null;
            float bestScore = VECTOR_THRESHOLD;
            for (AbilityTag ability : competencies) {
                if (!isValidCapabilityPlacement(ability)
                        || ability.getEmbeddingVector() == null || ability.getEmbeddingVector().isEmpty()) {
                    continue;
                }
                Float similarity = vectorEmbeddingService.cosineSimilarity(queryVector, ability.getEmbeddingVector());
                if (similarity != null && similarity > bestScore) {
                    bestScore = similarity;
                    best = ability;
                }
            }

            if (best != null) {
                return TaxonomyClassifyResult.of(best, "VECTOR", BigDecimal.valueOf(bestScore));
            }
        } catch (Exception e) {
            log.warn("技能归层向量匹配失败: skillName={}, error={}", skillName, e.getMessage());
        }
        return null;
    }

    /**
     * A classifier result is usable only when it identifies an active L2
     * capability under an active, same-category L1 domain. This prevents a
     * stale taxonomy rule or vector hit from creating an orphan/cross-domain
     * tag.
     */
    private boolean isValidCapabilityPlacement(AbilityTag capability) {
        if (!AbilityTagHierarchy.isAssessable(capability)
                || capability.getParentId() == null || capability.getParentId() == 0L) {
            return false;
        }
        AbilityTag domain = abilityTagMapper.selectById(capability.getParentId());
        return AbilityTagHierarchy.isEnabledDomain(domain)
                && java.util.Objects.equals(domain.getTagCategory(), capability.getTagCategory());
    }
}
