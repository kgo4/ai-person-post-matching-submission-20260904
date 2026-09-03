package com.example.matching.service.assessment.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.common.enums.EligibilityEnum;
import com.example.matching.common.enums.EvidenceStatusEnum;
import com.example.matching.common.enums.TagResolutionStatusEnum;
import com.example.matching.dto.assessment.ResumeAbilityClaimDTO;
import com.example.matching.entity.ability.PersonAbilityClaim;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.entity.workflow.PersonAbilityClaimGroup;
import com.example.matching.mapper.ability.PersonAbilityClaimMapper;
import com.example.matching.mapper.workflow.PersonAbilityClaimGroupMapper;
import com.example.matching.service.assessment.AbilityEvidenceCollectionService;
import com.example.matching.service.assessment.AssessmentEvidenceLedgerService;
import com.example.matching.service.system.AbilityTagService;
import com.example.matching.service.system.AbilityTagHierarchy;
import com.example.matching.service.governance.GovernanceFilterRuleService;
import com.example.matching.service.governance.GovernanceFilterRuleEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 能力证据收集服务实现
 * <p>
 * 将每阶段输出保存为原始能力证据，不做正式融合。
 * 阶段 1 输出：COLLECTED + DISPLAY_ONLY。
 *
 * @author system
 */
@Slf4j
@Service
public class AbilityEvidenceCollectionServiceImpl implements AbilityEvidenceCollectionService {

    /** 无意义词黑名单：单凭这些词不能构成能力主张 */
    private static final List<String> MEANINGLESS_WORDS = List.of(
            "能力", "技能", "经验", "熟悉", "了解", "掌握", "擅长", "学习", "工作", "项目",
            "相关", "良好", "较好", "优秀", "一定", "一定的基础", "office", "word"
    );

    /** 最低证据文本长度（字符） */
    /** Product/vendor names are evidence context, not transferable abilities. */
    private static final List<String> PRODUCT_ONLY_NAMES = List.of(
            "讯飞星火大模型", "chatgpt", "gpt-4", "gpt-3.5", "claude", "claude code",
            "copilot", "github", "gitlab", "vscode", "visual studio", "intellij idea",
            "navicat", "postman", "ollama", "lm studio", "anythingllm", "openclaw",
            "ksweb", "青龙面板", "alist", "codex"
    );

    private static final int MIN_EVIDENCE_LENGTH = 6;

    private final PersonAbilityClaimMapper claimMapper;
    private final PersonAbilityClaimGroupMapper claimGroupMapper;
    private final AbilityTagService abilityTagService;
    private final GovernanceFilterRuleService governanceFilterRuleService;
    private AssessmentEvidenceLedgerService evidenceLedgerService;

    public AbilityEvidenceCollectionServiceImpl(
            PersonAbilityClaimMapper claimMapper,
            PersonAbilityClaimGroupMapper claimGroupMapper,
            AbilityTagService abilityTagService) {
        this(claimMapper, claimGroupMapper, abilityTagService, null);
    }

    @Autowired
    public AbilityEvidenceCollectionServiceImpl(
            PersonAbilityClaimMapper claimMapper,
            PersonAbilityClaimGroupMapper claimGroupMapper,
            AbilityTagService abilityTagService,
            GovernanceFilterRuleService governanceFilterRuleService) {
        this.claimMapper = claimMapper;
        this.claimGroupMapper = claimGroupMapper;
        this.abilityTagService = abilityTagService;
        this.governanceFilterRuleService = governanceFilterRuleService;
    }

    @Autowired(required = false)
    void setEvidenceLedgerService(AssessmentEvidenceLedgerService evidenceLedgerService) {
        this.evidenceLedgerService = evidenceLedgerService;
    }

