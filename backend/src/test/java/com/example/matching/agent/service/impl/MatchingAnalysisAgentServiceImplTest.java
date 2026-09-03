package com.example.matching.agent.service.impl;

import com.example.matching.agent.config.LangChain4jAgentProperties;
import com.example.matching.agent.dto.AgentContextPackage;
import com.example.matching.agent.dto.MatchingAnalysisAgentRequest;
import com.example.matching.agent.dto.MatchingAnalysisAgentResult;
import com.example.matching.agent.dto.MatchingAnalysisModelResult;
import com.example.matching.agent.dto.graph.AgentGraphContext;
import com.example.matching.agent.lc4j.MatchingAnalysisAiService;
import com.example.matching.agent.service.AgentContextPackageService;
import com.example.matching.agent.service.AgentFallbackService;
import com.example.matching.agent.service.AgentGraphContextAssembler;
import com.example.matching.infrastructure.llm.LlmResponseParser;
import com.example.matching.service.rag.RagRetrievalService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 匹配分析 Agent 图谱预构建改造测试（方案第十四章 6/8 项）。
 */
class MatchingAnalysisAgentServiceImplTest {

    private MatchingAnalysisAgentServiceImpl service(
            LangChain4jAgentProperties properties,
            AgentContextPackageService contextPackageService,
            AgentFallbackService fallbackService,
            RagRetrievalService ragRetrievalService,
            GroundedAgentOutputValidator validator,
            AgentGraphContextAssembler assembler,
            MatchingAnalysisAiService model) {
        @SuppressWarnings("unchecked")
        ObjectProvider<MatchingAnalysisAiService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(model);
        return new MatchingAnalysisAgentServiceImpl(
                properties, contextPackageService, fallbackService, new ObjectMapper(),
                mock(LlmResponseParser.class), ragRetrievalService, validator,
                mock(AgentRunConfidencePolicy.class), assembler, provider);
    }

    @Test
    void buildsGraphContextAndPutsItIntoAgentContextPackage() {
        LangChain4jAgentProperties properties = new LangChain4jAgentProperties();
        properties.setEnabled(true);
        AgentContextPackage context = new AgentContextPackage();
        context.setEmpId(7L);
        context.setPostId(9L);
        AgentGraphContext graphContext = new AgentGraphContext();
        graphContext.setStatus("FRESH");
        AgentGraphContextAssembler assembler = mock(AgentGraphContextAssembler.class);
        when(assembler.buildForMatching(7L, 9L)).thenReturn(graphContext);
        MatchingAnalysisAiService model = mock(MatchingAnalysisAiService.class);
        when(model.analyze(any())).thenReturn(new MatchingAnalysisModelResult());
        AgentContextPackageService contextPackageService = mock(AgentContextPackageService.class);
        when(contextPackageService.buildForMatchingRecord(100L)).thenReturn(context);
        GroundedAgentOutputValidator validator = mock(GroundedAgentOutputValidator.class);
        when(validator.validateMatching(any(), eq(context)))
                .thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));

        MatchingAnalysisAgentServiceImpl service = service(
                properties, contextPackageService, mock(AgentFallbackService.class),
                mock(RagRetrievalService.class), validator, assembler, model);

        MatchingAnalysisAgentRequest request = new MatchingAnalysisAgentRequest();
        request.setMatchingRecordId(100L);
        service.analyze(request);

        // 子图已装配并放入 AgentContextPackage（一次性进入上下文）
        verify(assembler).buildForMatching(7L, 9L);
        assertThat(context.getGraphContext()).isSameAs(graphContext);
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(model).analyze(promptCaptor.capture());
        assertThat(promptCaptor.getValue()).contains("graphContext");
    }

    @Test
    void continuesWhenGraphContextUnavailable() {
        LangChain4jAgentProperties properties = new LangChain4jAgentProperties();
        properties.setEnabled(true);
        AgentContextPackage context = new AgentContextPackage();
        context.setEmpId(7L);
        context.setPostId(9L);
        AgentGraphContextAssembler assembler = mock(AgentGraphContextAssembler.class);
        // 装配器失败也返回 UNAVAILABLE 上下文，不抛异常
        AgentGraphContext unavailable = new AgentGraphContext();
        unavailable.setStatus("UNAVAILABLE");
        when(assembler.buildForMatching(7L, 9L)).thenReturn(unavailable);
        MatchingAnalysisAiService model = mock(MatchingAnalysisAiService.class);
        when(model.analyze(any())).thenReturn(new MatchingAnalysisModelResult());
        AgentContextPackageService contextPackageService = mock(AgentContextPackageService.class);
        when(contextPackageService.buildForMatchingRecord(100L)).thenReturn(context);
        GroundedAgentOutputValidator validator = mock(GroundedAgentOutputValidator.class);
        when(validator.validateMatching(any(), eq(context)))
                .thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));

        MatchingAnalysisAgentServiceImpl service = service(
                properties, contextPackageService, mock(AgentFallbackService.class),
                mock(RagRetrievalService.class), validator, assembler, model);

        MatchingAnalysisAgentRequest request = new MatchingAnalysisAgentRequest();
        request.setMatchingRecordId(100L);
        MatchingAnalysisAgentResult result = service.analyze(request);

        // 图谱不可用不阻断主业务：模型被调用且结果返回
        verify(model).analyze(any());
        assertThat(result).isNotNull();
    }

    @Test
    void keepsRagContextAndStructuredHitsInPrompt() {
        LangChain4jAgentProperties properties = new LangChain4jAgentProperties();
        properties.setEnabled(true);
        AgentContextPackage context = new AgentContextPackage();
        context.setEmpId(7L);
        context.setPostId(9L);
        AgentGraphContextAssembler assembler = mock(AgentGraphContextAssembler.class);
        AgentGraphContext graphContext = new AgentGraphContext();
        graphContext.setStatus("FRESH");
        when(assembler.buildForMatching(7L, 9L)).thenReturn(graphContext);
        RagRetrievalService ragRetrievalService = mock(RagRetrievalService.class);
        MatchingAnalysisAiService model = mock(MatchingAnalysisAiService.class);
        when(model.analyze(any())).thenReturn(new MatchingAnalysisModelResult());
        AgentContextPackageService contextPackageService = mock(AgentContextPackageService.class);
        when(contextPackageService.buildForMatchingRecord(100L)).thenReturn(context);
        GroundedAgentOutputValidator validator = mock(GroundedAgentOutputValidator.class);
        when(validator.validateMatching(any(), eq(context)))
                .thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));

        MatchingAnalysisAgentServiceImpl service = service(
                properties, contextPackageService, mock(AgentFallbackService.class),
                ragRetrievalService, validator, assembler, model);

        MatchingAnalysisAgentRequest request = new MatchingAnalysisAgentRequest();
        request.setMatchingRecordId(100L);
        service.analyze(request);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(model).analyze(promptCaptor.capture());
        assertThat(promptCaptor.getValue()).contains("agentContext").contains("graphContext");
    }
}
