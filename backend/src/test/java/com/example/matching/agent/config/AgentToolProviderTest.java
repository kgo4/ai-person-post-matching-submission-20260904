package com.example.matching.agent.config;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentToolProviderTest {

    @Test
    void rejectsToolCallsAfterThePerRequestBudgetIsExhausted() {
        LangChain4jAgentProperties properties = new LangChain4jAgentProperties();
        properties.setMaxToolCallsPerRequest(1);
        AgentToolProvider provider = new AgentToolProvider(properties, metrics(), new SimpleMeterRegistry());

        ToolProviderResult tools = provider.forTools(new EchoTool())
                .provideTools(new ToolProviderRequest(null, new UserMessage("test")));
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("call-1")
                .name("echo")
                .arguments("{\"value\":\"ok\"}")
                .build();

        assertEquals("ok", tools.tools().values().iterator().next().execute(request, null));
        assertThrows(AgentToolLimitExceededException.class,
                () -> tools.tools().values().iterator().next().execute(request, null));
    }

    @Test
    void reusesTheSameToolResultWithinOneAgentRequest() {
        LangChain4jAgentProperties properties = new LangChain4jAgentProperties();
        properties.setMaxToolCallsPerRequest(2);
        CountingEchoTool echoTool = new CountingEchoTool();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AgentToolProvider provider = new AgentToolProvider(properties, new AgentObservationMetrics(registry), registry);

        ToolProviderResult tools = provider.forTools(echoTool)
                .provideTools(new ToolProviderRequest(null, new UserMessage("test")));
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("call-1")
                .name("echo")
                .arguments("{\"value\":\"ok\"}")
                .build();

        assertEquals("ok", tools.tools().values().iterator().next().execute(request, 42L));
        assertEquals("ok", tools.tools().values().iterator().next().execute(request, 42L));
        assertEquals(1, echoTool.invocations);
        assertEquals(1d, registry.get("agent.tool.calls").tag("tool", "echo")
                .tag("cache", "miss").tag("outcome", "success").counter().count());
        assertEquals(1d, registry.get("agent.tool.calls").tag("tool", "echo")
                .tag("cache", "hit").tag("outcome", "success").counter().count());
    }

    @Test
    void returnsStableResultWhenPerToolQuotaExhausted() {
        LangChain4jAgentProperties properties = new LangChain4jAgentProperties();
        properties.setMaxToolCallsPerRequest(100);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AgentToolProvider provider = new AgentToolProvider(properties, new AgentObservationMetrics(registry), registry);

        ToolProviderResult tools = provider.forTools(new EchoTool())
                .provideTools(new ToolProviderRequest(null, new UserMessage("test")));
        var executor = tools.tools().values().iterator().next();
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("call-1")
                .name("echo")
                .arguments("{\"value\":\"ok\"}")
                .build();

        // 默认每工具上限 6：前 6 次正常，第 7 次返回耗尽结果
        for (int i = 0; i < 6; i++) {
            assertEquals("ok", executor.execute(request, null));
        }
        String result = executor.execute(request, null);
        assertTrue(result.contains("tool_budget_exhausted"));
    }

    private AgentObservationMetrics metrics() {
        return new AgentObservationMetrics(new SimpleMeterRegistry());
    }

}

class AgentToolCallScopeTest {

    @Test
    void budgetIsSharedAcrossToolProviderCallbacksWithinOneAgentCall() {
        // M10：一次 Agent 调用内多轮 ToolProvider 回调复用同一计数器（总调用数不超过预算）
        LangChain4jAgentProperties properties = new LangChain4jAgentProperties();
        properties.setMaxToolCallsPerRequest(2);
        AgentToolProvider provider = new AgentToolProvider(properties, metrics(), new SimpleMeterRegistry());
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("call-1")
                .name("echo")
                .arguments("{\"value\":\"ok\"}")
                .build();

        try (AgentToolProvider.AgentCallScope scope = provider.beginCall()) {
            // 第 1 轮：调用 2 次，用满预算
            ToolProviderResult round1 = provider.forTools(new EchoTool())
                    .provideTools(new ToolProviderRequest(null, new UserMessage("test")));
            assertEquals("ok", round1.tools().values().iterator().next().execute(request, null));
            assertEquals("ok", round1.tools().values().iterator().next().execute(request, null));

            // 第 2 轮（同一 Agent 调用）：预算已耗尽，不再重置
            ToolProviderResult round2 = provider.forTools(new EchoTool())
                    .provideTools(new ToolProviderRequest(null, new UserMessage("test")));
            assertThrows(AgentToolLimitExceededException.class,
                    () -> round2.tools().values().iterator().next().execute(request, null));
        } finally {
            provider.endCall();
        }
    }

