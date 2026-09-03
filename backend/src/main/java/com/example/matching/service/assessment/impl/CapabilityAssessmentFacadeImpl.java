package com.example.matching.service.assessment.impl;

import com.example.matching.application.assessment.CapabilityAssessmentFacade;
import com.example.matching.common.enums.EligibilityEnum;
import com.example.matching.common.enums.EvidenceStatusEnum;
import com.example.matching.common.enums.StageTypeEnum;
import com.example.matching.common.enums.WorkflowStatusEnum;
import com.example.matching.dto.assessment.EligibilityPrecheckResult;
import com.example.matching.dto.assessment.ProvisionalAbilitySnapshotDTO;
import com.example.matching.dto.assessment.ResumeAbilityClaimDTO;
import com.example.matching.entity.workflow.PersonCapabilityStageRun;
import com.example.matching.entity.workflow.PersonCapabilityWorkflow;
import com.example.matching.service.assessment.AbilityEvidenceCollectionService;
import com.example.matching.service.assessment.AbilityProfileProjectionService;
import com.example.matching.service.assessment.CapabilityAssessmentWorkflowService;
import com.example.matching.service.assessment.ProvisionalMatchingSnapshotService;
import com.example.matching.service.assessment.impl.CapabilityAssessmentWorkflowServiceImpl;
import com.example.matching.vo.assessment.CapabilityAssessmentVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 人员能力评估应用层门面实现
 * <p>
 * 唯一工作流入口：状态推进、任务投递、结果查询。
 *
 * @author system
 */
@Slf4j
@Service
public class CapabilityAssessmentFacadeImpl implements CapabilityAssessmentFacade {

    private final CapabilityAssessmentWorkflowService workflowService;
    private final AbilityEvidenceCollectionService evidenceCollectionService;
    private final AbilityProfileProjectionService projectionService;
    private final ProvisionalMatchingSnapshotService snapshotService;
    private final com.example.matching.service.employee.AiTestService aiTestService;
    private final com.example.matching.mapper.workflow.AiTestCoverageMapper coverageMapper;
    private final com.example.matching.mapper.workflow.PersonAbilityClaimGroupMapper claimGroupMapper;
    private final com.example.matching.mapper.ability.PersonAbilityClaimMapper claimMapper;
    private final com.example.matching.service.employee.VideoInterviewService videoInterviewService;
    private final com.example.matching.mapper.employee.EmpAiTestMapper empAiTestMapper;
    private final com.example.matching.service.assessment.AggregateAbilityHarnessService aggregateHarnessService;
    private final com.example.matching.service.assessment.AbilityLevelConfirmationService levelConfirmationService;
    private final com.example.matching.port.assessment.CapabilityStageLifecycleEventPublisher lifecycleEventPublisher;
    private final com.example.matching.service.assessment.AssessmentReportService assessmentReportService;
    private final com.example.matching.service.assessment.AssessmentScopeService assessmentScopeService;
    private com.example.matching.service.assessment.CapabilityAssessmentOrchestrator assessmentOrchestrator;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public CapabilityAssessmentFacadeImpl(
            CapabilityAssessmentWorkflowService workflowService,
            AbilityEvidenceCollectionService evidenceCollectionService,
            AbilityProfileProjectionService projectionService,
            ProvisionalMatchingSnapshotService snapshotService,
            com.example.matching.service.employee.AiTestService aiTestService,
            com.example.matching.mapper.workflow.AiTestCoverageMapper coverageMapper,
            com.example.matching.mapper.workflow.PersonAbilityClaimGroupMapper claimGroupMapper,
            com.example.matching.mapper.ability.PersonAbilityClaimMapper claimMapper,
            com.example.matching.service.employee.VideoInterviewService videoInterviewService,
            com.example.matching.mapper.employee.EmpAiTestMapper empAiTestMapper,
            com.example.matching.service.assessment.AggregateAbilityHarnessService aggregateHarnessService,
            com.example.matching.service.assessment.AbilityLevelConfirmationService levelConfirmationService,
            com.example.matching.port.assessment.CapabilityStageLifecycleEventPublisher lifecycleEventPublisher,
            com.example.matching.service.assessment.AssessmentReportService assessmentReportService,
            com.example.matching.service.assessment.AssessmentScopeService assessmentScopeService,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.workflowService = workflowService;
        this.evidenceCollectionService = evidenceCollectionService;
        this.projectionService = projectionService;
        this.snapshotService = snapshotService;
        this.aiTestService = aiTestService;
        this.coverageMapper = coverageMapper;
        this.claimGroupMapper = claimGroupMapper;
        this.claimMapper = claimMapper;
        this.videoInterviewService = videoInterviewService;
        this.empAiTestMapper = empAiTestMapper;
        this.aggregateHarnessService = aggregateHarnessService;
        this.levelConfirmationService = levelConfirmationService;
        this.lifecycleEventPublisher = lifecycleEventPublisher;
        this.assessmentReportService = assessmentReportService;
        this.assessmentScopeService = assessmentScopeService;
        this.objectMapper = objectMapper;
    }

