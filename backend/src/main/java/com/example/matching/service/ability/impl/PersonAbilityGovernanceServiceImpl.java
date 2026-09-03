package com.example.matching.service.ability.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.entity.ability.AgentMemory;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.ability.PersonAbilityGovernanceEvent;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.ability.PersonAbilityGovernanceEventMapper;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.ability.PersonAbilityProfileMapper;
import com.example.matching.entity.ability.PersonAbilityProfile;
import com.example.matching.service.ability.AgentMemoryService;
import com.example.matching.service.ability.PersonAbilityGovernanceService;
import com.example.matching.service.system.AbilityTagService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PersonAbilityGovernanceServiceImpl implements PersonAbilityGovernanceService {

    private final PersonAbilityGovernanceEventMapper eventMapper;
    private final EmpAbilityMapper empAbilityMapper;
    private final PersonAbilityProfileMapper personAbilityProfileMapper;
    private final AbilityTagService abilityTagService;
    private final AgentMemoryService agentMemoryService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public PersonAbilityGovernanceEvent replaceTag(Long empId, Long oldTagId, Long newTagId,
                                                     String reason, Long operatorId) {
        return replaceTag(empId, oldTagId, newTagId, reason, operatorId, false);
    }

    @Override
    @Transactional
    public PersonAbilityGovernanceEvent replaceTag(Long empId, Long oldTagId, Long newTagId,
                                                     String reason, Long operatorId, boolean generalizeRule) {
        EmpAbility empAbility = empAbilityMapper.selectOne(Wrappers.<EmpAbility>lambdaQuery()
                .eq(EmpAbility::getEmpId, empId)
                .eq(EmpAbility::getTagId, oldTagId)
                .last("LIMIT 1"));

        if (empAbility == null) {
            throw BusinessException.of(ErrorCodeEnum.NOT_FOUND, "未找到该员工的能力记录: empId=" + empId + ", tagId=" + oldTagId).entity("EMP_ABILITY", empId).operation("replaceTag").build();
        }

        AbilityTag oldTag = abilityTagService.getById(oldTagId);
        AbilityTag newTag = abilityTagService.getById(newTagId);
        String oldTagName = oldTag != null ? oldTag.getTagName() : "未知";
        String newTagName = newTag != null ? newTag.getTagName() : "未知";

        PersonAbilityGovernanceEvent event = new PersonAbilityGovernanceEvent();
        event.setEmpId(empId);
        event.setOldTagId(oldTagId);
        event.setOldTagName(oldTagName);
        event.setNewTagId(newTagId);
        event.setNewTagName(newTagName);
        event.setOldLevel(empAbility.getMasteryLevel());
        event.setNewLevel(empAbility.getMasteryLevel());
        event.setOldConfidence(empAbility.getSourceWeight() != null
                ? empAbility.getSourceWeight().multiply(BigDecimal.valueOf(100)) : null);
        event.setNewConfidence(empAbility.getSourceWeight() != null
                ? empAbility.getSourceWeight().multiply(BigDecimal.valueOf(100)) : null);
        event.setModifyType("TAG_REPLACE");
        event.setModifyReason(reason);
        event.setCreatedBy(operatorId);
        event.setCreatedTime(LocalDateTime.now());
        event.setGenerateMemory(generalizeRule ? 1 : 0);
        eventMapper.insert(event);

        empAbility.setTagId(newTagId);
        empAbilityMapper.updateById(empAbility);
        PersonAbilityProfile profile = findProfile(empId, oldTagId);
        if (profile != null) {
            profile.setTagId(newTagId);
            profile.setAbilityName(newTagName);
            personAbilityProfileMapper.updateById(profile);
        }

        if (generalizeRule && "RESUME_PARSE".equals(empAbility.getEvaluationSource())) {
            AgentMemory memory = buildTagNormalizeMemory(oldTagName, newTagName, oldTagId, newTagId, event.getId());
            memory.setStatus("ACTIVE");
            Long memoryId = agentMemoryService.createMemory(memory);
            event.setMemoryId(memoryId);
            eventMapper.updateById(event);
            log.info("简历能力名称纠偏已记住: empId={}, {} -> {}, memoryId={}", empId, oldTagName, newTagName, memoryId);
        }

        return event;
    }

    @Override
    @Transactional
    public PersonAbilityGovernanceEvent changeLevel(Long empId, Long tagId, Integer newLevel,
                                                      String reason, Long operatorId) {
        return changeLevel(empId, tagId, newLevel, reason, operatorId, false, null);
    }

    @Override
    @Transactional
    public PersonAbilityGovernanceEvent changeLevel(Long empId, Long tagId, Integer newLevel,
                                                      String reason, Long operatorId,
                                                      boolean generalizeRule, Integer maxLevelCap) {
        EmpAbility empAbility = empAbilityMapper.selectOne(Wrappers.<EmpAbility>lambdaQuery()
                .eq(EmpAbility::getEmpId, empId)
                .eq(EmpAbility::getTagId, tagId)
                .last("LIMIT 1"));

        if (empAbility == null) {
            throw BusinessException.of(ErrorCodeEnum.NOT_FOUND, "未找到该员工的能力记录: empId=" + empId + ", tagId=" + tagId).entity("EMP_ABILITY", empId).operation("changeLevel").build();
        }

        AbilityTag tag = abilityTagService.getById(tagId);
        String tagName = tag != null ? tag.getTagName() : "未知";
        Integer oldLevel = empAbility.getMasteryLevel();
        if (newLevel == null) {
            throw BusinessException.of(ErrorCodeEnum.PARAM_ERROR, "新能力等级不能为空")
                    .entity("EMP_ABILITY", empId).operation("changeLevel").build();
        }

        PersonAbilityGovernanceEvent event = new PersonAbilityGovernanceEvent();
        event.setEmpId(empId);
        event.setOldTagId(tagId);
        event.setOldTagName(tagName);
        event.setNewTagId(tagId);
        event.setNewTagName(tagName);
        event.setOldLevel(oldLevel);
        event.setNewLevel(newLevel);
        event.setOldConfidence(empAbility.getSourceWeight() != null
                ? empAbility.getSourceWeight().multiply(BigDecimal.valueOf(100)) : null);
        event.setNewConfidence(empAbility.getSourceWeight() != null
                ? empAbility.getSourceWeight().multiply(BigDecimal.valueOf(100)) : null);
        String modifyType = oldLevel == null ? "LEVEL_SET"
                : newLevel > oldLevel ? "LEVEL_UP"
                : newLevel < oldLevel ? "LEVEL_DOWN" : "LEVEL_UNCHANGED";
        event.setModifyType(modifyType);
        event.setModifyReason(reason);
        event.setCreatedBy(operatorId);
        event.setCreatedTime(LocalDateTime.now());
        event.setGenerateMemory(1);
        eventMapper.insert(event);

        empAbility.setMasteryLevel(newLevel);
        empAbilityMapper.updateById(empAbility);
        PersonAbilityProfile profile = findProfile(empId, tagId);
        if (profile != null) {
            profile.setFinalLevel(newLevel);
            personAbilityProfileMapper.updateById(profile);
        }

        // HR 修正统一累积成记忆：降级→等级上限，升级→等级下限（LEVEL_UNCHANGED 不生成）
        if (!"LEVEL_UNCHANGED".equals(modifyType)) {
            Integer maxLevel = ("LEVEL_DOWN".equals(modifyType) || "LEVEL_SET".equals(modifyType)) ? newLevel : null;
            Integer minLevel = "LEVEL_UP".equals(modifyType) ? newLevel : null;
            // 显式传入的等级上限优先
            if (maxLevelCap != null) {
                maxLevel = maxLevelCap;
                minLevel = null;
            }
            AgentMemory memory = buildLevelRuleMemory(tagName, tagId, reason, event.getId(), null, maxLevel, minLevel);
            memory.setStatus("ACTIVE");
            Long memoryId = agentMemoryService.createMemory(memory);
            event.setMemoryId(memoryId);
            eventMapper.updateById(event);
            log.info("等级修改完成（已生成记忆）: empId={}, tag={}, {} -> {}, memoryId={}",
                    empId, tagName, oldLevel, newLevel, memoryId);
        } else {
            log.info("等级修改完成（等级未变化，不生成记忆）: empId={}, tag={}, {} -> {}",
                    empId, tagName, oldLevel, newLevel);
        }

        return event;
    }

    @Override
    @Transactional
    public PersonAbilityGovernanceEvent removeTag(Long empId, Long tagId, String reason, Long operatorId) {
        return removeTag(empId, tagId, reason, operatorId, false);
    }

    @Override
    @Transactional
    public PersonAbilityGovernanceEvent removeTag(Long empId, Long tagId, String reason,
                                                    Long operatorId, boolean generalizeRule) {
        EmpAbility empAbility = empAbilityMapper.selectOne(Wrappers.<EmpAbility>lambdaQuery()
                .eq(EmpAbility::getEmpId, empId)
                .eq(EmpAbility::getTagId, tagId)
                .last("LIMIT 1"));

        if (empAbility == null) {
            throw BusinessException.of(ErrorCodeEnum.NOT_FOUND, "未找到该员工的能力记录: empId=" + empId + ", tagId=" + tagId).entity("EMP_ABILITY", empId).operation("removeTag").build();
        }

        AbilityTag tag = abilityTagService.getById(tagId);
        String tagName = tag != null ? tag.getTagName() : "未知";

        PersonAbilityGovernanceEvent event = new PersonAbilityGovernanceEvent();
        event.setEmpId(empId);
        event.setOldTagId(tagId);
        event.setOldTagName(tagName);
        event.setOldLevel(empAbility.getMasteryLevel());
        event.setOldConfidence(empAbility.getSourceWeight() != null
                ? empAbility.getSourceWeight().multiply(BigDecimal.valueOf(100)) : null);
        event.setModifyType("REMOVE_TAG");
        event.setModifyReason(reason);
        event.setCreatedBy(operatorId);
        event.setCreatedTime(LocalDateTime.now());
        event.setGenerateMemory(1);
        eventMapper.insert(event);

        empAbilityMapper.deleteById(empAbility.getId());
        PersonAbilityProfile profile = findProfile(empId, tagId);
        if (profile != null) {
            profile.setIsDeleted(1);
            personAbilityProfileMapper.updateById(profile);
        }

        // HR 修正统一累积成记忆（默认 ACTIVE，可全局/逐条关闭），下次提取时拒绝该标签
        AgentMemory memory = buildTagRejectMemory(tagName, tagId, reason, event.getId());
        memory.setStatus("ACTIVE");
        Long memoryId = agentMemoryService.createMemory(memory);
        event.setMemoryId(memoryId);
        eventMapper.updateById(event);
        log.info("标签删除完成（已生成记忆）: empId={}, tag={}, memoryId={}", empId, tagName, memoryId);

        return event;
    }

    @Override
    @Transactional
    public List<PersonAbilityGovernanceEvent> renameTag(Long tagId, String newName, String reason, Long operatorId) {
        AbilityTag tag = abilityTagService.getById(tagId);
        if (tag == null) {
            throw BusinessException.of(ErrorCodeEnum.NOT_FOUND, "标签不存在: " + tagId).entity("ABILITY_TAG", tagId).operation("renameTag").build();
        }

        String oldName = tag.getTagName();
        tag.setTagName(newName);
        abilityTagService.updateById(tag);
        abilityTagService.addAlias(tagId, oldName, "GOVERNANCE");

        List<EmpAbility> empAbilities = empAbilityMapper.selectList(Wrappers.<EmpAbility>lambdaQuery()
                .eq(EmpAbility::getTagId, tagId));

        List<PersonAbilityProfile> profiles = personAbilityProfileMapper.selectList(
                Wrappers.<PersonAbilityProfile>lambdaQuery().eq(PersonAbilityProfile::getTagId, tagId));
        for (PersonAbilityProfile profile : profiles) {
            profile.setAbilityName(newName);
            personAbilityProfileMapper.updateById(profile);
        }

        List<PersonAbilityGovernanceEvent> events = new ArrayList<>();
        for (EmpAbility empAbility : empAbilities) {
            PersonAbilityGovernanceEvent event = new PersonAbilityGovernanceEvent();
            event.setEmpId(empAbility.getEmpId());
            event.setOldTagId(tagId);
            event.setOldTagName(oldName);
            event.setNewTagId(tagId);
            event.setNewTagName(newName);
            event.setOldLevel(empAbility.getMasteryLevel());
            event.setNewLevel(empAbility.getMasteryLevel());
            event.setModifyType("TAG_RENAME");
            event.setModifyReason(reason);
            event.setCreatedBy(operatorId);
            event.setCreatedTime(LocalDateTime.now());
            event.setGenerateMemory(1);
            eventMapper.insert(event);
            events.add(event);
        }

        AgentMemory memory = buildTagNormalizeMemory(oldName, newName, tagId, tagId, null);
        memory.setStatus("ACTIVE");
        Long memoryId = agentMemoryService.createMemory(memory);

        for (PersonAbilityGovernanceEvent event : events) {
            event.setMemoryId(memoryId);
            eventMapper.updateById(event);
        }

        log.info("标签重命名完成: {} -> {}, 影响{}条能力记录, memoryId={}", oldName, newName, events.size(), memoryId);
        return events;
    }

    private PersonAbilityProfile findProfile(Long empId, Long tagId) {
        return personAbilityProfileMapper.selectOne(Wrappers.<PersonAbilityProfile>lambdaQuery()
                .eq(PersonAbilityProfile::getEmpId, empId)
                .eq(PersonAbilityProfile::getTagId, tagId)
                .eq(PersonAbilityProfile::getIsDeleted, 0)
                .last("LIMIT 1"));
    }

    @Override
    public List<PersonAbilityGovernanceEvent> getGovernanceHistory(Long empId) {
        return eventMapper.selectList(Wrappers.<PersonAbilityGovernanceEvent>lambdaQuery()
                .eq(PersonAbilityGovernanceEvent::getEmpId, empId)
                .orderByDesc(PersonAbilityGovernanceEvent::getCreatedTime));
    }

    @Override
    public List<PersonAbilityGovernanceEvent> getGovernanceByTag(Long tagId) {
        return eventMapper.selectList(Wrappers.<PersonAbilityGovernanceEvent>lambdaQuery()
                .and(w -> w
                        .eq(PersonAbilityGovernanceEvent::getOldTagId, tagId)
                        .or()
                        .eq(PersonAbilityGovernanceEvent::getNewTagId, tagId))
                .orderByDesc(PersonAbilityGovernanceEvent::getCreatedTime));
    }

    @Override
    @Transactional
    public PersonAbilityGovernanceEvent createEvent(PersonAbilityGovernanceEvent event) {
        if (event.getCreatedTime() == null) {
            event.setCreatedTime(LocalDateTime.now());
        }
        if (event.getGenerateMemory() == null) {
            event.setGenerateMemory(0);
        }
        eventMapper.insert(event);
        log.info("创建治理事件: id={}, type={}, empId={}, generalizeMemory={}",
                event.getId(), event.getModifyType(), event.getEmpId(), event.getGenerateMemory());
        return event;
    }

    @Override
    @Transactional
    public void generateAgentMemory(PersonAbilityGovernanceEvent event) {
        if (event == null || event.getModifyType() == null) {
            return;
        }

        String modifyType = event.getModifyType();
        AgentMemory memory = new AgentMemory();

        switch (modifyType) {
            case "TAG_REPLACE":
                memory = buildTagNormalizeMemory(event.getOldTagName(), event.getNewTagName(),
                        event.getOldTagId(), event.getNewTagId(), event.getId());
                break;

            case "ABILITY_RENAME":
                // 正式能力名称修改同样需要沉淀为归一化记忆，但不依赖标签库或 tagId。
                memory = buildTagNormalizeMemory(event.getOldTagName(), event.getNewTagName(),
                        event.getOldTagId(), event.getNewTagId(), event.getId());
                break;

            case "LEVEL_UP":
            case "LEVEL_DOWN":
                AbilityTag tag = abilityTagService.getById(event.getOldTagId());
                String tagName = tag != null ? tag.getTagName() : "未知";
                Integer maxLevel = "LEVEL_DOWN".equals(modifyType) ? event.getNewLevel() : null;
                Integer minLevel = "LEVEL_UP".equals(modifyType) ? event.getNewLevel() : null;
                memory = buildLevelRuleMemory(tagName, event.getOldTagId(), event.getModifyReason(),
                        event.getId(), null, maxLevel, minLevel);
                break;

            case "DELETE_ABILITY":
            case "REMOVE_TAG":
                memory = buildTagRejectMemory(event.getOldTagName(), event.getOldTagId(),
                        event.getModifyReason(), event.getId());
                break;

            case "MANUAL_ADD":
                memory.setMemoryType("TERM_INTERPRETATION");
                memory.setTitle("正例标签: " + event.getNewTagName());
                memory.setContent("\"" + event.getNewTagName() + "\" 是有效能力标签");
                memory.setTriggerExpressionsJson("[\"" + event.getNewTagName() + "\"]");
                memory.setApplicableScope("EMPLOYEE_ABILITY_EXTRACTION");
                memory.setPriority(60);
                memory.setRuleStrength("GUIDANCE");
                break;

            case "EVIDENCE_UPDATE":
                log.info("证据更新事件不生成记忆: eventId={}", event.getId());
                return;

            default:
                log.warn("未知的修改类型，无法生成记忆: {}", modifyType);
                return;
        }

        memory.setSourceEventId(event.getId());
        memory.setStatus("ACTIVE");
        Long memoryId = agentMemoryService.createMemory(memory);

        event.setMemoryId(memoryId);
        eventMapper.updateById(event);

        log.info("生成Agent记忆: eventId={}, memoryId={}, type={}", event.getId(), memoryId, memory.getMemoryType());
    }

    // ──────────────── 记忆构建方法 ────────────────

    private AgentMemory buildTagNormalizeMemory(String oldName, String newName,
                                                  Long oldTagId, Long newTagId, Long eventId) {
        AgentMemory memory = new AgentMemory();
        memory.setMemoryType("TAG_NORMALIZE");
        memory.setTitle(oldName + " -> " + newName);
        memory.setContent("\"" + oldName + "\" 归一为 \"" + newName + "\"");
        memory.setTriggerExpressionsJson("[\"" + oldName + "\"]");
        memory.setApplicableScope("EMPLOYEE_ABILITY_EXTRACTION");
        memory.setPriority(80);
        memory.setSourceEventId(eventId);
        memory.setRuleStrength("HARD");

        String payload = buildNormalizePayload(oldName, newName, oldTagId, newTagId, "TAG_NORMALIZE");
        memory.setRulePayloadJson(payload);
        memory.setRuleKey(AgentMemoryServiceImpl.computeRuleKey("EMPLOYEE_ABILITY_EXTRACTION", "TAG_NORMALIZE", payload));

        return memory;
    }

    private AgentMemory buildTagRejectMemory(String tagName, Long tagId, String reason, Long eventId) {
        AgentMemory memory = new AgentMemory();
        memory.setMemoryType("TAG_REJECT");
        memory.setTitle("拒绝标签: " + tagName);
        memory.setContent("\"" + tagName + "\" 不是有效能力标签，不应输出");
        memory.setTriggerExpressionsJson("[\"" + tagName + "\"]");
        memory.setApplicableScope("EMPLOYEE_ABILITY_EXTRACTION");
        memory.setPriority(85);
        memory.setSourceEventId(eventId);
        memory.setRuleStrength("HARD");

        String payload = buildRejectPayload(tagName, tagId, "TAG_REJECT");
        memory.setRulePayloadJson(payload);
        memory.setRuleKey(AgentMemoryServiceImpl.computeRuleKey("EMPLOYEE_ABILITY_EXTRACTION", "TAG_REJECT", payload));

        return memory;
    }

    private AgentMemory buildLevelRuleMemory(String tagName, Long tagId, String reason,
                                               Long eventId, String sourceType,
                                               Integer maxLevel, Integer minLevel) {
        AgentMemory memory = new AgentMemory();
        memory.setMemoryType("LEVEL_RULE");
        memory.setTitle(tagName + " 等级偏好");
        StringBuilder content = new StringBuilder("\"").append(tagName).append("\"");
        if (maxLevel != null) content.append(" 等级上限 ").append(maxLevel);
        if (minLevel != null) content.append(" 等级下限 ").append(minLevel);
        memory.setContent(content.toString());
        memory.setTriggerExpressionsJson("[\"" + tagName + "\"]");
        memory.setApplicableScope(sourceType != null ? sourceType : "EMPLOYEE_ABILITY_EXTRACTION");
        memory.setPriority(75);
        memory.setSourceEventId(eventId);
        memory.setRuleStrength("HARD");

        String payload = buildLevelRulePayload(tagName, tagId, maxLevel, minLevel, "LEVEL_RULE");
        memory.setRulePayloadJson(payload);
        memory.setRuleKey(AgentMemoryServiceImpl.computeRuleKey(memory.getApplicableScope(), "LEVEL_RULE", payload));

        return memory;
    }

    // ──────────────── rulePayloadJson 构建 ────────────────

    private String buildNormalizePayload(String oldName, String newName,
                                          Long oldTagId, Long newTagId, String memoryType) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            ObjectNode condition = root.putObject("condition");
            condition.putArray("sourceTerms").add(oldName);
            condition.put("candidateTagId", oldTagId);

            ObjectNode action = root.putObject("action");
            action.put("kind", "NORMALIZE_TO_TAG");
            action.put("targetTagId", newTagId);
            action.put("targetName", newName);

            root.put("reason", "人工确认的标签归一规则: " + oldName + " -> " + newName);
            root.put("ruleStrength", "HARD");

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("构建rulePayloadJson失败", e);
            return null;
        }
    }

    private String buildRejectPayload(String tagName, Long tagId, String memoryType) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            ObjectNode condition = root.putObject("condition");
            condition.putArray("sourceTerms").add(tagName);
            condition.put("candidateTagId", tagId);

            ObjectNode action = root.putObject("action");
            action.put("kind", "REJECT");

            root.put("reason", "人工确认的标签拒绝规则: " + tagName + " 不是有效能力标签");
            root.put("ruleStrength", "HARD");

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("构建rulePayloadJson失败", e);
            return null;
        }
    }

    private String buildLevelRulePayload(String tagName, Long tagId, Integer maxLevel, Integer minLevel, String memoryType) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            ObjectNode condition = root.putObject("condition");
            condition.putArray("sourceTerms").add(tagName);
            condition.put("candidateTagId", tagId);

            ObjectNode action = root.putObject("action");
            action.put("kind", "ENFORCE_LEVEL");
            if (maxLevel != null) {
                action.put("maxLevel", maxLevel);
            }
            if (minLevel != null) {
                action.put("minLevel", minLevel);
            }

            root.put("reason", "人工确认的等级偏好: " + tagName);
            root.put("ruleStrength", "HARD");

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("构建rulePayloadJson失败", e);
            return null;
        }
    }
}
