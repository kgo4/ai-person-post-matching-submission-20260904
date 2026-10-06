package com.example.matching.service.learning;

import com.example.matching.ai.service.LangChain4jChatService;
import com.example.matching.ai.service.PromptTemplateService;
import com.example.matching.dto.kg.context.GraphLearningPrerequisiteContext;
import com.example.matching.dto.learning.AiLearningSuggestionDTO;
import com.example.matching.mapper.learning.AiLearningSuggestionLogMapper;
import com.example.matching.mapper.learning.LearningResourceMapper;
import com.example.matching.mapper.matching.MatchingRecordMapper;
import com.example.matching.port.closure.MatchDiagnosisQueryPort;
import com.example.matching.resilience.AiServiceResilience;
import com.example.matching.service.kg.KnowledgeGraphQueryService;
import com.example.matching.service.learning.impl.AiLearningSuggestionServiceImpl;
import com.example.matching.service.rag.RagRetrievalRequest;
import com.example.matching.service.rag.RagRetrievalResult;
import com.example.matching.service.rag.RagRetrievalService;
import com.example.matching.service.rag.RagScenarioEnum;
import com.example.matching.entity.learning.LearningResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiLearningSuggestionGroundingTest {

    @Mock
    private LangChain4jChatService chatService;
    @Mock
    private PromptTemplateService promptTemplateService;
    @Mock
    private AiServiceResilience aiServiceResilience;
    @Mock
    private RagRetrievalService ragRetrievalService;
    @Mock
    private MatchDiagnosisQueryPort matchDiagnosisQueryPort;
    @Mock
    private LearningResourceMapper resourceMapper;
    @Mock
    private MatchingRecordMapper matchingRecordMapper;
    @Mock
    private AiLearningSuggestionValidator validator;
    @Mock
    private AiLearningSuggestionLogMapper suggestionLogMapper;
    @Mock
    private LearningPathPlanService learningPathPlanService;
    @Mock
    private KnowledgeGraphQueryService graphQueryService;

    @Test
    void rendersGraphPrerequisitesAndRagContextForVerifiedGaps() {
        AiLearningSuggestionServiceImpl service = new AiLearningSuggestionServiceImpl(
                chatService, promptTemplateService, aiServiceResilience, new ObjectMapper(), ragRetrievalService,
                matchDiagnosisQueryPort, resourceMapper, matchingRecordMapper, validator, suggestionLogMapper,
                learningPathPlanService, graphQueryService,
                new com.example.matching.infrastructure.llm.LlmResponseParser(new ObjectMapper()));

        when(promptTemplateService.render(eq("learning-suggestion-prompt"), anyMap())).thenReturn("prompt");
        when(chatService.chat(eq("learning-suggestion"), eq("prompt"), any()))
                .thenReturn("{\"suggestions\":[]}");
        when(validator.validate(any(), any(), any(), any(), any()))
                .thenReturn(new AiLearningSuggestionDTO.ValidationSummary());

        LearningResource resource = new LearningResource();
        resource.setId(1L);
        resource.setTitle("Java Basics");
        resource.setAbilityName("Java");
        resource.setStatus(1);
        when(resourceMapper.selectList(any())).thenReturn(List.of(resource));

        AiLearningSuggestionDTO.Request.GapInput gap = new AiLearningSuggestionDTO.Request.GapInput();
        gap.setTagId(7L);
        gap.setAbilityName("Java");
        gap.setCurrentLevel(BigDecimal.valueOf(2));
        gap.setRequiredLevel(4);
        AiLearningSuggestionDTO.Request request = new AiLearningSuggestionDTO.Request();
        request.setMatchingRecordId(42L);
        request.setGaps(List.of(gap));

        GraphLearningPrerequisiteContext prereqContext = new GraphLearningPrerequisiteContext(
                List.of(7L), List.of(new GraphLearningPrerequisiteContext.PrerequisiteNode(
                        7L, "Java", 5L, "OOP", "PREREQUISITE_OF", List.of("fact:TAG"), "v1")));
        when(graphQueryService.getLearningPrerequisiteContext(anyList()))
                .thenReturn(prereqContext);
        when(ragRetrievalService.retrieveContext(contains("Java"),
                eq(RagScenarioEnum.LEARNING_RECOMMENDATION), eq(5)))
                .thenReturn("RAG_LEARNING");

        service.generateSuggestions(request);

        verify(graphQueryService).getLearningPrerequisiteContext(anyList());
        verify(ragRetrievalService).retrieveContext(contains("Java"),
                eq(RagScenarioEnum.LEARNING_RECOMMENDATION), eq(5));

        ArgumentCaptor<Map<String, Object>> modelCaptor = ArgumentCaptor.forClass(Map.class);
        verify(promptTemplateService).render(eq("learning-suggestion-prompt"), modelCaptor.capture());
        Map<String, Object> model = modelCaptor.getValue();
        assertThat(model).containsKey("graphPrerequisites");
        assertThat(model).containsKey("ragContext");
    }
}
