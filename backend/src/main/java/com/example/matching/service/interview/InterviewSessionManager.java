package com.example.matching.service.interview;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.dto.interview.AnswerQualityEvaluation;
import com.example.matching.dto.interview.FollowUpDecision;
import com.example.matching.entity.employee.EmpResumeParse;
import com.example.matching.entity.employee.EmpVideoInterviewQuestion;
import com.example.matching.entity.employee.EmpVideoInterviewSession;
import com.example.matching.entity.interview.InterviewConversationState;
import com.example.matching.entity.interview.InterviewFollowUpQuestion;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.event.InterviewActionEvent;
import com.example.matching.event.InterviewFinishedEvent;
import com.example.matching.event.InterviewWsEvent;
import com.example.matching.mapper.employee.EmpResumeParseMapper;
import com.example.matching.mapper.employee.EmpVideoInterviewQuestionMapper;
import com.example.matching.mapper.employee.EmpVideoInterviewSessionMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.infrastructure.llm.memory.ChatMemoryProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * 面试会话管理器（业务层）
 * <p>
 * 定时器管理委托给 {@link InterviewTimerManager}，
 * 面试结束后的 AI 分析通过 {@link InterviewFinishedEvent} 异步处理。
 */
@Slf4j
@Component
public class InterviewSessionManager {

    private final EmpVideoInterviewSessionMapper sessionMapper;
    private final EmpVideoInterviewQuestionMapper questionMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final InterviewConversationStateService stateService;
    private final InterviewAnswerQualityService qualityService;
    private final InterviewFollowUpPolicyService policyService;
    private final InterviewFollowUpGenerationService generationService;
    private final InterviewFollowUpRuntimeService runtimeService;
    private final AbilityTagMapper abilityTagMapper;
    private final ObjectMapper objectMapper;
    private final InterviewTimerManager timerManager;
    private final InterviewDurationPolicy durationPolicy;

    private final EmpResumeParseMapper resumeParseMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final InterviewSessionContextSupport contextSupport;
    private final InterviewSessionStateSupport stateSupport;
    private final InterviewTranscriptBuffer transcriptBuffer;
    private final Executor interviewRealtimeExecutor;

    /** 会话级聊天记忆（Redis/Caffeine），面试结束时清理；未启用时为 null */
    @Autowired(required = false)
    private ChatMemoryProvider chatMemoryProvider;

    private static final String REDIS_KEY_PREFIX_IDX = "interview:qidx:";
    private static final String REDIS_KEY_PREFIX_START = "interview:start:";
    private static final Duration REDIS_TTL = Duration.ofMinutes(60);

    // 会话ID -> 当前题索引


    // 会话ID -> 面试开始时间
    private final Map<String, LocalDateTime> interviewStartTimes = new ConcurrentHashMap<>();

    private static final int STATUS_CREATED = 0;
    private static final int STATUS_QUESTION_GENERATED = 1;
    private static final int STATUS_IN_PROGRESS = 2;
    private static final int STATUS_FINISHED = 3;

    private static final String ENDED_BY_TIMEOUT = "TIMEOUT";
    private static final String ENDED_BY_MANUAL_NEXT = "MANUAL_NEXT";

    private static final int MAX_INTERVIEW_DURATION_MINUTES = 45;

    public InterviewSessionManager(
            EmpVideoInterviewSessionMapper sessionMapper,
            EmpVideoInterviewQuestionMapper questionMapper,
            ApplicationEventPublisher eventPublisher,
            InterviewConversationStateService stateService,
            InterviewAnswerQualityService qualityService,
            InterviewFollowUpPolicyService policyService,
            InterviewFollowUpGenerationService generationService,
            InterviewFollowUpRuntimeService runtimeService,
            AbilityTagMapper abilityTagMapper,
            ObjectMapper objectMapper,
            InterviewTimerManager timerManager,
            InterviewDurationPolicy durationPolicy,
            @Autowired(required = false) EmpResumeParseMapper resumeParseMapper,
            @Autowired(required = false) StringRedisTemplate stringRedisTemplate,
            InterviewSessionContextSupport contextSupport,
            InterviewSessionStateSupport stateSupport,
            InterviewTranscriptBuffer transcriptBuffer,
            @Qualifier("interviewRealtimeExecutor") Executor interviewRealtimeExecutor) {
        this.sessionMapper = sessionMapper;
        this.questionMapper = questionMapper;
        this.eventPublisher = eventPublisher;
        this.stateService = stateService;
        this.qualityService = qualityService;
        this.policyService = policyService;
        this.generationService = generationService;
        this.runtimeService = runtimeService;
        this.abilityTagMapper = abilityTagMapper;
        this.objectMapper = objectMapper;
        this.timerManager = timerManager;
        this.durationPolicy = durationPolicy;
        this.resumeParseMapper = resumeParseMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.contextSupport = contextSupport;
        this.stateSupport = stateSupport;
        this.transcriptBuffer = transcriptBuffer;
        this.interviewRealtimeExecutor = interviewRealtimeExecutor;
    }