    @Autowired(required = false)
    void setAssessmentOrchestrator(com.example.matching.service.assessment.CapabilityAssessmentOrchestrator assessmentOrchestrator) {
        this.assessmentOrchestrator = assessmentOrchestrator;
    }

    @Override
    public CapabilityAssessmentVO.WorkflowView getOrCreateWorkflow(Long empId, Long operatorId) {
        PersonCapabilityWorkflow workflow = workflowService.getOrCreateActiveWorkflow(empId, operatorId);
        return toWorkflowView(workflow);
    }

    @Override
    public CapabilityAssessmentVO.WorkflowView getActiveWorkflow(Long empId) {
        PersonCapabilityWorkflow workflow = workflowService.getActiveWorkflow(empId);
        return workflow == null ? null : toWorkflowView(workflow);
    }

    @Override
    public CapabilityAssessmentVO.WorkflowView getWorkflow(Long workflowId) {
        return toWorkflowView(workflowService.getWorkflow(workflowId));
    }

    @Override
    public com.example.matching.dto.assessment.AssessmentScopeDTO getAssessmentScope(Long workflowId) {
        PersonCapabilityWorkflow workflow = workflowService.getWorkflow(workflowId);
        return assessmentOrchestrator != null
                ? assessmentOrchestrator.freezeScope(workflowId, workflow.getEmpId(), workflow.getPostId())
                : assessmentScopeService.build(workflowId, workflow.getEmpId(), workflow.getPostId());
    }

    @Override
    @Transactional
    public int submitResumeEvidence(Long empId, Long resumeParseId, List<ResumeAbilityClaimDTO> claims, Long operatorId) {
        PersonCapabilityWorkflow workflow = workflowService.getOrCreateActiveWorkflow(empId, operatorId);
        String status = workflow.getStatus();
        if (!WorkflowStatusEnum.RESUME_REQUIRED.getCode().equals(status)
                && !WorkflowStatusEnum.RESUME_PARSING.getCode().equals(status)
                && !WorkflowStatusEnum.RESUME_EVIDENCE_READY.getCode().equals(status)
                && !WorkflowStatusEnum.RESUME_PARSED_NO_EVIDENCE.getCode().equals(status)) {
            throw new IllegalStateException("当前工作流状态不允许提交简历证据: " + status);
        }
        // 创建简历解析阶段运行（PENDING，由协调器推进）
        String inputHash = CapabilityAssessmentWorkflowServiceImpl.hashInput(
                workflow.getId().toString(), "RESUME_PARSE", String.valueOf(resumeParseId));
        PersonCapabilityStageRun parseRun = workflowService.createStageRun(workflow.getId(),
                StageTypeEnum.RESUME_PARSE.getCode(), inputHash,
                "{\"resumeParseId\":" + resumeParseId + "}", "RESUME_PARSE", resumeParseId);

        // 创建证据提取阶段并保存证据
        String extractHash = CapabilityAssessmentWorkflowServiceImpl.hashInput(
                workflow.getId().toString(), "RESUME_CLAIM_EXTRACTION", String.valueOf(resumeParseId));
        PersonCapabilityStageRun extractRun = workflowService.createStageRun(workflow.getId(),
                StageTypeEnum.RESUME_CLAIM_EXTRACTION.getCode(), extractHash,
                "{\"resumeParseId\":" + resumeParseId + "}", "RESUME_PARSE", resumeParseId);

        int saved = evidenceCollectionService.saveResumeClaims(
                workflow.getId(), extractRun.getId(), empId, claims, operatorId);
        // 按能力聚合分组
        evidenceCollectionService.groupClaimsByAbility(workflow.getId(), empId);
        // 发布生命周期事件，由协调器推进：
        // 1. TASK_CLAIMED: RESUME_REQUIRED → RESUME_PARSING（任务被抢占）
        lifecycleEventPublisher.publish(com.example.matching.event.CapabilityStageLifecycleEvent.of(
                workflow.getId(), parseRun.getId(), StageTypeEnum.RESUME_PARSE.getCode(),
                "RESUME_PARSE", resumeParseId,
                com.example.matching.common.enums.StageLifecycleEventType.TASK_CLAIMED, null, null));
        // 2. RESUME_PARSE TASK_SUCCEEDED: RESUME_PARSING → RESUME_EVIDENCE_READY（解析完成）
        lifecycleEventPublisher.publish(com.example.matching.event.CapabilityStageLifecycleEvent.succeeded(
                workflow.getId(), parseRun.getId(), StageTypeEnum.RESUME_PARSE.getCode(),
                "RESUME_PARSE", resumeParseId));
        // 3. 证据提取：有证据 → TASK_SUCCEEDED，无证据 → NO_EVIDENCE
        if (saved > 0) {
            lifecycleEventPublisher.publish(com.example.matching.event.CapabilityStageLifecycleEvent.succeeded(
                    workflow.getId(), extractRun.getId(), StageTypeEnum.RESUME_CLAIM_EXTRACTION.getCode(),
                    "RESUME_PARSE", resumeParseId));
        } else {
            lifecycleEventPublisher.publish(com.example.matching.event.CapabilityStageLifecycleEvent.noEvidence(
                    workflow.getId(), extractRun.getId(), StageTypeEnum.RESUME_CLAIM_EXTRACTION.getCode(),
                    "RESUME_PARSE", resumeParseId));
        }
        return saved;
    }

