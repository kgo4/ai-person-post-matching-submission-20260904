package com.example.matching.application.system;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.matching.dto.system.api.SkillTaxonomyMapRequest;
import com.example.matching.dto.system.api.SkillTaxonomyMapResponse;
import com.example.matching.entity.system.SkillTaxonomyMap;
import com.example.matching.service.system.SkillTaxonomyMapService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 技能→能力规则映射 Facade。
 * <p>
 * Controller 层经由本 Facade 访问，避免直接注入 Service 或暴露 Entity。
 */
@Service
@RequiredArgsConstructor
public class SkillTaxonomyMapApiFacade {

    private final SkillTaxonomyMapService skillTaxonomyMapService;

    public IPage<SkillTaxonomyMapResponse> pageRules(long current, long size, String keyword, Long abilityTagId) {
        return skillTaxonomyMapService.pageRules(current, size, keyword, abilityTagId)
                .convert(this::toResponse);
    }

    public SkillTaxonomyMapResponse createRule(SkillTaxonomyMapRequest request) {
        return toResponse(skillTaxonomyMapService.createRule(toEntity(request)));
    }

    public SkillTaxonomyMapResponse updateRule(Long id, SkillTaxonomyMapRequest request) {
        return toResponse(skillTaxonomyMapService.updateRule(id, toEntity(request)));
    }

    public void updateStatus(Long id, Integer status) {
        SkillTaxonomyMap patch = new SkillTaxonomyMap();
        patch.setStatus(status);
        skillTaxonomyMapService.updateRule(id, patch);
    }

    public void deleteRule(Long id) {
        skillTaxonomyMapService.deleteRule(id);
    }

    private SkillTaxonomyMap toEntity(SkillTaxonomyMapRequest r) {
        SkillTaxonomyMap e = new SkillTaxonomyMap();
        e.setSkillName(r.skillName());
        e.setAbilityTagId(r.abilityTagId());
        e.setCategory(r.category());
        e.setConfidence(r.confidence());
        e.setSource(r.source());
        e.setStatus(r.status());
        return e;
    }

    private SkillTaxonomyMapResponse toResponse(SkillTaxonomyMap e) {
        return new SkillTaxonomyMapResponse(
                e.getId(), e.getSkillName(), e.getAbilityTagId(), e.getCategory(),
                e.getConfidence(), e.getSource(), e.getStatus(),
                e.getCreatedTime(), e.getUpdatedTime());
    }
}