    // ==================== 监听用户操作事件 ====================

    @EventListener
    public void onManualNext(InterviewActionEvent event) {
        if (event.getType() == InterviewActionEvent.Type.MANUAL_NEXT) {
            try {
                handleManualNext(event.getSessionId());
            } catch (Exception e) {
                log.error("处理手动下一题失败: {}", e.getMessage(), e);
            }
        }
    }

    // ==================== 面试流程控制 ====================

    /**
     * 状态转换只由 SessionManager 负责：条件更新仅允许 status=1(QUESTION_GENERATED)
     * -> status=2(IN_PROGRESS)，防止重复 START 覆盖进行中会话。
     *
     * @return true=转换成功；false=当前状态不允许（重复/非法 START）
     */
    public boolean markInterviewInProgress(Long sessionId) {
        return sessionMapper.transitionStatus(sessionId, STATUS_QUESTION_GENERATED, STATUS_IN_PROGRESS) == 1;
    }

    /**
     * 统一面试启动状态机（WS START 与 REST startInterview 共用）：
     * 1. 最前面执行条件状态迁移 status=1 -> status=2；
     * 2. 初始化题目索引与会话状态；
     * 3. 推送欢迎消息与第 0 题。
     * <p>
     * 失败语义：尚未推题时回滚到 status=1 允许重新开始；已推题后失败保持
     * status=2，由 RESUME 恢复。
     */
    public void startInterviewFlow(String sessionId) throws Exception {
        Long id = Long.parseLong(sessionId);

        if (!markInterviewInProgress(id)) {
            // 区分拒绝原因给出明确提示：status=0 是题目未生成的半成品会话（新流程异常残留），
            // status=2 是已在进行中（WS/REST 竞争条件下先到者已启动，后到者幂等返回），
            // status>=3 是已结束——不再笼统提示"恢复面试"造成新流程误报。
            com.example.matching.entity.employee.EmpVideoInterviewSession session =
                    sessionMapper.selectById(id);
            if (session != null && session.getStatus() != null
                    && session.getStatus() == STATUS_IN_PROGRESS) {
                log.info("面试已在运行中，忽略重复的 start 请求: sessionId={}", sessionId);
                return;
            }
            String hint = session != null && session.getStatus() != null && session.getStatus() == 0
                    ? "面试题目尚未准备好，请返回评估流程重新发起面试"
                    : "该面试已在进行或已结束，请查看面试结果";
            log.warn("拒绝重复/非法 START_INTERVIEW: sessionId={}, status={}",
                    sessionId, session != null ? session.getStatus() : "?");
            throw new IllegalStateException(hint);
        }

        boolean questionPushed = false;
        try {
            stateSupport.putQuestionIndex(sessionId, 0);
            contextSupport.backupQuestionIndexToRedis(sessionId, 0);

            LocalDateTime interviewStartedAt = LocalDateTime.now();
            interviewStartTimes.put(sessionId, interviewStartedAt);
            stateSupport.persistInterviewStartedAt(id, interviewStartedAt);
            contextSupport.backupInterviewStartToRedis(sessionId, interviewStartedAt);

            stateService.initState(id);

            List<EmpVideoInterviewQuestion> questions = stateSupport.loadQuestions(id);
            if (questions.isEmpty()) {
                throw new IllegalStateException("没有面试题目");
            }

            eventPublisher.publishEvent(InterviewWsEvent.sendMessage(sessionId, "SEND_MESSAGE",
                    "你好！我是今天的 AI 面试官，将围绕岗位要求进行结构化面试。请放松，展示你最真实的水平。准备好了我们就开始。"));

            pushQuestionAwaitingReadCompletion(sessionId, questions, 0);
            questionPushed = true;
        } catch (Exception e) {
            if (!questionPushed) {
                // 尚未推题：回滚到 status=1，允许重新开始；已推题则保持 status=2 供 RESUME
                int rolledBack = sessionMapper.transitionStatus(id, STATUS_IN_PROGRESS, STATUS_QUESTION_GENERATED);
                log.warn("面试启动失败，已回滚到待开始状态: sessionId={}, rolledBack={}, error={}",
                        sessionId, rolledBack, e.getMessage());
            }
            throw e;
        }
    }

    public void startInterview(Long sessionId) throws Exception {
        startInterviewFlow(String.valueOf(sessionId));
    }

    /**
     * 会话是否允许 START（status=1 题目已生成待开始）。
     * WS/REST 入口守卫使用；最终仲裁仍由 startInterviewFlow 的条件更新完成。
     */
    public boolean isStartable(Long sessionId) {
        EmpVideoInterviewSession session = sessionMapper.selectById(sessionId);
        return session != null && session.getStatus() != null && session.getStatus() == STATUS_QUESTION_GENERATED;
    }

