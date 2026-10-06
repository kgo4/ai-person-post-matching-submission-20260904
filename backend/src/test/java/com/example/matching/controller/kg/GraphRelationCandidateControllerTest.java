package com.example.matching.controller.kg;

import com.example.matching.application.kg.GraphRelationCandidateApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.kg.GraphRelationCandidateCreateDTO;
import com.example.matching.dto.kg.GraphRelationCandidateReviewDTO;
import com.example.matching.dto.kg.GraphRelationCandidateRevokeDTO;
import com.example.matching.dto.kg.api.GraphRelationCandidateResponse;
import com.example.matching.utils.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GraphRelationCandidateControllerTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityUtils.clear();
    }

    private static GraphRelationCandidateResponse createResponse(Long id, String reviewStatus) {
        return new GraphRelationCandidateResponse(
                id, "CAND_001", "ABILITY_1", "ABILITY_2", "PREREQUISITE",
                "EMBEDDING_SIMILARITY", new BigDecimal("0.87"), "[\"ref-1\"]",
                reviewStatus, null, null, null, 7L, null, null);
    }

    @Test
    void createDelegatesWithCurrentUserId() {
        GraphRelationCandidateApiFacade facade = mock(GraphRelationCandidateApiFacade.class);
        GraphRelationCandidateController controller = new GraphRelationCandidateController(facade);

        GraphRelationCandidateCreateDTO request = new GraphRelationCandidateCreateDTO();
        request.setSourceNodeKey("ABILITY_1");
        request.setTargetNodeKey("ABILITY_2");
        request.setDiscoveryMethod("EMBEDDING_SIMILARITY");
        request.setSemanticScore(new BigDecimal("0.87"));
        request.setSourceRefs(List.of("ref-1"));

        GraphRelationCandidateResponse candidate = createResponse(1L, "PENDING");

        SecurityUtils.setCurrentUserId(7L);
        when(facade.create(request, 7L)).thenReturn(candidate);

        R<GraphRelationCandidateResponse> response = controller.create(request);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isEqualTo(candidate);
        verify(facade).create(request, 7L);
    }

    @Test
    void createDelegatesWithNullCurrentUser() {
        GraphRelationCandidateApiFacade facade = mock(GraphRelationCandidateApiFacade.class);
        GraphRelationCandidateController controller = new GraphRelationCandidateController(facade);

        GraphRelationCandidateCreateDTO request = new GraphRelationCandidateCreateDTO();
        request.setSourceNodeKey("ABILITY_1");
        request.setTargetNodeKey("ABILITY_2");
        request.setDiscoveryMethod("RAG_EXTRACTION");
        request.setSourceRefs(List.of("ref-1"));

        GraphRelationCandidateResponse candidate = createResponse(2L, "PENDING");
        when(facade.create(request, null)).thenReturn(candidate);

        R<GraphRelationCandidateResponse> response = controller.create(request);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isEqualTo(candidate);
    }

    @Test
    void reviewDelegatesWithCurrentUserId() {
        GraphRelationCandidateApiFacade facade = mock(GraphRelationCandidateApiFacade.class);
        GraphRelationCandidateController controller = new GraphRelationCandidateController(facade);

        GraphRelationCandidateReviewDTO request = new GraphRelationCandidateReviewDTO();
        request.setDecision("APPROVE");
        request.setReviewReason("证据充分");

        GraphRelationCandidateResponse candidate = createResponse(1L, "APPROVED");

        SecurityUtils.setCurrentUserId(9L);
        when(facade.review(1L, request, 9L)).thenReturn(candidate);

        R<GraphRelationCandidateResponse> response = controller.review(1L, request);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isEqualTo(candidate);
        verify(facade).review(1L, request, 9L);
    }

    @Test
    void revokeDelegatesWithCurrentUserId() {
        GraphRelationCandidateApiFacade facade = mock(GraphRelationCandidateApiFacade.class);
        GraphRelationCandidateController controller = new GraphRelationCandidateController(facade);

        GraphRelationCandidateRevokeDTO request = new GraphRelationCandidateRevokeDTO();
        request.setRevokeReason("重复候选");

        GraphRelationCandidateResponse candidate = createResponse(1L, "REVOKED");

        SecurityUtils.setCurrentUserId(9L);
        when(facade.revoke(1L, request, 9L)).thenReturn(candidate);

        R<GraphRelationCandidateResponse> response = controller.revoke(1L, request);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isEqualTo(candidate);
        verify(facade).revoke(1L, request, 9L);
    }

    @Test
    void listReturnsCandidates() {
        GraphRelationCandidateApiFacade facade = mock(GraphRelationCandidateApiFacade.class);
        GraphRelationCandidateController controller = new GraphRelationCandidateController(facade);

        GraphRelationCandidateResponse candidate = createResponse(1L, "PENDING");
        when(facade.list("PENDING")).thenReturn(List.of(candidate));

        R<List<GraphRelationCandidateResponse>> response = controller.list("PENDING");

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).containsExactly(candidate);
    }

    @Test
    void policiesReturnsGovernancePolicies() {
        GraphRelationCandidateApiFacade facade = mock(GraphRelationCandidateApiFacade.class);
        GraphRelationCandidateController controller = new GraphRelationCandidateController(facade);

        Map<String, Object> policies = Map.of(
                "autoApproveThreshold", 0.9D, "allowManualReview", true);
        when(facade.policies()).thenReturn(policies);

        R<Map<String, Object>> response = controller.policies();

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).containsEntry("autoApproveThreshold", 0.9D);
    }

    @Test
    void checkReturnsInspectionResult() {
        GraphRelationCandidateApiFacade facade = mock(GraphRelationCandidateApiFacade.class);
        GraphRelationCandidateController controller = new GraphRelationCandidateController(facade);

        Map<String, Object> checkResult = Map.of(
                "healthy", true, "activeRelations", 12, "issues", List.of());
        when(facade.check()).thenReturn(checkResult);

        R<Map<String, Object>> response = controller.check();

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).containsEntry("healthy", true).containsEntry("activeRelations", 12);
    }
}