    @Override
    @Transactional
    public com.example.matching.dto.assessment.GenerateVerificationTestResponse generateTest(
            Long workflowId, Long postId, Long operatorId) {
        PersonCapabilityWorkflow workflow = workflowService.getWorkflow(workflowId);
        String status = workflow.getStatus();
        if (!WorkflowStatusEnum.RESUME_EVIDENCE_READY.getCode().equals(status)
                && !WorkflowStatusEnum.RESUME_PARSED_NO_EVIDENCE.getCode().equals(status)
                && !WorkflowStatusEnum.TEST_GENERATING.getCode().equals(status)) {
            throw new IllegalStateException("当前工作流状态不允许生成验证测试: " + status);
        }
        // 岗位只提供测试上下文，不扩大冻结的简历能力范围；必须先验证其模型可用，
        // 以避免把无模型岗位写入工作流后才失败。
        if (postId == null) {
            throw new IllegalArgumentException("请先选择已配置能力模型的目标岗位");
        }
        aiTestService.assertWorkflowTestPostConfigured(postId);
        if (postId != null && workflow.getPostId() == null) {
            workflowService.bindPost(workflowId, postId);
            workflow.setPostId(postId);
        } else if (postId != null && !workflow.getPostId().equals(postId)) {
            throw new IllegalStateException("工作流已绑定岗位，不允许更换: "
                    + workflow.getPostId() + " -> " + postId);
        }
        // 前置校验：简历证据阶段必须已完成
        workflowService.assertStagePrerequisite(workflowId, "AI_TEST_GENERATION");
        Long selectedPostId = workflow.getPostId();
        // Resume claims define scope; the optional post only enriches it.
        // Re-run admission/grouping here as a repair boundary for workflows created
        // before the resume-to-taxonomy admission path was enabled.
        evidenceCollectionService.groupClaimsByAbility(workflowId, workflow.getEmpId());
        com.example.matching.dto.assessment.AssessmentScopeDTO scope =
                assessmentOrchestrator != null
                        ? assessmentOrchestrator.freezeScope(workflowId, workflow.getEmpId(), selectedPostId)
                        : assessmentScopeService.build(workflowId, workflow.getEmpId(), selectedPostId);
        // 生成验证测试（基于简历 Claim 与目标岗位，投递异步题目生成）
        com.example.matching.entity.employee.EmpAiTest test = aiTestService.generateWorkflowTest(
                workflow.getEmpId(), workflowId, selectedPostId, operatorId);
        // 阶段运行：题目生成已投递（PENDING，由协调器推进）；inputHash 绑定 scopeHash，重试复用同一范围
        String hash = CapabilityAssessmentWorkflowServiceImpl.hashInput(
                workflowId.toString(), "AI_TEST_GENERATION", scope.scopeHash());
        PersonCapabilityStageRun stageRun = workflowService.createStageRun(
                workflowId, "AI_TEST_GENERATION", hash,
                buildScopeSnapshotJson(selectedPostId, test.getId(), scope), "AI_TEST", test.getId());
        // 题目任务已投递：发布 TASK_CLAIMED，协调器推进工作流到 TEST_GENERATING
        lifecycleEventPublisher.publish(com.example.matching.event.CapabilityStageLifecycleEvent.of(
                workflowId, stageRun.getId(), "AI_TEST_GENERATION", "AI_TEST", test.getId(),
                com.example.matching.common.enums.StageLifecycleEventType.TASK_CLAIMED, null, null));
        com.example.matching.dto.assessment.GenerateVerificationTestResponse response =
                new com.example.matching.dto.assessment.GenerateVerificationTestResponse();
        response.setStageRun(toStageRunView(stageRun));
        response.setTestId(test.getId());
        response.setPostId(selectedPostId);
        return response;
    }

    /**
     * 序列化评估范围快照（含 scopeHash），写入 AI_TEST_GENERATION 阶段运行的 inputSnapshotJson，
     * 供题目生成上下文与事后追溯反查岗位要求、简历 Claim。
     */
    private String buildScopeSnapshotJson(Long postId, Long testId,
                                          com.example.matching.dto.assessment.AssessmentScopeDTO scope) {
        try {
            java.util.LinkedHashMap<String, Object> snapshot = new java.util.LinkedHashMap<>();
            snapshot.put("postId", postId);
            snapshot.put("testId", testId);
            snapshot.put("scopeHash", scope.scopeHash());
            snapshot.put("scope", scope);
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            throw new IllegalStateException("序列化评估范围快照失败", e);
        }
    }