    /**
     * 会话是否进行中（status=2）。WS RESUME 入口守卫使用。
     */
    public boolean isActive(Long sessionId) {
        EmpVideoInterviewSession session = sessionMapper.selectById(sessionId);
        return session != null && session.getStatus() != null && session.getStatus() == STATUS_IN_PROGRESS;
    }

    /**
     * 按题目序号解析题目 ID（供 WS 推送当前题目时绑定转写缓冲）。
     */
    public Long resolveQuestionId(Long sessionId, int questionOrder) {
        List<EmpVideoInterviewQuestion> questions = stateSupport.loadQuestions(sessionId);
        for (EmpVideoInterviewQuestion q : questions) {
            if (q.getQuestionOrder() != null && q.getQuestionOrder() == questionOrder) {
                return q.getId();
            }
        }
        return null;
    }

    public void handleManualNext(String sessionId) throws Exception {
        log.info("手动下一题，sessionId: {}", sessionId);
        Long id = Long.parseLong(sessionId);

        InterviewConversationState state = stateService.getState(id);
        if (state == InterviewConversationState.EVALUATING_ANSWER) {
            eventPublisher.publishEvent(InterviewWsEvent.sendMessage(sessionId, "SEND_MESSAGE",
                    "正在分析当前回答并决定是否追问，请稍候。"));
            return;
        }
        if (state == InterviewConversationState.ANSWERING_FOLLOW_UP) {
            handleFollowUpAnswered(id, ENDED_BY_MANUAL_NEXT);
        } else {
            EvaluationWork work = prepareAnswerEvaluation(id, ENDED_BY_MANUAL_NEXT);
            if (work == null) {
                return;
            }
            eventPublisher.publishEvent(InterviewWsEvent.answerAnalysisStarted(sessionId));
            try {
                interviewRealtimeExecutor.execute(() -> runAnswerEvaluation(work));
            } catch (RejectedExecutionException e) {
                // Do not drop an answer when the realtime queue is temporarily saturated.
                log.warn("面试实时核验队列已满，回退同步处理: sessionId={}", sessionId);
                runAnswerEvaluation(work);
            }
        }
    }

    public void nextQuestion(Long sessionId, String endedBy) throws Exception {
        EvaluationWork work = prepareAnswerEvaluation(sessionId, endedBy);
        if (work != null) {
            evaluatePreparedAnswer(work);
        }
    }

    /** Freezes the same answer window for both timeout and manual-next paths. */
    private EvaluationWork prepareAnswerEvaluation(Long sessionId, String endedBy) throws Exception {
        String sessionKey = sessionId.toString();

        if (stateService.getState(sessionId) == InterviewConversationState.EVALUATING_ANSWER) {
            log.info("当前回答仍在评估，忽略重复切题请求: sessionId={}", sessionId);
            return null;
        }

        // 总时长上限检查
        LocalDateTime startTime = interviewStartTimes.get(sessionKey);
        if (startTime != null) {
            long elapsedMinutes = java.time.Duration.between(startTime, LocalDateTime.now()).toMinutes();
            if (elapsedMinutes >= MAX_INTERVIEW_DURATION_MINUTES) {
                log.warn("面试总时长超限，强制结束 sessionId={}, elapsed={}min", sessionKey, elapsedMinutes);
                eventPublisher.publishEvent(InterviewWsEvent.sendMessage(sessionKey, "SEND_MESSAGE",
                        "面试已进行 " + elapsedMinutes + " 分钟，达到时长上限，系统将自动结束面试。感谢你的参与！"));
                finishInterview(sessionId);
                return null;
            }
        }
        Integer currentIndex = stateSupport.getQuestionIndex(sessionKey);
        if (currentIndex == null) {
            throw new IllegalStateException("面试未开始");
        }

        timerManager.stopTimer(sessionKey);
        timerManager.stopCountdown(sessionKey);

        stateSupport.recordQuestionEnd(sessionId, currentIndex, endedBy);
        timerManager.clearAnswerWindow(sessionKey);

        stateSupport.requireStateTransition(sessionId, InterviewConversationState.ANSWERING_PRESET,
                InterviewConversationState.EVALUATING_ANSWER);

        List<EmpVideoInterviewQuestion> questions = stateSupport.loadQuestions(sessionId);
        // M14：评估前先冲刷缓冲中的最后一段转写，确保 answerTranscript 完整
        if (transcriptBuffer != null) {
            transcriptBuffer.flushCurrentQuestion(sessionId);
        }
        EmpVideoInterviewQuestion staleQuestion = questions.get(currentIndex);
        EmpVideoInterviewQuestion currentQuestion = staleQuestion.getId() == null ? null
                : questionMapper.selectById(staleQuestion.getId());
        if (currentQuestion == null) {
            throw new IllegalStateException("当前面试题不存在或转写刷新失败");
        }
        String answerText = currentQuestion.getAnswerTranscript();

        String[] abilityInfo = stateSupport.getAbilityInfo(currentQuestion);
        String resumeClaim = contextSupport.getResumeClaimForSession(sessionId);
        EmpVideoInterviewSession evaluationSession = sessionMapper.selectById(sessionId);
        long sessionVersion = evaluationSession == null || evaluationSession.getSessionVersion() == null
                ? -1L : evaluationSession.getSessionVersion();
        return new EvaluationWork(sessionId, sessionKey, questions, currentIndex, currentQuestion,
                answerText, abilityInfo, resumeClaim, sessionVersion);
    }

