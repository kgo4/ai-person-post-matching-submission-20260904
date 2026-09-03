package com.example.matching.service.interview;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.entity.employee.EmpVideoInterviewSession;
import com.example.matching.entity.employee.EmpVideoInterviewQuestion;
import com.example.matching.mapper.employee.EmpVideoInterviewSessionMapper;
import com.example.matching.mapper.employee.EmpVideoInterviewQuestionMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewTranscriptBuffer {

    private static final String REDIS_KEY_PREFIX_TRANSCRIPT = "interview:transcript:";
    private static final String REDIS_KEY_PREFIX_ANSWER = "interview:answer:";
    private static final Duration REDIS_TTL = Duration.ofMinutes(10);
    private static final String LOCK_NAME = "interview-transcript-flush";

    private final EmpVideoInterviewSessionMapper sessionMapper;
    private final EmpVideoInterviewQuestionMapper questionMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final com.example.matching.service.common.DistributedLockService distributedLockService;
    private final com.example.matching.schedule.SchedulerMetrics schedulerMetrics;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.example.matching.schedule.ScheduledTaskRunner taskRunner;
    private final Map<Long, StringBuilder> pendingTranscripts = new ConcurrentHashMap<>();
    private final Map<Long, Long> currentQuestionIds = new ConcurrentHashMap<>();
    private final Map<Long, StringBuilder> pendingQuestionAnswers = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> acceptedSessionSegments = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> acceptedQuestionSegments = new ConcurrentHashMap<>();

    @PostConstruct
    public void recoverFromRedis() {
        try {
            recoverTranscriptsFromRedis();
            recoverAnswersFromRedis();
        } catch (Exception e) {
            // Redis 不可用时降级：转录仅保存在本地内存，Redis 恢复后由下一次会话写入补齐
            log.warn("从 Redis 恢复面试转录失败（降级为仅本地内存）: {}", e.getMessage());
        }
    }

    private void recoverTranscriptsFromRedis() {
        var keys = stringRedisTemplate.keys(REDIS_KEY_PREFIX_TRANSCRIPT + "*");
        if (keys != null) {
            for (String key : keys) {
                try {
                    Long sessionId = Long.parseLong(key.substring(REDIS_KEY_PREFIX_TRANSCRIPT.length()));
                    String value = stringRedisTemplate.opsForValue().get(key);
                    if (value != null && !value.isBlank()) {
                        pendingTranscripts.put(sessionId, new StringBuilder(value));
                        log.info("Recovered transcript from Redis: sessionId={}", sessionId);
                    }
                } catch (Exception e) {
                    log.warn("Failed to recover transcript key: {}", key, e);
                }
            }
        }
    }

    private void recoverAnswersFromRedis() {
        var keys = stringRedisTemplate.keys(REDIS_KEY_PREFIX_ANSWER + "*");
        if (keys != null) {
            for (String key : keys) {
                try {
                    Long questionId = Long.parseLong(key.substring(REDIS_KEY_PREFIX_ANSWER.length()));
                    String value = stringRedisTemplate.opsForValue().get(key);
                    if (value != null && !value.isBlank()) {
                        pendingQuestionAnswers.put(questionId, new StringBuilder(value));
                        log.info("Recovered answer from Redis: questionId={}", questionId);
                    }
                } catch (Exception e) {
                    log.warn("Failed to recover answer key: {}", key, e);
                }
            }
        }
    }

    public void append(Long sessionId, String text) {
        if (!acceptSegment(acceptedSessionSegments, sessionId, text)) return;
        pendingTranscripts.compute(sessionId, (ignored, buffer) -> {
            StringBuilder target = buffer == null ? new StringBuilder() : buffer;
            if (!target.isEmpty()) target.append('\n');
            target.append(text.trim());
            return target;
        });
        StringBuilder current = pendingTranscripts.get(sessionId);
        if (current != null) {
            try {
                stringRedisTemplate.opsForValue().set(
                        REDIS_KEY_PREFIX_TRANSCRIPT + sessionId, current.toString(), REDIS_TTL);
            } catch (Exception e) {
                log.warn("Failed to backup transcript to Redis: sessionId={}", sessionId, e);
            }
        }
    }

    public void setCurrentQuestion(Long sessionId, Long questionId) {
        Long previousQuestionId = currentQuestionIds.put(sessionId, questionId);
        if (previousQuestionId != null && !previousQuestionId.equals(questionId)) flushQuestion(previousQuestionId);
    }

    public void appendCurrentQuestion(Long sessionId, String text) {
        Long questionId = currentQuestionIds.get(sessionId);
        if (questionId == null) return;
        if (!acceptSegment(acceptedQuestionSegments, questionId, text)) return;
        pendingQuestionAnswers.compute(questionId, (ignored, buffer) -> append(buffer, text));
        StringBuilder current = pendingQuestionAnswers.get(questionId);
        if (current != null) {
            backupAnswer(questionId, current.toString());
        }
    }

    @Scheduled(fixedDelay = 2000)
    public void flushPending() {
        if (taskRunner != null) {
            taskRunner.run("interview_transcript_flush", this::flushPendingInternal);
        } else {
            flushPendingInternal();
        }
    }

    private void flushPendingInternal() {
        var lock = distributedLockService.tryAcquire(LOCK_NAME);
        if (lock == null) {
            log.debug("Interview transcript flush skipped: lock held by another instance");
            return;
        }
        try {
            pendingTranscripts.keySet().forEach(this::flush);
            pendingQuestionAnswers.keySet().forEach(this::flushQuestion);
        } catch (Exception e) {
            log.error("Interview transcript flush failed, transcripts may be delayed", e);
            schedulerMetrics.recordFailure("interview_transcript_flush");
        } finally {
            lock.close();
        }
    }

    public void flush(Long sessionId) {
        StringBuilder pending = pendingTranscripts.remove(sessionId);
        if (pending == null || pending.isEmpty()) return;
        try {
            String appendText = pending.toString();
            // SQL 原子追加：由数据库行锁串行化并发 flush（调度线程与 WS 线程可能同时触发），
            // 杜绝 selectById+updateById 的 read-modify-write 丢失更新
            int rows = sessionMapper.update(null, Wrappers.<EmpVideoInterviewSession>lambdaUpdate()
                    .eq(EmpVideoInterviewSession::getId, sessionId)
                    .setSql("transcript_text = CASE WHEN IFNULL(TRIM(transcript_text), '') = '' "
                            + "THEN {0} ELSE CONCAT(TRIM(transcript_text), '\n', {0}) END", appendText)
                    .setSql("transcript_json = CASE WHEN IFNULL(TRIM(transcript_json), '') = '' "
                            + "THEN {0} ELSE CONCAT(TRIM(transcript_json), '\n', {0}) END", appendText));
            if (rows != 1) {
                throw new IllegalStateException("Interview transcript update did not affect a row");
            }
            clearBackup(REDIS_KEY_PREFIX_TRANSCRIPT + sessionId, "transcript", sessionId);
        } catch (Exception exception) {
            pendingTranscripts.merge(sessionId, pending, (current, failed) -> {
                failed.append('\n').append(current);
                return failed;
            });
            try {
                stringRedisTemplate.opsForValue().set(
                        REDIS_KEY_PREFIX_TRANSCRIPT + sessionId, pending.toString(), REDIS_TTL);
                log.warn("Failed to flush interview transcript to DB, backed up to Redis: sessionId={}", sessionId, exception);
            } catch (Exception backupFailed) {
                log.error("Interview transcript flush failed and Redis backup also failed, "
                        + "transcript is only in-memory: sessionId={}", sessionId, exception);
                schedulerMetrics.recordFailure("interview_transcript_flush");
            }
        }
    }

    public void flushSession(Long sessionId) {
        flush(sessionId);
        Long questionId = currentQuestionIds.remove(sessionId);
        if (questionId != null) flushQuestion(questionId);
        acceptedSessionSegments.remove(sessionId);
        if (questionId != null) acceptedQuestionSegments.remove(questionId);
    }

    /**
     * 冲刷当前题目缓冲到数据库。
     * <p>
     * 供 nextQuestion() 等读取 answerTranscript 前调用，确保缓冲中的最后一段
     * 转写先落库再被评估读取，避免评估漏掉尾部回答（M14）。
     */
    public void flushCurrentQuestion(Long sessionId) {
        Long questionId = currentQuestionIds.get(sessionId);
        if (questionId != null) {
            flushQuestion(questionId);
        }
    }

    private void flushQuestion(Long questionId) {
        StringBuilder pending = pendingQuestionAnswers.remove(questionId);
        if (pending == null || pending.isEmpty()) return;
        try {
            // SQL 原子追加：与 flush() 同理，避免并发 flush 的 read-modify-write 丢失更新
            String appendText = pending.toString();
            int rows = questionMapper.update(null, Wrappers.<EmpVideoInterviewQuestion>lambdaUpdate()
                    .eq(EmpVideoInterviewQuestion::getId, questionId)
                    .setSql("answer_transcript = CASE WHEN IFNULL(TRIM(answer_transcript), '') = '' "
                            + "THEN {0} ELSE CONCAT(TRIM(answer_transcript), '\n', {0}) END", appendText));
            if (rows != 1) {
                throw new IllegalStateException("Interview answer update did not affect a row");
            }
            clearBackup(REDIS_KEY_PREFIX_ANSWER + questionId, "answer", questionId);
        } catch (Exception exception) {
            pendingQuestionAnswers.merge(questionId, pending, (current, failed) -> append(failed, current.toString()));
            try {
                stringRedisTemplate.opsForValue().set(
                        REDIS_KEY_PREFIX_ANSWER + questionId, pending.toString(), REDIS_TTL);
                log.warn("Failed to flush interview answer transcript to DB, backed up to Redis: questionId={}", questionId, exception);
            } catch (Exception backupFailed) {
                log.error("Interview answer flush failed and Redis backup also failed, "
                        + "answer is only in-memory: questionId={}", questionId, exception);
                schedulerMetrics.recordFailure("interview_transcript_flush");
            }
        }
    }

    private StringBuilder append(StringBuilder buffer, String text) {
        StringBuilder target = buffer == null ? new StringBuilder() : buffer;
        if (!target.isEmpty()) target.append('\n');
        target.append(text.trim());
        return target;
    }

    static boolean appendDistinct(StringBuilder target, String text) {
        String normalized = normalizeSegment(text);
        if (normalized.isEmpty()) return false;
        for (String line : target.toString().split("\\R")) {
            if (normalizeSegment(line).equals(normalized)) return false;
        }
        if (!target.isEmpty()) target.append('\n');
        target.append(text.trim());
        return true;
    }

    private boolean acceptSegment(Map<Long, Set<String>> acceptedSegments, Long ownerId, String text) {
        String normalized = normalizeSegment(text);
        if (normalized.isEmpty()) return false;
        boolean accepted = acceptedSegments.computeIfAbsent(ownerId, ignored -> ConcurrentHashMap.newKeySet())
                .add(normalized);
        if (!accepted) {
            log.debug("忽略 ASR 重复最终转写片段，ownerId={}", ownerId);
        }
        return accepted;
    }

    private static String normalizeSegment(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "").trim();
    }

    private void backupAnswer(Long questionId, String value) {
        try {
            stringRedisTemplate.opsForValue().set(REDIS_KEY_PREFIX_ANSWER + questionId, value, REDIS_TTL);
        } catch (Exception e) {
            log.warn("Failed to backup interview answer to Redis: questionId={}", questionId, e);
        }
    }

    private void clearBackup(String key, String type, Long id) {
        try {
            stringRedisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Failed to clear Redis {} backup: id={}", type, id, e);
        }
    }
}
