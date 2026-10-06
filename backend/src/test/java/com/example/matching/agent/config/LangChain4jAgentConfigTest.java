package com.example.matching.agent.config;

import com.example.matching.agent.lc4j.MatchingAnalysisAiService;
import com.example.matching.agent.tools.EmployeeProfileTool;
import com.example.matching.agent.tools.EvidenceContextTool;
import com.example.matching.agent.tools.KnowledgeGraphTool;
import com.example.matching.agent.tools.PostRequirementTool;
import dev.langchain4j.service.tool.ToolProvider;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Primary;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LangChain4jAgentConfigTest {

    @Test
    void jsonGuardChatModelIsThePrimaryChatLanguageModel() throws NoSuchMethodException {
        // Task13：@Primary 从 observed 模型移到 JsonGuardChatModel，
        // 12 个 AiService 的 ChatModel 注入按类型 + @Primary 自动落到四层防御
        Method observedFactory = LangChain4jAgentConfig.class.getMethod(
                "langChain4jChatLanguageModel",
                com.example.matching.infrastructure.llm.EnterpriseChatLanguageModel.class,
                AgentObservationMetrics.class);
        Method guardFactory = LangChain4jAgentConfig.class.getMethod(
                "jsonGuardChatModel",
                dev.langchain4j.model.chat.ChatModel.class,
                com.example.matching.agent.json.CapabilityProbe.class,
                com.example.matching.agent.json.JsonRetryPolicy.class,
                AgentObservationMetrics.class,
                com.example.matching.ai.service.LlmInputGuard.class);

        assertThat(observedFactory.getAnnotation(Primary.class)).isNull();
        assertThat(guardFactory.getAnnotation(Primary.class)).isNotNull();
    }

    @Test
    void matchingAnalysisAgentDoesNotRegisterRetrievalTools() throws Exception {
        // 匹配分析只消费服务端构建的受限上下文，不允许模型再检索员工、岗位或证据。
        Method factoryMethod = LangChain4jAgentConfig.class.getMethod(
                "matchingAnalysisAiService",
                dev.langchain4j.model.chat.ChatModel.class);

        assertThat(factoryMethod.getParameterTypes())
                .containsExactly(dev.langchain4j.model.chat.ChatModel.class);
    }

    @Test
    void prebuiltGraphAgentsDoNotRegisterKnowledgeGraphTool() {
        assertThat(method("learningPathAiService").getParameterTypes()).doesNotContain(KnowledgeGraphTool.class);
        assertThat(method("interviewPlanAiService").getParameterTypes()).doesNotContain(KnowledgeGraphTool.class);
        assertThat(method("interviewObservationAiService").getParameterTypes()).doesNotContain(KnowledgeGraphTool.class);
    }

    private Method method(String name) {
        return java.util.Arrays.stream(LangChain4jAgentConfig.class.getDeclaredMethods())
                .filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void extractionAgentsDoNotRegisterKnowledgeGraphTool() {
        // Task7：员工/岗位提取 Agent 移除全局图谱工具，保留各自资料/要求工具
        assertThat(method("employeeAbilityAiService").getParameterTypes()).doesNotContain(KnowledgeGraphTool.class);
        assertThat(method("postAbilityAiService").getParameterTypes()).doesNotContain(KnowledgeGraphTool.class);
        assertThat(method("employeeAbilityAiService").getParameterTypes())
                .contains(EmployeeProfileTool.class, EvidenceContextTool.class);
        assertThat(method("postAbilityAiService").getParameterTypes())
                .contains(PostRequirementTool.class);
    }

    @Test
    void matchingAndInterviewAgentsKeepTheirGraphContext() {
        // 匹配/面试等仍需图谱上下文的 Agent 未被误删 KnowledgeGraphTool
        assertThat(method("pmsAbilityAnalysisAiService").getParameterTypes())
                .contains(KnowledgeGraphTool.class);
        assertThat(method("matchingAnalysisAiService").getParameterTypes())
                .doesNotContain(KnowledgeGraphTool.class); // 图谱预构建后匹配 Agent 不再注册
    }

    @Test
    void interviewReportAgentDoesNotRegisterRetrievalTools() throws Exception {
        // 报告阶段只归纳已传入的会话观察，不能检索资料来发现或补充能力。
        Method factoryMethod = LangChain4jAgentConfig.class.getMethod(
                "interviewReportAiService",
                dev.langchain4j.model.chat.ChatModel.class,
                com.example.matching.infrastructure.llm.memory.LangChain4jChatMemoryProvider.class);

        assertThat(factoryMethod.getParameterTypes())
                .containsExactly(
                        dev.langchain4j.model.chat.ChatModel.class,
                        com.example.matching.infrastructure.llm.memory.LangChain4jChatMemoryProvider.class);
    }
}
