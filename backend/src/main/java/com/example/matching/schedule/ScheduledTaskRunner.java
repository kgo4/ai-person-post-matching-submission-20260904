package com.example.matching.schedule;

import com.example.matching.entity.system.SysOperationLog;
import com.example.matching.service.common.DistributedLockService;
import com.example.matching.service.system.SysOperationLogService;
import com.example.matching.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 统一调度任务执行器
 * <p>
 * 所有 {@code @Scheduled} 任务方法体只委托给本 runner，统一完成：
 * <ol>
 *   <li>获取分布式锁（未获取到则跳过本次执行）</li>
 *   <li>设置系统上下文 {@link SecurityUtils#setSystemContext()}</li>
 *   <li>记录开始时间、成功耗时或完整 ERROR</li>
 *   <li>失败时调用 {@link SchedulerMetrics#recordFailure(String)}</li>
 *   <li>写一条 SysOperationLog 系统操作记录</li>
 *   <li>finally 清理 SecurityUtils、SecurityContextHolder 和 MDC</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledTaskRunner {

    private final SchedulerMetrics schedulerMetrics;
    private final DistributedLockService lockService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private SysOperationLogService sysOperationLogService;

    /**
     * 运行调度任务；任务抛出的异常被捕获并记录（不向调度器传播）。
     * 通过分布式锁保证集群环境中同一任务只在一个节点上执行。
     *
     * @param taskName 任务名称（用于日志、指标和审计）
     * @param task     任务体
     */
    public void run(String taskName, Runnable task) {
        try (DistributedLockService.LockHandle ignored = lockService.tryAcquire("scheduler:" + taskName)) {
            if (ignored == null) {
                log.debug("调度任务跳过（未获取到分布式锁）: task={}", taskName);
                return;
            }
            long start = System.currentTimeMillis();
            SecurityUtils.setSystemContext();
            try {
                task.run();
                long costMs = System.currentTimeMillis() - start;
                log.debug("调度任务完成: task={}, costMs={}", taskName, costMs);
            } catch (Exception e) {
                long costMs = System.currentTimeMillis() - start;
                log.error("调度任务执行失败: task={}, costMs={}", taskName, costMs, e);
                schedulerMetrics.recordFailure(taskName);
                writeAudit(taskName, "FAILED", safeTruncate(e.getMessage(), 500), costMs);
            } finally {
                SecurityUtils.clear();
                SecurityContextHolder.clearContext();
                MDC.clear();
            }
        }
    }

    private void writeAudit(String taskName, String result, String error, long costMs) {
        if (sysOperationLogService == null) {
            return;
        }
        try {
            SysOperationLog audit = new SysOperationLog();
            audit.setUserId(SecurityUtils.getCurrentUserId());
            audit.setRealName(SecurityUtils.getCurrentUsername());
            audit.setOperationModule("SCHEDULER");
            audit.setOperationType(result);
            audit.setOperationDesc("调度任务[" + taskName + "] " + result
                    + (error != null ? ": " + error : "") + ", costMs=" + costMs);
            audit.setRequestUrl("/schedule/" + taskName);
            audit.setOperationTime(LocalDateTime.now());
            audit.setCostTime(costMs);
            sysOperationLogService.save(audit);
        } catch (Exception e) {
            log.warn("调度任务审计日志写入失败: task={}", taskName, e);
        }
    }

    private String safeTruncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
