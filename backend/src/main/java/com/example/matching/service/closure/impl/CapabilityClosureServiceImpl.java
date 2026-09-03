package com.example.matching.service.closure.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.dto.closure.CapabilityClosureResult;
import com.example.matching.dto.closure.LearningOutcomeConfirmDTO;
import com.example.matching.dto.closure.MatchDiagnosisResult;
import com.example.matching.dto.learning.LearningPathGenerateRequest;
import com.example.matching.dto.learning.LearningPathRequestDTO;
import com.example.matching.dto.matching.MatchingReportDTO;
import com.example.matching.entity.closure.CapabilityClosureLog;
import com.example.matching.entity.closure.MatchingRematchValidation;
import com.example.matching.entity.ability.PersonAbilityProfile;
import com.example.matching.mapper.ability.PersonAbilityProfileMapper;
import com.example.matching.mapper.closure.CapabilityClosureLogMapper;
import com.example.matching.mapper.closure.MatchingRematchValidationMapper;
import com.example.matching.agent.dto.person.PersonAbilityClaim;
import com.example.matching.agent.dto.person.PersonAbilityExtractionResult;
import com.example.matching.common.constant.SourceRefConstants;
import com.example.matching.common.enums.AbilitySourceType;
import com.example.matching.common.enums.TaskStatusEnum;
import com.example.matching.port.evolution.EvolutionQueryPort;
import com.example.matching.port.evolution.EvolutionQueryPort.EvolutionChangeItemDTO;
import com.example.matching.port.evolution.EvolutionQueryPort.EvolutionTaskDTO;
import com.example.matching.port.matching.MatchingQueryPort;
import com.example.matching.port.matching.MatchingQueryPort.MatchingRecordDTO;
import com.example.matching.port.post.PostQueryPort;
import com.example.matching.port.post.PostQueryPort.PostAbilityDTO;
import com.example.matching.port.tag.TagQueryPort;
import com.example.matching.port.tag.TagQueryPort.TagDTO;
import com.example.matching.port.talent.TalentQueryPort;
import com.example.matching.port.talent.TalentQueryPort.EmployeeAbilityDTO;
import com.example.matching.service.ability.AbilityEvidenceIngestionService;
import com.example.matching.service.agent.AgentBusinessApplyService;
import com.example.matching.service.closure.CapabilityClosureService;
import com.example.matching.service.matching.MatchingTaskService;
import com.example.matching.service.kg.GraphChangeSetService;
import com.example.matching.service.kg.KnowledgeGraphBuildService;
import com.example.matching.service.learning.LearningPathService;
import com.example.matching.service.learning.LearningPathPlanService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CapabilityClosureServiceImpl implements CapabilityClosureService {

    private static final String STATUS_SUCCEEDED = "SUCCEEDED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String GRAPH_PENDING = "PENDING";
    private static final String GRAPH_SUCCEEDED = "SUCCEEDED";

    private final CapabilityClosureLogMapper closureLogMapper;
    private final MatchingRematchValidationMapper matchingRematchValidationMapper;
    private final PersonAbilityProfileMapper personAbilityProfileMapper;
    private final PostQueryPort postQueryPort;
    private final EvolutionQueryPort evolutionQueryPort;
    private final MatchingQueryPort matchingQueryPort;
    private final TalentQueryPort talentQueryPort;
    private final TagQueryPort tagQueryPort;
    private final AbilityEvidenceIngestionService abilityEvidenceIngestionService;
    private final AgentBusinessApplyService agentBusinessApplyService;
    private final LearningPathService learningPathService;
    private final LearningPathPlanService learningPathPlanService;
    private final ObjectMapper objectMapper;
    private final KnowledgeGraphBuildService knowledgeGraphBuildService;
    private final GraphChangeSetService graphChangeSetService;
    private final MatchingTaskService matchingTaskService;
    private final com.example.matching.service.common.DistributedLockService distributedLockService;
    private final com.example.matching.schedule.SchedulerMetrics schedulerMetrics;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.example.matching.schedule.ScheduledTaskRunner taskRunner;

    @Override
    @Transactional
    public CapabilityClosureResult onEmergingPostConfirmed(Long postId) {
        String businessKey = "POST_EMERGING_CONFIRMED:POST:" + postId;
        CapabilityClosureLog existing = findLog(businessKey);
        if (isSucceeded(existing)) {
            return toResult(existing);
        }

        try {
            List<PostAbilityDTO> models = postQueryPort.listRequirementsByPostId(postId);
            int count = 0;
            for (PostAbilityDTO model : models) {
                if (model.id() == null) {
                    continue;
                }
                abilityEvidenceIngestionService.ingestPostAbilityModel(model.id(), "EMERGING_POST");
                count++;
            }
            String graphStatus = refreshGraph(postId);
            return saveResult("POST_EMERGING_CONFIRMED", "POST", postId, businessKey,
                    STATUS_SUCCEEDED, count, count, graphStatus, "Post closure completed");
        } catch (Exception e) {
            log.warn("Emerging post closure failed: postId={}, error={}", postId, e.getMessage());
            return saveResult("POST_EMERGING_CONFIRMED", "POST", postId, businessKey,
                    STATUS_FAILED, 0, 0, "FAILED", e.getMessage());
        }
    }

    @Override
    @Transactional
    public CapabilityClosureResult onPostEvolutionApplied(Long taskId) {
        String businessKey = "POST_EVOLUTION_APPLIED:TASK:" + taskId;
        CapabilityClosureLog existing = findLog(businessKey);
        if (isSucceeded(existing)) {
            return toResult(existing);
        }

        try {
            EvolutionTaskDTO task = evolutionQueryPort.getTaskById(taskId);
            if (task == null || task.postId() == null) {
                return saveResult("POST_EVOLUTION_APPLIED", "TASK", taskId, businessKey,
                        STATUS_FAILED, 0, 0, "FAILED", "Evolution task not found");
            }

            List<EvolutionChangeItemDTO> items = evolutionQueryPort.listApprovedChangeItems(taskId);
            Set<Long> ingestedModelIds = new LinkedHashSet<>();
            for (EvolutionChangeItemDTO item : items) {
                if (item.tagId() == null) {
                    continue;
                }
                PostAbilityDTO model = postQueryPort.getRequirementByPostAndTag(task.postId(), item.tagId());
                if (model != null && model.id() != null && ingestedModelIds.add(model.id())) {
                    abilityEvidenceIngestionService.ingestPostAbilityModel(model.id(), "POST_EVOLUTION");
                }
            }

            int count = ingestedModelIds.size();
            String graphStatus = refreshGraph(taskId);
            return saveResult("POST_EVOLUTION_APPLIED", "TASK", taskId, businessKey,
                    STATUS_SUCCEEDED, count, count, graphStatus, "Post evolution closure completed");
        } catch (Exception e) {
            log.warn("Post evolution closure failed: taskId={}, error={}", taskId, e.getMessage());
            return saveResult("POST_EVOLUTION_APPLIED", "TASK", taskId, businessKey,
                    STATUS_FAILED, 0, 0, "FAILED", e.getMessage());
        }
    }

    @Override
    public MatchDiagnosisResult diagnoseMatchingRecord(Long matchingRecordId) {
        MatchingRecordDTO record = matchingQueryPort.getById(matchingRecordId);
        MatchDiagnosisResult result = new MatchDiagnosisResult();
        result.setMatchingRecordId(matchingRecordId);
        if (record == null) {
            return result;
        }
        result.setEmpId(record.empId());
        result.setPostId(record.postId());

        List<MatchDiagnosisResult.GapItem> gaps = extractGapsFromReport(record);
        if (gaps.isEmpty()) {
            gaps = compareAbilityLevels(record.empId(), record.postId());
        }
        result.setGaps(gaps);

        if (!gaps.isEmpty()) {
            ensureLearningPathPlan(matchingRecordId);
            LearningPathRequestDTO request = new LearningPathRequestDTO();
            request.setAbilityNames(gaps.stream()
                    .map(MatchDiagnosisResult.GapItem::getAbilityName)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList());
            request.setTargetLevel(gaps.stream()
                    .map(MatchDiagnosisResult.GapItem::getRequiredLevel)
                    .filter(Objects::nonNull)
                    .max(Integer::compareTo)
                    .orElse(3));
            result.setLearningPath(learningPathService.generateLearningPath(request));
        }

        return result;
    }

    private void ensureLearningPathPlan(Long matchingRecordId) {
        try {
            LearningPathGenerateRequest request = new LearningPathGenerateRequest();
            request.setMatchingRecordId(matchingRecordId);
            request.setIncludeProjectTasks(true);
            request.setForceRegenerate(false);
            learningPathPlanService.generateFromMatchingRecord(request);
        } catch (Exception e) {
            // Diagnosis remains available when plan persistence is temporarily unavailable.
            log.warn("Unable to create learning path for matching record {}", matchingRecordId, e);
        }
    }

    @Override
    @Transactional
    public CapabilityClosureResult onLearningOutcomeConfirmed(LearningOutcomeConfirmDTO dto) {
        Long tagId = dto.getTagId();
        if (tagId == null && hasText(dto.getAbilityName())) {
            tagId = resolveTagId(dto.getAbilityName());
        }
        String abilityIdentity = tagId != null ? "TAG:" + tagId : "NAME:" + normalizeAbilityName(dto.getAbilityName());
        String businessKey = "LEARNING_OUTCOME_CONFIRMED:EMP:" + dto.getEmpId()
                + ":" + abilityIdentity + ":RESOURCE:" + nullToZero(dto.getCompletedResourceId());
        CapabilityClosureLog existing = findLog(businessKey);
        if (isSucceeded(existing)) {
            return toResult(existing);
        }

        try {
            if (tagId == null && !hasText(dto.getAbilityName())) {
                return saveResult("LEARNING_OUTCOME_CONFIRMED", "EMP_ABILITY", dto.getEmpId(), businessKey,
                        STATUS_FAILED, 0, 0, "FAILED", "Missing ability name");
            }
            if (dto.getConfirmedLevel() == null || dto.getConfirmedLevel() < 1 || dto.getConfirmedLevel() > 5) {
                return saveResult("LEARNING_OUTCOME_CONFIRMED", "EMP_ABILITY", dto.getEmpId(), businessKey,
                        STATUS_FAILED, 0, 0, "FAILED", "Missing or invalid reviewer-confirmed ability level");
            }

            // 通过 AgentBusinessApplyService 写入，经过 Harness 校验
            String sourceType = AbilitySourceType.canonicalize(
                    defaultString(dto.getConfirmationSource(), AbilitySourceType.LEARNING_PROJECT));
            Long sourceRefId = dto.getCompletedResourceId() != null ? dto.getCompletedResourceId() : dto.getEmpId();

            PersonAbilityClaim claim = new PersonAbilityClaim();
            claim.setEmpId(dto.getEmpId());
            claim.setAbilityTagId(tagId);
            claim.setAbilityName(dto.getAbilityName());
            claim.setMasteryLevel(dto.getConfirmedLevel());
            claim.setConfidenceScore(BigDecimal.valueOf(90));
            claim.setSourceType(sourceType);
            claim.setSourceRefId(sourceRefId);
            claim.setEvidenceText(buildRemarkWithAiMetadata(dto));
            claim.setSourceRefs(List.of(
                    SourceRefConstants.sourceRef(sourceType, sourceRefId),
                    "learning:resource:" + nullToZero(dto.getCompletedResourceId())));

            PersonAbilityExtractionResult extractionResult = new PersonAbilityExtractionResult();
            extractionResult.setEmpId(dto.getEmpId());
            extractionResult.setSourceType(sourceType);
            extractionResult.setSourceRefId(sourceRefId);
            extractionResult.setClaims(List.of(claim));

            AgentBusinessApplyService.PersonAbilityApplyResult applyResult =
                    agentBusinessApplyService.applyPersonAbilities(extractionResult);

            if (applyResult.getPassCount() > 0 && tagId != null) {
                promoteApprovedProfile(dto.getEmpId(), tagId, dto.getConfirmedLevel());
            }

            if ((applyResult.getPassCount() > 0 || applyResult.getReviewCount() > 0) && tagId != null) {
                // 查询刚写入的 EmpAbility 记录，获取其 ID 供证据注入使用
                EmployeeAbilityDTO writtenAbility = talentQueryPort.getEmpAbility(
                        dto.getEmpId(), tagId, sourceType);
                if (writtenAbility != null) {
                    abilityEvidenceIngestionService.ingestEmployeeAbility(writtenAbility.id(), sourceType);
                }
            }

            if (applyResult.getPassCount() > 0 && tagId != null && dto.getBeforeLevel() != null
                    && dto.getConfirmedLevel() > dto.getBeforeLevel()) {
                triggerRematchValidation(dto, tagId, businessKey);
            }

            String graphStatus = refreshGraph(dto.getEmpId());
            int evidenceCount = applyResult.getPassCount() + applyResult.getReviewCount();
            return saveResult("LEARNING_OUTCOME_CONFIRMED", "EMP_ABILITY", dto.getEmpId(), businessKey,
                    STATUS_SUCCEEDED, evidenceCount, 1, graphStatus,
                    "Learning outcome closure via Harness: pass=" + applyResult.getPassCount()
                            + " review=" + applyResult.getReviewCount()
                            + " block=" + applyResult.getBlockCount());
        } catch (Exception e) {
            log.warn("Learning outcome closure failed: empId={}, tagId={}, error={}", dto.getEmpId(), tagId, e.getMessage());
            return saveResult("LEARNING_OUTCOME_CONFIRMED", "EMP_ABILITY", dto.getEmpId(), businessKey,
                    STATUS_FAILED, 0, 0, "FAILED", e.getMessage());
        }
    }

    @Override
    public CapabilityClosureResult getLatestByBusinessKey(String businessKey) {
        CapabilityClosureLog log = findLog(businessKey);
        return log != null ? toResult(log) : null;
    }

    private List<MatchDiagnosisResult.GapItem> extractGapsFromReport(MatchingRecordDTO record) {
        if (!hasText(record.quantitativeReport())) {
            return List.of();
        }
        try {
            MatchingReportDTO report = objectMapper.readValue(record.quantitativeReport(), MatchingReportDTO.class);
            if (report.getAbilityDetails() == null || report.getAbilityDetails().isEmpty()) {
                return List.of();
            }
            List<MatchDiagnosisResult.GapItem> gaps = new ArrayList<>();
            for (MatchingReportDTO.AbilityDetail detail : report.getAbilityDetails()) {
                if (detail.isPassed() && !detail.isWeakEvidence()) {
                    continue;
                }
                MatchDiagnosisResult.GapItem gap = new MatchDiagnosisResult.GapItem();
                gap.setTagId(detail.getTagId());
                gap.setAbilityName(detail.getTagName());
                gap.setCurrentLevel(detail.getActualLevel());
                gap.setRequiredLevel(detail.getRequiredLevel());
                gap.setWeakEvidence(detail.isWeakEvidence());
                gap.setReason(resolveGapReason(detail));
                gaps.add(gap);
            }
            return gaps;
        } catch (Exception e) {
            log.debug("Failed to parse matching report for diagnosis: recordId={}", record.id());
            return List.of();
        }
    }

    private List<MatchDiagnosisResult.GapItem> compareAbilityLevels(Long empId, Long postId) {
        if (empId == null || postId == null) {
            return List.of();
        }
        List<PostAbilityDTO> requirements = postQueryPort.listRequirementsByPostId(postId);
        if (requirements.isEmpty()) {
            return List.of();
        }
        List<EmployeeAbilityDTO> abilities = talentQueryPort.listAbilitiesByEmpId(empId);

        Map<Long, EmployeeAbilityDTO> abilityByTag = new HashMap<>();
        for (EmployeeAbilityDTO ability : abilities) {
            abilityByTag.put(ability.tagId(), ability);
        }
        Map<Long, String> tagNames = loadTagNames(requirements);

        List<MatchDiagnosisResult.GapItem> gaps = new ArrayList<>();
        for (PostAbilityDTO req : requirements) {
            Integer requiredLevel = req.minRequiredLevel() != null ? req.minRequiredLevel() : 0;
            EmployeeAbilityDTO ability = abilityByTag.get(req.tagId());
            int currentLevel = ability != null && ability.masteryLevel() != null ? ability.masteryLevel() : 0;
            if (currentLevel >= requiredLevel) {
                continue;
            }
            MatchDiagnosisResult.GapItem gap = new MatchDiagnosisResult.GapItem();
            gap.setTagId(req.tagId());
            gap.setAbilityName(tagNames.getOrDefault(req.tagId(), "Ability#" + req.tagId()));
            gap.setCurrentLevel(BigDecimal.valueOf(currentLevel));
            gap.setRequiredLevel(requiredLevel);
            gap.setWeakEvidence(ability == null);
            gap.setReason("Current level " + currentLevel + " is below required level " + requiredLevel);
            gaps.add(gap);
        }
        return gaps;
    }

    private Map<Long, String> loadTagNames(List<PostAbilityDTO> requirements) {
        Set<Long> tagIds = new LinkedHashSet<>();
        for (PostAbilityDTO req : requirements) {
            if (req.tagId() != null) {
                tagIds.add(req.tagId());
            }
        }
        if (tagIds.isEmpty()) {
            return Map.of();
        }
        List<TagDTO> tags = tagQueryPort.batchGetTags(new ArrayList<>(tagIds));
        Map<Long, String> result = new LinkedHashMap<>();
        for (TagDTO tag : tags) {
            result.put(tag.id(), tag.tagName());
        }
        return result;
    }

    private Long resolveTagId(String abilityName) {
        TagDTO tag = tagQueryPort.getTagByName(abilityName);
        return tag != null ? tag.id() : null;
    }

    private String resolveGapReason(MatchingReportDTO.AbilityDetail detail) {
        if (hasText(detail.getPassedDesc())) {
            return detail.getPassedDesc();
        }
        if (detail.isWeakEvidence()) {
            return "Evidence is weak and needs confirmation";
        }
        return "Ability does not meet the post requirement";
    }

    private CapabilityClosureLog findLog(String businessKey) {
        return closureLogMapper.selectOne(
                Wrappers.<CapabilityClosureLog>lambdaQuery()
                        .eq(CapabilityClosureLog::getBusinessKey, businessKey)
                        .last("LIMIT 1"));
    }

    private boolean isSucceeded(CapabilityClosureLog log) {
        return log != null && STATUS_SUCCEEDED.equals(log.getClosureStatus());
    }

    private CapabilityClosureResult saveResult(String eventType,
                                               String sourceType,
                                               Long sourceRefId,
                                               String businessKey,
                                               String status,
                                               int evidenceCount,
                                               int knowledgeDocCount,
                                               String graphRefreshStatus,
                                               String message) {
        CapabilityClosureLog log = new CapabilityClosureLog();
        log.setEventType(eventType);
        log.setSourceType(sourceType);
        log.setSourceRefId(sourceRefId);
        log.setBusinessKey(businessKey);
        log.setClosureStatus(status);
        log.setEvidenceCount(evidenceCount);
        log.setKnowledgeDocCount(knowledgeDocCount);
        log.setGraphRefreshStatus(graphRefreshStatus);
        log.setMessage(message);
        closureLogMapper.insert(log);
        return toResult(log);
    }

    private CapabilityClosureResult toResult(CapabilityClosureLog log) {
        CapabilityClosureResult result = new CapabilityClosureResult();
        result.setEventType(log.getEventType());
        result.setSourceType(log.getSourceType());
        result.setSourceRefId(log.getSourceRefId());
        result.setBusinessKey(log.getBusinessKey());
        result.setClosureStatus(log.getClosureStatus());
        result.setEvidenceCount(log.getEvidenceCount());
        result.setKnowledgeDocCount(log.getKnowledgeDocCount());
        result.setGraphRefreshStatus(log.getGraphRefreshStatus());
        result.setMessage(log.getMessage());
        return result;
    }

    private String refreshGraph(Long sourceRefId) {
        try {
            graphChangeSetService.requestChange("CLOSURE", "GRAPH", sourceRefId,
                    "UPSERT", Map.of("action", "rebuild"), null);
            return GRAPH_PENDING;
        } catch (Exception e) {
            log.warn("Graph change set write failed during capability closure: {}", e.getMessage());
            return GRAPH_PENDING;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String defaultString(String value, String defaultValue) {
        return hasText(value) ? value : defaultValue;
    }

    private long nullToZero(Long value) {
        return value != null ? value : 0L;
    }

    /**
     * 构建包含AI追溯信息的备注
     */
    private String buildRemarkWithAiMetadata(LearningOutcomeConfirmDTO dto) {
        StringBuilder remark = new StringBuilder();
        if (hasText(dto.getNote())) {
            remark.append(dto.getNote());
        }
        // 如果有AI追溯信息，附加到备注中
        if (dto.getAiSuggestionId() != null || hasText(dto.getRagChunkIds())) {
            remark.append(" [AI追溯:");
            if (dto.getAiSuggestionId() != null) {
                remark.append("suggestionId=").append(dto.getAiSuggestionId());
            }
            if (hasText(dto.getRagChunkIds())) {
                remark.append(", ragChunkIds=").append(dto.getRagChunkIds());
            }
            remark.append("]");
        }
        return remark.toString();
    }

    private void triggerRematchValidation(LearningOutcomeConfirmDTO dto, Long tagId, String businessKey) {
        List<PostAbilityDTO> postModels = postQueryPort.listRequirementsByTagId(tagId);
        if (postModels.isEmpty()) {
            log.debug("No post ability models found for tagId={}, skipping re-match validation", tagId);
            return;
        }
        List<Long> postIds = postModels.stream()
                .map(PostAbilityDTO::postId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (postIds.isEmpty()) {
            log.debug("No valid postIds for tagId={}, skipping re-match validation", tagId);
            return;
        }

        List<MatchingRecordDTO> records = matchingQueryPort.listRecentRecordsByEmpAndPosts(
                dto.getEmpId(), postIds, LocalDateTime.now().minusDays(90));
        if (records.isEmpty()) {
            log.debug("No recent matching records for empId={} and tagId={}, skipping re-match validation",
                    dto.getEmpId(), tagId);
            return;
        }

        for (MatchingRecordDTO record : records) {
            // 修复：原实现未开启 AI 匹配（默认 false），新记录只有 L2 分数，
            // 与 oldScore（可能是含 LLM 的 L3 aiMatchScore）直接对比口径不一致，
            // 学习效果会被系统性偏差误判。
            MatchingRematchValidation validation = new MatchingRematchValidation();
            validation.setClosureBusinessKey(businessKey);
            validation.setOriginalMatchingRecordId(record.id());
            validation.setOldScore(record.aiMatchScore());
            validation.setOldMatchStatus(record.matchStatus());
            validation.setValidationStatus("WAIT_VECTOR_SYNC");
            validation.setEmpId(dto.getEmpId());
            validation.setPostId(record.postId());
            validation.setTagId(tagId);
            matchingRematchValidationMapper.insert(validation);

            log.info("Re-match validation waiting for employee vector sync: empId={}, postId={}, tagId={}, recordId={}",
                    dto.getEmpId(), record.postId(), tagId, record.id());
        }
    }

    private String normalizeAbilityName(String abilityName) {
        return abilityName == null ? "" : abilityName.trim().replaceAll("[^\\p{L}\\p{N}]", "").toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * 项目审核人已明确确认等级，且 Harness 已放行该学习成果时，才同步已审核的正式画像。
     * 这不是用学习路径目标等级覆盖画像，目标等级永远不参与这里的写入。
     */
    private void promoteApprovedProfile(Long empId, Long tagId, Integer confirmedLevel) {
        PersonAbilityProfile profile = personAbilityProfileMapper.selectOne(
                Wrappers.<PersonAbilityProfile>lambdaQuery()
                        .eq(PersonAbilityProfile::getEmpId, empId)
                        .eq(PersonAbilityProfile::getTagId, tagId)
                        .eq(PersonAbilityProfile::getReviewState, "APPROVED")
                        .last("LIMIT 1"));
        if (profile == null) {
            return;
        }
        profile.setFinalLevel(confirmedLevel);
        profile.setLastEvidenceTime(LocalDateTime.now());
        personAbilityProfileMapper.updateById(profile);
        log.info("学习成果已同步已审核人员画像: empId={}, tagId={}, confirmedLevel={}",
                empId, tagId, confirmedLevel);
    }

    @Scheduled(fixedDelayString = "${closure.rematch-validation.zombie-scan-delay-ms:300000}")
    public void scanTimeoutValidations() {
        if (taskRunner != null) {
            taskRunner.run("rematch_validation_zombie_scan", this::scanTimeoutValidationsInternal);
        } else {
            scanTimeoutValidationsInternal();
        }
    }

    private void scanTimeoutValidationsInternal() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusHours(1);
            List<MatchingRematchValidation> waitingForVector = matchingRematchValidationMapper.selectList(
                    Wrappers.<MatchingRematchValidation>lambdaQuery()
                            .eq(MatchingRematchValidation::getValidationStatus, "WAIT_VECTOR_SYNC")
                            .le(MatchingRematchValidation::getCreatedTime, cutoff));
            for (MatchingRematchValidation validation : waitingForVector) {
                submitRematchAfterVectorTimeout(validation);
            }
            int updated = matchingRematchValidationMapper.update(null,
                    Wrappers.<MatchingRematchValidation>lambdaUpdate()
                            .eq(MatchingRematchValidation::getValidationStatus, "PENDING")
                            .le(MatchingRematchValidation::getCreatedTime, cutoff)
                            .set(MatchingRematchValidation::getValidationStatus, "TIMEOUT")
                            .set(MatchingRematchValidation::getFailReason, "Validation timed out after 1 hour"));
            if (updated > 0) {
                log.warn("超时匹配验证已标记: count={}", updated);
            }
        } catch (Exception e) {
            log.error("Rematch validation zombie scan failed", e);
            schedulerMetrics.recordFailure("rematch_validation_zombie_scan");
        }
    }

    private void submitRematchAfterVectorTimeout(MatchingRematchValidation validation) {
        int claimed = matchingRematchValidationMapper.update(null,
                Wrappers.<MatchingRematchValidation>lambdaUpdate()
                        .eq(MatchingRematchValidation::getId, validation.getId())
                        .eq(MatchingRematchValidation::getValidationStatus, "WAIT_VECTOR_SYNC")
                        .set(MatchingRematchValidation::getValidationStatus, "SUBMITTING"));
        if (claimed == 0) {
            return;
        }
        try {
            com.example.matching.dto.matching.MatchingExecuteDTO.MatchingPair pair =
                    new com.example.matching.dto.matching.MatchingExecuteDTO.MatchingPair();
            pair.setEmpId(validation.getEmpId());
            pair.setPostId(validation.getPostId());
            com.example.matching.dto.matching.MatchingExecuteDTO executeDTO =
                    new com.example.matching.dto.matching.MatchingExecuteDTO();
            executeDTO.setPairs(List.of(pair));
            executeDTO.setMode("SINGLE_EVAL");
            executeDTO.setEnableAiMatching(true);
            String taskId = matchingTaskService.submitTask(executeDTO);
            matchingRematchValidationMapper.update(null,
                    Wrappers.<MatchingRematchValidation>lambdaUpdate()
                            .eq(MatchingRematchValidation::getId, validation.getId())
                            .eq(MatchingRematchValidation::getValidationStatus, "SUBMITTING")
                            .set(MatchingRematchValidation::getTaskId, taskId)
                            .set(MatchingRematchValidation::getValidationStatus, "PENDING")
                            .set(MatchingRematchValidation::getFailReason,
                                    "Vector sync timeout; submitted deterministic rematch fallback"));
            log.warn("向量同步超时，已提交重匹配兜底: validationId={}, taskId={}", validation.getId(), taskId);
        } catch (Exception e) {
            matchingRematchValidationMapper.update(null,
                    Wrappers.<MatchingRematchValidation>lambdaUpdate()
                            .eq(MatchingRematchValidation::getId, validation.getId())
                            .eq(MatchingRematchValidation::getValidationStatus, "SUBMITTING")
                            .set(MatchingRematchValidation::getValidationStatus, "WAIT_VECTOR_SYNC")
                            .set(MatchingRematchValidation::getFailReason,
                                    "Vector sync timeout fallback submission failed: " + truncate(e.getMessage())));
            log.warn("向量同步超时后的重匹配提交失败: validationId={}", validation.getId(), e);
        }
    }

    private String truncate(String message) {
        if (!hasText(message)) {
            return "unknown error";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
