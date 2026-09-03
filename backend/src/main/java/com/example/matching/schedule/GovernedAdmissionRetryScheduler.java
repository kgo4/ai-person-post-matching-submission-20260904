package com.example.matching.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.entity.governance.GovernanceAdmissionRecord;
import com.example.matching.mapper.governance.GovernanceAdmissionMapper;
import com.example.matching.service.governance.GovernedAdmissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * RETRYABLE 治理准入重试调度器：解析器/DB 故障期间搁浅的准入
 * 按指数退避时间到期后重新执行 Harness 校验并在原记录上落地结果。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GovernedAdmissionRetryScheduler {

    private static final String RETRYABLE = "RETRYABLE";
    private static final int MAX_RETRIES = 10;
    private static final int BATCH_SIZE = 50;

    private final GovernanceAdmissionMapper admissionMapper;
    private final GovernedAdmissionService governedAdmissionService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ScheduledTaskRunner taskRunner;

    @Scheduled(fixedDelayString = "${governance.admission.retry-delay-ms:60000}")
    public void retryDueAdmissions() {
        runScheduled("governed_admission_retry", this::retryDueAdmissionsInternal);
    }

    private void retryDueAdmissionsInternal() {
        try {
            List<GovernanceAdmissionRecord> due = admissionMapper.selectList(
                    new LambdaQueryWrapper<GovernanceAdmissionRecord>()
                            .eq(GovernanceAdmissionRecord::getApplyStatus, RETRYABLE)
                            .le(GovernanceAdmissionRecord::getNextRetryTime, LocalDateTime.now())
                            .le(GovernanceAdmissionRecord::getRetryCount, MAX_RETRIES)
                            .last("LIMIT " + BATCH_SIZE));
            int retried = 0;
            for (GovernanceAdmissionRecord record : due) {
                if (governedAdmissionService.retryDueAdmission(record.getId()) != null) {
                    retried++;
                }
            }
            if (retried > 0) {
                log.info("治理准入重试完成: retried={}, scanned={}", retried, due.size());
            }
        } catch (Exception e) {
            log.error("Governed admission retry scan failed, retryable admissions may be stuck", e);
        }
    }

    private void runScheduled(String taskName, Runnable task) {
        if (taskRunner != null) {
            taskRunner.run(taskName, task);
            return;
        }
        try {
            task.run();
        } catch (Exception e) {
            log.error("Governed admission retry scan failed, retryable admissions may be stuck", e);
        }
    }
}
