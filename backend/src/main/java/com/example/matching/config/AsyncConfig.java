package com.example.matching.config;

import com.example.matching.utils.SecurityUtils;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务执行器配置
 * <p>
 * 为 @Async 与所有由 Controller 显式 inject 的 {@link Executor} 提供一致的 {@link ThreadPoolTaskExecutor}，
 * 关键能力是 {@link ContextCopyingTaskDecorator}：把调用线程的 SecurityUtils ThreadLocal、
 * SecurityContext 与 MDC（含 TraceContext traceId）复制到子线程，并在子线程结束时统一清理，
 * 避免内存泄漏、审计字段丢失与异步链路 traceId 断裂。
 */
@Slf4j
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    /** 系统操作员 ID（用于没有租户上下文的异步/定时/消息任务占位） */
    public static final long SYSTEM_USER_ID = 0L;
    public static final String SYSTEM_USERNAME = "system";

    /**
     * 主异步执行器，命名为 {@code applicationTaskExecutor} 以覆盖 Spring Boot
     * 默认 ApplicationTaskExecutor，确保所有 inject {@link Executor} 的入口都拿到这个具备上下文复制的池子。
     */
    @Bean(name = "applicationTaskExecutor")
    public ThreadPoolTaskExecutor applicationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("app-async-");
        executor.setTaskDecorator(new ContextCopyingTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * Bounded pool for outbound LLM calls. Keeping it separate prevents a slow
     * provider from occupying the executor used by ordinary application tasks.
     */
    @Bean(name = "aiTaskExecutor")
    public ThreadPoolTaskExecutor aiTaskExecutor(MeterRegistry meterRegistry) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ai-task-");
        executor.setTaskDecorator(new ContextCopyingTaskDecorator());
        executor.setRejectedExecutionHandler(new AiExecutorRejectionHandler(meterRegistry));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /** 批量岗位导入专用 AI 队列，避免被治理任务挤占。 */
    @Bean(name = "postImportAiExecutor")
    public ThreadPoolTaskExecutor postImportAiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("post-import-ai-");
        executor.setTaskDecorator(new ContextCopyingTaskDecorator());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /** 标签治理旁路队列，低并发且永不丢弃任务。 */
    @Bean(name = "abilityTagGovernanceExecutor")
    public ThreadPoolTaskExecutor abilityTagGovernanceExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("ability-tag-governance-");
        executor.setTaskDecorator(new ContextCopyingTaskDecorator());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * 面试的“下一题”只负责调度既有回答核验流程。独立的小队列避免普通异步任务
     * 延迟用户操作确认，实际模型并发仍由 AiProviderConcurrencyGate 统一限制。
     */
    @Bean(name = "interviewRealtimeExecutor")
    public ThreadPoolTaskExecutor interviewRealtimeExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("interview-realtime-");
        executor.setTaskDecorator(new ContextCopyingTaskDecorator());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * AI 池拒绝策略：记录指标并抛 {@link RejectedExecutionException}。
     * <p>
     * 计划要求：不允许在 Rabbit 监听器/调度线程上回退执行慢 LLM（会破坏吞吐保证）。
     * 提交方（retry service 等）捕获异常后通过既有状态机将记录转回可重试状态。
     */
    static final class AiExecutorRejectionHandler implements RejectedExecutionHandler {

        private final MeterRegistry meterRegistry;

        AiExecutorRejectionHandler(MeterRegistry meterRegistry) {
            this.meterRegistry = meterRegistry;
        }

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            Counter.builder("matching.ai.executor.rejected")
                    .register(meterRegistry)
                    .increment();
            io.micrometer.core.instrument.Gauge.builder("matching.ai.executor.queue_depth", executor,
                            e -> e.getQueue().size())
                    .register(meterRegistry);
            log.warn("AI task executor rejected submission: queueSize={}, poolSize={}, active={}",
                    executor.getQueue().size(), executor.getPoolSize(), executor.getActiveCount());
            throw new RejectedExecutionException("AI task executor saturated");
        }
    }

    @Override
    public Executor getAsyncExecutor() {
        return applicationTaskExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) ->
                log.error("异步方法抛出未捕获异常: method={}, error={}", method.getName(), ex.getMessage(), ex);
    }

    /**
     * 把调用线程的 SecurityUtils / SecurityContext / MDC（含 traceId）复制到执行线程，
     * 执行完成后清理子线程的 ThreadLocal，避免线程池复用时身份串号与 traceId 断裂。
     */
    public static final class ContextCopyingTaskDecorator implements TaskDecorator {
        @Override
        public Runnable decorate(Runnable runnable) {
            // 捕获调用方上下文（提交任务那一刻）
            Long callerUserId = SecurityUtils.getCurrentUserId();
            String callerUsername = SecurityUtils.getCurrentUsername();
            SecurityContext callerCtx = SecurityContextHolder.getContext();
            Map<String, String> callerMdc = MDC.getCopyOfContextMap();

            return () -> {
                try {
                    if (callerMdc != null && !callerMdc.isEmpty()) {
                        MDC.setContextMap(callerMdc);
                    }
                    if (callerUserId != null) {
                        SecurityUtils.setCurrentUserId(callerUserId);
                    } else {
                        // 调用方没有上下文（如从 RabbitListener 间接调用 @Async 时）—— 以系统身份兜底
                        SecurityUtils.setCurrentUserId(SYSTEM_USER_ID);
                        SecurityUtils.setCurrentUsername(SYSTEM_USERNAME);
                    }
                    if (callerUsername != null) {
                        SecurityUtils.setCurrentUsername(callerUsername);
                    }
                    if (callerCtx != null && callerCtx.getAuthentication() != null) {
                        SecurityContextHolder.setContext(callerCtx);
                    }
                    runnable.run();
                } finally {
                    SecurityUtils.clear();
                    SecurityContextHolder.clearContext();
                    MDC.clear();
                }
            };
        }
    }
}