    /** Preserves the existing quality-evaluation then policy-controlled follow-up flow. */
    private void runAnswerEvaluation(EvaluationWork work) {
        try {
            evaluatePreparedAnswer(work);
        } catch (Exception e) {
            log.error("回答核验处理失败，按未评分处理并继续主流程: sessionId={}, questionId={}",
                    work.sessionId(), work.currentQuestion().getId(), e);
            continueAfterEvaluationFailure(work);
        }
    }

    /** A transient scoring failure must not trap an active interview in EVALUATING_ANSWER. */
    private void continueAfterEvaluationFailure(EvaluationWork work) {
        if (!isEvaluationActive(work)) {
            return;
        }
        try {
            work.currentQuestion().setAnswerScore(null);
            work.currentQuestion().setAnalysisComment("自动评分服务暂不可用，本题未评分且未触发追问");
            questionMapper.updateById(work.currentQuestion());
            proceedToNextQuestion(work.sessionId(), work.sessionKey(), work.questions(), work.currentIndex());
        } catch (Exception continuationError) {
            log.error("评分失败后的会话推进失败: sessionId={}, questionId={}",
                    work.sessionId(), work.currentQuestion().getId(), continuationError);
        }
    }

    private void evaluatePreparedAnswer(EvaluationWork work) throws Exception {
        Long sessionId = work.sessionId();
        AnswerQualityEvaluation evaluation = qualityService.evaluate(
                sessionId, work.currentQuestion(), work.answerText(), work.abilityInfo()[0], work.abilityInfo()[1], work.resumeClaim());
        if (!isEvaluationActive(work)) {
            log.info("回答评估结果已过期，丢弃: sessionId={}, questionId={}", sessionId, work.currentQuestion().getId());
            return;
        }
        persistQuestionQualityResult(work.currentQuestion(), evaluation);

        List<InterviewFollowUpQuestion> existingFollowUps =
                runtimeService.getFollowUpsByParentQuestion(sessionId, work.currentQuestion().getId());
        FollowUpDecision decision = policyService.decide(
                sessionId, work.currentQuestion().getId(), evaluation, existingFollowUps);

        if (!isEvaluationActive(work)) {
            log.info("回答评估决策已过期，丢弃: sessionId={}, questionId={}", sessionId, work.currentQuestion().getId());
            return;
        }

        if (decision.shouldFollowUp()) {
            try {
                InterviewFollowUpQuestion followUp = generationService.generate(
                        decision, work.currentQuestion(), work.answerText(),
                        work.abilityInfo()[0], work.abilityInfo()[1], contextSupport.buildFollowUpRagContext(sessionId), evaluation, existingFollowUps);
                if (!isEvaluationActive(work)) {
                    log.info("追问生成结果已过期，丢弃: sessionId={}, questionId={}", sessionId, work.currentQuestion().getId());
                    return;
                }
                followUp.setSessionId(sessionId);
                followUp.setFollowUpOrder(existingFollowUps.size() + 1);
                followUp = runtimeService.saveAndPush(followUp);
                try {
                    runtimeService.saveQualityEvaluation(followUp.getId(), objectMapper.writeValueAsString(evaluation));
                } catch (Exception e) {
                    log.warn("保存评估结果失败: {}", e.getMessage());
                }
                stateSupport.requireStateTransition(sessionId, InterviewConversationState.EVALUATING_ANSWER,
                        InterviewConversationState.FOLLOW_UP_QUESTION);
                log.info("触发追问，sessionId={}, parentQuestionId={}, followUpId={}, type={}",
                        sessionId, work.currentQuestion().getId(), followUp.getId(), decision.followUpType());
            } catch (Exception e) {
                log.error("追问生成或保存失败，继续下一主问题: sessionId={}, questionId={}",
                        sessionId, work.currentQuestion().getId(), e);
                if (isEvaluationActive(work)) {
                    proceedToNextQuestion(sessionId, work.sessionKey(), work.questions(), work.currentIndex());
                }
            }
        } else {
            log.info("不需要追问，sessionId={}, reason={}", sessionId, decision.terminationReason());
            if (isEvaluationActive(work)) {
                proceedToNextQuestion(sessionId, work.sessionKey(), work.questions(), work.currentIndex());
            }
        }
    }

