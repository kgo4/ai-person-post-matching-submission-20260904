package com.example.matching.agent.service.impl;

import com.example.matching.agent.config.LangChain4jAgentProperties;
import com.example.matching.agent.dto.AgentContextPackage;
import com.example.matching.agent.dto.LearningPathAgentRequest;
import com.example.matching.agent.dto.LearningPathAgentResult;
import com.example.matching.agent.lc4j.LearningPathAiService;
import com.example.matching.agent.service.AgentContextPackageService;
import com.example.matching.agent.service.AgentFallbackService;
import com.example.matching.agent.service.LearningPathAgentService;
import com.example.matching.application.agent.PostRequirementSnapshot;
import com.example.matching.dto.kg.context.GraphLearningPrerequisiteContext;
import com.example.matching.infrastructure.llm.LlmResponseParser;
import com.example.matching.service.kg.KnowledgeGraphQueryService;
import com.example.matching.service.rag.RagRetrievalService;
import com.example.matching.service.rag.RagScenarioEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 学习路径Agent服务实现
 * <p>
 * 使用LangChain4j AiServices + Tool编排
 *
 * @author system
 */
@Slf4j
@Service
public class LearningPathAgentServiceImpl extends AbstractAgentService implements LearningPathAgentService {

    private final LangChain4jAgentProperties properties;
    private final AgentContextPackageService contextPackageService;
    private final AgentFallbackService fallbackService;
    private final ObjectMapper objectMapper;
    private final LlmResponseParser llmResponseParser;
    private final com.example.matching.agent.service.AgentGraphContextAssembler agentGraphContextAssembler;
    private final RagRetrievalService ragRetrievalService;
    private final GroundedAgentOutputValidator outputValidator;
    private final LearningPathAiService learningPathAiService;

    public LearningPathAgentServiceImpl(
            LangChain4jAgentProperties properties,
            AgentContextPackageService contextPackageService,
            AgentFallbackService fallbackService,
            ObjectMapper objectMapper,
            LlmResponseParser llmResponseParser,
            com.example.matching.agent.service.AgentGraphContextAssembler agentGraphContextAssembler,
            RagRetrievalService ragRetrievalService,
            GroundedAgentOutputValidator outputValidator,
            AgentRunConfidencePolicy confidencePolicy,
            ObjectProvider<LearningPathAiService> aiServiceProvider) {
        super(confidencePolicy);
        this.properties = properties;
        this.contextPackageService = contextPackageService;
        this.fallbackService = fallbackService;
        this.objectMapper = objectMapper;
        this.llmResponseParser = llmResponseParser;
        this.agentGraphContextAssembler = agentGraphContextAssembler;
        this.ragRetrievalService = ragRetrievalService;
        this.outputValidator = outputValidator;
        this.learningPathAiService = aiServiceProvider.getIfAvailable();
    }

    @Override
    public LearningPathAgentResult preview(LearningPathAgentRequest request) {
        AgentContextPackage context = contextPackageService.buildForMatchingRecord(request.getMatchingRecordId());

        if (!properties.isEnabled() || learningPathAiService == null) {
            log.info("LangChain4j未启用，使用降级方案");
            return fallbackService.fallbackLearningPath(context);
        }

        return runWithFallback(() -> {
            Set<Long> gapTagIds = deriveGapTagIds(context);
            // 图谱预构建：一次性获得岗位能力缺口、当前/目标等级、核心性权重、
            // 前置能力关系与前置能力是否已满足；模型不再自行判断学习顺序
            com.example.matching.agent.dto.graph.AgentGraphContext graphContext =
                    agentGraphContextAssembler.buildForLearningPath(
                            context.getEmpId(), context.getPostId(), gapTagIds);
            context.setGraphContext(graphContext);

            String ragQuery = context.getPostRequirements() != null
                    ? context.getPostRequirements().stream()
                            .map(r -> r.abilityName())
                            .filter(Objects::nonNull)
                            .collect(Collectors.joining(" "))
                    : "";
            String ragContext = "";
            if (!ragQuery.isBlank()) {
                try {
                    ragContext = ragRetrievalService.retrieveContext(ragQuery,
                            RagScenarioEnum.LEARNING_RECOMMENDATION, 5);
                } catch (Exception e) {
                    log.warn("RAG context retrieval failed: {}", e.getMessage());
                }
            }

            Map<String, Object> promptContext = new LinkedHashMap<>();
            promptContext.put("agentContext", context);
            promptContext.put("graphContext", graphContext);
            if (!ragContext.isBlank()) {
                promptContext.put("ragContext", ragContext);
            }
            String contextJson = objectMapper.writeValueAsString(promptContext);
            LearningPathAgentResult result = com.example.matching.agent.config.AgentToolProvider
                    .withScope(() -> learningPathAiService.generatePath(contextJson));
            if (result == null) {
                throw new IllegalStateException("Learning path returned no structured result");
            }

            var validated = outputValidator.validateLearningPath(result, context, gapTagIds);
            if (validated.isEmpty()) {
                throw new IllegalStateException("Learning path validation failed");
            }
            result = validated.get();

            String rawOutput = objectMapper.writeValueAsString(result);
            log.info("学习路径Agent预览完成: steps={}", result.getSteps() != null ? result.getSteps().size() : 0);
            return finalizeRun(result, result.getSourceRefs(), false, rawOutput);
        }, e -> {
            log.error("LangChain4j调用失败，使用降级方案", e);
            return fallbackService.fallbackLearningPath(context);
        });
    }

    private Set<Long> deriveGapTagIds(AgentContextPackage context) {
        if (context.getPostRequirements() == null) {
            return Set.of();
        }
        Map<Long, Integer> employeeLevels = context.getEmployeeAbilities() == null
                ? Map.of()
                : context.getEmployeeAbilities().stream()
                        .filter(a -> a.abilityTagId() != null)
                        .collect(Collectors.toMap(
                                a -> a.abilityTagId(),
                                a -> a.currentLevel() != null ? a.currentLevel() : 0,
                                Math::max));

        // 缺口必须从能力模型与员工能力事实计算，不能依赖通用评分维度名称。
        return context.getPostRequirements().stream()
                .filter(r -> r.required() && r.abilityTagId() != null)
                .filter(r -> employeeLevels.getOrDefault(r.abilityTagId(), 0)
                        < (r.requiredLevel() != null ? r.requiredLevel() : 0))
                .map(PostRequirementSnapshot::abilityTagId)
                .collect(Collectors.toSet());
    }
}