    @Override
    @Transactional
    public int saveResumeClaims(Long workflowId, Long stageRunId, Long empId,
                                List<ResumeAbilityClaimDTO> claims, Long operatorId) {
        if (claims == null || claims.isEmpty()) {
            return 0;
        }

        List<ResumeAbilityClaimDTO> uniqueClaims = deduplicateResumeClaims(claims);
        Map<String, ResumeAbilityClaimDTO> existingKeys = findExistingResumeClaimKeys(empId, uniqueClaims);
        int saved = 0;
        List<String> rejected = new ArrayList<>();
        for (ResumeAbilityClaimDTO dto : uniqueClaims) {
            String rejectReason = validateResumeClaim(dto);
            if (rejectReason != null) {
                rejected.add(dto.getAbilityName() + ": " + rejectReason);
                continue;
            }
            String claimKey = resumeClaimKey(dto.getSourceRefId(), dto.getNormalizedAbilityName(), dto.getAbilityName());
            if (existingKeys.containsKey(claimKey)) {
                log.info("简历证据已存在，跳过重复保存: empId={}, sourceRefId={}, ability={}",
                        empId, dto.getSourceRefId(), dto.getAbilityName());
                continue;
            }
            PersonAbilityClaim claim = new PersonAbilityClaim();
            claim.setEmpId(empId);
            claim.setAbilityName(dto.getAbilityName());
            claim.setNormalizedAbilityName(StringUtils.hasText(dto.getNormalizedAbilityName())
                    ? dto.getNormalizedAbilityName() : dto.getAbilityName());
            claim.setClaimedLevel(dto.getClaimedLevel());
            claim.setSourceType("RESUME_PARSE");
            claim.setSourceRefId(dto.getSourceRefId());
            claim.setSourceWeight(BigDecimal.ONE);
            claim.setEvidenceText(dto.getEvidenceText());
            claim.setSourceRefsJson(toJsonArray(dto.getSourceRefs()));
            claim.setConfidenceScore(dto.getConfidenceScore() != null ? dto.getConfidenceScore() : BigDecimal.valueOf(60));
            claim.setStatus("ACTIVE");
            claim.setWorkflowId(workflowId);
            claim.setStageRunId(stageRunId);
            claim.setEvidenceStatus(EvidenceStatusEnum.COLLECTED.getCode());
            claim.setEligibility(EligibilityEnum.DISPLAY_ONLY.getCode());
            claim.setCreatedBy(operatorId);
            claim.setCreatedTime(LocalDateTime.now());
            claim.setUpdatedTime(LocalDateTime.now());
            claim.setVersion(0);
            try {
                claimMapper.insert(claim);
                existingKeys.put(claimKey, dto);
                saved++;
            } catch (DuplicateKeyException e) {
                // 并发重放时由数据库唯一键完成最终仲裁；该条按幂等成功处理，不能阻断后续事件。
                log.info("简历证据并发重复，按幂等处理: empId={}, sourceRefId={}, ability={}",
                        empId, dto.getSourceRefId(), dto.getAbilityName());
            }
        }
        if (!rejected.isEmpty()) {
            log.warn("简历证据校验拒绝 {} 条: {}", rejected.size(), rejected);
        }
        log.info("保存简历能力证据: workflowId={}, input={}, unique={}, saved={}, rejected={}",
                workflowId, claims.size(), uniqueClaims.size(), saved, rejected.size());
        return saved;
    }

    private List<ResumeAbilityClaimDTO> deduplicateResumeClaims(List<ResumeAbilityClaimDTO> claims) {
        Map<String, ResumeAbilityClaimDTO> unique = new LinkedHashMap<>();
        for (ResumeAbilityClaimDTO dto : claims) {
            if (dto == null) {
                continue;
            }
            String key = resumeClaimKey(dto.getSourceRefId(), dto.getNormalizedAbilityName(), dto.getAbilityName());
            ResumeAbilityClaimDTO existing = unique.get(key);
            if (existing == null || isBetterResumeClaim(dto, existing)) {
                unique.put(key, dto);
            }
        }
        return new ArrayList<>(unique.values());
    }

    private boolean isBetterResumeClaim(ResumeAbilityClaimDTO candidate, ResumeAbilityClaimDTO existing) {
        int candidateEvidenceLength = candidate.getEvidenceText() == null ? 0 : candidate.getEvidenceText().length();
        int existingEvidenceLength = existing.getEvidenceText() == null ? 0 : existing.getEvidenceText().length();
        if (candidateEvidenceLength != existingEvidenceLength) {
            return candidateEvidenceLength > existingEvidenceLength;
        }
        BigDecimal candidateConfidence = candidate.getConfidenceScore() == null
                ? BigDecimal.ZERO : candidate.getConfidenceScore();
        BigDecimal existingConfidence = existing.getConfidenceScore() == null
                ? BigDecimal.ZERO : existing.getConfidenceScore();
        return candidateConfidence.compareTo(existingConfidence) > 0;
    }