    @Override
    @Transactional
    public PersonCapabilityStageRun submitTest(Long workflowId, Long testId,
                                               java.util.Map<String, Object> answers, Long operatorId) {
        PersonCapabilityWorkflow workflow = workflowService.getWorkflow(workflowId);
        String status = workflow.getStatus();
        if (!WorkflowStatusEnum.TEST_IN_PROGRESS.getCode().equals(status)
                && !WorkflowStatusEnum.TEST_EVALUATING.getCode().equals(status)) {
            throw new IllegalStateException("当前工作流状态不允许提交测试答案: " + status);
        }
        // 服务端归属查询：测试必须属于当前工作流、员工与岗位（不信任前端传入的 testId 归属）
        com.example.matching.entity.employee.EmpAiTest test = empAiTestMapper.selectById(testId);
        if (test == null) {
            throw new IllegalArgumentException("测试记录不存在: testId=" + testId);
        }
        if (!java.util.Objects.equals(test.getEmpId(), workflow.getEmpId())) {
            throw new IllegalStateException("测试归属员工不匹配: test.empId=" + test.getEmpId()
                    + ", workflow.empId=" + workflow.getEmpId());
        }
        if (!java.util.Objects.equals(test.getWorkflowId(), workflowId)) {
            throw new IllegalStateException("测试不属于该工作流: test.workflowId=" + test.getWorkflowId()
                    + ", workflowId=" + workflowId);
        }
        if (workflow.getPostId() != null && !java.util.Objects.equals(test.getPostId(), workflow.getPostId())) {
            throw new IllegalStateException("测试岗位与工作流绑定岗位不匹配: test.postId=" + test.getPostId()
                    + ", workflow.postId=" + workflow.getPostId());
        }
        Integer testStatus = test.getStatus();
        boolean answerable = testStatus == null || testStatus == 0;
        boolean alreadySubmitted = testStatus != null && testStatus == 1;
        if (!answerable && !alreadySubmitted) {
            throw new IllegalStateException("测试状态不允许提交答案: status=" + testStatus);
        }
        // 答案哈希作为幂等键：重复提交返回原 stageRun，不重复创建 AI_TEST_EVALUATION 评分 stageRun
        String answerHash = hashAnswers(answers);
        String hash = CapabilityAssessmentWorkflowServiceImpl.hashInput(
                workflowId.toString(), "AI_TEST_EVALUATION", String.valueOf(testId), answerHash);
        PersonCapabilityStageRun stageRun = workflowService.createStageRun(
                workflowId, "AI_TEST_EVALUATION", hash,
                "{\"testId\":" + testId + ",\"answerHash\":\"" + answerHash + "\"}", "AI_TEST", testId);
        // 仅首次提交触发评分任务；重复提交不重复触发
        if (answerable) {
            aiTestService.submitAnswers(testId, answers);
        }
        // 用户提交答案：发布 USER_ACTION_STARTED，协调器推进 TEST_IN_PROGRESS -> TEST_EVALUATING
        lifecycleEventPublisher.publish(com.example.matching.event.CapabilityStageLifecycleEvent.of(
                workflowId, stageRun.getId(), "AI_TEST_EVALUATION", "AI_TEST", testId,
                com.example.matching.common.enums.StageLifecycleEventType.USER_ACTION_STARTED, null, null));
        return stageRun;
    }

    private String hashAnswers(java.util.Map<String, Object> answers) {
        try {
            return CapabilityAssessmentWorkflowServiceImpl.hashInput(objectMapper.writeValueAsString(answers));
        } catch (Exception e) {
            throw new IllegalStateException("答案哈希计算失败", e);
        }
    }

