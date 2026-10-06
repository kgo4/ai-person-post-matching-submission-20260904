package com.example.matching.event.listener;

import com.example.matching.event.CommunicationInterviewCreatedEvent;
import com.example.matching.service.interview.CommunicationInterviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 视频终面发起后，异步生成「沟通要点快照」并回写。
 *
 * <p>范式与 {@link PostEmergingConfirmedListener} 一致：{@code AFTER_COMMIT} + 主线程池，
 * 保证主流程（发起邀约）立即返回、且异步任务读到的一定是已提交的数据。</p>
 *
 * <p>失败只记日志：快照是辅助材料，缺失不影响终面记录本身的完整性。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommunicationInterviewBriefingListener {

    private final CommunicationInterviewService interviewService;

    @Async("applicationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(CommunicationInterviewCreatedEvent event) {
        try {
            interviewService.writeBriefingSnapshotQuietly(
                    event.interviewId(), event.empId(), event.matchingRecordId());
        } catch (Exception e) {
            log.warn("沟通要点快照异步生成失败: interviewId={}", event.interviewId(), e);
        }
    }
}
