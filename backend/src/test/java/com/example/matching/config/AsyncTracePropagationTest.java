package com.example.matching.config;

import com.example.matching.common.trace.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Async TraceId Propagation")
class AsyncTracePropagationTest {

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    @DisplayName("TaskDecorator copies MDC traceId to worker thread")
    void decoratorCopiesTraceIdToWorker() throws Exception {
        AsyncConfig.ContextCopyingTaskDecorator decorator = new AsyncConfig.ContextCopyingTaskDecorator();
        TraceContext.set("trace-async-99");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> workerTraceId = new AtomicReference<>();
        Runnable task = decorator.decorate(() -> {
            workerTraceId.set(TraceContext.getOrNull());
            latch.countDown();
        });
        task.run();

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(workerTraceId.get()).isEqualTo("trace-async-99");
        // 子线程执行后清理 MDC，避免线程池复用时串号
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    @DisplayName("Worker without caller MDC gets no stale traceId")
    void workerWithoutCallerMdcGetsNone() throws Exception {
        AsyncConfig.ContextCopyingTaskDecorator decorator = new AsyncConfig.ContextCopyingTaskDecorator();
        TraceContext.clear();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> workerTraceId = new AtomicReference<>();
        Runnable task = decorator.decorate(() -> {
            workerTraceId.set(TraceContext.getOrNull());
            latch.countDown();
        });
        task.run();

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(workerTraceId.get()).isNull();
    }
}
