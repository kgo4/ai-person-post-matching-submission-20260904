package com.example.matching.service.learning;

import com.example.matching.ai.service.LangChain4jChatService;
import com.example.matching.ai.service.PromptTemplateService;
import com.example.matching.dto.learning.AiLearningSuggestionDTO;
import com.example.matching.dto.learning.LearningPathGenerateRequest;
import com.example.matching.mapper.learning.AiLearningSuggestionLogMapper;
import com.example.matching.mapper.learning.LearningResourceMapper;
import com.example.matching.mapper.matching.MatchingRecordMapper;
import com.example.matching.port.closure.MatchDiagnosisQueryPort;
import com.example.matching.resilience.AiServiceResilience;
import com.example.matching.service.kg.KnowledgeGraphQueryService;
import com.example.matching.service.learning.impl.AiLearningSuggestionServiceImpl;
import com.example.matching.service.rag.RagRetrievalService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiLearningSuggestionServiceImplTest {

    @Mock
    private LangChain4jChatService chatService;
    @Mock
    private PromptTemplateService promptTemplateService;
    @Mock
    private AiServiceResilience aiServiceResilience;
    @Mock
    private RagRetrievalService ragContextService;
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
    private KnowledgeGraphQueryService knowledgeGraphQueryService;

    @Test
    void matchingLinkedSuggestionsPersistPlanBeforeReturningInsufficientEvidence() {
        AiLearningSuggestionServiceImpl service = new AiLearningSuggestionServiceImpl(
                chatService, promptTemplateService, aiServiceResilience, new ObjectMapper(), ragContextService,
                matchDiagnosisQueryPort, resourceMapper, matchingRecordMapper, validator, suggestionLogMapper,
                learningPathPlanService, knowledgeGraphQueryService,
                new com.example.matching.infrastructure.llm.LlmResponseParser(new ObjectMapper()));
        when(resourceMapper.selectList(any())).thenReturn(List.of());

        AiLearningSuggestionDTO.Request.GapInput gap = new AiLearningSuggestionDTO.Request.GapInput();
        gap.setTagId(6L);
        gap.setAbilityName("Java");
        gap.setCurrentLevel(BigDecimal.valueOf(2));
        gap.setRequiredLevel(4);
        AiLearningSuggestionDTO.Request request = new AiLearningSuggestionDTO.Request();
        request.setMatchingRecordId(42L);
        request.setGaps(List.of(gap));

        service.generateSuggestions(request);

        ArgumentCaptor<LearningPathGenerateRequest> planRequest =
                ArgumentCaptor.forClass(LearningPathGenerateRequest.class);
        verify(learningPathPlanService).generateFromMatchingRecord(planRequest.capture());
        assertThat(planRequest.getValue().getMatchingRecordId()).isEqualTo(42L);
        assertThat(planRequest.getValue().getIncludeProjectTasks()).isTrue();
        assertThat(planRequest.getValue().getForceRegenerate()).isFalse();
    }
}