    private Map<String, ResumeAbilityClaimDTO> findExistingResumeClaimKeys(Long empId,
                                                                            List<ResumeAbilityClaimDTO> claims) {
        if (empId == null || claims.isEmpty()) {
            return new LinkedHashMap<>();
        }
        List<Long> sourceRefIds = claims.stream()
                .map(ResumeAbilityClaimDTO::getSourceRefId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (sourceRefIds.isEmpty()) {
            return new LinkedHashMap<>();
        }
        List<PersonAbilityClaim> existingClaims = claimMapper.selectList(
                new LambdaQueryWrapper<PersonAbilityClaim>()
                        .eq(PersonAbilityClaim::getEmpId, empId)
                        .eq(PersonAbilityClaim::getSourceType, "RESUME_PARSE")
                        .in(PersonAbilityClaim::getSourceRefId, sourceRefIds)
                        .eq(PersonAbilityClaim::getStatus, "ACTIVE"));
        Map<String, ResumeAbilityClaimDTO> result = new LinkedHashMap<>();
        for (PersonAbilityClaim existing : existingClaims) {
            result.put(resumeClaimKey(existing.getSourceRefId(), existing.getNormalizedAbilityName(), existing.getAbilityName()), null);
        }
        return result;
    }

    private String resumeClaimKey(Long sourceRefId, String normalizedName, String abilityName) {
        return String.valueOf(sourceRefId) + "|" + canonicalAbilityName(normalizedName, abilityName);
    }

    private String canonicalAbilityName(String normalizedName, String abilityName) {
        String value = StringUtils.hasText(normalizedName) ? normalizedName : abilityName;
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT);
    }

    /**
     * 简历 Claim 确定性校验。
     *
     * @return 拒绝原因，null 表示通过
     */
    private String validateResumeClaim(ResumeAbilityClaimDTO dto) {
        if (dto == null) {
            return "空主张";
        }
        if (!StringUtils.hasText(dto.getAbilityName())) {
            return "能力名称为空";
        }
        if (dto.getClaimedLevel() == null || dto.getClaimedLevel() < 1 || dto.getClaimedLevel() > 5) {
            return "声明等级非法: " + dto.getClaimedLevel();
        }
        if (!StringUtils.hasText(dto.getEvidenceText())) {
            return "原文证据为空";
        }
        if (dto.getEvidenceText().trim().length() < MIN_EVIDENCE_LENGTH) {
            return "证据文本过短";
        }
        // 句子型、无意义词拒绝
        String trimmed = dto.getAbilityName().trim();
        if (governanceFilterRuleService != null) {
            GovernanceFilterRuleEngine.PersonFilterResult configuredResult =
                    governanceFilterRuleService.evaluatePersonAbility(trimmed);
            if (configuredResult.filtered()) {
                return "命中人员能力过滤规则: " + configuredResult.ruleName();
            }
        }
        if (isProductOnlyAbilityName(trimmed)) {
            return "产品、厂商或具体模型名称不是可核验能力，应提取其体现的具体实践能力";
        }
        if (MEANINGLESS_WORDS.contains(trimmed)) {
            return "无意义能力词";
        }
        // 证据必须能定位到解析后的简历文本
        if (!StringUtils.hasText(dto.getEvidenceLocation())) {
            return "证据无法定位到简历原文";
        }
        // sourceRef 必须有效
        if (dto.getSourceRefId() == null || (dto.getSourceRefs() == null || dto.getSourceRefs().isEmpty())) {
            return "来源引用无效";
        }
        return null;
    }

