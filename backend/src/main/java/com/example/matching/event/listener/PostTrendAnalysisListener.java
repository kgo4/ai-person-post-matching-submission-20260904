package com.example.matching.event.listener;

import com.example.matching.common.enums.TaskStatusEnum;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.dto.post.PostTrendAnalysisSummary;
import com.example.matching.event.PostTrendAnalysisQueuedEvent;
import com.example.matching.mapper.post.PostTrendTaskMapper;
import com.example.matching.service.post.PostTrendAnalysisService;
import com.example.matching.service.post.PostTrendTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 岗位趋势解析任务的执行入口。
 * <p>
 * 任务行在事务提交后才被投递执行（材料已落库、已切片），执行线程先 CAS 抢占 PENDING → RUNNING，
 * 抢不到说明任务已被取消或已在执行，直接退出，避免重复解析同一批材料。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostTrendAnalysisListener {

    private final PostTrendTaskMapper taskMapper;
    private final PostTrendTaskService taskService;
    private final PostTrendAnalysisService analysisService;

    @Async("applicationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(PostTrendAnalysisQueuedEvent event) {
        execute(event.taskId(), event.sourceDocumentIds(), event.sourceCategories());
    }

    /**
     * 抢占 → 解析 → 置终态。
     * <p>
     * 解析链路上的任何异常都必须在这里收敛成 FAILED：否则任务会永远停在 RUNNING，
     * 界面上表现为「一直解析中」，用户既拿不到结果也无法重新发起。
     */
    public void execute(Long taskId, List<Long> documentIds, List<String> categories) {
        int claimed = taskMapper.claimPendingTask(taskId,
                TaskStatusEnum.PENDING.getCode(), TaskStatusEnum.RUNNING.getCode());
        if (claimed == 0) {
            log.info("趋势解析任务未被抢占（已取消或已在执行）: taskId={}", taskId);
            return;
        }
        try {
            PostTrendAnalysisSummary summary = analysisService.analyze(taskId, documentIds, categories);
            taskService.markAnalysisFinished(taskId, summary);
        } catch (Exception e) {
            log.error("趋势解析任务执行失败: taskId={}", taskId, e);
            taskService.markFailed(taskId, resolveMessage(e));
        }
    }

    private String resolveMessage(Exception e) {
        if (e instanceof BusinessException && StringUtils.hasText(e.getMessage())) {
            return e.getMessage();
        }
        String message = e.getMessage();
        return StringUtils.hasText(message)
                ? "解析失败：" + message
                : "解析失败：" + e.getClass().getSimpleName();
    }
}
