package com.example.matching.service.system;

import com.example.matching.entity.system.AbilityTag;

import java.math.BigDecimal;

/**
 * 技能归层结果
 *
 * @param abilityTag 归属的能力层（L1）标签
 * @param source     归类来源：RULE-规则表 / VECTOR-向量匹配
 * @param confidence 置信度：规则=映射表 confidence，向量=相似度
 */
public record TaxonomyClassifyResult(AbilityTag abilityTag, String source, BigDecimal confidence) {

    public static TaxonomyClassifyResult of(AbilityTag abilityTag, String source, BigDecimal confidence) {
        return new TaxonomyClassifyResult(abilityTag, source, confidence);
    }
}
