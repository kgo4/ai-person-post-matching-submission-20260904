package com.example.matching.service.interview;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.entity.interview.InterviewFollowUpQuestion;
import com.example.matching.event.InterviewWsEvent;
import com.example.matching.mapper.interview.InterviewFollowUpQuestionMapper;
import com.example.matching.mapper.employee.EmpVideoInterviewSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 追问运行时服务
 * <p>
 * 负责保存追问记录、更新追问状态、接收追问回答、把追问通过 WebSocket 推送到前端。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewFollowUpRuntimeService {

    private static final int MAX_TRIGGER_REASON_LENGTH = 2_000;

    private final InterviewFollowUpQuestionMapper followUpQuestionMapper;
    private final EmpVideoInterviewSessionMapper sessionMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final InterviewDurationPolicy durationPolicy;
    private final com.example.matching.service.common.DistributedLockService distributedLockService;
    private final com.example.matching.schedule.SchedulerMetrics schedulerMetrics;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.example.matching.schedule.ScheduledTaskRunner taskRunner;

    private static final String LOCK_NAME = "interview-followup-flush";

    /** 会话ID -> 当前活跃追问ID */
    private final ConcurrentHashMap<Long, Long> activeFollowUpMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, StringBuilder> pendingAnswers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Set<String>> acceptedAnswerSegments = new ConcurrentHashMap<>();

    /**
     * 保存追问并推送到前端
     */
    @Transactional
    public InterviewFollowUpQuestion saveAndPush(InterviewFollowUpQuestion followUp) {
        requireActiveSession(followUp.getSessionId());
        followUp.setTriggerReason(truncate(followUp.getTriggerReason(), MAX_TRIGGER_REASON_LENGTH));
        followUp.setDurationSeconds(durationPolicy.durationForFollowUp(followUp));
        followUp.setFollowUpStatus("SUGGESTED");
        followUp.setCreatedTime(LocalDateTime.now());
        followUp.setUpdatedTime(LocalDateTime.now());
        followUpQuestionMapper.insert(followUp);

        // 更新状态为 ASKED
        followUp.setFollowUpStatus("ASKED");
        followUpQuestionMapper.updateById(followUp);

        // 记录为当前活跃追问
        activeFollowUpMap.put(followUp.getSessionId(), followUp.getId());

        // 推送到前端
        if (isSessionInProgress(followUp.getSessionId())) {
            pushFollowUpToWebSocket(followUp);
        } else {
            followUp.setFollowUpStatus("SKIPPED");
            followUp.setFollowUpConclusion("会话已结束，追问结果已丢弃");
            followUp.setUpdatedTime(LocalDateTime.now());
            followUpQuestionMapper.updateById(followUp);
        }

        log.info("追问已保存并推送，followUpId={}, sessionId={}, parentQuestionId={}",
                followUp.getId(), followUp.getSessionId(), followUp.getParentQuestionId());

        return followUp;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }

    private void requireActiveSession(Long sessionId) {
        if (!isSessionInProgress(sessionId)) {
            throw new IllegalStateException("面试会话已结束，拒绝保存追问");
        }
    }

    private boolean isSessionInProgress(Long sessionId) {
        if (sessionId == null) return false;
        var session = sessionMapper.selectById(sessionId);
        return session != null && session.getStatus() != null && session.getStatus() == 2;
    }

    /**
     * 接收追问回答（ASR 转录回调）
     */
    public void appendFollowUpTranscript(Long sessionId, String text) {
        Long activeFollowUpId = activeFollowUpMap.get(sessionId);
        if (activeFollowUpId == null) {
            log.debug("当前会话没有活跃追问，sessionId={}", sessionId);
            return;
        }

        String normalized = normalizeSegment(text);
        if (normalized.isEmpty() || !acceptedAnswerSegments
                .computeIfAbsent(activeFollowUpId, ignored -> ConcurrentHashMap.newKeySet())
                .add(normalized)) {
            return;
        }
        pendingAnswers.compute(activeFollowUpId, (ignored, buffer) -> append(buffer, text));
    }

    /**
     * 标记追问完成
     */
    @Transactional
    public void markAnswered(Long followUpId, String answerText) {
        flushAnswer(followUpId);
        InterviewFollowUpQuestion followUp = followUpQuestionMapper.selectById(followUpId);
        if (followUp == null) return;

        if (answerText != null && !answerText.isBlank()) {
            followUp.setAnswerText(answerText);
        }
        followUp.setFollowUpStatus("ANSWERED");
        followUp.setUpdatedTime(LocalDateTime.now());
        followUpQuestionMapper.updateById(followUp);

        // 清除活跃追问
        activeFollowUpMap.remove(followUp.getSessionId());
        acceptedAnswerSegments.remove(followUpId);

        log.info("追问已标记完成，followUpId={}", followUpId);
    }

    /**
     * 标记追问跳过
     */
    @Transactional
    public void markSkipped(Long followUpId, String reason) {
        InterviewFollowUpQuestion followUp = followUpQuestionMapper.selectById(followUpId);
        if (followUp == null) return;

        followUp.setFollowUpStatus("SKIPPED");
        followUp.setFollowUpConclusion(reason);
        followUp.setUpdatedTime(LocalDateTime.now());
        followUpQuestionMapper.updateById(followUp);

        activeFollowUpMap.remove(followUp.getSessionId());
        acceptedAnswerSegments.remove(followUpId);

        log.info("追问已标记跳过，followUpId={}, reason={}", followUpId, reason);
    }

    /**
     * 获取当前活跃追问
     */
    public InterviewFollowUpQuestion getActiveFollowUp(Long sessionId) {
        Long activeFollowUpId = activeFollowUpMap.get(sessionId);
        if (activeFollowUpId != null) {
            return followUpQuestionMapper.selectById(activeFollowUpId);
        }

        InterviewFollowUpQuestion activeFollowUp = followUpQuestionMapper.selectOne(
                Wrappers.<InterviewFollowUpQuestion>lambdaQuery()
                        .eq(InterviewFollowUpQuestion::getSessionId, sessionId)
                        .eq(InterviewFollowUpQuestion::getFollowUpStatus, "ASKED")
                        .orderByDesc(InterviewFollowUpQuestion::getId)
                        .last("LIMIT 1"));
        if (activeFollowUp != null) {
            activeFollowUpMap.put(sessionId, activeFollowUp.getId());
        }
        return activeFollowUp;
    }

    /**
     * 获取当前题的已有追问列表
     */
    public List<InterviewFollowUpQuestion> getFollowUpsByParentQuestion(Long sessionId, Long parentQuestionId) {
        return followUpQuestionMapper.selectList(
                Wrappers.<InterviewFollowUpQuestion>lambdaQuery()
                        .eq(InterviewFollowUpQuestion::getSessionId, sessionId)
                        .eq(InterviewFollowUpQuestion::getParentQuestionId, parentQuestionId)
                        .ne(InterviewFollowUpQuestion::getFollowUpStatus, "SKIPPED")
                        .orderByAsc(InterviewFollowUpQuestion::getFollowUpOrder)
        );
    }

    /**
     * 获取会话的所有已回答追问
     */
    public List<InterviewFollowUpQuestion> getAnsweredFollowUps(Long sessionId) {
        return followUpQuestionMapper.selectList(
                Wrappers.<InterviewFollowUpQuestion>lambdaQuery()
                        .eq(InterviewFollowUpQuestion::getSessionId, sessionId)
                        .eq(InterviewFollowUpQuestion::getFollowUpStatus, "ANSWERED")
                        .orderByAsc(InterviewFollowUpQuestion::getFollowUpOrder)
        );
    }

    /**
     * 获取会话所有追问
     */
    public List<InterviewFollowUpQuestion> getAllFollowUps(Long sessionId) {
        return followUpQuestionMapper.selectList(
                Wrappers.<InterviewFollowUpQuestion>lambdaQuery()
                        .eq(InterviewFollowUpQuestion::getSessionId, sessionId)
                        .orderByAsc(InterviewFollowUpQuestion::getFollowUpOrder)
        );
    }

    /**
     * 保存回答质量评估到追问记录
     */
    @Transactional
    public void saveQualityEvaluation(Long followUpId, String evaluationJson) {
        InterviewFollowUpQuestion followUp = followUpQuestionMapper.selectById(followUpId);
        if (followUp == null) return;

        followUp.setQualityEvaluationJson(evaluationJson);
        followUp.setUpdatedTime(LocalDateTime.now());
        followUpQuestionMapper.updateById(followUp);
    }

    /**
     * 清除会话的活跃追问状态
     */
    public void clearActiveFollowUp(Long sessionId) {
        activeFollowUpMap.remove(sessionId);
    }

    @Scheduled(fixedDelay = 2000)
    public void flushPendingAnswers() {
        if (taskRunner != null) {
            taskRunner.run("interview_followup_flush", this::flushPendingAnswersInternal);
        } else {
            flushPendingAnswersInternal();
        }
    }

    private void flushPendingAnswersInternal() {
        var lock = distributedLockService.tryAcquire(LOCK_NAME);
        if (lock == null) {
            log.debug("Interview follow-up flush skipped: lock held by another instance");
            return;
        }
        try {
            pendingAnswers.keySet().forEach(this::flushAnswer);
        } catch (Exception e) {
            log.error("Interview follow-up flush failed, answers may be delayed", e);
            schedulerMetrics.recordFailure("interview_followup_flush");
        } finally {
            lock.close();
        }
    }

    private void flushAnswer(Long followUpId) {
        StringBuilder pending = pendingAnswers.remove(followUpId);
        if (pending == null || pending.isEmpty()) return;
        InterviewFollowUpQuestion followUp = followUpQuestionMapper.selectById(followUpId);
        if (followUp == null || "ANSWERED".equals(followUp.getFollowUpStatus())) return;
        String previous = followUp.getAnswerText();
        followUp.setAnswerText(previous == null || previous.isBlank() ? pending.toString() : previous.trim() + "\n" + pending);
        followUp.setUpdatedTime(LocalDateTime.now());
        followUpQuestionMapper.updateById(followUp);
    }

    private StringBuilder append(StringBuilder buffer, String text) {
        StringBuilder target = buffer == null ? new StringBuilder() : buffer;
        if (!target.isEmpty()) target.append('\n');
        target.append(text.trim());
        return target;
    }

    private String normalizeSegment(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "").trim();
    }

    /**
     * 通过 WebSocket 推送追问到前端
     */
    private void pushFollowUpToWebSocket(InterviewFollowUpQuestion followUp) {
        try {
            int durationSeconds = durationPolicy.durationForFollowUp(followUp);
            eventPublisher.publishEvent(InterviewWsEvent.pushFollowUpQuestion(
                    followUp.getSessionId().toString(),
                    followUp.getId(),
                    followUp.getParentQuestionId(),
                    followUp.getQuestionText(),
                    durationSeconds,
                    followUp.getFollowUpOrder() != null ? followUp.getFollowUpOrder() : 1
            ));
        } catch (Exception e) {
            log.error("推送追问到WebSocket失败: {}", e.getMessage(), e);
        }
    }
}
