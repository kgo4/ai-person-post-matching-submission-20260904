package com.example.matching.service.ability.converter;

import com.example.matching.entity.ability.PersonAbilityClaim;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 人员能力声明转换器
 * <p>
 * 负责在 agent DTO 和 entity 之间进行转换。
 *
 * @author system
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PersonAbilityClaimConverter {

    private final ObjectMapper objectMapper;

    /**
     * 从 agent DTO 转换为 entity
     */
    public PersonAbilityClaim fromAgentClaim(com.example.matching.agent.dto.person.PersonAbilityClaim source) {
        if (source == null) {
            return null;
        }

        PersonAbilityClaim target = new PersonAbilityClaim();
        target.setEmpId(source.getEmpId());
        target.setTagId(source.getAbilityTagId());
        target.setAbilityName(source.getAbilityName());
        target.setClaimedLevel(source.getMasteryLevel());
        target.setSourceType(source.getSourceType());
        target.setSourceRefId(source.getSourceRefId());
        target.setEvidenceText(source.getEvidenceText());
        target.setConfidenceScore(source.getConfidenceScore());
        target.setStatus("ACTIVE");

        if (source.getSourceRefs() != null && !source.getSourceRefs().isEmpty()) {
            try {
                target.setSourceRefsJson(objectMapper.writeValueAsString(source.getSourceRefs()));
            } catch (Exception e) {
                log.warn("Failed to serialize sourceRefs: {}", e.getMessage());
                target.setSourceRefsJson("[]");
            }
        }

        return target;
    }

    /**
     * 从 entity 转换为 agent DTO
     */
    public com.example.matching.agent.dto.person.PersonAbilityClaim toAgentClaim(PersonAbilityClaim source) {
        if (source == null) {
            return null;
        }

        com.example.matching.agent.dto.person.PersonAbilityClaim target =
                new com.example.matching.agent.dto.person.PersonAbilityClaim();

        target.setEmpId(source.getEmpId());
        target.setAbilityTagId(source.getTagId());
        target.setAbilityName(source.getAbilityName());
        target.setMasteryLevel(source.getClaimedLevel());
        target.setSourceType(source.getSourceType());
        target.setSourceRefId(source.getSourceRefId());
        target.setEvidenceText(source.getEvidenceText());
        target.setConfidenceScore(source.getConfidenceScore());

        if (source.getSourceRefsJson() != null && !source.getSourceRefsJson().isBlank()) {
            try {
                List<String> sourceRefs = objectMapper.readValue(
                        source.getSourceRefsJson(),
                        new TypeReference<List<String>>() {}
                );
                target.setSourceRefs(sourceRefs);
            } catch (Exception e) {
                log.warn("Failed to deserialize sourceRefsJson: {}", e.getMessage());
                target.setSourceRefs(new ArrayList<>());
            }
        }

        return target;
    }

    /**
     * 批量从 agent DTO 转换为 entity
     */
    public List<PersonAbilityClaim> fromAgentClaims(List<com.example.matching.agent.dto.person.PersonAbilityClaim> sources) {
        if (sources == null) {
            return List.of();
        }
        List<PersonAbilityClaim> results = new ArrayList<>();
        for (com.example.matching.agent.dto.person.PersonAbilityClaim source : sources) {
            PersonAbilityClaim target = fromAgentClaim(source);
            if (target != null) {
                results.add(target);
            }
        }
        return results;
    }

    /**
     * 批量从 entity 转换为 agent DTO
     */
    public List<com.example.matching.agent.dto.person.PersonAbilityClaim> toAgentClaims(List<PersonAbilityClaim> sources) {
        if (sources == null) {
            return List.of();
        }
        List<com.example.matching.agent.dto.person.PersonAbilityClaim> results = new ArrayList<>();
        for (PersonAbilityClaim source : sources) {
            com.example.matching.agent.dto.person.PersonAbilityClaim target = toAgentClaim(source);
            if (target != null) {
                results.add(target);
            }
        }
        return results;
    }
}
