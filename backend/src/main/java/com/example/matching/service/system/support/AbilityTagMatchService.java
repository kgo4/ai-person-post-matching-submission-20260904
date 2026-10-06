package com.example.matching.service.system.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.common.util.AbilityNameNormalizer;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.service.system.AbilityTagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 能力标签确定性兜底匹配（不调用向量/AI 服务）。
 * <p>
 * 匹配优先级：
 * <ol>
 *   <li>标签名称精确匹配</li>
 *   <li>标签别名精确匹配（ability_tag_alias）</li>
 *   <li>标签名称归一化精确匹配（AbilityNameNormalizer，兼容中英文括号/横线等）</li>
 *   <li>标签名称归一化包含匹配（双向 contains）</li>
 * </ol>
 * <p>
 * 用途：人员在提取链路 / 治理准入 PASS 但缺少正式标签时，用能力名做服务端确定性匹配，
 * 命中即补全 matchedTagId，从而减少"无标签 → REVIEW"的审核任务量。
 */
@Component
@RequiredArgsConstructor
public class AbilityTagMatchService {

    private final AbilityTagService abilityTagService;

    /**
     * 按能力名称确定性匹配一个启用标签。
     *
     * @param abilityName 能力名称
     * @return 命中的启用标签；未命中返回 null
     */
    public AbilityTag matchByName(String abilityName) {
        if (!StringUtils.hasText(abilityName)) {
            return null;
        }
        String trimmed = abilityName.trim();

        // 1. 精确
        AbilityTag tag = abilityTagService.findByName(trimmed);
        if (tag != null) {
            return tag;
        }
        // 2. 别名
        tag = abilityTagService.findByAlias(trimmed);
        if (tag != null) {
            return tag;
        }

        // 3-4. 归一化遍历（精确 + 包含）
        String normalized = AbilityNameNormalizer.normalize(trimmed);
        if (normalized.isBlank()) {
            return null;
        }
        List<AbilityTag> all = abilityTagService.list(
                Wrappers.<AbilityTag>lambdaQuery().eq(AbilityTag::getStatus, 1));
        for (AbilityTag t : all) {
            if (t.getTagName() == null) {
                continue;
            }
            if (AbilityNameNormalizer.normalize(t.getTagName()).equals(normalized)) {
                return t;
            }
        }
        for (AbilityTag t : all) {
            if (t.getTagName() == null) {
                continue;
            }
            String tNorm = AbilityNameNormalizer.normalize(t.getTagName());
            if (tNorm.contains(normalized) || normalized.contains(tNorm)) {
                return t;
            }
        }
        return null;
    }
}
