package com.example.matching.service.assessment.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.entity.ability.PersonAbilityClaim;
import com.example.matching.entity.interview.InterviewAbilityObservation;
import com.example.matching.entity.workflow.PersonCapabilityStageRun;
import com.example.matching.mapper.interview.InterviewAbilityObservationMapper;
import com.example.matching.service.assessment.AbilityEvidenceCollectionService;
import com.example.matching.service.assessment.CapabilityAssessmentWorkflowService;
import com.example.matching.service.assessment.InterviewAssessmentEvidenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 面试证据收集与聚合审核推进服务实现
 * <p>
 * 面试分析完成后：将全部面试观察保存为 AI_INTERVIEW 证据 Claim
 * （不预过滤 Harness 决策，由聚合阶段统一审核），按能力聚合，
 * 标记证据就绪并投递 AGGREGATE_HARNESS 阶段任务。
 *
 * @author system
 */
@Slf4j
@Service
public class InterviewAssessmentEvidenceServiceImpl implements InterviewAssessmentEvidenceService {

    private final InterviewAbilityObservationMapper observationMapper;
    private final AbilityEvidenceCollectionService evidenceCollectionService;
    private final CapabilityAssessmentWorkflowService workflowService;
    private final com.example.matching.port.assessment.CapabilityStageLifecycleEventPublisher lifecycleEventPublisher;

    public InterviewAssessmentEvidenceServiceImpl(
            InterviewAbilityObservationMapper observationMapper,
            AbilityEvidenceCollectionService evidenceCollectionService,
            CapabilityAssessmentWorkflowService workflowService,
            com.example.matching.port.assessment.CapabilityStageLifecycleEventPublisher lifecycleEventPublisher) {
        this.observationMapper = observationMapper;
        this.evidenceCollectionService = evidenceCollectionService;
        this.workflowService = workflowService;
        this.lifecycleEventPublisher = lifecycleEventPublisher;
    }

