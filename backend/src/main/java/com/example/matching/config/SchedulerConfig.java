package com.example.matching.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 调度器配置
 * <p>
 * 提供统一 {@link ThreadPoolTaskScheduler}，配置：
 * <pre>
 * app:
 *   scheduler:
 *     pool-size: 8
 *     error-log-stacktrace: true
 * </pre>
 */
@Slf4j
@Configuration
public class SchedulerConfig {

    @Bean
    public ThreadPoolTaskScheduler taskScheduler(
            @Value("${app.scheduler.pool-size:8}") int poolSize,
            @Value("${app.scheduler.error-log-stacktrace:true}") boolean errorLogStacktrace) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("app-scheduled-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.setErrorHandler(throwable -> {
            if (errorLogStacktrace) {
                log.error("调度任务未捕获异常（含堆栈）", throwable);
            } else {
                log.error("调度任务未捕获异常: {}", throwable.getMessage());
            }
        });
        scheduler.initialize();
        return scheduler;
    }
}