    @Override
    @Transactional
    public com.example.matching.dto.assessment.CreateAssessmentInterviewResponse createInterview(Long workflowId, Long operatorId) {
        PersonCapabilityWorkflow workflow = workflowService.getWorkflow(workflowId);
        String status = workflow.getStatus();
        if (!WorkflowStatusEnum.TEST_EVIDENCE_READY.getCode().equals(status)
                && !WorkflowStatusEnum.INTERVIEW_PREPARING.getCode().equals(status)
                && !WorkflowStatusEnum.INTERVIEW_IN_PROGRESS.getCode().equals(status)) {
            throw new IllegalStateException("当前工作流状态不允许发起面试: " + status);
        }
        // 前置校验：测试证据阶段必须已完成
        workflowService.assertStagePrerequisite(workflowId, "AI_INTERVIEW");

        // 幂等复用：仅当评估流程"面试未完成"（准备中/进行中，用户中途退出重新进入）时，
        // 复用已有面试会话；否则（新评估流程或面试已结束进入后续阶段）一律重新生成，禁止复用。
        boolean interviewOngoing = WorkflowStatusEnum.INTERVIEW_PREPARING.getCode().equals(status)
                || WorkflowStatusEnum.INTERVIEW_IN_PROGRESS.getCode().equals(status);
        List<com.example.matching.entity.employee.EmpVideoInterviewSession> existing =
                interviewOngoing
                        ? videoInterviewService.lambdaQuery()
                                .eq(com.example.matching.entity.employee.EmpVideoInterviewSession::getWorkflowId, workflowId)
                                .in(com.example.matching.entity.employee.EmpVideoInterviewSession::getStatus, 0, 1, 2)
                                .orderByDesc(com.example.matching.entity.employee.EmpVideoInterviewSession::getId)
                                .last("LIMIT 1")
                                .list()
                        : List.of();
        if (!existing.isEmpty()) {
            com.example.matching.entity.employee.EmpVideoInterviewSession session = existing.get(0);
            // 题目未生成（status=0）：评估流程创建会话后无自动生成链路，这里补生成题目保证可直接开始
            if (session.getStatus() == 0) {
                try {
                    videoInterviewService.generateQuestions(session.getId(),
                            new com.example.matching.dto.employee.video.VideoInterviewQuestionGenerateDTO());
                } catch (Exception e) {
                    log.warn("面试会话题目补生成失败，可再次点击进入面试重试: sessionId={}, error={}",
                            session.getId(), e.getMessage());
                }
            }
            com.example.matching.dto.assessment.CreateAssessmentInterviewResponse resp =
                    new com.example.matching.dto.assessment.CreateAssessmentInterviewResponse();
            resp.setSessionId(session.getId());
            resp.setPostId(session.getPostId());
            log.info("复用已有面试会话: workflowId={}, sessionId={}, status={}",
                    workflowId, session.getId(), session.getStatus());
            return resp;
        }

        // 目标岗位：优先从工作流绑定的 post_id 获取（单一真相源），兜底从测试记录获取
        Long targetPostId = workflow.getPostId();
        if (targetPostId == null) {
            com.example.matching.entity.employee.EmpAiTest test = empAiTestMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<
                            com.example.matching.entity.employee.EmpAiTest>()
                            .eq(com.example.matching.entity.employee.EmpAiTest::getWorkflowId, workflowId)
                            .orderByDesc(com.example.matching.entity.employee.EmpAiTest::getId)
                            .last("LIMIT 1"));
            if (test == null || test.getPostId() == null) {
                throw new IllegalStateException("工作流无绑定岗位且无测试记录，无法确定面试目标岗位");
            }
            targetPostId = test.getPostId();
        }
        // 创建工作流面试会话
        com.example.matching.dto.employee.video.VideoInterviewCreateDTO dto =
                new com.example.matching.dto.employee.video.VideoInterviewCreateDTO();
        dto.setEmpId(workflow.getEmpId());
        dto.setPostId(targetPostId);
        dto.setWorkflowId(workflowId);
        dto.setSessionName("能力评估AI面试");
        dto.setInterviewMode("POST_BASED");
        com.example.matching.entity.employee.EmpVideoInterviewSession session =
                videoInterviewService.createSession(dto, operatorId);
        // 创建面试阶段运行（PENDING，由协调器推进）
        String hash = CapabilityAssessmentWorkflowServiceImpl.hashInput(
                workflowId.toString(), "AI_INTERVIEW", String.valueOf(session.getId()));
        PersonCapabilityStageRun stageRun = workflowService.createStageRun(workflowId, "AI_INTERVIEW", hash,
                "{\"sessionId\":" + session.getId() + "}", "AI_INTERVIEW", session.getId());
        // 关键修复：创建会话不等同于面试进行中。
        // 仅发布 TASK_CLAIMED（会话已创建、准备开始），协调器推进到 INTERVIEW_PREPARING；
        // 必须等会话初始化、首题生成完成（TASK_READY_FOR_USER）才进入 INTERVIEW_IN_PROGRESS。
        lifecycleEventPublisher.publish(com.example.matching.event.CapabilityStageLifecycleEvent.of(
                workflowId, stageRun.getId(), "AI_INTERVIEW", "AI_INTERVIEW", session.getId(),
                com.example.matching.common.enums.StageLifecycleEventType.TASK_CLAIMED, null, null));
        // 关键：创建会话后立即生成题目（status 0->1）。评估流程没有异步题目生成链路，
        // 若缺失则前端进入后发 START_INTERVIEW 会被拒（isStartable 要求 status=1）。
        // 题目生成失败必须抛出异常整体回滚——不允许留下 status=0 的半成品会话，
        // 否则会再次出现"面试当前状态不允许开始/请使用恢复面试"的误报。
        videoInterviewService.generateQuestions(session.getId(),
                new com.example.matching.dto.employee.video.VideoInterviewQuestionGenerateDTO());
        com.example.matching.dto.assessment.CreateAssessmentInterviewResponse resp = new com.example.matching.dto.assessment.CreateAssessmentInterviewResponse(); resp.setStageRun(toStageRunView(stageRun)); resp.setSessionId(session.getId()); resp.setPostId(targetPostId); return resp;
    }

    @Override
    @Transactional
    public PersonCapabilityStageRun finishInterview(Long workflowId, Long sessionId, Long operatorId) {
        PersonCapabilityWorkflow workflow = workflowService.getWorkflow(workflowId);
        String status = workflow.getStatus();
        if (!WorkflowStatusEnum.INTERVIEW_IN_PROGRESS.getCode().equals(status)
                && !WorkflowStatusEnum.INTERVIEW_ANALYZING.getCode().equals(status)) {
            throw new IllegalStateException("当前工作流状态不允许结束面试: " + status);
        }
        // 面试结束由现有会话管理器触发 AI 分析；工作流进入分析中，
        // 分析完成后由 InterviewPostAnalysisListener 发布成功事件推进聚合审核
        String hash = CapabilityAssessmentWorkflowServiceImpl.hashInput(
                workflowId.toString(), "AI_INTERVIEW", String.valueOf(sessionId));
        PersonCapabilityStageRun stageRun = workflowService.createStageRun(workflowId, "AI_INTERVIEW", hash,
                "{\"sessionId\":" + sessionId + ",\"analyzing\":true}", "AI_INTERVIEW", sessionId);
        // 候选人完成面试：发布 USER_ACTION_COMPLETED，协调器推进 INTERVIEW_IN_PROGRESS -> INTERVIEW_ANALYZING
        lifecycleEventPublisher.publish(com.example.matching.event.CapabilityStageLifecycleEvent.of(
                workflowId, stageRun.getId(), "AI_INTERVIEW", "AI_INTERVIEW", sessionId,
                com.example.matching.common.enums.StageLifecycleEventType.USER_ACTION_COMPLETED, null, null));
        return stageRun;
    }

    @Override
    public void retryStage(Long workflowId, String stageType, Long operatorId) {
        workflowService.retryStage(workflowId, stageType, operatorId);
        // 发布重试操作事件：协调器将 FAILED 工作流恢复到该阶段对应的可推进状态
        PersonCapabilityStageRun latest = workflowService.getLatestStageRun(workflowId, stageType);
        Long stageRunId = latest != null ? latest.getId() : null;
        Long sourceRefId = latest != null ? latest.getSourceRefId() : workflowId;
        lifecycleEventPublisher.publish(com.example.matching.event.CapabilityStageLifecycleEvent.of(
                workflowId, stageRunId, stageType,
                stageType, sourceRefId,
                com.example.matching.common.enums.StageLifecycleEventType.USER_ACTION_STARTED,
                null, null));
    }

    @Override
    public Map<String, Object> getProfile(Long empId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("confirmed", projectionService.getConfirmedProfile(empId));
        result.put("provisional", projectionService.getProvisionalView(empId));
        return result;
    }

    @Override
    public List<EligibilityPrecheckResult> precheckEligibility(List<Long> empIds, List<Long> postIds) {
        return snapshotService.precheck(empIds, postIds);
    }

    @Override
    public ProvisionalAbilitySnapshotDTO buildProvisionalSnapshot(Long empId, boolean acknowledged, Long operatorId) {
        return snapshotService.buildSnapshot(empId, acknowledged, operatorId);
    }

    @Override
    public List<com.example.matching.dto.assessment.HarnessBatchItemResultDTO> getHarnessResults(Long workflowId) {
        return aggregateHarnessService.getHarnessResults(workflowId);
    }

    @Override
    public List<com.example.matching.entity.workflow.PersonAbilityLevelDecision> listDecisions(Long workflowId) {
        return levelConfirmationService.listDecisions(workflowId);
    }

    @Override
    public com.example.matching.entity.workflow.PersonAbilityLevelDecision humanConfirmDecision(
            Long decisionId, Integer finalLevel, Integer finalConfidence, String reason, Long reviewerId) {
        return levelConfirmationService.humanConfirm(decisionId, finalLevel, finalConfidence, reason, reviewerId);
    }

    @Override
    public com.example.matching.entity.workflow.PersonAbilityLevelDecision humanRejectDecision(
            Long decisionId, String reason, Long reviewerId) {
        return levelConfirmationService.humanReject(decisionId, reason, reviewerId);
    }

    @Override
    public void recalculateDecisions(Long workflowId, String newPolicyVersion) {
        levelConfirmationService.recalculateByPolicy(workflowId, newPolicyVersion);
    }

    @Override
    public List<com.example.matching.port.assessment.AssessmentReportPort.WorkflowReportDTO> listAssessmentReports(Long empId) {
        return assessmentReportService.listByEmpId(empId);
    }

    @Override
    public com.example.matching.port.assessment.AssessmentReportPort.ReportDTO getAssessmentReport(Long workflowId) {
        return assessmentReportService.getByWorkflowId(workflowId);
    }

    private CapabilityAssessmentVO.WorkflowView toWorkflowView(PersonCapabilityWorkflow workflow) {
        CapabilityAssessmentVO.WorkflowView view = new CapabilityAssessmentVO.WorkflowView();
        view.setWorkflowId(workflow.getId());
        view.setEmpId(workflow.getEmpId());
        view.setWorkflowStatus(workflow.getStatus());
        view.setStatus(workflow.getStatus());
        view.setCurrentStage(workflow.getCurrentStage());
        view.setActiveStageRunId(workflow.getActiveStageRunId());
        view.setWorkflowVersion(workflow.getWorkflowVersion());
        view.setStartedAt(workflow.getStartedAt());
        view.setCompletedAt(workflow.getCompletedAt());
        view.setFailedReason(workflow.getFailedReason());
        view.setAvailableActions(availableActions(workflow.getStatus()));
        view.setNextStepHint(nextStepHint(workflow.getStatus()));
        view.setDisplayStatus(displayStatus(workflow.getStatus()));
        view.setStageRuns(buildStageRunViews(workflow.getId()));
        view.setCurrentStageDetail(buildCurrentStageDetail(workflow));
        // 推断证据结果：有 RESUME_CLAIM_EXTRACTION 阶段运行的 errorCode=NO_EVIDENCE 即无证据
        resolveEvidenceOutcome(view, workflow);
        return view;
    }

    /**
     * 推断证据结果：检查 RESUME_CLAIM_EXTRACTION 阶段运行是否发布过 NO_EVIDENCE。
     */
    private void resolveEvidenceOutcome(CapabilityAssessmentVO.WorkflowView view, PersonCapabilityWorkflow workflow) {
        PersonCapabilityStageRun extractRun = workflowService.getLatestStageRun(
                workflow.getId(), "RESUME_CLAIM_EXTRACTION");
        if (extractRun == null) {
            // 未开始证据提取（如工作流刚进入 RESUME_PARSING）
            return;
        }
        if ("NO_EVIDENCE".equals(extractRun.getFailureCode())) {
            view.setEvidenceOutcome("NO_EVIDENCE");
            view.setEvidenceFailureCode(extractRun.getFailureCode());
            view.setEvidenceFailureMessage(extractRun.getFailureMessage());
        } else if ("SUCCEEDED".equals(extractRun.getStatus())) {
            // 有证据被成功保存
            view.setEvidenceOutcome("GROUNDED");
        }
    }

    /**
     * 后端统一生成展示状态，前端直接展示，不自行推断。
     */
    private String displayStatus(String status) {
        return switch (WorkflowStatusEnum.fromCode(status)) {
            case RESUME_REQUIRED -> "待上传简历";
            case RESUME_PARSING -> "简历解析中";
            case RESUME_PARSED_NO_EVIDENCE -> "简历已解析（无可用证据）";
            case TEST_GENERATING -> "测试题目生成中";
            case TEST_IN_PROGRESS -> "测试进行中";
            case TEST_EVALUATING -> "测试评分中";
            case TEST_EVIDENCE_READY -> "测试证据就绪";
            case INTERVIEW_PREPARING -> "面试准备中";
            case INTERVIEW_IN_PROGRESS -> "面试进行中";
            case INTERVIEW_ANALYZING -> "面试分析中";
            case AGGREGATE_HARNESS_RUNNING -> "聚合审核中";
            case LEVEL_CONFIRMING -> "等级确认中";
            case REVIEW_REQUIRED -> "需人工复核";
            case RECOVERY_REQUIRED -> "需恢复失败阶段";
            case COMPLETED -> "已完成";
            case FAILED -> "已失败";
            case CANCELLED -> "已取消";
            default -> status;
        };
    }

    /**
     * 当前阶段运行详情（页面以工作流为主、阶段运行为证据、业务任务状态为补充）。
     */
    private CapabilityAssessmentVO.CurrentStageView buildCurrentStageDetail(PersonCapabilityWorkflow workflow) {
        CapabilityAssessmentVO.CurrentStageView detail = new CapabilityAssessmentVO.CurrentStageView();
        // 优先使用活跃阶段运行；否则按当前阶段类型取最近一条
        PersonCapabilityStageRun activeRun = workflow.getActiveStageRunId() != null
                ? workflowService.getStageRun(workflow.getActiveStageRunId()) : null;
        if (activeRun == null && workflow.getCurrentStage() != null) {
            activeRun = workflowService.getLatestStageRun(workflow.getId(), workflow.getCurrentStage());
        }
        if (activeRun == null) {
            return null;
        }
        detail.setStageType(activeRun.getStageType());
        detail.setRunStatus(activeRun.getStatus());
        detail.setSourceRefId(activeRun.getSourceRefId());
        detail.setUpdatedAt(activeRun.getUpdatedTime() != null ? activeRun.getUpdatedTime() : activeRun.getCreatedTime());
        detail.setFailureMessage(activeRun.getFailureMessage());
        detail.setRetryable(isRetryable(workflow.getStatus(), activeRun));
        return detail;
    }

    /**
     * 失败是否可重试：工作流 FAILED 且存在失败阶段运行。
     */
    private boolean isRetryable(String workflowStatus, PersonCapabilityStageRun stageRun) {
        if (!WorkflowStatusEnum.FAILED.getCode().equals(workflowStatus)
                && !WorkflowStatusEnum.RECOVERY_REQUIRED.getCode().equals(workflowStatus)) {
            return false;
        }
        return stageRun != null && ("FAILED_RETRYABLE".equals(stageRun.getStatus())
                || "FAILED_FINAL".equals(stageRun.getStatus()));
    }

    private List<CapabilityAssessmentVO.StageRunView> buildStageRunViews(Long workflowId) {
        List<com.example.matching.entity.workflow.PersonCapabilityStageRun> runs =
                workflowService.listStageRuns(workflowId);
        List<CapabilityAssessmentVO.StageRunView> views = new ArrayList<>();
        for (com.example.matching.entity.workflow.PersonCapabilityStageRun run : runs) {
            CapabilityAssessmentVO.StageRunView view = new CapabilityAssessmentVO.StageRunView();
            view.setStageRunId(run.getId());
            view.setStageType(run.getStageType());
            view.setStatus(run.getStatus());
            view.setAttemptCount(run.getAttemptCount());
            view.setStartedAt(run.getStartedAt());
            view.setCompletedAt(run.getCompletedAt());
            view.setFailureCode(run.getFailureCode());
            view.setFailureMessage(run.getFailureMessage());
            view.setSourceRefId(run.getSourceRefId());
            view.setSourceRefType(run.getSourceRefType());
            views.add(view);
        }
        return views;
    }

    private List<String> availableActions(String status) {
        List<String> actions = new ArrayList<>();
        switch (WorkflowStatusEnum.fromCode(status)) {
            case RESUME_REQUIRED -> actions.add("UPLOAD_RESUME");
            case RESUME_PARSING -> actions.add("VIEW_PROGRESS");
            case RESUME_EVIDENCE_READY, RESUME_PARSED_NO_EVIDENCE -> actions.add("GENERATE_TEST");
            case TEST_GENERATING -> {
                actions.add("VIEW_PROGRESS");
                actions.add("RETRY_FAILED_STAGE");
            }
            case TEST_IN_PROGRESS -> actions.add("VIEW_PROGRESS");
            case TEST_EVIDENCE_READY -> actions.add("CREATE_INTERVIEW");
            // 面试准备中：会话已创建待开始，允许再次发起/继续面试（createInterview 支持该状态，
            // 前端"发起 AI 面试/进入面试"按钮依赖该动作）
            case INTERVIEW_PREPARING -> actions.add("CREATE_INTERVIEW");
            // 面试进行中：中途退出重新进入评估流程需能"继续面试"（复用同一会话），
            // 必须暴露 CREATE_INTERVIEW 否则前端无进入入口
            case INTERVIEW_IN_PROGRESS -> {
                actions.add("CREATE_INTERVIEW");
                actions.add("VIEW_PROGRESS");
            }
            case INTERVIEW_ANALYZING,
                 AGGREGATE_HARNESS_RUNNING, LEVEL_CONFIRMING -> actions.add("VIEW_PROGRESS");
            case REVIEW_REQUIRED -> actions.add("REVIEW_DECISIONS");
            case RECOVERY_REQUIRED, FAILED -> actions.add("RETRY_FAILED_STAGE");
            case COMPLETED -> actions.add("VIEW_PROFILE");
            default -> {
            }
        }
        return actions;
    }

    private String nextStepHint(String status) {
        return switch (WorkflowStatusEnum.fromCode(status)) {
            case RESUME_REQUIRED -> "请上传简历开始能力评估";
            case RESUME_PARSING -> "简历解析中，请稍候";
            case RESUME_EVIDENCE_READY -> "简历证据已就绪，下一步：生成验证测试";
            case RESUME_PARSED_NO_EVIDENCE -> "简历已解析（无可用证据），可生成低证据验证测试";
            case TEST_GENERATING -> "验证测试生成中";
            case TEST_IN_PROGRESS -> "测试进行中，请完成作答";
            case TEST_EVIDENCE_READY -> "测试证据就绪，下一步：发起 AI 面试";
            case INTERVIEW_PREPARING -> "面试准备中";
            case INTERVIEW_IN_PROGRESS -> "面试进行中";
            case INTERVIEW_ANALYZING -> "面试分析中，完成后自动进入聚合审核";
            case AGGREGATE_HARNESS_RUNNING -> "聚合能力审核中";
            case LEVEL_CONFIRMING -> "最终等级确认中";
            case REVIEW_REQUIRED -> "存在待人工复核的能力，请前往复核";
            case RECOVERY_REQUIRED -> "当前阶段未完成，可恢复失败阶段；简历证据与已完成阶段将保留";
            case COMPLETED -> "评估完成，可查看画像并发起匹配";
            case FAILED -> "流程失败：" + "可重试失败阶段";
            default -> "";
        };
    }
}
