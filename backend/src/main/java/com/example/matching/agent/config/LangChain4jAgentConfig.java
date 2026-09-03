package com.example.matching.agent.config;

import com.example.matching.agent.lc4j.AiTestAiService;
import com.example.matching.agent.lc4j.EmployeeAbilityAiService;
import com.example.matching.agent.lc4j.EvidenceGovernanceAiService;
import com.example.matching.agent.lc4j.InterviewAnswerQualityAiService;
import com.example.matching.agent.lc4j.InterviewFollowUpAiService;
import com.example.matching.agent.lc4j.InterviewObservationAiService;
import com.example.matching.agent.lc4j.InterviewPlanAiService;
import com.example.matching.agent.lc4j.InterviewReportAiService;
import com.example.matching.agent.lc4j.LearningPathAiService;
import com.example.matching.agent.lc4j.MatchingAnalysisAiService;
import com.example.matching.agent.lc4j.PmsAbilityAnalysisAiService;
import com.example.matching.agent.lc4j.PostAbilityAiService;
import com.example.matching.agent.lc4j.PostEvolutionAiService;
import com.example.matching.agent.tools.*;
import com.example.matching.infrastructure.llm.memory.LangChain4jChatMemoryProvider;
import com.example.matching.infrastructure.llm.EnterpriseChatLanguageModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * LangChain4j Agent 配置
 *
 * @author system
 */
@Configuration
@EnableConfigurationProperties({LangChain4jAgentProperties.class, AgentMemoryProperties.class})
public class LangChain4jAgentConfig {

    private final AgentToolProvider agentToolProvider;
    private final com.example.matching.agent.json.JsonContractInjector jsonContractInjector;

    public LangChain4jAgentConfig(AgentToolProvider agentToolProvider,
                                  com.example.matching.agent.json.JsonContractInjector jsonContractInjector) {
        this.agentToolProvider = agentToolProvider;
        this.jsonContractInjector = jsonContractInjector;
    }

    @Bean
    @ConditionalOnProperty(prefix = "langchain4j.agents", name = "enabled", havingValue = "true")
    public ChatModel langChain4jChatLanguageModel(
            EnterpriseChatLanguageModel enterpriseChatLanguageModel,
            AgentObservationMetrics metrics) {
        return new ObservedChatLanguageModel(enterpriseChatLanguageModel, metrics);
    }

    @Bean
    @ConditionalOnProperty(prefix = "langchain4j.agents", name = "enabled", havingValue = "true")
    public com.example.matching.agent.json.CapabilityProbe capabilityProbe() {
        return new com.example.matching.agent.json.CapabilityProbe();
    }

    @Bean
    @ConditionalOnProperty(prefix = "langchain4j.agents", name = "enabled", havingValue = "true")
    public com.example.matching.agent.json.JsonRetryPolicy jsonRetryPolicy(
            org.springframework.core.env.Environment env) {
        int max = env.getProperty("ai.json.retry.max-attempts", Integer.class, 2);
        long backoff = env.getProperty("ai.json.retry.base-backoff-millis", Long.class, 50L);
        return new com.example.matching.agent.json.JsonRetryPolicy(max, backoff);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "langchain4j.agents", name = "enabled", havingValue = "true")
    public ChatModel jsonGuardChatModel(
            ChatModel langChain4jChatLanguageModel,
            com.example.matching.agent.json.CapabilityProbe capabilityProbe,
            com.example.matching.agent.json.JsonRetryPolicy jsonRetryPolicy,
            AgentObservationMetrics agentObservationMetrics,
            com.example.matching.ai.service.LlmInputGuard llmInputGuard) {
        return new com.example.matching.agent.json.JsonGuardChatModel(
                langChain4jChatLanguageModel, capabilityProbe, jsonRetryPolicy, agentObservationMetrics,
                llmInputGuard);
    }

