package com.example.matching.service.employee.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.matching.common.exception.AiServiceException;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.dto.employee.video.VideoInterviewCreateDTO;
import com.example.matching.dto.employee.video.VideoInterviewFrameDTO;
import com.example.matching.dto.employee.video.VideoInterviewImportDTO;
import com.example.matching.dto.employee.video.VideoInterviewQuestionGenerateDTO;
import com.example.matching.entity.employee.*;
import com.example.matching.integration.volcengine.DoubaoChatClient;
import com.example.matching.integration.volcengine.VideoInterviewPromptBuilder;
import com.example.matching.mapper.employee.*;
import com.example.matching.port.post.PostQueryPort;
import com.example.matching.port.tag.TagQueryPort;
import com.example.matching.port.talent.TalentQueryPort;
import com.example.matching.service.interview.InterviewSessionManager;
import com.example.matching.service.interview.InterviewWebSocketTicketService;
import com.example.matching.service.ability.PersonAbilityProfileAgent;
import com.example.matching.service.employee.VideoInterviewService;
import com.example.matching.dto.interview.CompetencyReport;
import com.example.matching.service.interview.AIInterviewAgent;
import com.example.matching.vo.employee.video.VideoInterviewAbilityVO;
import com.example.matching.vo.employee.video.VideoInterviewDetailVO;
import com.example.matching.vo.employee.video.VideoInterviewWsTicketVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * AI视频面试服务实现
 * <p>
 * 职责：
 * 1. 创建/查询面试会话
 * 2. 管理状态流转：创建、题目已生成、进行中、结束、分析中、完成、失败、已导入
 * 3. 保存题目、证据、视频帧、转写文本等业务数据
 * 4. 调用 {@link AIInterviewAgent} 生成面试计划、执行面试观察、生成胜任力报告
 * 5. 调用 {@link PersonAbilityProfileAgent} 把面试观察纳入最终人员能力画像
 * <p>
 * 不再直接调用 DoubaoChatClient、VideoInterviewPromptBuilder。
 * 不再逐题 AI 分析和能力聚合，这些职责已迁移到 Agent 层。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoInterviewServiceImpl extends ServiceImpl<EmpVideoInterviewSessionMapper, EmpVideoInterviewSession> implements VideoInterviewService {

    private final EmpVideoInterviewSessionMapper sessionMapper;
    private final EmpVideoInterviewQuestionMapper questionMapper;
    private final EmpVideoInterviewEvidenceMapper evidenceMapper;
    private final EmpVideoInterviewAbilityMapper abilityMapper;
    private final TalentQueryPort talentQueryPort;
    private final PostQueryPort postQueryPort;
    private final TagQueryPort tagQueryPort;
    private final ObjectMapper objectMapper;
    private final InterviewSessionManager interviewSessionManager;
    private final InterviewWebSocketTicketService ticketService;
    private final AIInterviewAgent aiInterviewAgent;
    private final PersonAbilityProfileAgent personAbilityProfileAgent;
    private final VideoInterviewPromptBuilder promptBuilder;
    private final VideoInterviewVisualAnalyzer visualAnalyzer;
    private final VideoInterviewQuestionBuilder questionBuilder;
    private final com.example.matching.service.system.SystemAiModelConfigService systemAiModelConfigService;
    private final com.example.matching.port.assessment.CapabilityStageLifecycleEventPublisher lifecycleEventPublisher;
    private final com.example.matching.service.assessment.InterviewAssessmentEvidenceService interviewAssessmentEvidenceService;

    // ==================== 会话状态常量 ====================
    private static final int STATUS_CREATED = 0;
    private static final int STATUS_QUESTION_GENERATED = 1;
    private static final int STATUS_IN_PROGRESS = 2;
    private static final int STATUS_FINISHED = 3;
    private static final int STATUS_ANALYZING = 4;
    private static final int STATUS_COMPLETED = 5;
    private static final int STATUS_IMPORTED = 6;
    private static final int STATUS_FAILED = 7;

    @Qualifier("aiTaskExecutor")
    private final java.util.concurrent.Executor aiTaskExecutor;

    // ==================== 面试模式 ====================
    private static final String MODE_POST_BASED = "POST_BASED";

    // ==================== 问题类型 ====================
    private static final String Q_TYPE_TECHNICAL = "TECHNICAL";
    private static final String Q_TYPE_BEHAVIORAL = "BEHAVIORAL";
    private static final String Q_TYPE_GENERAL = "GENERAL";

    // ==================== 题目数量限制 ====================
    private static final int DEFAULT_QUESTION_COUNT = 6;
    private static final int MIN_QUESTION_COUNT = 3;
    private static final int MAX_QUESTION_COUNT = 10;

    // ==================== 证据类型 ====================
    private static final String EVIDENCE_TYPE_VISUAL = "VISUAL";

    // ==================== 核心业务方法 ====================

    @Override
    @Transactional
    public EmpVideoInterviewSession createSession(VideoInterviewCreateDTO dto, Long userId) {
        // 校验员工是否存在
        TalentQueryPort.EmployeeDTO employee = talentQueryPort.getEmployeeById(dto.getEmpId());
        if (employee == null) {
            throw new BusinessException(404, "员工不存在");
        }

        // POST_BASED模式必须指定岗位
        if (MODE_POST_BASED.equals(dto.getInterviewMode()) && dto.getPostId() == null) {
            throw new BusinessException(400, "基于岗位的面试模式必须指定岗位ID");
        }

        // 校验岗位是否存在
        if (dto.getPostId() != null) {
            long postModelCount = postQueryPort.countRequirementsByPostId(dto.getPostId());
            if (postModelCount == 0) {
                log.warn("岗位 {} 没有能力模型配置，将使用通用问题", dto.getPostId());
            }
        }

        EmpVideoInterviewSession session = new EmpVideoInterviewSession();
        session.setEmpId(dto.getEmpId());
        session.setPostId(dto.getPostId());
        session.setWorkflowId(dto.getWorkflowId());
        session.setSessionName(dto.getSessionName());
        session.setInterviewMode(dto.getInterviewMode());
        session.setStatus(STATUS_CREATED);
        session.setQuestionCount(0);
        session.setCreatedBy(userId);
        session.setCreatedTime(LocalDateTime.now());
        sessionMapper.insert(session);

        log.info("创建视频面试会话成功，会话ID: {}，员工ID: {}，模式: {}", session.getId(), dto.getEmpId(), dto.getInterviewMode());
        return session;
    }

    @Override
    @Transactional
    public void generateQuestions(Long sessionId, VideoInterviewQuestionGenerateDTO dto) {
        EmpVideoInterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(404, "会话不存在");
        }
        if (session.getStatus() != STATUS_CREATED) {
            throw new BusinessException(400, "会话状态不允许生成题目");
        }
        int questionCount = systemAiModelConfigService.getInterviewQuestionCount();
        try {
            aiInterviewAgent.generateInterviewPlan(new AIInterviewAgent.InterviewPlanRequest(
                    sessionId,
                    session.getEmpId(),
                    null,
                    session.getPostId(),
                    null,
                    null,
                    null,
                    null,
                    questionCount
            ));
        } catch (Exception e) {
            log.warn("AIInterviewAgent question generation failed, using fallback questions: {}", e.getMessage());
            if (session.getWorkflowId() != null) {
                throw new BusinessException(502,
                        "能力评估面试题生成失败，不能使用未绑定简历能力标签的通用题: " + e.getMessage());
            }
            List<EmpVideoInterviewQuestion> fallbackQuestions = questionBuilder.buildGeneralQuestions(sessionId, 1, questionCount);
            for (EmpVideoInterviewQuestion question : fallbackQuestions) {
                questionMapper.insert(question);
            }
        }
        long generatedCount = countGeneratedQuestions(sessionId);
        if (generatedCount == 0) {
            log.warn("No interview questions persisted after AI generation, using fallback questions: sessionId={}", sessionId);
            if (session.getWorkflowId() != null) {
                throw new BusinessException(502, "能力评估面试题未生成任何合规的简历能力核验题");
            }
            List<EmpVideoInterviewQuestion> fallbackQuestions = questionBuilder.buildGeneralQuestions(sessionId, 1, questionCount);
            for (EmpVideoInterviewQuestion question : fallbackQuestions) {
                questionMapper.insert(question);
            }
            generatedCount = countGeneratedQuestions(sessionId);
        }
        if (generatedCount == 0) {
            throw new BusinessException(500, "题目生成失败");
        }
        session.setStatus(STATUS_QUESTION_GENERATED);
        session.setQuestionCount((int) generatedCount);
        sessionMapper.updateById(session);
        log.info("Generated interview questions through AIInterviewAgent, sessionId={}, count={}",
                sessionId, session.getQuestionCount());
        // 面试会话初始化、首题生成完成：发布 TASK_READY_FOR_USER
        // 协调器将阶段运行置 WAITING_USER，工作流 INTERVIEW_PREPARING -> INTERVIEW_IN_PROGRESS
        publishLifecycleReadyForUser(session);
    }

    /**
     * 工作流面试会话就绪：发布 TASK_READY_FOR_USER 生命周期事件。
     */
    private void publishLifecycleReadyForUser(EmpVideoInterviewSession session) {
        if (session == null || session.getWorkflowId() == null) {
            return;
        }
        try {
            lifecycleEventPublisher.publish(com.example.matching.event.CapabilityStageLifecycleEvent.of(
                    session.getWorkflowId(), null, "AI_INTERVIEW",
                    "AI_INTERVIEW", session.getId(),
                    com.example.matching.common.enums.StageLifecycleEventType.TASK_READY_FOR_USER,
                    null, null));
        } catch (Exception e) {
            log.warn("发布面试就绪事件失败: sessionId={}, error={}", session.getId(), e.getMessage());
        }
    }

    @Override
    public VideoInterviewWsTicketVO issueWebSocketTicket(Long sessionId, Long userId) {
        EmpVideoInterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(404, "会话不存在");
        }
        if (userId == null || session.getCreatedBy() == null || !session.getCreatedBy().equals(userId)) {
            throw new BusinessException(403, "无权操作此会话");
        }
        InterviewWebSocketTicketService.IssuedTicket issued = ticketService.issue(sessionId, userId);
        return new VideoInterviewWsTicketVO(issued.ticket(), issued.expiresAt());
    }

    private long countGeneratedQuestions(Long sessionId) {
        Long generatedCount = questionMapper.selectCount(
                Wrappers.<EmpVideoInterviewQuestion>lambdaQuery()
                        .eq(EmpVideoInterviewQuestion::getSessionId, sessionId));
        return generatedCount != null ? generatedCount : 0L;
    }

    @Override
    @Transactional
    public void uploadFrame(Long sessionId, VideoInterviewFrameDTO dto) {
        EmpVideoInterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(404, "会话不存在");
        }
        if (dto.getImageDataUrl() == null || !dto.getImageDataUrl().startsWith("data:image/jpeg;base64,")) {
            throw new BusinessException(400, "仅支持JPEG抽帧数据");
        }

        EmpVideoInterviewEvidence evidence = new EmpVideoInterviewEvidence();
        evidence.setSessionId(sessionId);
        evidence.setQuestionId(questionBuilder.findQuestionId(sessionId, dto.getQuestionOrder()));
        evidence.setEvidenceType(EVIDENCE_TYPE_VISUAL);
        evidence.setStartSecond(dto.getCaptureSecond());
        evidence.setEndSecond(dto.getCaptureSecond());
        evidence.setEvidenceText("实时视频抽帧已采集，可用于后续多模态分析");
        evidence.setFrameRefsJson(visualAnalyzer.buildFrameRefsJson(sessionId, dto));
        evidence.setConfidenceScore(BigDecimal.ONE);
        evidenceMapper.insert(evidence);
    }

    @Override
    @Transactional
    public void startInterview(Long sessionId) {
        // 守卫：防止 WebSocket START 与 REST START 竞争导致重复启动报错。
        // WS 连接建立后前端会自动发送 START_INTERVIEW 消息，若用户同时点击开始按钮，
        // REST 路径应幂等处理——已在运行中的会话直接返回成功。
        if (!interviewSessionManager.isStartable(sessionId)) {
            if (interviewSessionManager.isActive(sessionId)) {
                log.info("面试已在运行中（WS已启动），忽略重复 REST start 请求，会话ID: {}", sessionId);
                return;
            }
            throw new BusinessException(400, "面试当前状态不允许开始，请刷新页面后重试");
        }
        try {
            interviewSessionManager.startInterview(sessionId);
            log.info("面试已开始，会话ID: {}", sessionId);
        } catch (AiServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("开始面试失败: {}", e.getMessage(), e);
            throw AiServiceException.retryable("Volcengine", "startInterview", "开始面试失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void nextQuestion(Long sessionId) {
        try {
            interviewSessionManager.nextQuestion(sessionId, "MANUAL_NEXT");
            log.info("手动切换下一题，会话ID: {}", sessionId);
        } catch (AiServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("切换下一题失败: {}", e.getMessage(), e);
            throw AiServiceException.retryable("Volcengine", "nextQuestion", "切换下一题失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void finishInterview(Long sessionId) {
        try {
            interviewSessionManager.finishInterview(sessionId);
            log.info("面试已结束，会话ID: {}", sessionId);
        } catch (AiServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("结束面试失败: {}", e.getMessage(), e);
            throw AiServiceException.retryable("Volcengine", "finishInterview", "结束面试失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void analyze(Long sessionId) {
        EmpVideoInterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(404, "会话不存在");
        }
        if (session.getStatus() == STATUS_FAILED) {
            // Manual reanalysis must return to FINISHED before the CAS below.
            if (com.example.matching.entity.interview.InterviewConversationState.EVALUATING_ANSWER.name()
                    .equals(session.getConversationState())) {
                throw new BusinessException(409, "面试回答仍在评估中，请先重新作答");
            }
            if (sessionMapper.resetFailedAnalysisToFinished(sessionId) == 1) {
                session = sessionMapper.selectById(sessionId);
            }
        }
        // CAS 抢占 FINISHED(3) -> ANALYZING(4)：与 InterviewPostAnalysisListener 的异步分析互斥，
        // 防止双跑（重复 LLM 调用与重复报告写入）
        int claimed = sessionMapper.update(null, Wrappers.<EmpVideoInterviewSession>lambdaUpdate()
                .eq(EmpVideoInterviewSession::getId, sessionId)
                .eq(EmpVideoInterviewSession::getStatus, STATUS_FINISHED)
                .set(EmpVideoInterviewSession::getStatus, STATUS_ANALYZING));
        if (claimed != 1) {
            log.info("视频面试不在待分析状态，跳过重复触发（可能正在分析或已完成），会话ID: {}", sessionId);
            return;
        }
        session.setStatus(STATUS_ANALYZING);

        try {
            visualAnalyzer.analyzeVisualEvidence(sessionId);

            aiInterviewAgent.conductInterviewAndObserve(sessionId);

            CompetencyReport report =
                    aiInterviewAgent.generateCompetencyReport(sessionId);

            if (report != null) {
                // 没有回答证据时正常完成但不伪造 0 分，前端据此显示“证据不足”。
                session.setOverallScore(report.degraded() ? null : BigDecimal.valueOf(report.overallScore()));
                session.setSummaryReport(report.conclusion());
            }

            session.setStatus(STATUS_COMPLETED);
            sessionMapper.updateById(session);

            // 工作流面试：分析完成后保存面试证据并推进聚合审核
            // （与 InterviewPostAnalysisListener 完整链路一致；失败时回退到 FINISHED(3)
            // 并记录失败原因，由 InterviewAnalysisRecoveryScheduler 自动重试）
            if (session.getWorkflowId() != null) {
                try {
                    interviewAssessmentEvidenceService.saveInterviewEvidenceAndAdvance(
                            session.getWorkflowId(), session.getEmpId(), sessionId);
                } catch (Exception e) {
                    log.error("面试证据保存/聚合推进失败，回退待重试: sessionId={}, error={}",
                            sessionId, e.getMessage(), e);
                    String reason = e.getMessage();
                    if (reason != null && reason.length() > 500) {
                        reason = reason.substring(0, 500) + "...";
                    }
                    sessionMapper.update(null, Wrappers.<EmpVideoInterviewSession>lambdaUpdate()
                            .eq(EmpVideoInterviewSession::getId, sessionId)
                            .eq(EmpVideoInterviewSession::getStatus, STATUS_COMPLETED)
                            .set(EmpVideoInterviewSession::getStatus, STATUS_FINISHED)
                            .set(EmpVideoInterviewSession::getAnalysisFailedReason, reason));
                }
            }

            // 能力画像构建作为独立后台任务，避免用户等待。
            // 仅非工作流面试（无 workflowId 的旧视频面试）走此路径；工作流面试的画像统一由
            // AbilityProfileProjectionService 在 AGGREGATE_HARNESS + LEVEL_CONFIRMATION 之后投影写入，
            // 此处不再旁路构建，避免绕过治理直接写 PersonAbilityProfile 造成双写/覆盖。
            if (session.getWorkflowId() == null) {
                Long analysisEmpId = session.getEmpId();
                CompletableFuture.runAsync(() -> {
                    try {
                        personAbilityProfileAgent.buildProfileWithInterview(analysisEmpId, sessionId);
                        log.info("能力画像构建完成: sessionId={}", sessionId);
                    } catch (Exception e) {
                        log.error("能力画像构建失败(不影响面试分析结果): sessionId={}, error={}", sessionId, e.getMessage(), e);
                    }
                }, aiTaskExecutor);
            }

        } catch (AiServiceException e) {
            session.setStatus(STATUS_FAILED);
            session.setErrorMessage("AI面试分析失败: " + e.getMessage());
            session.setAnalysisFailedReason(session.getErrorMessage());
            sessionMapper.updateById(session);
            publishAnalysisFailure(session, e.getClass().getSimpleName(), session.getErrorMessage());
            throw e;
        } catch (Exception e) {
            log.error("AI面试分析失败: {}", e.getMessage(), e);
            session.setStatus(STATUS_FAILED);
            session.setErrorMessage("AI面试分析失败: " + e.getMessage());
            session.setAnalysisFailedReason(session.getErrorMessage());
            sessionMapper.updateById(session);
            publishAnalysisFailure(session, e.getClass().getSimpleName(), session.getErrorMessage());
            throw AiServiceException.retryable("Volcengine", "analyzeInterview", "AI面试分析失败: " + e.getMessage(), e);
        }
    }

    private void publishAnalysisFailure(EmpVideoInterviewSession session, String errorCode, String message) {
        if (session == null || session.getWorkflowId() == null) {
            return;
        }
        try {
            lifecycleEventPublisher.publish(com.example.matching.event.CapabilityStageLifecycleEvent.failedFinal(
                    session.getWorkflowId(), null, "AI_INTERVIEW", "AI_INTERVIEW", session.getId(),
                    errorCode, message));
        } catch (Exception publishError) {
            log.warn("面试失败生命周期事件发布失败: sessionId={}, error={}",
                    session.getId(), publishError.getMessage());
        }
    }

    @Override
    @Async
    public void analyzeAsync(Long sessionId) {
        try {
            log.info("异步分析开始，会话ID: {}", sessionId);
            analyze(sessionId);
        } catch (Exception e) {
            log.error("异步分析失败，会话ID: {}, error: {}", sessionId, e.getMessage(), e);
        }
    }

    @Override
    public List<EmpVideoInterviewSession> listByEmpId(Long empId) {
        return sessionMapper.selectList(
                Wrappers.<EmpVideoInterviewSession>lambdaQuery()
                        .eq(EmpVideoInterviewSession::getEmpId, empId)
                        .orderByDesc(EmpVideoInterviewSession::getCreatedTime)
        );
    }

    @Override
    public List<EmpVideoInterviewSession> listAll() {
        return sessionMapper.selectList(
                Wrappers.<EmpVideoInterviewSession>lambdaQuery()
                        .orderByDesc(EmpVideoInterviewSession::getCreatedTime)
        );
    }

    @Override
    public VideoInterviewDetailVO getDetail(Long sessionId) {
        EmpVideoInterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(404, "会话不存在");
        }

        VideoInterviewDetailVO vo = new VideoInterviewDetailVO();
        vo.setId(session.getId());
        vo.setEmpId(session.getEmpId());
        vo.setPostId(session.getPostId());
        vo.setSessionName(session.getSessionName());
        vo.setInterviewMode(session.getInterviewMode());
        vo.setVideoFilePath(session.getVideoFilePath());
        vo.setTranscriptText(session.getTranscriptText());
        vo.setSummaryReport(session.getSummaryReport());
        vo.setOverallScore(session.getOverallScore());
        vo.setStatus(session.getStatus());
        vo.setDurationSeconds(session.getDurationSeconds());
        vo.setQuestionCount(session.getQuestionCount());
        vo.setErrorMessage(session.getErrorMessage());
        vo.setCreatedTime(session.getCreatedTime());
        vo.setUpdatedTime(session.getUpdatedTime());

        // 加载问题列表
        List<EmpVideoInterviewQuestion> questionEntities = questionMapper.selectList(
                Wrappers.<EmpVideoInterviewQuestion>lambdaQuery()
                        .eq(EmpVideoInterviewQuestion::getSessionId, sessionId)
                        .orderByAsc(EmpVideoInterviewQuestion::getQuestionOrder)
        );
        List<VideoInterviewDetailVO.QuestionItem> questionItems = questionEntities.stream().map(q -> {
            VideoInterviewDetailVO.QuestionItem item = new VideoInterviewDetailVO.QuestionItem();
            item.setId(q.getId());
            item.setQuestionOrder(q.getQuestionOrder());
            item.setQuestionType(q.getQuestionType());
            item.setQuestionText(q.getQuestionText());
            item.setAnswerTranscript(q.getAnswerTranscript());
            item.setAnswerSummary(q.getAnswerSummary());
            item.setStartSecond(q.getStartSecond());
            item.setEndSecond(q.getEndSecond());
            item.setAnswerScore(q.getAnswerScore());
            item.setAnalysisComment(q.getAnalysisComment());
            return item;
        }).collect(Collectors.toList());
        vo.setQuestions(questionItems);

        // 加载证据列表
        List<EmpVideoInterviewEvidence> evidenceEntities = evidenceMapper.selectList(
                Wrappers.<EmpVideoInterviewEvidence>lambdaQuery()
                        .eq(EmpVideoInterviewEvidence::getSessionId, sessionId)
        );
        List<VideoInterviewDetailVO.EvidenceItem> evidenceItems = evidenceEntities.stream().map(e -> {
            VideoInterviewDetailVO.EvidenceItem item = new VideoInterviewDetailVO.EvidenceItem();
            item.setId(e.getId());
            item.setQuestionId(e.getQuestionId());
            item.setEvidenceType(e.getEvidenceType());
            item.setStartSecond(e.getStartSecond());
            item.setEndSecond(e.getEndSecond());
            item.setEvidenceText(e.getEvidenceText());
            item.setConfidenceScore(e.getConfidenceScore());
            item.setRawScore(e.getRawScore());
            return item;
        }).collect(Collectors.toList());
        vo.setEvidences(evidenceItems);

        // 加载能力提取列表
        List<EmpVideoInterviewAbility> abilityEntities = abilityMapper.selectList(
                Wrappers.<EmpVideoInterviewAbility>lambdaQuery()
                        .eq(EmpVideoInterviewAbility::getSessionId, sessionId)
        );
        List<VideoInterviewAbilityVO> abilityVOs = abilityEntities.stream().map(a -> {
            VideoInterviewAbilityVO abilityVO = new VideoInterviewAbilityVO();
            abilityVO.setId(a.getId());
            abilityVO.setTagId(a.getTagId());
            TagQueryPort.TagDTO tag = tagQueryPort.getTagById(a.getTagId());
            abilityVO.setTagName(tag != null ? tag.tagName() : "未知标签");
            abilityVO.setMasteryLevel(a.getMasteryLevel());
            abilityVO.setConfidenceScore(a.getConfidenceScore());
            abilityVO.setSourceWeight(a.getSourceWeight());
            abilityVO.setEvidenceSummary(a.getEvidenceSummary());
            abilityVO.setAnalysisComment(a.getAnalysisComment());
            abilityVO.setImportedFlag(a.getImportedFlag() == 1);
            return abilityVO;
        }).collect(Collectors.toList());
        vo.setAbilities(abilityVOs);

        return vo;
    }

    /**
     * @deprecated 使用新的AI面试流程代替：
     * 1. AIInterviewAgent.conductInterviewAndObserve(sessionId) - 生成面试能力观察
     * 2. PersonAbilityProfileAgent.buildProfileWithInterview(empId, sessionId) - 构建人员能力画像
     * 旧接口保留兼容性，但不再直接写EmpAbility。
     */
    @Override
    @Transactional
    @Deprecated
    public void importToAbilityProfile(Long sessionId, VideoInterviewImportDTO dto, Long userId) {
        EmpVideoInterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(404, "视频面试会话不存在");
        }
        if (session.getStatus() != STATUS_COMPLETED) {
            throw new BusinessException(400, "会话未完成分析，当前状态: " + session.getStatus());
        }
        if (session.getWorkflowId() != null) {
            throw new BusinessException(400,
                    "该面试属于能力评估工作流，能力画像由聚合审核/等级确认后统一投影，禁止通过旧接口直接导入");
        }

        // 重定向到新流程：生成面试能力观察
        aiInterviewAgent.conductInterviewAndObserve(sessionId);

        // 重定向到新流程：构建人员能力画像（融合多来源）
        personAbilityProfileAgent.buildProfileWithInterview(session.getEmpId(), sessionId);

        // 更新会话状态
        session.setStatus(STATUS_IMPORTED);
        sessionMapper.updateById(session);

        log.info("Deprecated video interview import redirected to profile fusion, sessionId={}, empId={}",
                sessionId, session.getEmpId());
    }

    // ==================== 内部辅助方法 ====================


}
