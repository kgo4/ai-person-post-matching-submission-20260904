package com.example.matching.service.system.impl;

import com.example.matching.dto.system.AbilityTagSaveDTO;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.event.PostAbilityTagGovernanceRequestedEvent;
import com.example.matching.service.system.AbilityTagService;
import com.example.matching.service.system.PostAbilityTagGovernanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 岗位能力表到系统标签库的非阻断、非 AI 旁路同步。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostAbilityTagGovernanceServiceImpl implements PostAbilityTagGovernanceService {

    private final AbilityTagService abilityTagService;

    @Override
    @Transactional
    public void govern(PostAbilityTagGovernanceRequestedEvent event) {
        if (event == null || !StringUtils.hasText(event.abilityName())) return;
        String name = event.abilityName().trim();

        // 标签库是非阻断的跨岗位能力索引，不进入候选、挂载或 AI 流程。
        AbilityTag existing = abilityTagService.findByName(name);
        if (existing == null) existing = abilityTagService.findByAlias(name);
        if (existing != null) {
            return;
        }
        try {
            AbilityTagSaveDTO dto = new AbilityTagSaveDTO();
            dto.setTagCode("POST_ABILITY_" + Integer.toUnsignedString(name.toLowerCase(java.util.Locale.ROOT).hashCode()));
            dto.setTagName(name);
            dto.setParentId(0L);
            dto.setTagCategory(StringUtils.hasText(event.tagCategory()) ? event.tagCategory() : "TECHNICAL");
            dto.setTagLevel(0);
            dto.setDescription("来自岗位能力表的标准能力名称");
            dto.setSortOrder(0);
            abilityTagService.saveTag(dto);
        } catch (Exception ex) {
            log.warn("岗位能力标签旁路同步失败，不影响岗位主流程: ability={}", name, ex);
        }
    }

}
