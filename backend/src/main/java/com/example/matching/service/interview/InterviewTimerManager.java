package com.example.matching.service.interview;

import com.example.matching.entity.employee.EmpVideoInterviewSession;
import com.example.matching.event.InterviewWsEvent;
import com.example.matching.mapper.employee.EmpVideoInterviewSessionMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 面试定时器管理器
 * <p>
 * 管理面试过程中的所有定时任务：答题倒计时、超时自动切题、追问倒计时。
 * 与 {@link InterviewSessionManager} 解耦，通过 Runnable 回调通信。
 */
@Slf4j
@Component
public class InterviewTimerManager {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);

    private final Map<String, ScheduledFuture<?>> questionTimers = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> countdownFutures = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> answerStartTimes = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> answerDeadlineTimes = new ConcurrentHashMap<>();

    private final EmpVideoInterviewSessionMapper sessionMapper;
    private final ApplicationEventPublisher eventPublisher;

    public InterviewTimerManager(EmpVideoInterviewSessionMapper sessionMapper,
                                  ApplicationEventPublisher eventPublisher) {
        this.sessionMapper = sessionMapper;
        this.eventPublisher = eventPublisher;
    }

    @PreDestroy
    public void shutdown() {
        questionTimers.values().forEach(timer -> timer.cancel(false));
        countdownFutures.values().forEach(timer -> timer.cancel(false));
        scheduler.shutdownNow();
    }

    /**
     * 启动回答倒计时 + 超时回调
     */
    public void startAnswerTimer(String sessionKey, int durationSeconds, int questionOrder, Runnable onTimeout) {
        LocalDateTime startedAt = LocalDateTime.now();
        LocalDateTime deadlineAt = startedAt.plusSeconds(durationSeconds);
        Long sessionId = Long.parseLong(sessionKey);

        persistAnswerWindow(sessionId, questionOrder, startedAt, deadlineAt);
        answerStartTimes.put(sessionKey, startedAt);
        answerDeadlineTimes.put(sessionKey, deadlineAt);

        startCountdownPush(sessionKey, deadlineAt);

        ScheduledFuture<?> timer = scheduler.schedule(() -> {
            try {
                if (!isSessionInProgress(sessionId)) return;
                log.info("题目超时，自动切题，sessionId: {}，questionOrder: {}", sessionKey, questionOrder);
                onTimeout.run();
            } catch (Exception e) {
                log.error("自动切题失败: {}", e.getMessage(), e);
            }
        }, durationSeconds, TimeUnit.SECONDS);

        questionTimers.put(sessionKey, timer);
    }

    /**
     * 启动追问倒计时 + 超时回调
     */
    public void startFollowUpTimer(String sessionKey, int durationSeconds, Long followUpId, Runnable onTimeout) {
        int duration = durationSeconds;
        Long sessionId = Long.parseLong(sessionKey);

        LocalDateTime startedAt = LocalDateTime.now();
        LocalDateTime deadlineAt = startedAt.plusSeconds(duration);

        persistAnswerWindow(sessionId, resolveCurrentQuestionOrder(sessionId), startedAt, deadlineAt);
        answerStartTimes.put(sessionKey, startedAt);
        answerDeadlineTimes.put(sessionKey, deadlineAt);

        startCountdownPush(sessionKey, deadlineAt);

        ScheduledFuture<?> timer = scheduler.schedule(() -> {
            try {
                if (!isSessionInProgress(sessionId)) return;
                log.info("追问超时，自动评估，sessionId: {}，followUpId: {}", sessionKey, followUpId);
                onTimeout.run();
            } catch (Exception e) {
                log.error("追问超时处理失败: {}", e.getMessage(), e);
            }
        }, duration, TimeUnit.SECONDS);

        questionTimers.put(sessionKey, timer);
    }

    /**
     * 恢复会话的本地定时器（从持久化状态重建）
     */
    public void restoreTimers(String sessionKey, Long sessionId, LocalDateTime deadlineAt, int remainingSeconds,
                              Runnable onTimeout) {
        if (!countdownFutures.containsKey(sessionKey)) {
            startCountdownPush(sessionKey, deadlineAt);
        }
        if (!questionTimers.containsKey(sessionKey)) {
            ScheduledFuture<?> timer = scheduler.schedule(() -> {
                try {
                    onTimeout.run();
                } catch (Exception e) {
                    log.error("恢复后的答题窗口超时处理失败，sessionId={}", sessionId, e);
                }
            }, remainingSeconds, TimeUnit.SECONDS);
            questionTimers.put(sessionKey, timer);
        }
    }

    public void stopTimer(String sessionKey) {
        ScheduledFuture<?> timer = questionTimers.remove(sessionKey);
        if (timer != null) {
            timer.cancel(false);
        }
    }

    public void stopCountdown(String sessionKey) {
        ScheduledFuture<?> future = countdownFutures.remove(sessionKey);
        if (future != null) {
            future.cancel(false);
        }
    }

    public void clearAnswerWindow(String sessionKey) {
        clearAnswerWindowInternal(Long.parseLong(sessionKey));
        answerStartTimes.remove(sessionKey);
        answerDeadlineTimes.remove(sessionKey);
    }

    public LocalDateTime getAnswerDeadline(String sessionKey) {
        return answerDeadlineTimes.get(sessionKey);
    }

    public void putAnswerStartTime(String sessionKey, LocalDateTime time) {
        answerStartTimes.put(sessionKey, time);
    }

    public void putAnswerDeadline(String sessionKey, LocalDateTime time) {
        answerDeadlineTimes.put(sessionKey, time);
    }

    // ==================== 内部方法 ====================

    private void startCountdownPush(String sessionKey, LocalDateTime deadlineAt) {
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try {
                int remaining = InterviewResumeState.remainingSeconds(deadlineAt, LocalDateTime.now());
                if (remaining > 0) {
                    eventPublisher.publishEvent(InterviewWsEvent.pushCountdown(sessionKey, remaining));
                }
            } catch (Exception e) {
                log.error("推送倒计时失败: {}", e.getMessage(), e);
            }
        }, 1, 1, TimeUnit.SECONDS);

        countdownFutures.put(sessionKey, future);
    }

    private void persistAnswerWindow(Long sessionId, Integer questionOrder,
                                     LocalDateTime startedAt, LocalDateTime deadlineAt) {
        EmpVideoInterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new IllegalStateException("面试会话不存在");
        }
        session.setCurrentQuestionOrder(questionOrder);
        session.setQuestionStartedAt(startedAt);
        session.setQuestionDeadlineAt(deadlineAt);
        sessionMapper.updateById(session);
    }

    private boolean isSessionInProgress(Long sessionId) {
        EmpVideoInterviewSession session = sessionMapper.selectById(sessionId);
        return session != null && session.getStatus() != null && session.getStatus() == 2;
    }

    private void clearAnswerWindowInternal(Long sessionId) {
        EmpVideoInterviewSession session = sessionMapper.selectById(sessionId);
        if (session != null) {
            session.setQuestionStartedAt(null);
            session.setQuestionDeadlineAt(null);
            sessionMapper.updateById(session);
        }
    }

    private int resolveCurrentQuestionOrder(Long sessionId) {
        EmpVideoInterviewSession session = sessionMapper.selectById(sessionId);
        if (session != null && session.getCurrentQuestionOrder() != null) {
            return session.getCurrentQuestionOrder();
        }
        throw new IllegalStateException("当前面试题目不存在");
    }
}