    @Override
    @Transactional
    public void saveInterviewEvidenceAndAdvance(Long workflowId, Long empId, Long sessionId) {
        List<InterviewAbilityObservation> observations = observationMapper.selectList(
                new LambdaQueryWrapper<InterviewAbilityObservation>()
                        .eq(InterviewAbilityObservation::getSessionId, sessionId)
                        .eq(InterviewAbilityObservation::getIsDeleted, 0));
        if (observations.isEmpty()) {
            log.info("面试无能力观察，跳过证据保存: sessionId={}", sessionId);
        }
        // 创建/复用 AI_INTERVIEW 阶段运行（分析完成）
        String hash = CapabilityAssessmentWorkflowServiceImpl.hashInput(
                workflowId.toString(), "AI_INTERVIEW", String.valueOf(sessionId));
        PersonCapabilityStageRun stageRun = workflowService.createStageRun(
                workflowId, "AI_INTERVIEW", hash,
                "{\"sessionId\":" + sessionId + "}", "AI_INTERVIEW", sessionId);

        int saved = 0;
        if (!observations.isEmpty()) {
            saved = evidenceCollectionService.saveInterviewClaims(
                    workflowId, stageRun.getId(), empId, toClaims(observations), null);
            if (saved > 0) {
                // 按能力聚合 + 标记聚合审核就绪
                evidenceCollectionService.groupClaimsByAbility(workflowId, empId);
                evidenceCollectionService.markReadyForAggregateHarness(workflowId);
            }
        }
        // 前置完成标记：同步将 AI_INTERVIEW 阶段运行置为 SUCCEEDED。
        // 否则 startNextStage 的前置校验（同事务读库）早于协调器异步处理
        // TASK_SUCCEEDED（事务提交后才生效），读到未完成状态抛"前置阶段未完成"，
        // 导致整个事务回滚（证据保存与事件发布全部丢失）。
        // 协调器随后处理 TASK_SUCCEEDED 时 CAS 幂等（SUCCEEDED→SUCCEEDED 保持），仅推进工作流状态。
        boolean marked = workflowService.casStageRunStatus(stageRun.getId(), stageRun.getStatus(),
                com.example.matching.common.enums.StageRunStatusEnum.SUCCEEDED.getCode(), null, null);
        if (!marked) {
            log.warn("面试阶段运行状态非预期，跳过聚合推进（可能已终态）: workflowId={}, stageRunId={}, status={}",
                    workflowId, stageRun.getId(), stageRun.getStatus());
            return;
        }
        // 不再直接推进工作流：发布面试分析成功生命周期事件，
        // 协调器推进 INTERVIEW_ANALYZING -> AGGREGATE_HARNESS_RUNNING。
        // 发布前查重：listener / analyze() / Reconciler 补偿可能重复调用本方法
        // （同一 stageRun 已由协调器处理过 TASK_SUCCEEDED），重复发布会产生重复事件与告警噪音。
        // 发布前查重：listener / analyze() / Reconciler 补偿可能重复调用本方法
        // （同一 stageRun 已由协调器处理过 TASK_SUCCEEDED），重复发布会产生重复事件与告警噪音。
        boolean alreadyPublished = workflowService.hasRecordedLifecycleEvent(stageRun.getId(), "TASK_SUCCEEDED");
        if (alreadyPublished) {
            log.info("面试阶段 TASK_SUCCEEDED 已发布过，跳过重复发布: workflowId={}, stageRunId={}",
                    workflowId, stageRun.getId());
        } else {
            lifecycleEventPublisher.publish(com.example.matching.event.CapabilityStageLifecycleEvent.succeeded(
                    workflowId, stageRun.getId(), "AI_INTERVIEW", "AI_INTERVIEW", sessionId));
        }

        // 创建 AGGREGATE_HARNESS 阶段运行并投递聚合审核任务（任务抢占后由消费者发布 TASK_CLAIMED）
        String harnessStage = "AGGREGATE_HARNESS_AND_LEVEL_CONFIRMATION";
        String harnessHash = CapabilityAssessmentWorkflowServiceImpl.hashInput(
                workflowId.toString(), harnessStage, String.valueOf(stageRun.getId()));
        workflowService.startNextStage(workflowId, harnessStage, harnessHash,
                "{\"sourceStageRunId\":" + stageRun.getId() + "}", null);
        log.info("面试证据已保存并发布生命周期事件，投递聚合审核: workflowId={}, sessionId={}, saved={}",
                workflowId, sessionId, saved);
    }

    /**
     * 面试观察转证据 Claim（保留全部观察，Harness 决策由聚合阶段统一判定）。
     */
    private List<PersonAbilityClaim> toClaims(List<InterviewAbilityObservation> observations) {
        List<PersonAbilityClaim> claims = new ArrayList<>();
        for (InterviewAbilityObservation observation : observations) {
            if (observation.getAbilityName() == null || observation.getObservedLevel() == null) {
                continue;
            }
            PersonAbilityClaim claim = new PersonAbilityClaim();
            claim.setEmpId(observation.getEmpId());
            claim.setTagId(observation.getTagId());
            claim.setAbilityName(observation.getAbilityName());
            claim.setNormalizedAbilityName(observation.getAbilityName());
            claim.setClaimedLevel(observation.getObservedLevel());
            claim.setSourceType("AI_INTERVIEW");
            claim.setSourceRefId(observation.getSessionId());
            claim.setSourceWeight(BigDecimal.ONE);
            claim.setEvidenceText(observation.getEvidenceText() != null
                    ? observation.getEvidenceText() : observation.getInterviewConclusion());
            claim.setSourceRefsJson(observation.getSourceRefsJson() != null
                    ? observation.getSourceRefsJson() : "[\"source:AI_INTERVIEW:" + observation.getSessionId() + "\"]");
            claim.setConfidenceScore(observation.getConfidenceScore() != null
                    ? observation.getConfidenceScore() : BigDecimal.valueOf(60));
            claim.setStatus("ACTIVE");
            // 未归类观察（tagId 为 null，无法映射到 scope 内标签）：保存为未归类观察，不进入画像
            if (observation.getTagId() == null) {
                claim.setEvidenceStatus(
                        com.example.matching.common.enums.EvidenceStatusEnum.UNCLASSIFIED_OBSERVATION.getCode());
            }
            claims.add(claim);
        }
        return claims;
    }
}
