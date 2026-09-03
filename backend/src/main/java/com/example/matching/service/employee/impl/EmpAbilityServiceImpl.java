package com.example.matching.service.employee.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.matching.common.enums.AbilityLevelEnum;
import com.example.matching.common.enums.AbilitySourceType;
import com.example.matching.common.enums.EvidenceStatusEnum;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.dto.ability.GovernanceTemplateDTO;
import com.example.matching.dto.employee.EmpAbilitySaveDTO;
import com.example.matching.entity.ability.PersonAbilityClaim;
import com.example.matching.entity.ability.PersonAbilityGovernanceEvent;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.entity.workflow.PersonAbilityClaimGroup;
import com.example.matching.event.AbilityChangeEvent;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.mapper.ability.PersonAbilityClaimMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.mapper.workflow.PersonAbilityClaimGroupMapper;
import com.example.matching.service.ability.AbilityEvidenceIngestionService;
import com.example.matching.service.ability.PersonAbilityGovernanceService;
import com.example.matching.service.employee.EmpAbilityService;
import com.example.matching.utils.SecurityUtils;
import com.example.matching.vo.employee.EmpAbilityProfileVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmpAbilityServiceImpl extends ServiceImpl<EmpAbilityMapper, EmpAbility> implements EmpAbilityService {

    private final EmpEmployeeMapper empEmployeeMapper;
    private final PersonAbilityClaimMapper personAbilityClaimMapper;
    private final AbilityTagMapper abilityTagMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final AbilityEvidenceIngestionService abilityEvidenceIngestionService;
    private final PersonAbilityGovernanceService governanceService;
    private final com.example.matching.converter.employee.EmpEmployeeConverter empEmployeeConverter;
    private final PersonAbilityClaimGroupMapper claimGroupMapper;

    @Override
    @Transactional
    public void saveAbility(EmpAbilitySaveDTO dto) {
        // Persist only canonical values so manual/API writes cannot reintroduce legacy sources.
        dto.setEvaluationSource(AbilitySourceType.canonicalize(dto.getEvaluationSource()));
        if (!StringUtils.hasText(dto.getAbilityName())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR.getCode(), "能力名称不能为空");
        }
        dto.setAbilityName(dto.getAbilityName().trim());

        // 获取当前操作人
        Long operatorId = SecurityUtils.getCurrentUserId();

        // 判断是新增还是更新
        boolean isNew = dto.getId() == null;
        EmpAbility oldAbility = null;

        if (!isNew) {
            oldAbility = getById(dto.getId());
            if (oldAbility == null) {
                throw new BusinessException(ErrorCodeEnum.NOT_FOUND.getCode(), "能力记录不存在");
            }
        }

        // 标签替换必须在覆盖旧 tagId 前完成治理，否则治理服务无法定位原标签。
        if (!isNew && dto.getGovernanceTemplate() != null
                && "TAG_REPLACE".equals(dto.getGovernanceTemplate().getModifyType())) {
            validateGovernanceTemplate(dto.getGovernanceTemplate(), false);
            processGovernance(dto, oldAbility, dto.getGovernanceTemplate(), operatorId);
        }

        // 保存能力记录
        Long abilityId;
        if (isNew) {
            EmpAbility ability = new EmpAbility();
            BeanUtils.copyProperties(dto, ability);
            save(ability);
            abilityId = ability.getId();
        } else {
            EmpAbility ability = oldAbility;
            BeanUtils.copyProperties(dto, ability, "id", "empId");
            updateById(ability);
            abilityId = ability.getId();
        }

        // 处理治理事件和Agent记忆生成
        if (dto.getGovernanceTemplate() != null
                && !"TAG_REPLACE".equals(dto.getGovernanceTemplate().getModifyType())) {
            GovernanceTemplateDTO template = dto.getGovernanceTemplate();

            // 校验治理模板
            validateGovernanceTemplate(template, isNew);

            // 根据修改类型调用相应的治理服务
            processGovernance(dto, oldAbility, template, operatorId);
        } else if (!isNew) {
            // 更新操作但没有治理模板，记录日志
            log.warn("能力更新未提供治理模板: abilityId={}, empId={}", abilityId, dto.getEmpId());
        }

        abilityEvidenceIngestionService.ingestEmployeeAbility(abilityId, "EMP_ABILITY");
        // 发布能力变更事件，触发向量同步
        eventPublisher.publishEvent(new AbilityChangeEvent(this, "EMP_ABILITY", dto.getEmpId()));
    }

    /**
     * 校验治理模板
     */
    private void validateGovernanceTemplate(GovernanceTemplateDTO template, boolean isNew) {
        if (!StringUtils.hasText(template.getModifyType())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR.getCode(), "修改类型不能为空");
        }
        if (!StringUtils.hasText(template.getReason())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR.getCode(), "人工修改能力必须填写修改原因");
        }

        // 根据修改类型校验必填字段
        switch (template.getModifyType()) {
            case "TAG_REPLACE":
                if (template.getNewTagId() == null) {
                    throw new BusinessException(ErrorCodeEnum.PARAM_ERROR.getCode(), "标签替换必须填写新标签ID");
                }
                break;
            case "LEVEL_UP":
            case "LEVEL_DOWN":
                if (template.getNewLevel() == null) {
                    throw new BusinessException(ErrorCodeEnum.PARAM_ERROR.getCode(), "等级修改必须填写新等级");
                }
                if (!StringUtils.hasText(template.getSupportEvidence())) {
                    throw new BusinessException(ErrorCodeEnum.PARAM_ERROR.getCode(), "等级修改必须填写支持证据");
                }
                break;
            case "DELETE_ABILITY":
                if (!StringUtils.hasText(template.getDeleteReason())) {
                    throw new BusinessException(ErrorCodeEnum.PARAM_ERROR.getCode(), "删除能力必须填写删除原因");
                }
                break;
            case "MANUAL_ADD":
                if (!StringUtils.hasText(template.getSupportEvidence())) {
                    throw new BusinessException(ErrorCodeEnum.PARAM_ERROR.getCode(), "新增能力必须填写支持证据");
                }
                break;
            default:
                break;
        }
    }

    /**
     * 处理治理事件和Agent记忆生成
     */
    private void processGovernance(EmpAbilitySaveDTO dto, EmpAbility oldAbility,
                                   GovernanceTemplateDTO template, Long operatorId) {
        String modifyType = template.getModifyType();

        switch (modifyType) {
            case "MANUAL_ADD":
                // 人工新增能力 - 创建新增事件
                createManualAddEvent(dto, template, operatorId);
                break;

            case "TAG_REPLACE":
                // 标签替换
                boolean rememberResumeNameCorrection = AbilitySourceType.RESUME_PARSE
                        .equals(AbilitySourceType.canonicalize(oldAbility != null
                                ? oldAbility.getEvaluationSource() : dto.getEvaluationSource()))
                        && Boolean.TRUE.equals(template.getRememberResumeNameCorrection());
                governanceService.replaceTag(
                        dto.getEmpId(),
                        template.getOldTagId() != null ? template.getOldTagId() : dto.getTagId(),
                        template.getNewTagId(),
                        template.getReason(),
                        operatorId,
                        rememberResumeNameCorrection
                );
                break;

            case "ABILITY_RENAME":
                createAbilityRenameEvent(dto, oldAbility, template, operatorId);
                break;

            case "LEVEL_UP":
            case "LEVEL_DOWN":
                // 等级修改
                governanceService.changeLevel(
                        dto.getEmpId(),
                        dto.getTagId(),
                        template.getNewLevel(),
                        template.getReason(),
                        operatorId
                );
                break;

            case "DELETE_ABILITY":
                // 删除能力
                governanceService.removeTag(
                        dto.getEmpId(),
                        dto.getTagId(),
                        template.getReason(),
                        operatorId
                );
                break;

            case "EVIDENCE_UPDATE":
                // 证据更新 - 创建证据更新事件
                createEvidenceUpdateEvent(dto, template, operatorId);
                break;

            default:
                log.warn("未知的修改类型: {}", modifyType);
                break;
        }
    }

    /**
     * 创建人工新增能力事件
     */
    private void createManualAddEvent(EmpAbilitySaveDTO dto, GovernanceTemplateDTO template, Long operatorId) {
        AbilityTag tag = abilityTagMapper.selectById(dto.getTagId());
        String abilityName = StringUtils.hasText(dto.getAbilityName())
                ? dto.getAbilityName().trim()
                : (tag != null ? tag.getTagName() : null);

        PersonAbilityGovernanceEvent event = new PersonAbilityGovernanceEvent();
        event.setEmpId(dto.getEmpId());
        event.setNewTagId(dto.getTagId());
        event.setNewTagName(abilityName);
        event.setNewLevel(dto.getMasteryLevel());
        event.setModifyType("MANUAL_ADD");
        event.setModifyReason(template.getReason());
        event.setTemplatePayloadJson(toJson(template));
        event.setCreatedBy(operatorId);

        // 保存事件
        governanceService.createEvent(event);

        // 生成Agent记忆草稿
        governanceService.generateAgentMemory(event);

        log.info("人工新增能力: empId={}, tagId={}, abilityName={}, level={}, eventId={}",
                dto.getEmpId(), dto.getTagId(), abilityName, dto.getMasteryLevel(), event.getId());
    }

    /**
     * 创建证据更新事件
     */
    private void createEvidenceUpdateEvent(EmpAbilitySaveDTO dto, GovernanceTemplateDTO template, Long operatorId) {
        AbilityTag tag = abilityTagMapper.selectById(dto.getTagId());
        String abilityName = StringUtils.hasText(dto.getAbilityName())
                ? dto.getAbilityName().trim()
                : (tag != null ? tag.getTagName() : null);

        PersonAbilityGovernanceEvent event = new PersonAbilityGovernanceEvent();
        event.setEmpId(dto.getEmpId());
        event.setOldTagId(dto.getTagId());
        event.setOldTagName(abilityName);
        event.setNewTagId(dto.getTagId());
        event.setNewTagName(abilityName);
        event.setNewLevel(dto.getMasteryLevel());
        event.setModifyType("EVIDENCE_UPDATE");
        event.setModifyReason(template.getReason());
        event.setTemplatePayloadJson(toJson(template));
        event.setCreatedBy(operatorId);

        // 保存事件
        governanceService.createEvent(event);

        // 生成Agent记忆草稿
        governanceService.generateAgentMemory(event);

        log.info("证据更新: empId={}, tagId={}, abilityName={}, eventId={}",
                dto.getEmpId(), dto.getTagId(), abilityName, event.getId());
    }

    /** 记录正式能力名称修改；能力名称可独立于标签库维护。 */
    private void createAbilityRenameEvent(EmpAbilitySaveDTO dto, EmpAbility oldAbility,
                                          GovernanceTemplateDTO template, Long operatorId) {
        PersonAbilityGovernanceEvent event = new PersonAbilityGovernanceEvent();
        event.setEmpId(dto.getEmpId());
        event.setOldTagId(oldAbility != null ? oldAbility.getTagId() : dto.getTagId());
        event.setNewTagId(dto.getTagId());
        event.setOldTagName(template.getOldAbilityName() != null
                ? template.getOldAbilityName() : (oldAbility != null ? oldAbility.getAbilityName() : null));
        event.setNewTagName(template.getNewAbilityName() != null
                ? template.getNewAbilityName() : dto.getAbilityName());
        event.setNewLevel(dto.getMasteryLevel());
        event.setModifyType("ABILITY_RENAME");
        event.setModifyReason(template.getReason());
        event.setTemplatePayloadJson(toJson(template));
        event.setCreatedBy(operatorId);
        governanceService.createEvent(event);
        governanceService.generateAgentMemory(event);
        log.info("人工修改能力名称: empId={}, oldName={}, newName={}, eventId={}",
                dto.getEmpId(), event.getOldTagName(), event.getNewTagName(), event.getId());
    }

    /**
     * 对象转JSON字符串
     */
    private String toJson(Object obj) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("JSON序列化失败", e);
            return "{}";
        }
    }

    @Override
    public EmpAbilityProfileVO getProfile(Long empId) {
        EmpEmployee emp = empEmployeeMapper.selectById(empId);
        if (emp == null) {
            throw new BusinessException(ErrorCodeEnum.EMPLOYEE_NOT_FOUND);
        }

        List<EmpAbility> abilities = list(Wrappers.<EmpAbility>lambdaQuery()
                .eq(EmpAbility::getEmpId, empId)
                .eq(EmpAbility::getIsDeleted, 0)
                .orderByDesc(EmpAbility::getUpdatedTime));

        // M17：DTO 收口——基础字段由 MapStruct 生成的 EmpEmployeeConverter 映射
        EmpAbilityProfileVO vo = empEmployeeConverter.toAbilityProfileVO(emp);

        List<EmpAbilityProfileVO.AbilityDetail> details = abilities.stream().map(a -> {
            EmpAbilityProfileVO.AbilityDetail detail = new EmpAbilityProfileVO.AbilityDetail();
            detail.setTagId(a.getTagId());
            AbilityTag tag = a.getTagId() == null ? null : abilityTagMapper.selectById(a.getTagId());
            detail.setTagName(resolveProfileAbilityName(a, tag));
            detail.setTagCategory(tag != null ? tag.getTagCategory() : "UNKNOWN");
            detail.setMasteryLevel(a.getMasteryLevel());
            detail.setMasteryLevelName(AbilityLevelEnum.getNameByLevel(a.getMasteryLevel()));
            return detail;
        }).collect(Collectors.toList());
        vo.setAbilityDetails(details);

        // 综合能力评分 = 各能力等级均值 * 20
        if (!details.isEmpty()) {
            double avg = details.stream().mapToInt(EmpAbilityProfileVO.AbilityDetail::getMasteryLevel).average().orElse(0);
            vo.setOverallScore(BigDecimal.valueOf(avg * 20).setScale(2, RoundingMode.HALF_UP));
        } else {
            vo.setOverallScore(BigDecimal.ZERO);
        }

        return vo;
    }

    static String resolveProfileAbilityName(EmpAbility ability, AbilityTag tag) {
        if (ability != null && StringUtils.hasText(ability.getAbilityName())) {
            return ability.getAbilityName();
        }
        if (tag != null && StringUtils.hasText(tag.getTagName())) {
            return tag.getTagName();
        }
        // 能力名称是正式表的权威字段；历史脏数据不再伪造占位名称。
        return "";
    }

    @Override
    public List<EmpAbility> listByEmpId(Long empId) {
        return list(Wrappers.<EmpAbility>lambdaQuery()
                .eq(EmpAbility::getEmpId, empId)
                .eq(EmpAbility::getIsDeleted, 0)
                .orderByDesc(EmpAbility::getUpdatedTime));
    }

    @Override
    public List<PersonAbilityClaim> listPendingClaims(Long empId) {
        // 1) 旧准入链路：status=PENDING_HARNESS_REVIEW 的能力主张
        List<PersonAbilityClaim> legacyPending = personAbilityClaimMapper.selectList(
                Wrappers.<PersonAbilityClaim>lambdaQuery()
                        .eq(PersonAbilityClaim::getEmpId, empId)
                        .eq(PersonAbilityClaim::getStatus, "PENDING_HARNESS_REVIEW")
                        .orderByDesc(PersonAbilityClaim::getCreatedTime));

        // 2) 评估工作流：被聚合 Harness 卡住（REVIEW/BLOCK）或已通过待等级确认（PASS）的能力组。
        //    评估链路在 person_ability_claim 上仅维护 status=ACTIVE + evidence_status，
        //    真正的 Harness 结论在 person_ability_claim_group.status 上，此处一并纳入并推导标识。
        List<PersonAbilityClaimGroup> groups = claimGroupMapper.selectList(
                Wrappers.<PersonAbilityClaimGroup>lambdaQuery()
                        .eq(PersonAbilityClaimGroup::getEmpId, empId)
                        .in(PersonAbilityClaimGroup::getStatus,
                                EvidenceStatusEnum.PENDING_MANUAL_REVIEW.getCode(),
                                EvidenceStatusEnum.READY_FOR_AGGREGATE_HARNESS.getCode(),
                                EvidenceStatusEnum.BLOCKED.getCode()));

        Map<Long, PersonAbilityClaim> merged = new LinkedHashMap<>();
        legacyPending.forEach(c -> merged.put(c.getId(), c));

        if (!groups.isEmpty()) {
            List<Long> groupIds = groups.stream()
                    .map(PersonAbilityClaimGroup::getId)
                    .collect(Collectors.toList());
            Map<Long, String> groupDecision = groups.stream().collect(Collectors.toMap(
                    PersonAbilityClaimGroup::getId,
                    g -> groupStatusToHarnessDecision(g.getStatus()),
                    (a, b) -> a));
            List<PersonAbilityClaim> workflowClaims = personAbilityClaimMapper.selectList(
                    Wrappers.<PersonAbilityClaim>lambdaQuery()
                            .in(PersonAbilityClaim::getClaimGroupId, groupIds));
            for (PersonAbilityClaim claim : workflowClaims) {
                // 评估链路不回写 claim.harness_decision，按聚合组状态推导展示标识
                String decision = groupDecision.get(claim.getClaimGroupId());
                if (decision != null) {
                    claim.setHarnessDecision(decision);
                }
                merged.putIfAbsent(claim.getId(), claim);
            }
        }

        return merged.values().stream()
                .sorted(Comparator.comparing(PersonAbilityClaim::getCreatedTime,
                        Comparator.nullsFirst(Comparator.naturalOrder())).reversed())
                .collect(Collectors.toList());
    }

    /**
     * 将聚合组状态映射为前端 Harness 决策标识（PASS/REVIEW/BLOCK）。
     */
    private String groupStatusToHarnessDecision(String groupStatus) {
        if (EvidenceStatusEnum.PENDING_MANUAL_REVIEW.getCode().equals(groupStatus)) {
            return "REVIEW";
        }
        if (EvidenceStatusEnum.BLOCKED.getCode().equals(groupStatus)) {
            return "BLOCK";
        }
        if (EvidenceStatusEnum.READY_FOR_AGGREGATE_HARNESS.getCode().equals(groupStatus)) {
            return "PASS";
        }
        return null;
    }

    @Override
    @Transactional
    public void batchSave(List<EmpAbilitySaveDTO> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        for (EmpAbilitySaveDTO dto : list) {
            saveAbility(dto);
        }
    }
}
