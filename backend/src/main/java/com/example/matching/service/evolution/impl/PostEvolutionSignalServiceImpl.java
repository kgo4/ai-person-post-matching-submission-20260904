package com.example.matching.service.evolution.impl;

import com.example.matching.common.constant.SourceRefConstants;
import com.example.matching.dto.harness.AiHarnessClaimDTO;
import com.example.matching.dto.harness.AiHarnessDecisionDTO;
import com.example.matching.entity.evolution.PostEvolutionChangeItem;
import com.example.matching.entity.evolution.PostEvolutionEvidence;
import com.example.matching.entity.evolution.PostEvolutionTask;
import com.example.matching.mapper.evolution.PostEvolutionChangeItemMapper;
import com.example.matching.mapper.evolution.PostEvolutionEvidenceMapper;
import com.example.matching.mapper.evolution.PostEvolutionTaskMapper;
import com.example.matching.service.evolution.PostEvolutionKnowledgeRetrievalService;
import com.example.matching.service.evolution.PostEvolutionSignalService;
import com.example.matching.service.evolution.support.EvolutionAbilityTagResolver;
import com.example.matching.service.evolution.support.ResolvedEvolutionAbility;
import com.example.matching.service.harness.AiTrustHarnessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 岗位演化信号服务实现
 * <p>
 * 将知识检索结果转换为岗位演化任务和变更项。
 * 每个信号必须携带统一 sourceRef，并经过 Harness 校验。
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostEvolutionSignalServiceImpl implements PostEvolutionSignalService {

    private final PostEvolutionKnowledgeRetrievalService knowledgeRetrievalService;
    private final AiTrustHarnessService harnessService;
    private final PostEvolutionTaskMapper taskMapper;
    private final PostEvolutionChangeItemMapper changeItemMapper;
    private final PostEvolutionEvidenceMapper evidenceMapper;
    private final ObjectMapper objectMapper;
    private final EvolutionAbilityTagResolver abilityTagResolver;

    @Override
    public List<EvolutionSignal> generateSignalsFromWhitepaper(String industry, Long documentId) {
        log.info("从行业白皮书生成演化信号: industry={}, documentId={}", industry, documentId);

        // 检索相关切片
        List<PostEvolutionKnowledgeRetrievalService.RetrievalResult> chunks =
                knowledgeRetrievalService.retrieveIndustryTrends(
                        industry,
                        List.of(industry, "岗位", "能力", "趋势"),
                        20
                );

        // 转换为信号
        return chunks.stream()
                .map(chunk -> convertToSignal(chunk, "INDUSTRY_TREND"))
                .flatMap(Optional::stream)
                .collect(Collectors.toList());
    }

    @Override
    public List<EvolutionSignal> generateSignalsFromCloudKnowledge(String businessDomain, Long documentId) {
        log.info("从云知识库生成演化信号: businessDomain={}, documentId={}", businessDomain, documentId);

        // 检索相关切片
        List<PostEvolutionKnowledgeRetrievalService.RetrievalResult> chunks =
                knowledgeRetrievalService.retrieveBusinessChanges(
                        businessDomain,
                        List.of(businessDomain, "业务", "岗位", "需求"),
                        20
                );

        // 转换为信号
        return chunks.stream()
                .map(chunk -> convertToSignal(chunk, "BUSINESS_CHANGE"))
                .flatMap(Optional::stream)
                .collect(Collectors.toList());
    }

    @Override
    public List<EvolutionSignal> generateSignalsFromRecruitmentJd(String postName, String batchNo) {
        log.info("从招聘JD生成演化信号: postName={}, batchNo={}", postName, batchNo);

        // 检索相关切片
        List<PostEvolutionKnowledgeRetrievalService.RetrievalResult> chunks =
                knowledgeRetrievalService.retrieveForPost(
                        new PostEvolutionKnowledgeRetrievalService.RetrievalRequest(
                                null, postName, null, null,
                                List.of(postName, "岗位职责", "任职要求"),
                                List.of(SourceRefConstants.SOURCE_RECRUITMENT_JD),
                                20
                        )
                );

        // 转换为信号（招聘数据权重较低）
        return chunks.stream()
                .map(chunk -> convertToSignal(chunk, "RECRUITMENT_JD"))
                .flatMap(Optional::stream)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public Long convertSignalsToEvolutionTask(Long postId, List<EvolutionSignal> signals,
                                               String triggerType, Long userId) {        log.info("将信号转换为演化任务: postId={}, signalCount={}, triggerType={}",
                postId, signals.size(), triggerType);

        // 1. 创建演化任务（M13：信号任务必须人工确认，即使 Harness PASS）
        PostEvolutionTask task = new PostEvolutionTask();
        task.setTaskCode("EVO_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        task.setPostId(postId);
        task.setTaskName("岗位演化任务 - " + triggerType);
        task.setTaskStatus("WAIT_CONFIRM");
        task.setSourceType(triggerType);
        task.setTriggerType(triggerType);
        task.setCreatedBy(userId);
        taskMapper.insert(task);

        // 2. 处理每个信号
        for (EvolutionSignal signal : signals) {
            processSignal(task.getId(), signal);
        }

        return task.getId();
    }

    /**
     * 处理单个信号
     */
    /** M4 冷却窗口：同指纹变更项 7 天内不重复创建 */
    private static final int COOLDOWN_DAYS = 7;

    private boolean isWithinCooldown(Long tagId, String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) {
            return false;
        }
        LocalDateTime since = LocalDateTime.now().minusDays(COOLDOWN_DAYS);
        Long count = changeItemMapper.selectCount(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<PostEvolutionChangeItem>lambdaQuery()
                        .eq(PostEvolutionChangeItem::getFingerprint, fingerprint)
                        .ge(PostEvolutionChangeItem::getCreatedTime, since));
        return count != null && count > 0;
    }

    /**
     * M4：对同一岗位、能力、变更类型、来源引用计算 fingerprint（MD5）。
     */
    private String computeFingerprint(Long taskId, EvolutionSignal signal) {
        String raw = taskId + ":" + (signal.abilityTagId() != null ? signal.abilityTagId() : "null")
                + ":" + signal.changeType()
                + ":" + (signal.sourceRefs().isEmpty() ? "" : signal.sourceRefs().get(0));
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return Integer.toHexString(raw.hashCode());
        }
    }

    private void processSignal(Long taskId, EvolutionSignal signal) {        // 1. 构建 Harness 声明
        AiHarnessClaimDTO claim = buildHarnessClaim(signal);

        // 2. 调用 Harness 校验
        AiHarnessDecisionDTO decision = harnessService.verify(claim);

        // 3. 根据决策处理
        if (decision.isBlock()) {
            log.info("信号被 BLOCK: abilityName={}, reasons={}", signal.abilityName(), decision.getReasons());
            return;
        }

        // 4. 创建变更项（M13：所有变更项初始 PENDING，即使 Harness PASS 也必须人工确认）
        PostEvolutionChangeItem changeItem = new PostEvolutionChangeItem();
        changeItem.setTaskId(taskId);
        changeItem.setChangeType(signal.changeType());
        changeItem.setChangeTypeExtended(signal.changeType());
        changeItem.setAbilityName(signal.abilityName());
        changeItem.setTagId(signal.abilityTagId());
        changeItem.setEvidenceText(signal.evidenceText());
        changeItem.setSourceRef(signal.sourceRefs().isEmpty() ? null : signal.sourceRefs().get(0));
        changeItem.setSourceRefsJson(toJson(signal.sourceRefs()));
        changeItem.setSupportScore(BigDecimal.valueOf(signal.supportScore()));
        changeItem.setConfidenceScore(BigDecimal.valueOf(signal.confidenceScore()));
        changeItem.setHarnessDecision(decision.getDecision());
        changeItem.setRiskLevel(decision.getRiskLevel());
        changeItem.setConfirmStatus("PENDING");
        // M4：同岗位+能力+变更类型+来源引用指纹，冷却窗口内不重复创建
        changeItem.setFingerprint(computeFingerprint(taskId, signal));
        if (isWithinCooldown(signal.abilityTagId(), changeItem.getFingerprint())) {
            log.info("同指纹变更项在冷却窗口内，跳过创建: taskId={}, fingerprint={}",
                    taskId, changeItem.getFingerprint());
            return;
        }
        changeItemMapper.insert(changeItem);

        // 5. 创建证据记录
        for (String sourceRef : signal.sourceRefs()) {
            PostEvolutionEvidence evidence = new PostEvolutionEvidence();
            evidence.setTaskId(taskId);
            evidence.setChangeItemId(changeItem.getId());
            evidence.setSourceRef(sourceRef);
            evidence.setEvidenceText(signal.evidenceText());
            evidence.setSourceType(parseSourceType(sourceRef));
            evidenceMapper.insert(evidence);
        }
    }

    /**
     * 构建 Harness 声明
     */
    private AiHarnessClaimDTO buildHarnessClaim(EvolutionSignal signal) {
        AiHarnessClaimDTO claim = new AiHarnessClaimDTO();
        claim.setScenario(SourceRefConstants.SCENARIO_POST_DYNAMIC_EVOLUTION);
        claim.setClaimType(SourceRefConstants.CLAIM_TYPE_POST_ABILITY_CHANGE);
        claim.setChangeType(signal.changeType());
        claim.setClaimText(signal.abilityName());
        claim.setEvidenceText(signal.evidenceText());
        claim.setSourceRefs(signal.sourceRefs());
        claim.setSourceType("POST_EVOLUTION");
        claim.setBusinessTargetType("POST_ABILITY_MODEL");
        return claim;
    }

    /**
     * 转换检索结果为信号
     */
    private Optional<EvolutionSignal> convertToSignal(PostEvolutionKnowledgeRetrievalService.RetrievalResult chunk,
                                                    String signalType) {
        ResolvedEvolutionAbility ability = abilityTagResolver.resolve(chunk.chunkText());
        if (ability == null) {
            log.debug("Skipping evolution evidence without a matched ability tag: sourceRef={}", chunk.sourceRef());
            return Optional.empty();
        }
        return Optional.of(new EvolutionSignal(
                signalType,
                ability.abilityName(),
                ability.tagId(),
                determineChangeType(chunk.chunkType()),
                chunk.chunkText(),
                List.of(chunk.sourceRef()),
                chunk.relevanceScore(),
                chunk.relevanceScore()
        ));
    }

    /**
     * 根据切片类型确定变更类型
     */
    private String determineChangeType(String chunkType) {
        if (chunkType == null) {
            return "ADD_ABILITY";
        }
        return switch (chunkType) {
            case "ABILITY_REQUIREMENT" -> "ADD_ABILITY";
            case "TASK_DESCRIPTION" -> "ADD_TASK";
            case "TOOL_TECH" -> "ADD_TOOL";
            case "POST_REQUIREMENT" -> "UPGRADE_LEVEL";
            default -> "ADD_ABILITY";
        };
    }

    /**
     * 从 sourceRef 解析来源类型
     */
    private String parseSourceType(String sourceRef) {
        if (sourceRef == null) {
            return "UNKNOWN";
        }
        String entityType = SourceRefConstants.parseEntityType(sourceRef);
        return entityType != null ? entityType : "UNKNOWN";
    }

    /**
     * 序列化为JSON
     */
    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}