    public void handleFollowUpAnswered(Long sessionId, String endedBy) throws Exception {
        String sessionKey = sessionId.toString();

        timerManager.stopTimer(sessionKey);
        timerManager.stopCountdown(sessionKey);
        timerManager.clearAnswerWindow(sessionKey);

        InterviewFollowUpQuestion activeFollowUp = runtimeService.getActiveFollowUp(sessionId);
        if (activeFollowUp == null) {
            log.warn("没有活跃追问，直接进入下一题，sessionId={}", sessionId);
            List<EmpVideoInterviewQuestion> questions = stateSupport.loadQuestions(sessionId);
            Integer currentIndex = stateSupport.getQuestionIndex(sessionKey);
            if (currentIndex != null) {
                proceedToNextQuestion(sessionId, sessionKey, questions, currentIndex);
            }
            return;
        }

        stateSupport.requireStateTransition(sessionId, InterviewConversationState.ANSWERING_FOLLOW_UP,
                InterviewConversationState.EVALUATING_ANSWER);

        try {
            if (ENDED_BY_TIMEOUT.equals(endedBy)) {
                runtimeService.markSkipped(activeFollowUp.getId(), "追问回答超时，证据不足");
            } else {
                runtimeService.markAnswered(activeFollowUp.getId(), activeFollowUp.getAnswerText());
            }
        } catch (Exception e) {
            log.warn("保存追问回答状态失败，继续面试主流程: sessionId={}, followUpId={}",
                    sessionId, activeFollowUp.getId(), e);
        }

        // A follow-up only fills one evidence gap for its parent question. Never chain follow-ups.
        stateSupport.requireStateTransition(sessionId, InterviewConversationState.EVALUATING_ANSWER,
                InterviewConversationState.NEXT_OR_FINISH);
        List<EmpVideoInterviewQuestion> questions = stateSupport.loadQuestions(sessionId);
        Integer currentIndex = stateSupport.getQuestionIndex(sessionKey);
        if (currentIndex != null) {
            proceedToNextQuestion(sessionId, sessionKey, questions, currentIndex);
        }
    }

    public void finishInterview(Long sessionId) throws Exception {
        String sessionKey = sessionId.toString();

        timerManager.stopTimer(sessionKey);
        timerManager.stopCountdown(sessionKey);

        // 结束事件会异步触发报告分析。先把最后一段 ASR 缓冲落库，避免分析线程看到空回答。
        if (transcriptBuffer != null) {
            transcriptBuffer.flushSession(sessionId);
        }

        Integer currentIndex = stateSupport.getQuestionIndex(sessionKey);
        if (currentIndex != null) {
            stateSupport.recordQuestionEnd(sessionId, currentIndex, ENDED_BY_MANUAL_NEXT);
        } else {
            List<EmpVideoInterviewQuestion> questions = stateSupport.loadQuestions(sessionId);
            for (int i = questions.size() - 1; i >= 0; i--) {
                EmpVideoInterviewQuestion q = questions.get(i);
                if (q.getStartSecond() != null && q.getEndSecond() == null) {
                    stateSupport.recordQuestionEnd(sessionId, i, ENDED_BY_MANUAL_NEXT);
                    break;
                }
            }
        }

        EmpVideoInterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null || session.getStatus() == null || session.getStatus() != STATUS_IN_PROGRESS
                || sessionMapper.transitionStatus(sessionId, STATUS_IN_PROGRESS, STATUS_FINISHED) != 1) {
            log.info("面试已结束或状态已变化，忽略重复结束请求: sessionId={}", sessionId);
            return;
        }
        clearSessionMemory(sessionId);

        stateSupport.removeQuestionIndex(sessionKey);
        interviewStartTimes.remove(sessionKey);
        contextSupport.clearRedisBackups(sessionKey);
        timerManager.clearAnswerWindow(sessionKey);
        stateSupport.requireStateTransition(sessionId, null, InterviewConversationState.FINISHED);
        stateService.clearState(sessionId);
        runtimeService.clearActiveFollowUp(sessionId);

        eventPublisher.publishEvent(InterviewWsEvent.sendMessage(sessionKey, "SEND_MESSAGE",
                "面试结束，感谢你的时间和分享。系统正在分析你的表现，请稍候。"));
        eventPublisher.publishEvent(InterviewWsEvent.interviewFinished(sessionKey));

        // 通过事件异步触发 AI 分析，消除 @Lazy 循环依赖
        eventPublisher.publishEvent(new InterviewFinishedEvent(sessionId));

