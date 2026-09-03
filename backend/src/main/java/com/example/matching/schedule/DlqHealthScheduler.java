package com.example.matching.schedule;

import com.example.matching.entity.system.SysOperationLog;
import com.example.matching.service.common.DlqReplayService;
import com.example.matching.service.system.SysOperationLogService;
import com.example.matching.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * DLQ 深度巡检调度器
 * <p>
 * 每 5 分钟检查一次 DLQ 深度；超过 {@code app.dlq.alert-threshold} 时
 * 记录结构化 ERROR 并写系统操作日志。无需 Prometheus 或新数据库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DlqHealthScheduler {

    private final DlqReplayService dlqReplayService;
    private final SysOperationLogService sysOperationLogService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ScheduledTaskRunner taskRunner;

    @Value("${app.dlq.alert-threshold:20}")
    private long alertThreshold;

    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    public void checkDlqDepth() {
        if (taskRunner != null) {
            taskRunner.run("dlq_health_check", this::checkDlqDepthInternal);
            return;
        }
        try {
            checkDlqDepthInternal();
        } catch (Exception e) {
            log.error("[DLQ_ALERT] DLQ 深度检查失败: {}", e.getMessage(), e);
        }
    }

    private void checkDlqDepthInternal() {
        DlqReplayService.DlqSummary summary = dlqReplayService.summary().withThreshold(alertThreshold);
        if (summary.alerting()) {
            log.error("[DLQ_ALERT] 死信队列深度超过阈值: messageCount={}, alertThreshold={}, checkedAt={}",
                    summary.messageCount(), summary.alertThreshold(), summary.checkedAt());
            writeAlertLog(summary);
        } else {
            log.info("DLQ 深度巡检正常: messageCount={}, alertThreshold={}", summary.messageCount(), summary.alertThreshold());
        }
    }

    private void writeAlertLog(DlqReplayService.DlqSummary summary) {
        try {
            SysOperationLog audit = new SysOperationLog();
            audit.setUserId(SecurityUtils.getCurrentUserId());
            audit.setRealName(SecurityUtils.getCurrentUsername());
            audit.setOperationModule("DLQ");
            audit.setOperationType("ALERT");
            audit.setOperationDesc("死信队列深度超过阈值: messageCount=" + summary.messageCount()
                    + ", alertThreshold=" + summary.alertThreshold());
            audit.setRequestUrl("/schedule/dlq-health");
            audit.setOperationTime(LocalDateTime.now());
            sysOperationLogService.save(audit);
        } catch (Exception e) {
            log.warn("DLQ 告警审计日志写入失败: {}", e.getMessage());
        }
    }
}
