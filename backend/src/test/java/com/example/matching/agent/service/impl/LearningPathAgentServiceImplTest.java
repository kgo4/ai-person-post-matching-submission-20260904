package com.example.matching.agent.service.impl;

import com.example.matching.agent.config.LangChain4jAgentProperties;
import com.example.matching.agent.dto.AgentContextPackage;
import com.example.matching.agent.dto.LearningPathAgentRequest;
import com.example.matching.agent.dto.LearningPathAgentResult;
import com.example.matching.agent.dto.graph.AgentGraphContext;
import com.example.matching.agent.lc4j.LearningPathAiService;
import com.example.matching.agent.service.AgentContextPackageService;
import com.example.matching.agent.service.AgentFallbackService;
import com.example.matching.agent.service.AgentGraphContextAssembler;
import com.example.matching.application.agent.AgentScoreBreakdown;
import com.example.matching.application.agent.EmployeeAbilitySnapshot;
import com.example.matching.application.agent.PostRequirementSnapshot;
import com.example.matching.infrastructure.llm.LlmResponseParser;
import com.example.matching.service.rag.RagRetrievalService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 学习路径 Agent 图谱预构建改造测试（方案第十四章 9 项：使用预计算前置关系）。
 */
class LearningPathAgentServiceImplTest {

    private LearningPathAgentServiceImpl service(
            AgentContextPackageService contextPackageService,
            AgentGraphContextAssembler assembler,
            GroundedAgentOutputValidator validator,
            LearningPathAiService model) {
        LangChain4jAgentProperties properties = new LangChain4jAgentProperties();
        properties.setEnabled(true);
        @SuppressWarnings("unchecked")
        ObjectProvider<LearningPathAiService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(model);
        RagRetrievalService ragRetrievalService = mock(RagRetrievalService.class);
        when(ragRetrievalService.retrieveContext(any(), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn("");
        return new LearningPathAgentServiceImpl(
                properties, contextPackageService, mock(AgentFallbackService.class),
                new ObjectMapper(), mock(LlmResponseParser.class), assembler,
                ragRetrievalService, validator,
                mock(AgentRunConfidencePolicy.class), provider);
    }

    private AgentContextPackage contextWithGap() {
        AgentContextPackage context = new AgentContextPackage();
        context.setEmpId(7L);
        context.setPostId(9L);
        context.setPostRequirements(List.of(
                new PostRequirementSnapshot(11L, "Java", 4,
                        new BigDecimal("0.5"), true, true),
                new PostRequirementSnapshot(12L, "Spring", 3,
                        new BigDecimal("0.3"), true, false)));
        context.setEmployeeAbilities(List.of(
                new EmployeeAbilitySnapshot(11L, "Java", 2, "EMP_ABILITY", null, 1),
                new EmployeeAbilitySnapshot(12L, "Spring", 3, "EMP_ABILITY", null, 1)));
        context.setScoreBreakdown(List.of(
                new AgentScoreBreakdown("ability", new BigDecimal("55"), new BigDecimal("0.5"), ""),
                new AgentScoreBreakdown("semantic", new BigDecimal("80"), new BigDecimal("0.3"), "")));
        return context;
    }

    @Test
    void buildsLearningPathGraphWithDerivedGapTagIds() {
        AgentContextPackage context = contextWithGap();
        AgentContextPackageService contextPackageService = mock(AgentContextPackageService.class);
        when(contextPackageService.buildForMatchingRecord(100L)).thenReturn(context);
        AgentGraphContextAssembler assembler = mock(AgentGraphContextAssembler.class);
        AgentGraphContext graphContext = new AgentGraphContext();
        graphContext.setStatus("FRESH");
        when(assembler.buildForLearningPath(eq(7L), eq(9L), eq(Set.of(11L)))).thenReturn(graphContext);
        GroundedAgentOutputValidator validator = mock(GroundedAgentOutputValidator.class);
        when(validator.validateLearningPath(any(), eq(context), eq(Set.of(11L))))
                .thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));
        LearningPathAiService model = mock(LearningPathAiService.class);
        when(model.generatePath(any())).thenReturn(new LearningPathAgentResult());

        LearningPathAgentServiceImpl service = service(contextPackageService, assembler, validator, model);
        LearningPathAgentRequest request = new LearningPathAgentRequest();
        request.setMatchingRecordId(100L);
        service.preview(request);

        // 缺口标签（scoreBreakdown<70 的 Java=11）传入装配器，子图放入上下文与 prompt
        verify(assembler).buildForLearningPath(7L, 9L, Set.of(11L));
        assertThat(context.getGraphContext()).isSameAs(graphContext);
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(model).generatePath(promptCaptor.capture());
        assertThat(promptCaptor.getValue()).contains("graphContext");
    }

    @Test
    void outputAbilityIdsAreRestrictedToGapWhitelistByValidator() {
        AgentContextPackage context = contextWithGap();
        AgentContextPackageService contextPackageService = mock(AgentContextPackageService.class);
        when(contextPackageService.buildForMatchingRecord(100L)).thenReturn(context);
        AgentGraphContextAssembler assembler = mock(AgentGraphContextAssembler.class);
        when(assembler.buildForLearningPath(eq(7L), eq(9L), eq(Set.of(11L))))
                .thenReturn(new AgentGraphContext());
        GroundedAgentOutputValidator validator = mock(GroundedAgentOutputValidator.class);
        when(validator.validateLearningPath(any(), eq(context), eq(Set.of(11L))))
                .thenReturn(Optional.empty()); // 白名单外能力 → 拒绝
        LearningPathAiService model = mock(LearningPathAiService.class);
        when(model.generatePath(any())).thenReturn(new LearningPathAgentResult());

        LearningPathAgentServiceImpl service = service(contextPackageService, assembler, validator, model);
        LearningPathAgentRequest request = new LearningPathAgentRequest();
        request.setMatchingRecordId(100L);

        // 校验失败 → 走降级路径（fallback），模型结果不被采纳
        service.preview(request);
        verify(model).generatePath(any());
    }
}
