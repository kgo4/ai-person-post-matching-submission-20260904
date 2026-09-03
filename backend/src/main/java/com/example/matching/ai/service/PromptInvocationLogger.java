package com.example.matching.ai.service;

import com.example.matching.common.trace.TraceContext;
import com.example.matching.entity.system.PromptInvocationLog;
import com.example.matching.mapper.system.PromptInvocationLogMapper;
import com.example.matching.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Prompt 调用埋点服务 —— 异步记录，不阻塞主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptInvocationLogger {

    private final PromptInvocationLogMapper logMapper;

    /**
     * 异步记录一次 Prompt 调用
     */
    @Async
    public void logInvocation(PromptInvocationLog entry) {
        try {
            entry.setCreatedTime(LocalDateTime.now());
            logMapper.insert(entry);
        } catch (Exception e) {
            log.warn("Prompt 调用埋点写入失败: {}", e.getMessage());
        }
    }

    /**
     * 后向回填人工反馈分（匹配完成后）
     */
    public void backfillFeedback(Long logId, Integer feedbackScore) {
        try {
            PromptInvocationLog entry = logMapper.selectById(logId);
            if (entry != null) {
                entry.setFeedbackScore(feedbackScore);
                logMapper.updateById(entry);
            }
        } catch (Exception e) {
            log.warn("反馈分回填失败 logId={}: {}", logId, e.getMessage());
        }
    }

    /**
     * 构建日志条目
     */
    public PromptInvocationLog buildEntry(String promptName, String promptVersion,
                                           String scenario, boolean success, boolean fallback,
                                           long latencyMs, int inputChars, int outputChars) {
        PromptInvocationLog entry = new PromptInvocationLog();
        entry.setPromptName(promptName);
        entry.setPromptVersion(promptVersion);
        entry.setScenario(scenario);
        entry.setModelName("deepseek-v4-flash");
        entry.setLatencyMs(latencyMs);
        entry.setToolLatencyMs(0L);
        entry.setModelRounds(1);
        entry.setRetryCount(0);
        entry.setQueueWaitMs(0L);
        entry.setCacheHit(false);
        entry.setSuccess(success);
        entry.setFallbackUsed(fallback);
        entry.setInputChars(inputChars);
        entry.setOutputChars(outputChars);
        entry.setTraceId(TraceContext.getOrNull());
        Long userId = SecurityUtils.getCurrentUserId();
        entry.setUserId(userId != null ? userId : 0L);
        return entry;
    }

    public PromptInvocationLog buildEntry(String promptName, String promptVersion,
                                           String scenario, boolean success, boolean fallback,
                                           long latencyMs, long toolLatencyMs, int modelRounds,
                                           int retryCount, long queueWaitMs, boolean cacheHit,
                                           int inputChars, int outputChars) {
        PromptInvocationLog entry = buildEntry(promptName, promptVersion, scenario, success, fallback,
                latencyMs, inputChars, outputChars);
        entry.setToolLatencyMs(toolLatencyMs);
        entry.setModelRounds(modelRounds);
        entry.setRetryCount(retryCount);
        entry.setQueueWaitMs(queueWaitMs);
        entry.setCacheHit(cacheHit);
        return entry;
    }
}