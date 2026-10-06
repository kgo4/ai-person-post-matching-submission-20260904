package com.example.matching.service.system.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.entity.system.SkillTaxonomyMap;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.mapper.system.SkillTaxonomyMapMapper;
import com.example.matching.service.system.SkillTaxonomyMapService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

/**
 * 技能→能力规则映射服务实现。
 * <p>
 * 提供规则的分页查询与增删改，供标签管理员维护「技能词 → 能力层标签」映射。
 */
@Service
@RequiredArgsConstructor
public class SkillTaxonomyMapServiceImpl extends ServiceImpl<SkillTaxonomyMapMapper, SkillTaxonomyMap>
        implements SkillTaxonomyMapService {

    private final AbilityTagMapper abilityTagMapper;

    @Override
    public IPage<SkillTaxonomyMap> pageRules(long current, long size, String keyword, Long abilityTagId) {
        LambdaQueryWrapper<SkillTaxonomyMap> wrapper = Wrappers.<SkillTaxonomyMap>lambdaQuery()
                .like(StringUtils.hasText(keyword), SkillTaxonomyMap::getSkillName, keyword)
                .eq(abilityTagId != null, SkillTaxonomyMap::getAbilityTagId, abilityTagId)
                .orderByDesc(SkillTaxonomyMap::getUpdatedTime);
        return page(new Page<>(current, size), wrapper);
    }

    @Override
    public SkillTaxonomyMap createRule(SkillTaxonomyMap rule) {
        validate(rule);
        long exists = count(Wrappers.<SkillTaxonomyMap>lambdaQuery()
                .eq(SkillTaxonomyMap::getSkillName, rule.getSkillName().trim()));
        if (exists > 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "技能词已存在映射规则: " + rule.getSkillName());
        }
        rule.setSkillName(rule.getSkillName().trim());
        rule.setStatus(rule.getStatus() != null ? rule.getStatus() : 1);
        rule.setSource(rule.getSource() != null ? rule.getSource() : "MANUAL");
        rule.setConfidence(rule.getConfidence() != null ? rule.getConfidence() : BigDecimal.ONE);
        rule.setCategory(rule.getCategory() != null ? rule.getCategory() : "TECHNICAL");
        save(rule);
        return rule;
    }

    @Override
    public SkillTaxonomyMap updateRule(Long id, SkillTaxonomyMap rule) {
        SkillTaxonomyMap existing = getById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "规则不存在: " + id);
        }
        if (rule.getAbilityTagId() != null) {
            validateAbilityTag(rule.getAbilityTagId());
        }
        rule.setId(id);
        updateById(rule);
        return getById(id);
    }

    @Override
    public void deleteRule(Long id) {
        removeById(id);
    }

    @Override
    public List<SkillTaxonomyMap> listByAbilityTagIds(List<Long> abilityTagIds) {
        if (abilityTagIds == null || abilityTagIds.isEmpty()) {
            return List.of();
        }
        return list(Wrappers.<SkillTaxonomyMap>lambdaQuery()
                .in(SkillTaxonomyMap::getAbilityTagId, abilityTagIds)
                .eq(SkillTaxonomyMap::getStatus, 1));
    }

    private void validate(SkillTaxonomyMap rule) {
        if (rule == null || !StringUtils.hasText(rule.getSkillName())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "技能词不能为空");
        }
        validateAbilityTag(rule.getAbilityTagId());
    }

    private void validateAbilityTag(Long abilityTagId) {
        if (abilityTagId == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "归属能力标签不能为空");
        }
        AbilityTag tag = abilityTagMapper.selectById(abilityTagId);
        if (tag == null || tag.getStatus() == null || tag.getStatus() != 1) {
            throw new BusinessException(ErrorCodeEnum.ABILITY_TAG_NOT_FOUND, "能力标签不存在或已停用: " + abilityTagId);
        }
    }
}