    @Bean
    @ConditionalOnProperty(prefix = "langchain4j.agents", name = "enabled", havingValue = "true")
    public MatchingAnalysisAiService matchingAnalysisAiService(ChatModel chatLanguageModel) {
        // 完整受限上下文已由服务端构建。匹配 Agent 只能解释事实，不能用工具扩展事实范围。
        return AiServices.builder(MatchingAnalysisAiService.class)
                .chatModel(chatLanguageModel)
                .systemMessageTransformer(jsonContractInjector.systemMessageTransformer("MATCHING_ANALYSIS"))
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "langchain4j.agents", name = "enabled", havingValue = "true")
    public LearningPathAiService learningPathAiService(
            ChatModel chatLanguageModel,
            EmployeeProfileTool employeeProfileTool,
            PostRequirementTool postRequirementTool,
            LearningResourceTool learningResourceTool) {
        return AiServices.builder(LearningPathAiService.class)
                .chatModel(chatLanguageModel)
                .systemMessageTransformer(jsonContractInjector.systemMessageTransformer("LEARNING_PATH"))
                .toolProvider(agentToolProvider.forTools(employeeProfileTool, postRequirementTool, learningResourceTool))
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "langchain4j.agents", name = "enabled", havingValue = "true")
    public EvidenceGovernanceAiService evidenceGovernanceAiService(
            ChatModel chatLanguageModel,
            EvidenceContextTool evidenceContextTool,
            EmployeeProfileTool employeeProfileTool) {
        return AiServices.builder(EvidenceGovernanceAiService.class)
                .chatModel(chatLanguageModel)
                .systemMessageTransformer(jsonContractInjector.systemMessageTransformer("EVIDENCE_GOVERNANCE"))
                .toolProvider(agentToolProvider.forTools(evidenceContextTool, employeeProfileTool))
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "langchain4j.agents", name = "enabled", havingValue = "true")
    public EmployeeAbilityAiService employeeAbilityAiService(
            ChatModel chatLanguageModel,
            EmployeeProfileTool employeeProfileTool,
            EvidenceContextTool evidenceContextTool) {
        // 提取 Agent 不读全局图谱（开放词表规则见提示词）：仅保留员工资料/证据工具
        return AiServices.builder(EmployeeAbilityAiService.class)
                .chatModel(chatLanguageModel)
                .systemMessageTransformer(jsonContractInjector.systemMessageTransformer())
                .toolProvider(agentToolProvider.forTools(employeeProfileTool, evidenceContextTool))
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "langchain4j.agents", name = "enabled", havingValue = "true")
    public PostAbilityAiService postAbilityAiService(
            ChatModel chatLanguageModel,
            PostRequirementTool postRequirementTool) {
        // 提取 Agent 不读全局图谱（开放词表规则见提示词）：仅保留岗位要求工具
        return AiServices.builder(PostAbilityAiService.class)
                .chatModel(chatLanguageModel)
                .systemMessageTransformer(jsonContractInjector.systemMessageTransformer("POST_ABILITY_EXTRACTION"))
                .toolProvider(agentToolProvider.forTools(postRequirementTool))
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "langchain4j.agents", name = "enabled", havingValue = "true")
    public PostEvolutionAiService postEvolutionAiService(ChatModel chatLanguageModel) {
        // 岗位演化 Agent：输入为岗位当前能力状态 + 检索证据片段，输出结构化变更建议。
        // 不配工具，能力来源与防幻觉由服务端在调用后校验。
        return AiServices.builder(PostEvolutionAiService.class)
                .chatModel(chatLanguageModel)
                .systemMessageTransformer(jsonContractInjector.systemMessageTransformer("POST_EVOLUTION"))
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "langchain4j.agents", name = "enabled", havingValue = "true")
    public AiTestAiService aiTestAiService(
            ChatModel chatLanguageModel) {
        return AiServices.builder(AiTestAiService.class)
                .chatModel(chatLanguageModel)
                .systemMessageTransformer(jsonContractInjector.systemMessageTransformer())
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "langchain4j.agents", name = "enabled", havingValue = "true")
    public PmsAbilityAnalysisAiService pmsAbilityAnalysisAiService(
            ChatModel chatLanguageModel,
            EmployeeProfileTool employeeProfileTool,
            EvidenceContextTool evidenceContextTool,
            KnowledgeGraphTool knowledgeGraphTool) {
        return AiServices.builder(PmsAbilityAnalysisAiService.class)
                .chatModel(chatLanguageModel)
                .systemMessageTransformer(jsonContractInjector.systemMessageTransformer("PMS_ABILITY_ANALYSIS"))
                .toolProvider(agentToolProvider.forTools(employeeProfileTool, evidenceContextTool, knowledgeGraphTool))
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "langchain4j.agents", name = "enabled", havingValue = "true")
    public LangChain4jChatMemoryProvider interviewPlanChatMemoryProvider(
            com.example.matching.infrastructure.llm.memory.ChatMemoryProvider memoryProvider) {
        return new LangChain4jChatMemoryProvider(memoryProvider, "INTERVIEW_PLAN");
    }

    @Bean
    @ConditionalOnProperty(prefix = "langchain4j.agents", name = "enabled", havingValue = "true")
    public LangChain4jChatMemoryProvider interviewAnswerQualityChatMemoryProvider(
            com.example.matching.infrastructure.llm.memory.ChatMemoryProvider memoryProvider) {
        return new LangChain4jChatMemoryProvider(memoryProvider, "INTERVIEW_ANSWER_QUALITY");
    }

