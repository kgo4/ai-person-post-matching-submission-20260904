package com.example.matching.agent.config;

import com.example.matching.agent.service.ToolInvocationBudget;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds LangChain4j tool providers with a request-scoped execution budget.
 * The framework's internal loop cap is intentionally supplemented with a
 * smaller application-level limit that can be configured per environment,
 * plus a per-tool quota ({@link ToolInvocationBudget}) to prevent a model
 * from wasting the request budget on repeated broad searches.
 */
@Component
public class AgentToolProvider {

    private final LangChain4jAgentProperties properties;
    private final AgentObservationMetrics metrics;
    private final MeterRegistry meterRegistry;

    /** Task10：可选注入的提取指标（图谱工具被调用即记录，提取链路应为 0） */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ExtractionMetrics extractionMetrics;

    public AgentToolProvider(LangChain4jAgentProperties properties,
                             AgentObservationMetrics metrics,
                             MeterRegistry meterRegistry) {
        this.properties = properties;
        this.metrics = metrics;
        this.meterRegistry = meterRegistry;
    }

    private static final ThreadLocal<AgentCallScope> ACTIVE_SCOPE = new ThreadLocal<>();

    public ToolProvider forTools(Object... tools) {
        Map<ToolSpecification, ToolExecutor> executors = createExecutors(tools);
        return request -> {
            AgentCallScope scope = ACTIVE_SCOPE.get();
            if (scope != null) {
                // M10：一次 Agent 调用内的多轮 ToolProvider 回调复用同一计数器/deadline/缓存
                return limitExecutors(executors, scope);
            }
            // 未接入调用级 scope 的调用点：保持每回调独立预算（原行为兜底）
            return limitExecutors(executors, new AgentCallScope(
                    System.nanoTime() + properties.getToolExecutionTimeoutSeconds() * 1_000_000_000L,
                    new ToolInvocationBudget(meterRegistry)));
        };
    }

    /**
     * M10：开始一次 Agent 调用级工具预算作用域。
     * 多轮 ToolProvider 回调复用同一计数器、deadline、单工具预算与结果缓存；
     * 调用结束后必须 {@link #endCall()}（或用 try-with-resources）。
     */
    public AgentCallScope beginCall() {
        AgentCallScope scope = new AgentCallScope(
                System.nanoTime() + properties.getToolExecutionTimeoutSeconds() * 1_000_000_000L,
                new ToolInvocationBudget(meterRegistry));
        ACTIVE_SCOPE.set(scope);
        return scope;
    }

    /** 结束当前 Agent 调用级工具预算作用域 */
    public void endCall() {
        ACTIVE_SCOPE.remove();
    }

    /**
     * M10：在 Agent 调用入口包一层调用级工具预算作用域（finally 清理）。
     */
    public <T> T withCallScope(java.util.function.Supplier<T> supplier) {
        if (ACTIVE_SCOPE.get() != null) {
            return supplier.get();
        }
        beginCall();
        try {
            return supplier.get();
        } finally {
            endCall();
        }
    }

    private static volatile AgentToolProvider ACTIVE_PROVIDER;

    @jakarta.annotation.PostConstruct
    void init() {
        ACTIVE_PROVIDER = this;
    }

    /**
     * M10：静态委托入口——Agent 调用点无需注入本组件即可包调用级预算作用域。
     * 单测环境无实例时直接执行（不改变行为）。
     */
    public static <T> T withScope(java.util.function.Supplier<T> supplier) {
        AgentToolProvider provider = ACTIVE_PROVIDER;
        if (provider == null) {
            return supplier.get();
        }
        return provider.withCallScope(supplier);
    }

    /** 一次 Agent 调用内的工具预算作用域（AutoCloseable 便于 try-with-resources） */
    public static final class AgentCallScope implements AutoCloseable {
        private final long deadlineNanos;
        private final AtomicInteger calls = new AtomicInteger();
        private final ToolInvocationBudget perToolBudget;
        private final Map<String, String> resultsByToolCall = new ConcurrentHashMap<>();

        private AgentCallScope(long deadlineNanos, ToolInvocationBudget perToolBudget) {
            this.deadlineNanos = deadlineNanos;
            this.perToolBudget = perToolBudget;
        }

        @Override
        public void close() {
            // close 仅清理引用；线程局部清理由 beginCall/endCall 配对完成
        }
    }

    private Map<ToolSpecification, ToolExecutor> createExecutors(Object... tools) {
        Map<ToolSpecification, ToolExecutor> executors = new LinkedHashMap<>();
        for (Object tool : tools) {
            for (Method method : tool.getClass().getDeclaredMethods()) {
                if (!method.isAnnotationPresent(Tool.class)) {
                    continue;
                }
                ToolSpecification specification = ToolSpecifications.toolSpecificationFrom(method);
                executors.put(specification, new DefaultToolExecutor(tool, method));
            }
        }
        return executors;
    }

    private ToolProviderResult limitExecutors(Map<ToolSpecification, ToolExecutor> executors,
                                               AgentCallScope scope) {
        Map<ToolSpecification, ToolExecutor> limited = new LinkedHashMap<>();

        executors.forEach((specification, executor) -> limited.put(specification, (request, memoryId) -> {
            if (System.nanoTime() > scope.deadlineNanos) {
                throw new AgentToolLimitExceededException("Agent tool execution exceeded the configured deadline");
            }
            int callNumber = scope.calls.incrementAndGet();
            if (callNumber > properties.getMaxToolCallsPerRequest()) {
                throw new AgentToolLimitExceededException(
                        "Agent tool-call budget exceeded: " + properties.getMaxToolCallsPerRequest());
            }
            // 单工具配额：耗尽返回稳定结果而非抛异常
            if (!scope.perToolBudget.tryAcquire(specification.name())) {
                return scope.perToolBudget.exhaustedResult();
            }
            String cacheKey = specification.name() + '\u0000' + request.arguments();
            boolean cacheHit = scope.resultsByToolCall.containsKey(cacheKey);
            long startNanos = System.nanoTime();
            try {
                recordGraphCallIfGraphTool(specification.name());
                String result = scope.resultsByToolCall.computeIfAbsent(cacheKey, ignored -> executor.execute(request, memoryId));
                metrics.recordToolCall(specification.name(), cacheHit, true, System.nanoTime() - startNanos);
                return result;
            } catch (RuntimeException ex) {
                metrics.recordToolCall(specification.name(), cacheHit, false, System.nanoTime() - startNanos);
                throw ex;
            }
        }));

        return new ToolProviderResult(limited);
    }

    /**
     * Task10：图谱工具被任何 Agent 调用时记录 extraction.graph_tool_calls。
     * 提取链路不应调用图谱工具；该指标 > 0 即配置回归。
     */
    private void recordGraphCallIfGraphTool(String toolName) {
        if (extractionMetrics != null && toolName != null && toolName.contains("KnowledgeGraph")) {
            extractionMetrics.graphToolCalled();
        }
    }
}
