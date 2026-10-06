package com.example.matching.controller.learning;

import com.example.matching.application.learning.LearningPathEnhancedApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.learning.LearningPathItemDTO;
import com.example.matching.dto.learning.LearningPathRequestDTO;
import com.example.matching.dto.learning.api.KnowledgeDomainResponse;
import com.example.matching.dto.learning.api.KnowledgeNodeResponse;
import com.example.matching.dto.learning.api.LearningQuizRecordRequest;
import com.example.matching.dto.learning.api.LearningQuizRecordResponse;
import com.example.matching.dto.learning.api.LearningQuizResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearningPathEnhancedControllerTest {

    private final LearningPathEnhancedApiFacade facade = mock(LearningPathEnhancedApiFacade.class);
    private final LearningPathEnhancedController controller = new LearningPathEnhancedController(facade);

    @Test
    void generateLearningPathByKnowledgeGraphReturnsItems() {
        LearningPathRequestDTO request = mock(LearningPathRequestDTO.class);
        LearningPathItemDTO item = mock(LearningPathItemDTO.class);
        when(facade.generateLearningPathByKnowledgeGraph(request)).thenReturn(List.of(item));

        R<List<LearningPathItemDTO>> response = controller.generateLearningPathByKnowledgeGraph(request);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).containsExactly(item);
    }

    @Test
    void generateLearningPathByMasteryReturnsItems() {
        LearningPathItemDTO item = mock(LearningPathItemDTO.class);
        when(facade.generateLearningPathByMastery(1L, 2L)).thenReturn(List.of(item));

        R<List<LearningPathItemDTO>> response = controller.generateLearningPathByMastery(1L, 2L);

        assertThat(response.getData()).containsExactly(item);
    }

    @Test
    void getLearningPathRecommendationsReturnsList() {
        when(facade.getLearningPathRecommendations(1L, 2L)).thenReturn(List.of(Map.of("path", "A")));

        R<List<Map<String, Object>>> response = controller.getLearningPathRecommendations(1L, 2L);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).hasSize(1);
    }

    @Test
    void getDomainMasteryScoresReturnsMap() {
        when(facade.getDomainMasteryScores(1L)).thenReturn(Map.of(10L, 0.8));

        R<Map<Long, Double>> response = controller.getDomainMasteryScores(1L);

        assertThat(response.getData()).containsEntry(10L, 0.8);
    }

    @Test
    void getNodeMasteryScoresReturnsMap() {
        when(facade.getNodeMasteryScores(1L, 10L)).thenReturn(Map.of(20L, 0.5));

        R<Map<Long, Double>> response = controller.getNodeMasteryScores(1L, 10L);

        assertThat(response.getData()).containsEntry(20L, 0.5);
    }

    @Test
    void getWeakPointsReturnsList() {
        when(facade.getWeakPoints(1L, 10)).thenReturn(List.of(Map.of("nodeId", 3L)));

        R<List<Map<String, Object>>> response = controller.getWeakPoints(1L, 10);

        assertThat(response.getData()).hasSize(1);
    }

    @Test
    void getDomainLearningOrderReturnsDomains() {
        KnowledgeDomainResponse domain = mock(KnowledgeDomainResponse.class);
        when(facade.getDomainLearningOrder(1L, 2L)).thenReturn(List.of(domain));

        R<List<KnowledgeDomainResponse>> response = controller.getDomainLearningOrder(1L, 2L);

        assertThat(response.getData()).containsExactly(domain);
    }

    @Test
    void getNodeLearningOrderReturnsNodes() {
        KnowledgeNodeResponse node = mock(KnowledgeNodeResponse.class);
        when(facade.getNodeLearningOrder(1L, 10L)).thenReturn(List.of(node));

        R<List<KnowledgeNodeResponse>> response = controller.getNodeLearningOrder(1L, 10L);

        assertThat(response.getData()).containsExactly(node);
    }

    @Test
    void updateLearningProgressReturnsOk() {
        R<Void> response = controller.updateLearningProgress(1L, 20L, "COMPLETED");

        assertThat(response.getCode()).isEqualTo(200);
        verify(facade).updateLearningProgress(1L, 20L, "COMPLETED");
    }

    @Test
    void getLearningProgressOverviewReturnsMap() {
        when(facade.getLearningProgressOverview(1L)).thenReturn(Map.of("total", 5));

        R<Map<String, Object>> response = controller.getLearningProgressOverview(1L);

        assertThat(response.getData()).containsEntry("total", 5);
    }

    @Test
    void getAllDomainsReturnsDomains() {
        KnowledgeDomainResponse domain = mock(KnowledgeDomainResponse.class);
        when(facade.getAllDomains()).thenReturn(List.of(domain));

        R<List<KnowledgeDomainResponse>> response = controller.getAllDomains();

        assertThat(response.getData()).containsExactly(domain);
    }

    @Test
    void getDomainByIdReturnsDomain() {
        KnowledgeDomainResponse domain = mock(KnowledgeDomainResponse.class);
        when(facade.getDomainById(5L)).thenReturn(domain);

        R<KnowledgeDomainResponse> response = controller.getDomainById(5L);

        assertThat(response.getData()).isSameAs(domain);
    }

    @Test
    void getNodesByDomainIdReturnsNodes() {
        KnowledgeNodeResponse node = mock(KnowledgeNodeResponse.class);
        when(facade.getNodesByDomainId(5L)).thenReturn(List.of(node));

        R<List<KnowledgeNodeResponse>> response = controller.getNodesByDomainId(5L);

        assertThat(response.getData()).containsExactly(node);
    }

    @Test
    void getAllQuizzesReturnsQuizzes() {
        LearningQuizResponse quiz = mock(LearningQuizResponse.class);
        when(facade.getAllQuizzes()).thenReturn(List.of(quiz));

        R<List<LearningQuizResponse>> response = controller.getAllQuizzes();

        assertThat(response.getData()).containsExactly(quiz);
    }

    @Test
    void getQuizzesByDomainIdReturnsQuizzes() {
        LearningQuizResponse quiz = mock(LearningQuizResponse.class);
        when(facade.getQuizzesByDomainId(5L)).thenReturn(List.of(quiz));

        R<List<LearningQuizResponse>> response = controller.getQuizzesByDomainId(5L);

        assertThat(response.getData()).containsExactly(quiz);
    }

    @Test
    void getQuizzesByNodeIdReturnsQuizzes() {
        LearningQuizResponse quiz = mock(LearningQuizResponse.class);
        when(facade.getQuizzesByNodeId(7L)).thenReturn(List.of(quiz));

        R<List<LearningQuizResponse>> response = controller.getQuizzesByNodeId(7L);

        assertThat(response.getData()).containsExactly(quiz);
    }

    @Test
    void submitQuizRecordReturnsRecord() {
        LearningQuizRecordRequest request = mock(LearningQuizRecordRequest.class);
        LearningQuizRecordResponse expected = mock(LearningQuizRecordResponse.class);
        when(facade.submitQuizRecord(request)).thenReturn(expected);

        R<LearningQuizRecordResponse> response = controller.submitQuizRecord(request);

        assertThat(response.getData()).isSameAs(expected);
    }

    @Test
    void getQuizRecordsByEmpIdReturnsRecords() {
        LearningQuizRecordResponse record = mock(LearningQuizRecordResponse.class);
        when(facade.getQuizRecordsByEmpId(1L)).thenReturn(List.of(record));

        R<List<LearningQuizRecordResponse>> response = controller.getQuizRecordsByEmpId(1L);

        assertThat(response.getData()).containsExactly(record);
    }
}