    @Bean
    @ConditionalOnProperty(prefix = "langchain4j.agents", name = "enabled", havingValue = "true")
    public LangChain4jChatMemoryProvider interviewFollowUpChatMemoryProvider(
            com.example.matching.infrastructure.llm.memory.ChatMemoryProvider memoryProvider) {
        return new LangChain4jChatMemoryProvider(memoryProvider, "INTERVIEW_FOLLOW_UP");
    }

    @Bean
    @ConditionalOnProperty(prefix = "langchain4j.agents", name = "enabled", havingValue = "true")
    public LangChain4jChatMemoryProvider interviewObservationChatMemoryProvider(
            com.example.matching.infrastructure.llm.memory.ChatMemoryProvider memoryProvider) {
        return new LangChain4jChatMemoryProvider(memoryProvider, "INTERVIEW_OBSERVATION");
    }

    @Bean
    @ConditionalOnProperty(prefix = "langchain4j.agents", name = "enabled", havingValue = "true")
    public LangChain4jChatMemoryProvider interviewReportChatMemoryProvider(
            com.example.matching.infrastructure.llm.memory.ChatMemoryProvider memoryProvider) {
        return new LangChain4jChatMemoryProvider(memoryProvider, "INTERVIEW_REPORT");
    }

    @Bean
    @ConditionalOnProperty(prefix = "langchain4j.agents", name = "enabled", havingValue = "true")
    public InterviewAnswerQualityAiService interviewAnswerQualityAiService(
            ChatModel chatLanguageModel,
            EmployeeProfileTool employeeProfileTool,
            PostRequirementTool postRequirementTool,
            EvidenceContextTool evidenceContextTool,
            LangChain4jChatMemoryProvider interviewAnswerQualityChatMemoryProvider) {
        return AiServices.builder(InterviewAnswerQualityAiService.class)
                .chatModel(chatLanguageModel)
                .systemMessageTransformer(jsonContractInjector.systemMessageTransformer())
                .chatMemoryProvider(interviewAnswerQualityChatMemoryProvider)
                .toolProvider(agentToolProvider.forTools(employeeProfileTool, postRequirementTool, evidenceContextTool))
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "langchain4j.agents", name = "enabled", havingValue = "true")
    public InterviewFollowUpAiService interviewFollowUpAiService(
            ChatModel chatLanguageModel,
            EvidenceContextTool evidenceContextTool,
            PostRequirementTool postRequirementTool,
            LangChain4jChatMemoryProvider interviewFollowUpChatMemoryProvider) {
        return AiServices.builder(InterviewFollowUpAiService.class)
                .chatModel(chatLanguageModel)
                .systemMessageTransformer(jsonContractInjector.systemMessageTransformer())
                .chatMemoryProvider(interviewFollowUpChatMemoryProvider)
                .toolProvider(agentToolProvider.forTools(evidenceContextTool, postRequirementTool))
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "langchain4j.agents", name = "enabled", havingValue = "true")
    public InterviewPlanAiService interviewPlanAiService(
            ChatModel chatLanguageModel,
            LangChain4jChatMemoryProvider interviewPlanChatMemoryProvider) {
        return AiServices.builder(InterviewPlanAiService.class)
                .chatModel(chatLanguageModel)
                .systemMessageTransformer(jsonContractInjector.systemMessageTransformer())
                .chatMemoryProvider(interviewPlanChatMemoryProvider)
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "langchain4j.agents", name = "enabled", havingValue = "true")
    public InterviewObservationAiService interviewObservationAiService(
            ChatModel chatLanguageModel,
            LangChain4jChatMemoryProvider interviewObservationChatMemoryProvider) {
        return AiServices.builder(InterviewObservationAiService.class)
                .chatModel(chatLanguageModel)
                .systemMessageTransformer(jsonContractInjector.systemMessageTransformer())
                .chatMemoryProvider(interviewObservationChatMemoryProvider)
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "langchain4j.agents", name = "enabled", havingValue = "true")
    public InterviewReportAiService interviewReportAiService(
            ChatModel chatLanguageModel,
            LangChain4jChatMemoryProvider interviewReportChatMemoryProvider) {
        return AiServices.builder(InterviewReportAiService.class)
                .chatModel(chatLanguageModel)
                .systemMessageTransformer(jsonContractInjector.systemMessageTransformer())
                .chatMemoryProvider(interviewReportChatMemoryProvider)
                .build();
    }
}