    @Test
    void resultCacheIsSharedAcrossCallbacksWithinOneAgentCall() {
        // M10：同参数工具调用结果缓存跨轮复用（一次 Agent 调用只执行一次）
        LangChain4jAgentProperties properties = new LangChain4jAgentProperties();
        properties.setMaxToolCallsPerRequest(10);
        AgentToolProvider provider = new AgentToolProvider(properties, metrics(), new SimpleMeterRegistry());
        CountingEchoTool echoTool = new CountingEchoTool();
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("call-1")
                .name("echo")
                .arguments("{\"value\":\"ok\"}")
                .build();

        try (AgentToolProvider.AgentCallScope scope = provider.beginCall()) {
            ToolProviderResult round1 = provider.forTools(echoTool)
                    .provideTools(new ToolProviderRequest(null, new UserMessage("test")));
            assertEquals("ok", round1.tools().values().iterator().next().execute(request, null));
            ToolProviderResult round2 = provider.forTools(echoTool)
                    .provideTools(new ToolProviderRequest(null, new UserMessage("test")));
            assertEquals("ok", round2.tools().values().iterator().next().execute(request, null));
            assertEquals(1, echoTool.invocations);
        } finally {
            provider.endCall();
        }
    }

    @Test
    void scopeIsClearedAfterEndCall() {
        LangChain4jAgentProperties properties = new LangChain4jAgentProperties();
        properties.setMaxToolCallsPerRequest(1);
        AgentToolProvider provider = new AgentToolProvider(properties, metrics(), new SimpleMeterRegistry());
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("call-1")
                .name("echo")
                .arguments("{\"value\":\"ok\"}")
                .build();

        provider.beginCall();
        provider.endCall();

        // 清理后新调用回到每回调独立预算（不被旧 scope 影响）
        ToolProviderResult tools = provider.forTools(new EchoTool())
                .provideTools(new ToolProviderRequest(null, new UserMessage("test")));
        assertEquals("ok", tools.tools().values().iterator().next().execute(request, null));
    }

    @Test
    void nestedCallScopeReusesOuterBudget() {
        LangChain4jAgentProperties properties = new LangChain4jAgentProperties();
        properties.setMaxToolCallsPerRequest(1);
        AgentToolProvider provider = new AgentToolProvider(properties, metrics(), new SimpleMeterRegistry());
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("call-1").name("echo").arguments("{\"value\":\"ok\"}").build();

        provider.withCallScope(() -> {
            ToolProviderResult tools = provider.forTools(new EchoTool())
                    .provideTools(new ToolProviderRequest(null, new UserMessage("test")));
            assertEquals("ok", tools.tools().values().iterator().next().execute(request, null));
            return provider.withCallScope(() -> {
                ToolProviderResult nested = provider.forTools(new EchoTool())
                        .provideTools(new ToolProviderRequest(null, new UserMessage("test")));
                assertThrows(AgentToolLimitExceededException.class,
                        () -> nested.tools().values().iterator().next().execute(request, null));
                return null;
            });
        });
    }

    private static AgentObservationMetrics metrics() {
        return new AgentObservationMetrics(new SimpleMeterRegistry());
    }
}


class EchoTool {
    @Tool("Echoes a value")
    String echo(String value) {
        return value;
    }
}

class CountingEchoTool {
    int invocations;

    @Tool("Echoes a value while counting executions")
    String echo(String value) {
        invocations++;
        return value;
    }
}