    /**
     * Reject product/vendor/model names as standalone claims while keeping
     * genuine capabilities such as "大模型应用开发" or "Redis缓存设计" valid.
     */
    static boolean isProductOnlyAbilityName(String abilityName) {
        if (!StringUtils.hasText(abilityName)) {
            return false;
        }
        String normalized = abilityName.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\-_()（）【】\\[\\]：:]+", "");
        return PRODUCT_ONLY_NAMES.stream().map(name -> name.toLowerCase(Locale.ROOT)
                        .replaceAll("[\\s\\-_()（）【】\\[\\]：:]+", ""))
                .anyMatch(normalized::equals)
                || normalized.matches("(讯飞|科大讯飞).{0,12}(星火|大模型)(v?\\d+(\\.\\d+)*)?")
                || normalized.matches("(gpt|chatgpt|claude|copilot).{0,8}v?\\d+(\\.\\d+)*");
    }

    @Override
    @Transactional
    public int saveTestClaims(Long workflowId, Long stageRunId, Long empId,
                              List<PersonAbilityClaim> claims, Long operatorId) {
        return saveGenericClaims(workflowId, stageRunId, empId, claims, "AI_TEST", operatorId);
    }

    @Override
    @Transactional
    public int saveInterviewClaims(Long workflowId, Long stageRunId, Long empId,
                                   List<PersonAbilityClaim> claims, Long operatorId) {
        return saveGenericClaims(workflowId, stageRunId, empId, claims, "AI_INTERVIEW", operatorId);
    }

    private int saveGenericClaims(Long workflowId, Long stageRunId, Long empId,
                                  List<PersonAbilityClaim> claims, String sourceType, Long operatorId) {
        if (claims == null || claims.isEmpty()) {
            return 0;
        }
        int saved = 0;
        Map<String, PersonAbilityClaimGroup> scopeGroups = verificationScopeGroups(workflowId, sourceType);
        for (PersonAbilityClaim src : claims) {
            if (!StringUtils.hasText(src.getAbilityName()) || src.getClaimedLevel() == null
                    || !StringUtils.hasText(src.getEvidenceText())) {
                log.warn("证据校验拒绝（字段缺失）: ability={}", src.getAbilityName());
                continue;
            }
            PersonAbilityClaimGroup scopeGroup = resolveScopeGroup(src, scopeGroups);
            if (!scopeGroups.isEmpty() && scopeGroup == null) {
                log.warn("丢弃越界{}证据: workflowId={}, tagId={}, abilityName={}",
                        sourceType, workflowId, src.getTagId(), src.getAbilityName());
                continue;
            }
            String normalizedName = StringUtils.hasText(src.getNormalizedAbilityName())
                    ? src.getNormalizedAbilityName() : src.getAbilityName();
            // 幂等去重：同工作流 + 同来源 + 同能力名已有 ACTIVE 证据则跳过（事件重放/巡检补偿兜底）
            Long existingCount = claimMapper.selectCount(
                    new LambdaQueryWrapper<PersonAbilityClaim>()
                            .eq(PersonAbilityClaim::getWorkflowId, workflowId)
                            .eq(PersonAbilityClaim::getSourceType, sourceType)
                            .eq(PersonAbilityClaim::getSourceRefId, src.getSourceRefId())
                            .eq(PersonAbilityClaim::getNormalizedAbilityName, normalizedName)
                            .eq(PersonAbilityClaim::getStatus, "ACTIVE"));
            if (existingCount != null && existingCount > 0) {
                log.info("证据已存在，跳过重复保存: workflowId={}, source={}:{}, ability={}",
                        workflowId, sourceType, src.getSourceRefId(), normalizedName);
                continue;
            }
            PersonAbilityClaim claim = new PersonAbilityClaim();
            claim.setEmpId(empId);
            claim.setAbilityName(src.getAbilityName());
            claim.setNormalizedAbilityName(normalizedName);
            claim.setClaimedLevel(src.getClaimedLevel());
            claim.setTagId(src.getTagId());
            claim.setSourceType(sourceType);
            claim.setSourceRefId(src.getSourceRefId());
            claim.setSourceWeight(src.getSourceWeight());
            claim.setEvidenceText(src.getEvidenceText());
            claim.setSourceRefsJson(src.getSourceRefsJson());
            claim.setConfidenceScore(src.getConfidenceScore() != null ? src.getConfidenceScore() : BigDecimal.valueOf(60));
            claim.setStatus("ACTIVE");
            claim.setWorkflowId(workflowId);
            claim.setStageRunId(stageRunId);
            claim.setScopeHash(scopeGroup != null ? scopeGroup.getScopeHash() : null);
            // 保留来源自带的证据状态（如 UNCLASSIFIED_OBSERVATION），未指定时默认 COLLECTED
            claim.setEvidenceStatus(src.getEvidenceStatus() != null
                    ? src.getEvidenceStatus() : EvidenceStatusEnum.COLLECTED.getCode());
            claim.setEligibility(EligibilityEnum.DISPLAY_ONLY.getCode());
            claim.setCreatedBy(operatorId);
            claim.setCreatedTime(LocalDateTime.now());
            claim.setUpdatedTime(LocalDateTime.now());
            claim.setVersion(0);
            claimMapper.insert(claim);
            if (evidenceLedgerService != null && scopeGroup != null) {
                evidenceLedgerService.record(claim, scopeGroup.getAssessmentAbilityId(),
                        scopeGroup.getCanonicalTagId(), null);
            }
            saved++;
        }
        log.info("保存{}证据: workflowId={}, saved={}", sourceType, workflowId, saved);
        return saved;
    }

    private Map<String, PersonAbilityClaimGroup> verificationScopeGroups(Long workflowId, String sourceType) {
        if (workflowId == null || !("AI_TEST".equals(sourceType) || "AI_INTERVIEW".equals(sourceType))) {
            return Map.of();
        }
        Map<String, PersonAbilityClaimGroup> result = new LinkedHashMap<>();
        for (PersonAbilityClaimGroup group : claimGroupMapper.selectList(
                new LambdaQueryWrapper<PersonAbilityClaimGroup>()
                        .eq(PersonAbilityClaimGroup::getWorkflowId, workflowId))) {
            if (group.getNormalizedAbilityName() != null) {
                result.put("N:" + group.getNormalizedAbilityName().trim().toLowerCase(Locale.ROOT), group);
            }
            if (group.getCanonicalTagId() != null) result.put("I:" + group.getCanonicalTagId(), group);
            if (group.getAssessmentAbilityId() != null) result.put("I:" + group.getAssessmentAbilityId(), group);
            if (group.getId() != null) result.put("I:" + group.getId(), group);
        }
        return result;
    }

    private boolean isWithinVerificationScope(PersonAbilityClaim claim,
                                               Map<String, PersonAbilityClaimGroup> groups) {
        return resolveScopeGroup(claim, groups) != null;
    }

    private PersonAbilityClaimGroup resolveScopeGroup(PersonAbilityClaim claim,
                                                      Map<String, PersonAbilityClaimGroup> groups) {
        if (claim.getTagId() != null && groups.containsKey("I:" + claim.getTagId())) {
            return groups.get("I:" + claim.getTagId());
        }
        String name = claim.getNormalizedAbilityName() != null
                ? claim.getNormalizedAbilityName() : claim.getAbilityName();
        return name == null ? null : groups.get("N:" + name.trim().toLowerCase(Locale.ROOT));
    }

    @Override
    @Transactional
    public int groupClaimsByAbility(Long workflowId, Long empId) {
        List<PersonAbilityClaim> claims = listClaimsByWorkflow(workflowId);
        if (claims.isEmpty()) {
            return 0;
        }
        // 复用该工作流已有分组（按 normalized_ability_name），避免每次证据进入都重复建组
        List<PersonAbilityClaimGroup> existingGroups = claimGroupMapper.selectList(
                new LambdaQueryWrapper<PersonAbilityClaimGroup>()
                        .eq(PersonAbilityClaimGroup::getWorkflowId, workflowId));
        Map<String, PersonAbilityClaimGroup> groupByName = new LinkedHashMap<>();
        for (PersonAbilityClaimGroup group : existingGroups) {
            groupByName.put(group.getNormalizedAbilityName(), group);
        }
        // 按标准化能力名称聚合
        Map<String, List<PersonAbilityClaim>> grouped = new LinkedHashMap<>();
        for (PersonAbilityClaim claim : claims) {
            String key = claim.getNormalizedAbilityName() != null
                    ? claim.getNormalizedAbilityName().trim()
                    : claim.getAbilityName().trim();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(claim);
        }
        int created = 0;
        for (Map.Entry<String, List<PersonAbilityClaim>> entry : grouped.entrySet()) {
            PersonAbilityClaim representative = entry.getValue().get(0);
            String abilityName = entry.getKey();
            PersonAbilityClaimGroup group = groupByName.get(abilityName);
            if (group == null) {
                // 标签解析：精确匹配 -> 别名匹配 -> 未解析
                AbilityTag tag = resolveTag(representative.getNormalizedAbilityName());
                group = new PersonAbilityClaimGroup();
                group.setWorkflowId(workflowId);
                group.setEmpId(empId);
                group.setNormalizedAbilityName(abilityName);
                group.setCanonicalTagId(tag != null ? tag.getId() : null);
                group.setTagResolutionStatus(tag != null
                        ? TagResolutionStatusEnum.RESOLVED.getCode()
                        : TagResolutionStatusEnum.TAG_CANDIDATE_PENDING.getCode());
                group.setStatus(EvidenceStatusEnum.COLLECTED.getCode());
                group.setCreatedTime(LocalDateTime.now());
                group.setUpdatedTime(LocalDateTime.now());
                group.setVersion(0);
                claimGroupMapper.insert(group);
                group.setAssessmentAbilityId(group.getId());
                claimGroupMapper.updateById(group);
                created++;
            } else if (EvidenceStatusEnum.COLLECTED.getCode().equals(group.getStatus())) {
                // 复用分组时保持标签解析最新（不改变已推进的审核状态）
                AbilityTag tag = resolveTag(representative.getNormalizedAbilityName());
                if (tag != null && !TagResolutionStatusEnum.RESOLVED.getCode().equals(group.getTagResolutionStatus())) {
                    group.setCanonicalTagId(tag.getId());
                    group.setTagResolutionStatus(TagResolutionStatusEnum.RESOLVED.getCode());
                    group.setUpdatedTime(LocalDateTime.now());
                    claimGroupMapper.updateById(group);
                }
            }
            // 回写 Claim 的 claimGroupId
            for (PersonAbilityClaim claim : entry.getValue()) {
                if (!group.getId().equals(claim.getClaimGroupId())) {
                    claim.setClaimGroupId(group.getId());
                    claimMapper.updateById(claim);
                }
            }
        }
        log.info("能力聚合分组: workflowId={}, groups={}", workflowId, created);
        return created;
    }

    /**
     * 标签解析：精确名称 -> 别名。
     */
    private AbilityTag resolveTag(String abilityName) {
        AbilityTag tag = abilityTagService.findByName(abilityName);
        if (AbilityTagHierarchy.isAssessable(tag)) {
            return tag;
        }
        AbilityTag alias = abilityTagService.findByAlias(abilityName);
        return AbilityTagHierarchy.isAssessable(alias) ? alias : null;
    }

    @Override
    @Transactional
    public void markReadyForAggregateHarness(Long workflowId) {
        List<PersonAbilityClaimGroup> groups = claimGroupMapper.selectList(
                new LambdaQueryWrapper<PersonAbilityClaimGroup>()
                        .eq(PersonAbilityClaimGroup::getWorkflowId, workflowId));
        for (PersonAbilityClaimGroup group : groups) {
            group.setStatus(EvidenceStatusEnum.READY_FOR_AGGREGATE_HARNESS.getCode());
            claimGroupMapper.updateById(group);
        }
        // Claim 同步标记
        List<PersonAbilityClaim> claims = listClaimsByWorkflow(workflowId);
        for (PersonAbilityClaim claim : claims) {
            claim.setEvidenceStatus(EvidenceStatusEnum.READY_FOR_AGGREGATE_HARNESS.getCode());
            claimMapper.updateById(claim);
        }
        log.info("标记聚合审核就绪: workflowId={}, groups={}, claims={}",
                workflowId, groups.size(), claims.size());
    }

    @Override
    public List<PersonAbilityClaim> listClaimsByWorkflow(Long workflowId) {
        return claimMapper.selectList(new LambdaQueryWrapper<PersonAbilityClaim>()
                .eq(PersonAbilityClaim::getWorkflowId, workflowId)
                .orderByAsc(PersonAbilityClaim::getId));
    }

    @Override
    public List<PersonAbilityClaim> listClaimsByGroup(Long claimGroupId) {
        return claimMapper.selectList(new LambdaQueryWrapper<PersonAbilityClaim>()
                .eq(PersonAbilityClaim::getClaimGroupId, claimGroupId)
                .orderByAsc(PersonAbilityClaim::getId));
    }

    private String toJsonArray(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(items.get(i).replace("\"", "\\\"")).append('"');
        }
        return sb.append(']').toString();
    }
}
