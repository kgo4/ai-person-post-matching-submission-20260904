package com.example.matching.ai.context.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.ai.context.dto.*;
import com.example.matching.ai.context.service.*;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.entity.contest.ContestEvidenceItem;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.post.PostPost;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.contest.ContestEvidenceItemMapper;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.mapper.matching.MatchingRecordMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.mapper.post.PostPostMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.service.kg.KnowledgeGraphQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI上下文包服务实现
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiContextPackageServiceImpl implements AiContextPackageService {

    private final MatchingRecordMapper matchingRecordMapper;
    private final EmpEmployeeMapper empEmployeeMapper;
    private final com.example.matching.port.employee.EmployeeAbilityReadPort employeeAbilityReadPort;
    private final PostPostMapper postPostMapper;
    private final PostAbilityModelMapper postAbilityModelMapper;
    private final AbilityTagMapper abilityTagMapper;
    private final ContestEvidenceItemMapper evidenceItemMapper;

    private final AiContextSourceRefService sourceRefService;
    private final AiContextCompressorService compressorService;
    private final AiContextSnapshotService snapshotService;
    private final KnowledgeGraphQueryService knowledgeGraphQueryService;

    @Override
    public AiContextPackageDTO buildForMatching(Long matchingRecordId) {
        // 1. 加载匹配记录
        MatchingRecord record = matchingRecordMapper.selectById(matchingRecordId);
        if (record == null) {
            throw new BusinessException(ErrorCodeEnum.MATCHING_RECORD_NOT_FOUND);
        }

        Long empId = record.getEmpId();
        Long postId = record.getPostId();

        // 2. 加载员工和岗位信息
        EmpEmployee emp = empEmployeeMapper.selectById(empId);
        PostPost post = postPostMapper.selectById(postId);

        // 3. 加载员工能力（方案第八章：权威源统一——person_ability_profile 优先，回退 emp_ability）
        List<com.example.matching.dto.matching.MatchingAbilitySnapshot> empAbilitySnapshots =
                employeeAbilityReadPort.loadAuthoritativeAbilities(List.of(empId))
                        .getOrDefault(empId, List.of());

        // 4. 加载岗位要求
        LambdaQueryWrapper<PostAbilityModel> postAbilityWrapper = new LambdaQueryWrapper<>();
        postAbilityWrapper.eq(PostAbilityModel::getPostId, postId)
                .eq(PostAbilityModel::getIsDeleted, 0);
        List<PostAbilityModel> postRequirements = postAbilityModelMapper.selectList(postAbilityWrapper);

        // 5. 加载能力标签
        Set<Long> tagIds = new HashSet<>();
        empAbilitySnapshots.stream().map(com.example.matching.dto.matching.MatchingAbilitySnapshot::tagId)
                .filter(Objects::nonNull).forEach(tagIds::add);
        postRequirements.stream().map(PostAbilityModel::getTagId)
                .filter(Objects::nonNull).forEach(tagIds::add);

        Map<Long, AbilityTag> tagMap = new HashMap<>();
        if (!tagIds.isEmpty()) {
            LambdaQueryWrapper<AbilityTag> tagWrapper = new LambdaQueryWrapper<>();
            tagWrapper.in(AbilityTag::getId, tagIds)
                    .eq(AbilityTag::getIsDeleted, 0);
            List<AbilityTag> tags = abilityTagMapper.selectList(tagWrapper);
            tagMap = tags.stream().collect(Collectors.toMap(AbilityTag::getId, t -> t, (a, b) -> a));
        }

        // 6. 加载证据 - 优先按员工能力ID查询，再补充少量岗位/公共证据
        //    （仅 emp_ability 来源具备能力记录 ID；融合画像能力按 tagId 关联公共证据）
        Set<Long> empAbilityIds = empAbilitySnapshots.stream()
                .map(com.example.matching.dto.matching.MatchingAbilitySnapshot::abilityId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> empAbilityTagIds = empAbilitySnapshots.stream()
                .map(com.example.matching.dto.matching.MatchingAbilitySnapshot::tagId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        List<ContestEvidenceItem> evidences = new ArrayList<>();
        if (!empAbilityIds.isEmpty()) {
            // 查询直接关联员工能力的证据
            LambdaQueryWrapper<ContestEvidenceItem> evidenceWrapper = new LambdaQueryWrapper<>();
            evidenceWrapper.eq(ContestEvidenceItem::getTargetType, "EMP_ABILITY")
                    .in(ContestEvidenceItem::getTargetRefId, empAbilityIds)
                    .eq(ContestEvidenceItem::getIsDeleted, 0);
            evidences = evidenceItemMapper.selectList(evidenceWrapper);
        }
        // 补充少量同标签的公共证据（排除已有的）
        if (!empAbilityTagIds.isEmpty() && evidences.size() < 30) {
            Set<Long> existingIds = evidences.stream().map(ContestEvidenceItem::getId).collect(Collectors.toSet());
            LambdaQueryWrapper<ContestEvidenceItem> publicWrapper = new LambdaQueryWrapper<>();
            publicWrapper.in(ContestEvidenceItem::getTagId, empAbilityTagIds)
                    .eq(ContestEvidenceItem::getIsDeleted, 0)
                    .notIn(!existingIds.isEmpty(), ContestEvidenceItem::getId, existingIds)
                    .orderByDesc(ContestEvidenceItem::getCredibilityScore)
                    .last("LIMIT 10");
            List<ContestEvidenceItem> publicEvidences = evidenceItemMapper.selectList(publicWrapper);
            evidences.addAll(publicEvidences);
        }

        // 7. 构建上下文包
        AiContextPackageDTO context = new AiContextPackageDTO();
        context.setScenario("MATCHING_ANALYSIS");
        context.setMatchingRecordId(matchingRecordId);
        context.setMatchScore(record.getFinalMatchScore() != null ? record.getFinalMatchScore() : record.getAiMatchScore());

        // 员工信息
        context.setEmpId(empId);
        if (emp != null) {
            context.setEmpName(emp.getRealName());
            context.setEmpCode(emp.getEmpCode());
            context.setEmpLevel(emp.getLevel());
        }

        // 岗位信息
        context.setPostId(postId);
        if (post != null) {
            context.setPostName(post.getPostName());
            context.setPostCode(post.getPostCode());
            context.setPostLevel(post.getPostLevel());
        }

        // 员工能力列表
        List<AiContextAbilityDTO> employeeAbilities = new ArrayList<>();
        List<AiContextSourceRefDTO> sourceRefs = new ArrayList<>();

        // 按tagId分组，取最高等级（与匹配引擎一致）
        Map<Long, com.example.matching.dto.matching.MatchingAbilitySnapshot> bestAbilityByTag =
                new LinkedHashMap<>();
        for (com.example.matching.dto.matching.MatchingAbilitySnapshot snapshot : empAbilitySnapshots) {
            Long abilityKey = snapshot.tagId() != null ? snapshot.tagId() : snapshot.abilityId();
            if (abilityKey == null) continue;
            com.example.matching.dto.matching.MatchingAbilitySnapshot existing =
                    bestAbilityByTag.get(abilityKey);
            int level = snapshot.level() != null ? snapshot.level() : 0;
            int existingLevel = existing != null && existing.level() != null ? existing.level() : 0;
            if (existing == null || level > existingLevel) {
                bestAbilityByTag.put(abilityKey, snapshot);
            }
        }

        for (com.example.matching.dto.matching.MatchingAbilitySnapshot ability : selectContextAbilities(empAbilitySnapshots)) {
            AbilityTag tag = tagMap.get(ability.tagId());
            AiContextAbilityDTO abilityDTO = new AiContextAbilityDTO();
            abilityDTO.setAbilityTagId(ability.tagId());
            String abilityName = firstNonBlank(ability.abilityName(), tag != null ? tag.getTagName() : null, null);
            if (abilityName == null) {
                log.warn("跳过无有效名称的人员能力: empId={}, abilityId={}, tagId={}",
                        empId, ability.abilityId(), ability.tagId());
                continue;
            }
            abilityDTO.setAbilityName(abilityName);
            abilityDTO.setCurrentLevel(ability.level());
            abilityDTO.setSource(ability.sourceType());
            abilityDTO.setCredibility(ability.confidence() != null ?
                    ability.confidence().multiply(new BigDecimal("100")) : null);
            employeeAbilities.add(abilityDTO);

            // 生成来源引用（画像来源无 emp_ability 记录 ID 时使用画像引用）
            AiContextSourceRefDTO ref = toSourceRef(ability, empId, tag);
            sourceRefs.add(ref);
            abilityDTO.setSourceRefs(List.of(ref.getRef()));
        }
        context.setEmployeeAbilities(employeeAbilities);

        // 岗位要求列表
        List<AiContextAbilityDTO> postRequirementDTOs = new ArrayList<>();
        for (PostAbilityModel requirement : postRequirements) {
            AbilityTag tag = tagMap.get(requirement.getTagId());
            String requirementName = firstNonBlank(requirement.getAbilityName(),
                    tag != null ? tag.getTagName() : null, null);
            if (requirementName == null) {
                log.warn("跳过无有效名称的岗位能力: postId={}, modelId={}, tagId={}",
                        requirement.getPostId(), requirement.getId(), requirement.getTagId());
                continue;
            }
            AiContextAbilityDTO reqDTO = new AiContextAbilityDTO();
            reqDTO.setAbilityTagId(requirement.getTagId());
            reqDTO.setAbilityName(requirementName);
            reqDTO.setRequiredLevel(requirement.getMinRequiredLevel());
            reqDTO.setWeight(requirement.getWeight());
            reqDTO.setRequired(requirement.getIsRequired() != null && requirement.getIsRequired() == 1);
            reqDTO.setCore(requirement.getIsCore() != null && requirement.getIsCore() == 1);
            postRequirementDTOs.add(reqDTO);

            // 生成来源引用
            AiContextSourceRefDTO ref = sourceRefService.fromPostAbilityModel(requirement, tag);
            sourceRefs.add(ref);
            reqDTO.setSourceRefs(List.of(ref.getRef()));
        }
        context.setPostRequirements(postRequirementDTOs);

        // 能力差距列表
        List<AiContextGapDTO> gaps = buildGaps(postRequirements, bestAbilityByTag, tagMap, sourceRefs);
        context.setGaps(gaps);

        // 证据列表
        List<AiContextEvidenceDTO> evidenceDTOs = new ArrayList<>();
        for (ContestEvidenceItem evidence : evidences) {
            AiContextEvidenceDTO evidenceDTO = new AiContextEvidenceDTO();
            evidenceDTO.setEvidenceId(evidence.getId());
            evidenceDTO.setEvidenceCode(evidence.getEvidenceCode());
            evidenceDTO.setSourceType(evidence.getSourceType());
            evidenceDTO.setSourceTitle(evidence.getSourceTitle());
            evidenceDTO.setSourceSnippet(truncate(evidence.getSourceText(), 200));
            evidenceDTO.setAbilityName(evidence.getAbilityName());
            evidenceDTO.setTagId(evidence.getTagId());
            evidenceDTO.setConfidenceScore(evidence.getConfidenceScore());
            evidenceDTO.setCredibilityScore(evidence.getCredibilityScore());
            evidenceDTO.setEvidenceStatus(evidence.getEvidenceStatus());

            // 生成来源引用
            AiContextSourceRefDTO ref = sourceRefService.fromEvidence(evidence);
            evidenceDTO.setSourceRef(ref.getRef());
            sourceRefs.add(ref);
            evidenceDTOs.add(evidenceDTO);
        }
        context.setEvidences(evidenceDTOs);

        // 评分明细
        context.setScoreBreakdown(buildScoreBreakdown(record));

        // 风险信号（弱证据风险基于权威能力快照来源统计）
        context.setRiskSignals(buildRiskSignals(gaps, empAbilitySnapshots, evidences, record));

        // 图谱摘要 - 从知识图谱获取能力差距路径并压缩
        context.setGraphSummary(buildGraphSummary(empId, postId));

        // 匹配记录来源引用（先add再set，确保包含在列表中）
        AiContextSourceRefDTO matchingRef = sourceRefService.fromMatchingRecord(record);
        sourceRefs.add(matchingRef);

        // 来源引用
        context.setSourceRefs(sourceRefs);

        // 8. 压缩上下文包
        AiContextPackageDTO compressed = compressorService.compress(context);

        // 9. 保存快照
        snapshotService.saveSnapshot(compressed);

        log.info("构建AI上下文包: matchingRecordId={}, empId={}, postId={}, gaps={}, evidences={}, tokens={}",
                matchingRecordId, empId, postId, gaps.size(), evidenceDTOs.size(), compressed.getTokenEstimate());

        return compressed;
    }

    @Override
    public AiContextPackageDTO buildForEmployee(Long empId) {
        // 加载员工信息
        EmpEmployee emp = empEmployeeMapper.selectById(empId);
        if (emp == null) {
            throw new BusinessException(ErrorCodeEnum.EMPLOYEE_NOT_FOUND);
        }

        // 加载员工能力（方案第八章：权威源统一——画像优先回退 emp_ability）
        List<com.example.matching.dto.matching.MatchingAbilitySnapshot> empAbilitySnapshots =
                employeeAbilityReadPort.loadAuthoritativeAbilities(List.of(empId))
                        .getOrDefault(empId, List.of());

        // 加载能力标签
        Set<Long> tagIds = empAbilitySnapshots.stream()
                .map(com.example.matching.dto.matching.MatchingAbilitySnapshot::tagId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, AbilityTag> tagMap = new HashMap<>();
        if (!tagIds.isEmpty()) {
            LambdaQueryWrapper<AbilityTag> tagWrapper = new LambdaQueryWrapper<>();
            tagWrapper.in(AbilityTag::getId, tagIds).eq(AbilityTag::getIsDeleted, 0);
            List<AbilityTag> tags = abilityTagMapper.selectList(tagWrapper);
            tagMap = tags.stream().collect(Collectors.toMap(AbilityTag::getId, t -> t, (a, b) -> a));
        }

        // 构建上下文
        AiContextPackageDTO context = new AiContextPackageDTO();
        context.setScenario("EMPLOYEE_PROFILE");
        context.setEmpId(empId);
        context.setEmpName(emp.getRealName());
        context.setEmpCode(emp.getEmpCode());
        context.setEmpLevel(emp.getLevel());

        // 员工能力列表
        List<AiContextAbilityDTO> employeeAbilities = new ArrayList<>();
        List<AiContextSourceRefDTO> sourceRefs = new ArrayList<>();
        Map<Long, com.example.matching.dto.matching.MatchingAbilitySnapshot> bestAbilityByTag =
                new LinkedHashMap<>();
        for (com.example.matching.dto.matching.MatchingAbilitySnapshot snapshot : empAbilitySnapshots) {
            Long abilityKey = snapshot.tagId() != null ? snapshot.tagId() : snapshot.abilityId();
            if (abilityKey == null) continue;
            com.example.matching.dto.matching.MatchingAbilitySnapshot existing =
                    bestAbilityByTag.get(abilityKey);
            int level = snapshot.level() != null ? snapshot.level() : 0;
            int existingLevel = existing != null && existing.level() != null ? existing.level() : 0;
            if (existing == null || level > existingLevel) {
                bestAbilityByTag.put(abilityKey, snapshot);
            }
        }

        for (com.example.matching.dto.matching.MatchingAbilitySnapshot ability : selectContextAbilities(empAbilitySnapshots)) {
            AbilityTag tag = tagMap.get(ability.tagId());
            AiContextAbilityDTO abilityDTO = new AiContextAbilityDTO();
            abilityDTO.setAbilityTagId(ability.tagId());
            String abilityName = firstNonBlank(ability.abilityName(), tag != null ? tag.getTagName() : null, null);
            if (abilityName == null) {
                log.warn("跳过无有效名称的人员能力: empId={}, abilityId={}, tagId={}",
                        empId, ability.abilityId(), ability.tagId());
                continue;
            }
            abilityDTO.setAbilityName(abilityName);
            abilityDTO.setCurrentLevel(ability.level());
            abilityDTO.setSource(ability.sourceType());
            employeeAbilities.add(abilityDTO);

            AiContextSourceRefDTO ref = toSourceRef(ability, empId, tag);
            sourceRefs.add(ref);
            abilityDTO.setSourceRefs(List.of(ref.getRef()));
        }
        context.setEmployeeAbilities(employeeAbilities);
        context.setSourceRefs(sourceRefs);

        AiContextPackageDTO compressed = compressorService.compress(context);
        snapshotService.saveSnapshot(compressed);
        return compressed;
    }

    @Override
    public AiContextPackageDTO buildForPost(Long postId) {
        // 加载岗位信息
        PostPost post = postPostMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException(ErrorCodeEnum.POST_NOT_FOUND);
        }

        // 加载岗位要求
        LambdaQueryWrapper<PostAbilityModel> postAbilityWrapper = new LambdaQueryWrapper<>();
        postAbilityWrapper.eq(PostAbilityModel::getPostId, postId)
                .eq(PostAbilityModel::getIsDeleted, 0);
        List<PostAbilityModel> postRequirements = postAbilityModelMapper.selectList(postAbilityWrapper);

        // 加载能力标签
        Set<Long> tagIds = postRequirements.stream().map(PostAbilityModel::getTagId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, AbilityTag> tagMap = new HashMap<>();
        if (!tagIds.isEmpty()) {
            LambdaQueryWrapper<AbilityTag> tagWrapper = new LambdaQueryWrapper<>();
            tagWrapper.in(AbilityTag::getId, tagIds).eq(AbilityTag::getIsDeleted, 0);
            List<AbilityTag> tags = abilityTagMapper.selectList(tagWrapper);
            tagMap = tags.stream().collect(Collectors.toMap(AbilityTag::getId, t -> t, (a, b) -> a));
        }

        // 构建上下文
        AiContextPackageDTO context = new AiContextPackageDTO();
        context.setScenario("POST_PROFILE");
        context.setPostId(postId);
        context.setPostName(post.getPostName());
        context.setPostCode(post.getPostCode());
        context.setPostLevel(post.getPostLevel());

        // 岗位要求列表
        List<AiContextAbilityDTO> postRequirementDTOs = new ArrayList<>();
        List<AiContextSourceRefDTO> sourceRefs = new ArrayList<>();
        for (PostAbilityModel requirement : postRequirements) {
            AbilityTag tag = tagMap.get(requirement.getTagId());
            AiContextAbilityDTO reqDTO = new AiContextAbilityDTO();
            reqDTO.setAbilityTagId(requirement.getTagId());
            String requirementName = firstNonBlank(requirement.getAbilityName(), tag != null ? tag.getTagName() : null, null);
            if (requirementName == null) {
                log.warn("跳过无有效名称的岗位能力: postId={}, modelId={}, tagId={}",
                        requirement.getPostId(), requirement.getId(), requirement.getTagId());
                continue;
            }
            reqDTO.setAbilityName(requirementName);
            reqDTO.setRequiredLevel(requirement.getMinRequiredLevel());
            reqDTO.setWeight(requirement.getWeight());
            reqDTO.setRequired(requirement.getIsRequired() != null && requirement.getIsRequired() == 1);
            reqDTO.setCore(requirement.getIsCore() != null && requirement.getIsCore() == 1);
            postRequirementDTOs.add(reqDTO);

            AiContextSourceRefDTO ref = sourceRefService.fromPostAbilityModel(requirement, tag);
            sourceRefs.add(ref);
            reqDTO.setSourceRefs(List.of(ref.getRef()));
        }
        context.setPostRequirements(postRequirementDTOs);
        context.setSourceRefs(sourceRefs);

        AiContextPackageDTO compressed = compressorService.compress(context);
        snapshotService.saveSnapshot(compressed);
        return compressed;
    }

    @Override
    public AiContextPackageDTO buildForLearningPath(Long matchingRecordId) {
        // 复用匹配上下文，增加学习路径相关信息
        AiContextPackageDTO context = buildForMatching(matchingRecordId);
        context.setScenario("LEARNING_PATH");
        return context;
    }

    private List<AiContextGapDTO> buildGaps(List<PostAbilityModel> postRequirements,
                                             Map<Long, com.example.matching.dto.matching.MatchingAbilitySnapshot> empAbilityByTag,
                                             Map<Long, AbilityTag> tagMap,
                                             List<AiContextSourceRefDTO> sourceRefs) {
        List<AiContextGapDTO> gaps = new ArrayList<>();

        for (PostAbilityModel requirement : postRequirements) {
            Long tagId = requirement.getTagId();
            com.example.matching.dto.matching.MatchingAbilitySnapshot empAbility = empAbilityByTag.get(tagId);
            if (empAbility == null && requirement.getAbilityName() != null) {
                String targetName = normalizeAbilityName(requirement.getAbilityName());
                empAbility = empAbilityByTag.values().stream()
                        .filter(a -> a != null && normalizeAbilityName(a.abilityName()).equals(targetName))
                        .findFirst().orElse(null);
            }
            AbilityTag tag = tagMap.get(tagId);

            int currentLevel = empAbility != null && empAbility.level() != null ?
                    empAbility.level() : 0;
            int requiredLevel = requirement.getMinRequiredLevel() != null ?
                    requirement.getMinRequiredLevel() : 3;
            boolean isCore = requirement.getIsCore() != null && requirement.getIsCore() == 1;

            // 只有存在差距时才添加
            if (currentLevel < requiredLevel) {
                String requirementName = firstNonBlank(requirement.getAbilityName(),
                        tag != null ? tag.getTagName() : null, null);
                if (requirementName == null) {
                    log.warn("跳过无有效名称的能力差距: postId={}, modelId={}, tagId={}",
                            requirement.getPostId(), requirement.getId(), requirement.getTagId());
                    continue;
                }
                AiContextGapDTO gap = new AiContextGapDTO();
                gap.setAbilityTagId(tagId);
                // 岗位能力名称独立于标签库，禁止用 null tagId 拼接伪名称。
                gap.setAbilityName(requirementName);
                gap.setCurrentLevel(currentLevel);
                gap.setRequiredLevel(requiredLevel);
                gap.setGap(requiredLevel - currentLevel);
                gap.setGapType(currentLevel == 0 ? "MISSING" : "LEVEL_GAP");
                gap.setPriority(calculatePriority(currentLevel, requiredLevel, isCore));
                gap.setCore(isCore);

                // 来源引用
                List<String> refs = new ArrayList<>();
                if (empAbility != null && empAbility.abilityId() != null) {
                    refs.add("fact:EMP_ABILITY:" + empAbility.abilityId());
                }
                refs.add("fact:POST_ABILITY_MODEL:" + requirement.getId());
                gap.setSourceRefs(refs);

                gaps.add(gap);
            }
        }

        return gaps;
    }

    private String calculatePriority(int currentLevel, int requiredLevel, boolean isCore) {
        int gap = requiredLevel - currentLevel;
        if (isCore && gap >= 2) return "HIGH";
        if (gap >= 2) return "HIGH";
        if (gap >= 1) return "MEDIUM";
        if (isCore) return "MEDIUM";
        return "LOW";
    }

    private List<AiContextScoreBreakdownDTO> buildScoreBreakdown(MatchingRecord record) {
        List<AiContextScoreBreakdownDTO> breakdown = new ArrayList<>();

        if (record.getPostModelScore() != null) {
            AiContextScoreBreakdownDTO dto = new AiContextScoreBreakdownDTO();
            dto.setDimension("能力模型分");
            dto.setScore(record.getPostModelScore());
            dto.setDescription("标签命中、等级、权重、必填/核心能力的逐项加权分");
            breakdown.add(dto);
        }

        if (record.getVectorScore() != null) {
            AiContextScoreBreakdownDTO dto = new AiContextScoreBreakdownDTO();
            dto.setDimension("向量语义分");
            dto.setScore(record.getVectorScore());
            dto.setDescription("整人×整岗向量语义相似度");
            breakdown.add(dto);
        }

        if (record.getAiScore() != null || record.getLlmScore() != null) {
            AiContextScoreBreakdownDTO dto = new AiContextScoreBreakdownDTO();
            dto.setDimension("AI建议分");
            dto.setScore(record.getAiScore() != null ? record.getAiScore() : record.getLlmScore());
            dto.setDescription("大模型结合证据生成的建议分");
            breakdown.add(dto);
        }

        return breakdown;
    }

    private List<AiContextRiskSignalDTO> buildRiskSignals(List<AiContextGapDTO> gaps,
                                                           List<com.example.matching.dto.matching.MatchingAbilitySnapshot> empAbilities,
                                                           List<ContestEvidenceItem> evidences,
                                                           MatchingRecord record) {
        List<AiContextRiskSignalDTO> risks = new ArrayList<>();

        // 1. 核心能力缺口风险
        for (AiContextGapDTO gap : gaps) {
            if (Boolean.TRUE.equals(gap.getCore()) && gap.getGap() >= 2) {
                AiContextRiskSignalDTO risk = new AiContextRiskSignalDTO();
                risk.setRiskType("CORE_ABILITY_GAP");
                risk.setRiskLevel("HIGH");
                risk.setMessage("岗位核心能力「" + gap.getAbilityName() + "」存在较大差距 (L" +
                        gap.getCurrentLevel() + " -> L" + gap.getRequiredLevel() + ")");
                risk.setSourceRefs(gap.getSourceRefs());
                risks.add(risk);
            }
        }

        // 2. 弱证据风险
        Map<String, Long> sourceTypeCounts = empAbilities.stream()
                .filter(a -> a.sourceType() != null)
                .collect(Collectors.groupingBy(
                        com.example.matching.dto.matching.MatchingAbilitySnapshot::sourceType,
                        Collectors.counting()));

        boolean onlyResumeParse = sourceTypeCounts.size() == 1 &&
                sourceTypeCounts.containsKey("RESUME_PARSE");
        if (onlyResumeParse) {
            AiContextRiskSignalDTO risk = new AiContextRiskSignalDTO();
            risk.setRiskType("WEAK_EVIDENCE");
            risk.setRiskLevel("MEDIUM");
            risk.setMessage("员工能力主要来自简历解析，缺少项目或面试证据");
            risks.add(risk);
        }

        // 3. AI自证据风险
        long aiGeneratedEvidence = evidences.stream()
                .filter(e -> "AI_GENERATED".equals(e.getSourceType()) ||
                        "RAG_SUMMARY".equals(e.getSourceType()) ||
                        "AI_CANDIDATE".equals(e.getSourceType()))
                .count();
        if (aiGeneratedEvidence > evidences.size() / 2) {
            AiContextRiskSignalDTO risk = new AiContextRiskSignalDTO();
            risk.setRiskType("SELF_EVIDENCE");
            risk.setRiskLevel("HIGH");
            risk.setMessage("超过半数证据来自AI生成，可信度较低");
            risks.add(risk);
        }

        // 4. 低模型质量风险
        if (record.getModelQualityCoefficient() != null &&
                record.getModelQualityCoefficient().compareTo(new BigDecimal("60")) < 0) {
            AiContextRiskSignalDTO risk = new AiContextRiskSignalDTO();
            risk.setRiskType("POST_MODEL_LOW_QUALITY");
            risk.setRiskLevel("MEDIUM");
            risk.setMessage("岗位能力模型质量系数较低 (" + record.getModelQualityCoefficient() + ")");
            risks.add(risk);
        }

        // 5. 反馈校准风险
        if (record.getFeedbackCalibration() != null &&
                record.getFeedbackCalibration().abs().compareTo(new BigDecimal("5")) > 0) {
            AiContextRiskSignalDTO risk = new AiContextRiskSignalDTO();
            risk.setRiskType("FEEDBACK_BIAS");
            risk.setRiskLevel("MEDIUM");
            risk.setMessage("人工反馈校准值较大 (" + record.getFeedbackCalibration() + ")，可能存在偏差");
            risks.add(risk);
        }

        return risks;
    }

    @SuppressWarnings("unchecked")
    private AiContextGraphSummaryDTO buildGraphSummary(Long empId, Long postId) {
        AiContextGraphSummaryDTO summary = new AiContextGraphSummaryDTO();
        try {
            Map<String, Object> gapPath = knowledgeGraphQueryService.getAbilityGapPath(empId, postId);
            if (gapPath != null) {
                // 提取节点和边的数量
                List<Map<String, Object>> nodes = (List<Map<String, Object>>) gapPath.getOrDefault("nodes", List.of());
                List<Map<String, Object>> edges = (List<Map<String, Object>>) gapPath.getOrDefault("edges", List.of());
                summary.setNodeCount(nodes.size());
                summary.setEdgeCount(edges.size());

                // 统计能力节点和证据节点
                long abilityCount = nodes.stream()
                        .filter(n -> "ABILITY".equals(n.get("nodeType")))
                        .count();
                long evidenceCount = nodes.stream()
                        .filter(n -> "EVIDENCE".equals(n.get("nodeType")))
                        .count();
                summary.setAbilityCount((int) abilityCount);
                summary.setEvidenceCount((int) evidenceCount);

                // 提取关键能力节点
                List<String> keyAbilityNodes = nodes.stream()
                        .filter(n -> "ABILITY".equals(n.get("nodeType")))
                        .map(n -> (String) n.get("label"))
                        .filter(Objects::nonNull)
                        .limit(10)
                        .collect(Collectors.toList());
                summary.setKeyAbilityNodes(keyAbilityNodes);

                // 提取关键路径摘要
                List<String> keyPaths = edges.stream()
                        .limit(10)
                        .map(e -> e.get("sourceNodeKey") + " -> " + e.get("targetNodeKey"))
                        .collect(Collectors.toList());
                summary.setKeyPaths(keyPaths);
            }
        } catch (Exception e) {
            log.warn("获取图谱摘要失败", e);
        }
        return summary;
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return null;
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }

    /**
     * 有 canonical tag 的能力按标签取最高等级；无标签能力没有可用的标签身份，
     * 必须按正式能力记录 ID 保留，避免多个 null 标签被压缩成一条。
     */
    static List<com.example.matching.dto.matching.MatchingAbilitySnapshot> selectContextAbilities(
            List<com.example.matching.dto.matching.MatchingAbilitySnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return List.of();
        }
        Map<String, com.example.matching.dto.matching.MatchingAbilitySnapshot> selected = new LinkedHashMap<>();
        for (com.example.matching.dto.matching.MatchingAbilitySnapshot snapshot : snapshots) {
            if (snapshot == null) {
                continue;
            }
            String key = snapshot.tagId() != null ? "tag:" + snapshot.tagId()
                    : snapshot.abilityId() != null ? "ability:" + snapshot.abilityId()
                    : "name:" + normalizeAbilityName(snapshot.abilityName());
            com.example.matching.dto.matching.MatchingAbilitySnapshot existing = selected.get(key);
            int level = snapshot.level() != null ? snapshot.level() : 0;
            int existingLevel = existing != null && existing.level() != null ? existing.level() : 0;
            if (existing == null || level > existingLevel) {
                selected.put(key, snapshot);
            }
        }
        return List.copyOf(selected.values());
    }

    private static String normalizeAbilityName(String abilityName) {
        return abilityName == null ? "" : abilityName.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }


    /**
     * 权威能力快照 → 来源引用（画像来源无 emp_ability 记录 ID 时使用画像引用）。
     */
    private AiContextSourceRefDTO toSourceRef(
            com.example.matching.dto.matching.MatchingAbilitySnapshot ability,
            Long empId, AbilityTag tag) {
        AiContextSourceRefDTO ref = new AiContextSourceRefDTO();
        if (ability.abilityId() != null) {
            ref.setRef(com.example.matching.common.constant.SourceRefConstants.empAbilityFactRef(ability.abilityId()));
            ref.setRefType(com.example.matching.common.constant.SourceRefConstants.PREFIX_FACT.replace(":", ""));
            ref.setRefId(String.valueOf(ability.abilityId()));
        } else {
            ref.setRef("fact:PERSON_ABILITY_PROFILE:" + empId + ":" + ability.tagId());
            ref.setRefType("FACT");
            ref.setRefId(empId + ":" + ability.tagId());
        }
        ref.setTitle(tag != null ? tag.getTagName() : "员工能力");
        ref.setSnippet((tag != null ? tag.getTagName() : "员工能力") + " 等级 " + ability.level());
        ref.setSourceType(ability.sourceType() != null
                ? ability.sourceType() : "EMP_ABILITY");
        ref.setConfidenceScore(ability.confidence() != null
                ? ability.confidence().multiply(new java.math.BigDecimal("100")) : null);
        return ref;
    }
}
