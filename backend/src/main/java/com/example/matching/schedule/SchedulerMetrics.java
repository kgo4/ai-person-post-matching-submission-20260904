package com.example.matching.schedule;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 调度失败计数指标：scheduler_failures_total{job="..."}。
 * <p>
 * 日志负责定位，指标负责告警。各调度类在整体失败或达到重试上限时调用 {@link #recordFailure(String)}。
 */
@Component
@RequiredArgsConstructor
public class SchedulerMetrics {

    private final MeterRegistry meterRegistry;
    private final Map<String, Counter> failureCounters = new ConcurrentHashMap<>();

    public void recordFailure(String job) {
        failureCounters.computeIfAbsent(job,
                        j -> Counter.builder("scheduler_failures_total")
                                .tag("job", j)
                                .register(meterRegistry))
                .increment();
    }
}