        log.info("面试结束，sessionId: {}", sessionId);
    }

    // ==================== 内部方法 ====================

    /**
     * 清理会话聊天记忆（Redis/Caffeine 两个实现均幂等）。
     * 清理失败只记录 ERROR，不回滚已完成的面试。
     */
    private void clearSessionMemory(Long sessionId) {
        if (chatMemoryProvider == null) {
            log.debug("ChatMemoryProvider 未启用，跳过会话记忆清理，sessionId={}", sessionId);
            return;
        }
        try {
            chatMemoryProvider.clear(sessionId);
            log.info("面试结束，已清理会话记忆，sessionId={}", sessionId);
        } catch (Exception e) {
            log.error("清理面试会话记忆失败，不影响已完成的面试，sessionId={}", sessionId, e);
        }
    }

    private boolean isEvaluationActive(Long sessionId) {
        EmpVideoInterviewSession session = sessionMapper.selectById(sessionId);
        return session != null && session.getStatus() != null && session.getStatus() == STATUS_IN_PROGRESS
                && stateService.getState(sessionId) == InterviewConversationState.EVALUATING_ANSWER;
    }

    private boolean isEvaluationActive(EvaluationWork work) {
        EmpVideoInterviewSession session = sessionMapper.selectById(work.sessionId());
        if (session == null || session.getStatus() == null || session.getStatus() != STATUS_IN_PROGRESS
                || stateService.getState(work.sessionId()) != InterviewConversationState.EVALUATING_ANSWER) {
            return false;
        }
        // Old rows created before optimistic versioning can have a null version. In that case,
        // the state/status guard above remains the compatibility safeguard.
        return work.sessionVersion() < 0 || session.getSessionVersion() == null
                || work.sessionVersion() == session.getSessionVersion();
    }

    private record EvaluationWork(
            Long sessionId,
            String sessionKey,
            List<EmpVideoInterviewQuestion> questions,
            int currentIndex,
            EmpVideoInterviewQuestion currentQuestion,
            String answerText,
            String[] abilityInfo,
            String resumeClaim,
            long sessionVersion) {
    }

    public void startAnswerPeriodAfterQuestionRead(Long sessionId, Integer questionOrder, Long followUpId) throws Exception {
        String sessionKey = sessionId.toString();
        InterviewConversationState state = stateService.getState(sessionId);

        if (state == InterviewConversationState.PRESET_QUESTION) {
            List<EmpVideoInterviewQuestion> questions = stateSupport.loadQuestions(sessionId);
            Integer index = stateSupport.resolveCurrentQuestionIndex(sessionId, sessionKey, questions);
            if (index == null) {
                log.info("忽略读题完成确认，当前主问题不存在: sessionId={}", sessionId);
                return;
            }
            if (index >= questions.size()) {
                log.warn("忽略读题完成确认，当前主问题索引无效: sessionId={}, index={}", sessionId, index);
                return;
            }
            EmpVideoInterviewQuestion question = questions.get(index);
            if (questionOrder == null || !questionOrder.equals(question.getQuestionOrder())) {
                log.info("忽略过期的主问题读题完成确认: sessionId={}, expectedOrder={}, actualOrder={}",
                        sessionId, question.getQuestionOrder(), questionOrder);
                return;
            }
            if (!stateService.transition(sessionId, InterviewConversationState.PRESET_QUESTION,
                    InterviewConversationState.ANSWERING_PRESET)) {
                return;
            }
            question.setStartSecond((int) (System.currentTimeMillis() / 1000));
            questionMapper.updateById(question);
            int durationSeconds = calculateDuration(question);
            timerManager.startAnswerTimer(sessionKey, durationSeconds, question.getQuestionOrder(), () -> {
                try {
                    nextQuestion(sessionId, ENDED_BY_TIMEOUT);
                } catch (Exception e) {
                    log.error("自动切题失败: {}", e.getMessage(), e);
                }
            });
            return;
        }

        if (state == InterviewConversationState.FOLLOW_UP_QUESTION) {
            InterviewFollowUpQuestion followUp = runtimeService.getActiveFollowUp(sessionId);
            if (followUp == null || followUpId == null || !followUpId.equals(followUp.getId())) {
                log.info("忽略过期的追问读题完成确认: sessionId={}, followUpId={}", sessionId, followUpId);
                return;
            }
            if (!stateService.transition(sessionId, InterviewConversationState.FOLLOW_UP_QUESTION,
                    InterviewConversationState.ANSWERING_FOLLOW_UP)) {
                return;
            }
            timerManager.startFollowUpTimer(sessionKey, durationPolicy.durationForFollowUp(followUp), followUp.getId(), () -> {
                try {
                    handleFollowUpAnswered(sessionId, ENDED_BY_TIMEOUT);
                } catch (Exception e) {
                    log.error("追问超时处理失败", e);
                }
            });
            return;
        }

        log.info("忽略读题完成确认，会话不处于待答状态: sessionId={}, state={}", sessionId, state);
    }

    private void pushQuestionAwaitingReadCompletion(String sessionKey, List<EmpVideoInterviewQuestion> questions,
                                                     int index) {
        EmpVideoInterviewQuestion question = questions.get(index);
        Long sessionId = Long.parseLong(sessionKey);

        int durationSeconds = calculateDuration(question);
        stateSupport.persistQuestionAwaitingRead(sessionId, question.getQuestionOrder());

        eventPublisher.publishEvent(InterviewWsEvent.pushQuestion(sessionKey, question.getQuestionOrder(),
                question.getQuestionText(), durationSeconds));

        log.info("推送题目，sessionId: {}，questionOrder: {}，duration: {}s", sessionKey, question.getQuestionOrder(), durationSeconds);
    }

    private void proceedToNextQuestion(Long sessionId, String sessionKey,
                                        List<EmpVideoInterviewQuestion> questions, int currentIndex) throws Exception {
        EmpVideoInterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null || session.getStatus() == null || session.getStatus() != STATUS_IN_PROGRESS) {
            log.info("会话已结束，拒绝继续下一题: sessionId={}", sessionId);
            return;
        }
        stateSupport.requireStateTransition(sessionId, null, InterviewConversationState.PRESET_QUESTION);

        int nextIndex = currentIndex + 1;
        if (nextIndex >= questions.size()) {
            finishInterview(sessionId);
        } else {
            stateSupport.putQuestionIndex(sessionKey, nextIndex);
            pushQuestionAwaitingReadCompletion(sessionKey, questions, nextIndex);
        }
    }

    private int calculateDuration(EmpVideoInterviewQuestion question) {
        // The policy is stateless; retain an answer window even in recovery/test paths
        // where the optional collaborator was not assembled.
        return (durationPolicy != null ? durationPolicy : new InterviewDurationPolicy()).durationForQuestion(question);
    }

    public InterviewResumeState recoverActiveSession(Long sessionId) throws Exception {
        EmpVideoInterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null || session.getStatus() == null || session.getStatus() != STATUS_IN_PROGRESS) {
            throw new IllegalStateException("面试会话未处于进行中状态");
        }
        if (session.getCurrentQuestionOrder() == null) {
            throw new IllegalStateException("面试会话没有当前题目");
        }

        String sessionKey = sessionId.toString();
        List<EmpVideoInterviewQuestion> questions = stateSupport.loadQuestions(sessionId);
        int currentQuestionIndexValue = stateSupport.findQuestionIndex(questions, session.getCurrentQuestionOrder());
        if (currentQuestionIndexValue < 0) {
            throw new IllegalStateException("当前面试题目不存在");
        }
        stateSupport.putQuestionIndex(sessionKey, currentQuestionIndexValue);
        EmpVideoInterviewQuestion currentQuestion = questions.get(currentQuestionIndexValue);
        InterviewFollowUpQuestion activeFollowUp = runtimeService.getActiveFollowUp(sessionId);
        InterviewConversationState conversationState = stateService.getState(sessionId);

        // The answer worker may still be running after a transport reconnect. Return its state
        // without restoring a timer or replaying the current question, otherwise a stale TTS can
        // overlap the eventual follow-up/next question.
        if (conversationState == InterviewConversationState.EVALUATING_ANSWER) {
            int durationSeconds = activeFollowUp == null
                    ? calculateDuration(currentQuestion)
                    : durationPolicy.durationForFollowUp(activeFollowUp);
            return new InterviewResumeState(
                    conversationState.name(),
                    currentQuestion.getQuestionOrder(),
                    currentQuestion.getQuestionText(),
                    activeFollowUp == null ? null : activeFollowUp.getId(),
                    activeFollowUp == null ? null : activeFollowUp.getFollowUpOrder(),
                    activeFollowUp == null ? null : activeFollowUp.getQuestionText(),
                    0L,
                    durationSeconds,
                    0,
                    session.getSessionVersion() == null ? 0L : session.getSessionVersion());
        }

        // PRESET/FOLLOW_UP means the client has not acknowledged that the question was read.
        // A stale deadline from a reconnect or an earlier question must not be treated as an
        // answer timeout: replay the question and let QUESTION_READ_COMPLETE open a new window.
        if (conversationState == InterviewConversationState.PRESET_QUESTION
                || conversationState == InterviewConversationState.FOLLOW_UP_QUESTION) {
            int durationSeconds = activeFollowUp == null
                    ? calculateDuration(currentQuestion)
                    : durationPolicy.durationForFollowUp(activeFollowUp);
            return new InterviewResumeState(
                    conversationState.name(),
                    currentQuestion.getQuestionOrder(),
                    currentQuestion.getQuestionText(),
                    activeFollowUp == null ? null : activeFollowUp.getId(),
                    activeFollowUp == null ? null : activeFollowUp.getFollowUpOrder(),
                    activeFollowUp == null ? null : activeFollowUp.getQuestionText(),
                    0L,
                    durationSeconds,
                    durationSeconds,
                    session.getSessionVersion() == null ? 0L : session.getSessionVersion());
        }

        if (session.getQuestionDeadlineAt() == null) {
            throw new IllegalStateException("面试会话没有可恢复的答题窗口");
        }

        timerManager.putAnswerStartTime(sessionKey, session.getQuestionStartedAt());
        timerManager.putAnswerDeadline(sessionKey, session.getQuestionDeadlineAt());
        if (session.getInterviewStartedAt() != null) {
            interviewStartTimes.put(sessionKey, session.getInterviewStartedAt());
        }

        LocalDateTime now = LocalDateTime.now();
        int remainingSeconds = InterviewResumeState.remainingSeconds(session.getQuestionDeadlineAt(), now);
        if (remainingSeconds == 0) {
            advanceExpiredAnswerWindow(sessionId);
            EmpVideoInterviewSession updatedSession = sessionMapper.selectById(sessionId);
            if (updatedSession == null || updatedSession.getStatus() == null
                    || updatedSession.getStatus() != STATUS_IN_PROGRESS) {
                return null;
            }
            return recoverActiveSession(sessionId);
        }

        timerManager.restoreTimers(sessionKey, sessionId, session.getQuestionDeadlineAt(), remainingSeconds, () -> {
            try {
                advanceExpiredAnswerWindow(sessionId);
            } catch (Exception ex) {
                log.error("恢复后的答题窗口超时处理失败，sessionId={}", sessionId, ex);
            }
        });

        int durationSeconds = activeFollowUp == null
                ? calculateDuration(currentQuestion)
                : durationPolicy.durationForFollowUp(activeFollowUp);
        return new InterviewResumeState(
                session.getConversationState(),
                currentQuestion.getQuestionOrder(),
                currentQuestion.getQuestionText(),
                activeFollowUp == null ? null : activeFollowUp.getId(),
                activeFollowUp == null ? null : activeFollowUp.getFollowUpOrder(),
                activeFollowUp == null ? null : activeFollowUp.getQuestionText(),
                session.getQuestionDeadlineAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                durationSeconds,
                remainingSeconds,
                session.getSessionVersion() == null ? 0L : session.getSessionVersion());
    }

    private void advanceExpiredAnswerWindow(Long sessionId) throws Exception {
        InterviewConversationState state = stateService.getState(sessionId);
        if (state == InterviewConversationState.ANSWERING_FOLLOW_UP) {
            handleFollowUpAnswered(sessionId, ENDED_BY_TIMEOUT);
            return;
        }
        if (state == InterviewConversationState.ANSWERING_PRESET) {
            nextQuestion(sessionId, ENDED_BY_TIMEOUT);
            return;
        }
        if (state != InterviewConversationState.EVALUATING_ANSWER) {
            log.info("忽略非答题状态的过期窗口: sessionId={}, state={}", sessionId, state);
        }
    }

    private void persistQuestionQualityResult(EmpVideoInterviewQuestion question,
                                              AnswerQualityEvaluation evaluation) {
        if (question == null || question.getId() == null || evaluation == null) {
            return;
        }
        question.setAnswerScore(BigDecimal.valueOf(evaluation.overallScore()));
        String conclusion = normalizeAnswerConclusion(evaluation.conclusion());
        question.setAnalysisComment(conclusion != null
                ? conclusion
                : "已完成本题回答质量核验");
        questionMapper.updateById(question);
    }

    private String normalizeAnswerConclusion(String conclusion) {
        if (conclusion == null || conclusion.isBlank()) return null;
        String value = conclusion.trim();
        return switch (value.toLowerCase(java.util.Locale.ROOT)) {
            case "answer is not supported", "answer not supported" -> "回答缺少可验证依据，暂不支持该能力结论";
            case "answer is partially supported", "answer partially supported" -> "回答提供了部分依据，仍需补充关键细节";
            case "answer is supported" -> "回答包含可验证依据";
            default -> value;
        };
    }


    public Integer getCurrentQuestionIndex(Long sessionId) {
        String key = sessionId.toString();
        Integer idx = stateSupport.getQuestionIndex(key);
        if (idx != null) return idx;
        if (stringRedisTemplate != null) {
            try {
                String val = stringRedisTemplate.opsForValue().get(REDIS_KEY_PREFIX_IDX + key);
                if (val != null) {
                    int recovered = Integer.parseInt(val);
                    stateSupport.putQuestionIndex(key, recovered);
                    return recovered;
                }
            } catch (Exception e) {
                log.debug("Failed to recover question index from Redis: sessionId={}", sessionId, e);
            }
        }
        EmpVideoInterviewSession session = sessionMapper.selectById(sessionId);
        if (session != null && session.getCurrentQuestionOrder() != null) {
            return session.getCurrentQuestionOrder();
        }
        return null;
    }

    public boolean isInterviewInProgress(Long sessionId) {
        return stateSupport.containsQuestionIndex(sessionId.toString());
    }
}


