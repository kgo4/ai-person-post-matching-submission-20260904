package com.example.matching.service.assessment.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.dto.assessment.AssessmentScopeDTO;
import com.example.matching.dto.assessment.AssessmentScopeDTO.AssessmentScopeItem;
import com.example.matching.dto.assessment.AssessmentScopeDTO.UncoveredPostRequirement;
import com.example.matching.entity.ability.PersonAbilityClaim;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.entity.workflow.PersonAbilityClaimGroup;
import com.example.matching.entity.workflow.PersonCapabilityWorkflow;
import com.example.matching.mapper.ability.PersonAbilityClaimMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.mapper.workflow.PersonAbilityClaimGroupMapper;
import com.example.matching.port.post.PostQueryPort;
import com.example.matching.service.assessment.AssessmentScopeService;
import com.example.matching.service.assessment.AssessmentEvidenceLedgerService;
import com.example.matching.service.assessment.CapabilityAssessmentWorkflowService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 评估范围服务实现。
 * <p>
 * 不可变业务规则（见实施计划 §0）：
 * <ul>
 *   <li>简历解析是唯一允许提出候选能力标签的阶段。</li>
 *   <li>一次评估必须绑定一个 postId；岗位能力要求来自服务端数据库，不信任前端或模型复制的 JD。</li>
 *   <li>评估范围包含所有简历声明标签；命中岗位要求时，岗位等级、权重和优先级仅作为核验参照。
 *       岗位未命中不阻止核验，岗位缺失声明仍记录为 uncoveredRequirements，且不写入人员能力事实。</li>
 * </ul>
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssessmentScopeServiceImpl implements AssessmentScopeService {

    private static final String SOURCE_RESUME_PARSE = "RESUME_PARSE";
    private static final String CLAIM_ACTIVE = "ACTIVE";
    private static final String UNCOVERED_REASON = "RESUME_NO_CLAIM";

    private final CapabilityAssessmentWorkflowService workflowService;
    private final PostQueryPort postQueryPort;
    private final PersonAbilityClaimMapper claimMapper;
    private final PersonAbilityClaimGroupMapper claimGroupMapper;
    private final AbilityTagMapper abilityTagMapper;
    private final ObjectMapper objectMapper;
    private AssessmentEvidenceLedgerService evidenceLedgerService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setEvidenceLedgerService(AssessmentEvidenceLedgerService evidenceLedgerService) {
        this.evidenceLedgerService = evidenceLedgerService;
    }

    @Override
    public AssessmentScopeDTO build(Long workflowId, Long empId, Long postId) {
        // 1. 工作流存在 + 员工归属校验
        PersonCapabilityWorkflow workflow = workflowService.getWorkflow(workflowId);
        if (!Objects.equals(workflow.getEmpId(), empId)) {
            throw new IllegalArgumentException("工作流归属员工不匹配: workflow.empId="
                    + workflow.getEmpId() + ", 请求 empId=" + empId);
        }
        // A post is optional context. Resume claims remain the verification scope without one.
        // 2. 岗位能力要求（服务端数据库，单一真相源）
        List<PostQueryPort.PostAbilityDTO> requirements = postId == null ? List.of() : postQueryPort.listRequirementsByPostId(postId);
        if (requirements == null) {
            requirements = List.of();
        }
        Map<Long, PostQueryPort.PostAbilityDTO> requirementByTag = new LinkedHashMap<>();
        for (PostQueryPort.PostAbilityDTO req : requirements) {
            if (req.tagId() != null) {
                requirementByTag.put(req.tagId(), req);
            }
        }

        // 3. 简历 Claim（唯一允许提出候选能力标签的阶段）
        List<PersonAbilityClaim> resumeClaims = claimMapper.selectList(new LambdaQueryWrapper<PersonAbilityClaim>()
                .eq(PersonAbilityClaim::getWorkflowId, workflowId)
                .eq(PersonAbilityClaim::getSourceType, SOURCE_RESUME_PARSE)
                .eq(PersonAbilityClaim::getStatus, CLAIM_ACTIVE)
                .orderByAsc(PersonAbilityClaim::getId));

        // 4. 简历 Claim 的规范标签 ID：优先 claim.tagId，否则从 claim group 的 canonicalTagId 解析
        Map<Long, Long> claimTagIdById = resolveClaimTagIds(workflowId, resumeClaims);
        Map<Long, List<PersonAbilityClaim>> claimsByTag = new LinkedHashMap<>();
        for (PersonAbilityClaim claim : resumeClaims) {
            Long tagId = claimTagIdById.get(claim.getId());
            if (tagId != null) {
                claimsByTag.computeIfAbsent(tagId, k -> new ArrayList<>()).add(claim);
            }
        }

        // 5. 每个简历声明标签都进入核验范围；岗位要求仅补充核验目标，不作为准入条件。
        List<AssessmentScopeItem> items = new ArrayList<>();
        for (Map.Entry<Long, List<PersonAbilityClaim>> e : claimsByTag.entrySet()) {
            Long tagId = e.getKey();
            PostQueryPort.PostAbilityDTO req = requirementByTag.get(tagId);
            items.add(buildItem(tagId, e.getValue(), req));
        }
        items.sort(Comparator.comparing(AssessmentScopeItem::abilityTagId));

        // 6. uncovered 岗位要求（岗位要求了但简历未声明对应标签）
        List<UncoveredPostRequirement> uncovered = new ArrayList<>();
        List<Long> uncoveredTagIds = new ArrayList<>();
        for (Long tagId : requirementByTag.keySet()) {
            if (!claimsByTag.containsKey(tagId)) {
                uncoveredTagIds.add(tagId);
            }
        }
        Map<Long, String> tagNameById = resolveTagNames(uncoveredTagIds);
        for (Long tagId : uncoveredTagIds) {
            PostQueryPort.PostAbilityDTO req = requirementByTag.get(tagId);
            uncovered.add(new UncoveredPostRequirement(
                    req.id(), tagId, tagNameById.getOrDefault(tagId, "能力#" + tagId),
                    req.minRequiredLevel(), UNCOVERED_REASON));
        }
        uncovered.sort(Comparator.comparing(UncoveredPostRequirement::abilityTagId));

        // 7. 稳定排序后计算 scopeHash（幂等复用）
        String scopeHash = computeHash(workflowId, empId, postId, items, uncovered);
        for (AssessmentScopeItem item : items) {
            if (item.assessmentAbilityId() == null) continue;
            PersonAbilityClaimGroup group = claimGroupMapper.selectById(item.assessmentAbilityId());
            if (group == null) continue;
            if (group.getScopeHash() != null && !scopeHash.equals(group.getScopeHash())) {
                throw new IllegalStateException("评估范围已冻结，禁止改变: workflowId=" + workflowId
                        + ", assessmentAbilityId=" + item.assessmentAbilityId());
            }
            if (group.getAssessmentAbilityId() == null) {
                group.setAssessmentAbilityId(group.getId());
            }
                group.setScopeHash(scopeHash);
                claimGroupMapper.updateById(group);
                for (PersonAbilityClaim claim : claimMapper.selectList(new LambdaQueryWrapper<PersonAbilityClaim>()
                        .eq(PersonAbilityClaim::getClaimGroupId, group.getId()))) {
                    claim.setScopeHash(scopeHash);
                    claimMapper.updateById(claim);
                    if (evidenceLedgerService != null) {
                        evidenceLedgerService.record(claim, group.getAssessmentAbilityId(),
                                group.getCanonicalTagId(), null);
                    }
                }
            }

        return new AssessmentScopeDTO(workflowId, empId, postId,
                List.copyOf(items), List.copyOf(uncovered), scopeHash);
    }

    /**
     * 简历 Claim -> 规范标签 ID 映射。
     * <p>
     * 简历 Claim 自身 tagId 可能为空（解析阶段按名称分组，标签 ID 落在 Claim Group 的 canonicalTagId）。
     */
    private Map<Long, Long> resolveClaimTagIds(Long workflowId, List<PersonAbilityClaim> resumeClaims) {
        Map<Long, PersonAbilityClaimGroup> groupById = new HashMap<>();
        List<PersonAbilityClaimGroup> groups = claimGroupMapper.selectList(
                new LambdaQueryWrapper<PersonAbilityClaimGroup>()
                        .eq(PersonAbilityClaimGroup::getWorkflowId, workflowId));
        for (PersonAbilityClaimGroup group : groups) {
            if (group.getId() != null) {
                groupById.put(group.getId(), group);
            }
        }
        Map<Long, Long> result = new HashMap<>();
        for (PersonAbilityClaim claim : resumeClaims) {
            Long tagId = claim.getTagId();
            if (tagId == null && claim.getClaimGroupId() != null) {
                PersonAbilityClaimGroup group = groupById.get(claim.getClaimGroupId());
                if (group != null) {
                    // A resume ability may be new to the global catalog. Its
                    // workflow claim-group ID is the immutable provisional scope ID
                    // until test/interview governance promotes it to a formal tag.
                    tagId = group.getAssessmentAbilityId() != null
                            ? group.getAssessmentAbilityId()
                            : (group.getCanonicalTagId() != null ? group.getCanonicalTagId() : group.getId());
                }
            }
            if (tagId != null) {
                result.put(claim.getId(), tagId);
            }
        }
        return result;
    }

    private AssessmentScopeItem buildItem(Long tagId, List<PersonAbilityClaim> claims,
                                          PostQueryPort.PostAbilityDTO req) {
        String abilityName = null;
        for (PersonAbilityClaim claim : claims) {
            if (claim.getNormalizedAbilityName() != null && !claim.getNormalizedAbilityName().isBlank()) {
                abilityName = claim.getNormalizedAbilityName();
                break;
            }
        }
        if (abilityName == null) {
            abilityName = claims.get(0).getAbilityName();
        }
        List<Long> claimIds = claims.stream()
                .map(PersonAbilityClaim::getId)
                .filter(Objects::nonNull)
                .sorted()
                .toList();
        int claimedLevel = claims.stream()
                .map(PersonAbilityClaim::getClaimedLevel)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(1);
        Long canonicalTagId = resolveCanonicalTagId(claims);
        return new AssessmentScopeItem(
                canonicalTagId != null ? canonicalTagId : tagId,
                claims.get(0).getClaimGroupId() != null ? claims.get(0).getClaimGroupId() : tagId,
                canonicalTagId, abilityName, claimIds, claimedLevel,
                req != null ? req.id() : null,
                req != null ? req.minRequiredLevel() : null,
                req != null && req.isRequired() != null && req.isRequired() == 1,
                req != null && req.isCore() != null && req.isCore() == 1,
                req != null ? req.weight() : null,
                collectEvidenceRefs(claims));
    }

    private Long resolveCanonicalTagId(List<PersonAbilityClaim> claims) {
        for (PersonAbilityClaim claim : claims) {
            if (claim.getTagId() != null) {
                return claim.getTagId();
            }
        }
        return null;
    }

    private List<String> collectEvidenceRefs(List<PersonAbilityClaim> claims) {
        LinkedHashSet<String> refs = new LinkedHashSet<>();
        for (PersonAbilityClaim claim : claims) {
            refs.addAll(parseJsonArray(claim.getSourceRefsJson()));
        }
        return new ArrayList<>(refs);
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<Long, String> resolveTagNames(List<Long> tagIds) {
        Map<Long, String> result = new HashMap<>();
        if (tagIds.isEmpty()) {
            return result;
        }
        for (AbilityTag tag : abilityTagMapper.selectBatchIds(tagIds)) {
            result.put(tag.getId(), tag.getTagName());
        }
        return result;
    }

    private String computeHash(Long workflowId, Long empId, Long postId,
                               List<AssessmentScopeItem> items,
                               List<UncoveredPostRequirement> uncovered) {
        try {
            return CapabilityAssessmentWorkflowServiceImpl.hashInput(
                    String.valueOf(workflowId), String.valueOf(empId), String.valueOf(postId),
                    objectMapper.writeValueAsString(items),
                    objectMapper.writeValueAsString(uncovered));
        } catch (Exception e) {
            throw new IllegalStateException("计算 scopeHash 失败", e);
        }
    }
}
