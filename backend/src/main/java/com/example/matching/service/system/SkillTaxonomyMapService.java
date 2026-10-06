package com.example.matching.service.system;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.matching.entity.system.SkillTaxonomyMap;

import java.util.List;

/**
 * 技能→能力规则映射服务
 * <p>
 * 供标签管理员维护「技能词 → 能力层标签」的高置信映射规则，
 * 是标签分层体系人工治理的入口。
 */
public interface SkillTaxonomyMapService extends IService<SkillTaxonomyMap> {

    /**
     * 分页查询规则。
     *
     * @param keyword      技能词模糊匹配
     * @param abilityTagId 按归属能力标签过滤（可为 null）
     */
    IPage<SkillTaxonomyMap> pageRules(long current, long size, String keyword, Long abilityTagId);

    /**
     * 新增规则。
     *
     * @return 创建后的规则实体
     */
    SkillTaxonomyMap createRule(SkillTaxonomyMap rule);

    /**
     * 更新规则（按 id）。
     */
    SkillTaxonomyMap updateRule(Long id, SkillTaxonomyMap rule);

    /**
     * 删除规则。
     */
    void deleteRule(Long id);

    /**
     * 按能力标签批量查询规则（供归层分类器使用）。
     */
    List<SkillTaxonomyMap> listByAbilityTagIds(List<Long> abilityTagIds);
}
