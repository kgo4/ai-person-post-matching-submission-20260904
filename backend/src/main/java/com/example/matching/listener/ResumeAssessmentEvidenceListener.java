package com.example.matching.listener;

import com.example.matching.service.employee.ResumeParseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 简历解析完成 -> 能力评估工作流证据保存监听器
 * <p>
 * 解析事务提交后，若员工存在活跃评估工作流，自动将解析结果保存为待验证能力证据
 * （COLLECTED + DISPLAY_ONLY），不触发正式入库。
 *
 * @author system
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeAssessmentEvidenceListener {

    private final ResumeParseService resumeParseService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onResumeParseCompleted(com.example.matching.event.ResumeParseCompletedEvent event) {
        try {
            int saved = resumeParseService.saveResumeEvidenceForWorkflow(event.parseId());
            if (saved > 0) {
                log.info("简历解析完成后自动保存评估证据: parseId={}, empId={}, saved={}",
                        event.parseId(), event.empId(), saved);
            }
        } catch (Exception e) {
            // 证据保存失败不影响简历解析本身，仅记录；后续由工作流重试/治理链路补偿。
            log.error("简历解析后保存评估证据失败: parseId={}, error={}",
                    event.parseId(), e.getMessage(), e);
        }
    }
}
