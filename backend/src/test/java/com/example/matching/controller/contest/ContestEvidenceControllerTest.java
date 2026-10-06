package com.example.matching.controller.contest;

import com.example.matching.application.contest.ContestEvidenceApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.contest.EvidenceCreateDTO;
import com.example.matching.dto.contest.EvidenceReviewDTO;
import com.example.matching.dto.contest.api.ContestEvidenceResponse;
import com.example.matching.dto.contest.api.EvidenceQuery;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContestEvidenceControllerTest {

    private static ContestEvidenceResponse evidence(Long id, String code) {
        return new ContestEvidenceResponse(
                id, code, "RESUME_PARSE", 500L, "张三简历", "来源原文",
                "POST", 200L, "Java", 10L, BigDecimal.valueOf(90), BigDecimal.valueOf(85),
                "PENDING", null, List.of(1L), List.of(2L), null, null, 100L, null, null, null
        );
    }

    @Test
    void createReturnsEvidenceResponse() {
        ContestEvidenceApiFacade facade = mock(ContestEvidenceApiFacade.class);
        ContestEvidenceController controller = new ContestEvidenceController(facade);
        EvidenceCreateDTO dto = new EvidenceCreateDTO();
        dto.setSourceType("RESUME_PARSE");
        dto.setAbilityName("Java");
        when(facade.create(any())).thenReturn(evidence(1L, "EV-1001"));

        R<ContestEvidenceResponse> response = controller.create(dto);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().id()).isEqualTo(1L);
        assertThat(response.getData().evidenceCode()).isEqualTo("EV-1001");
    }

    @Test
    void pageReturnsPageOfEvidenceResponses() {
        ContestEvidenceApiFacade facade = mock(ContestEvidenceApiFacade.class);
        ContestEvidenceController controller = new ContestEvidenceController(facade);
        PageResponse<ContestEvidenceResponse> page = new PageResponse<>(
                List.of(evidence(1L, "EV-1001")), 1, 1, 10, 1
        );
        when(facade.page(anyLong(), anyLong(), any(EvidenceQuery.class))).thenReturn(page);

        R<PageResponse<ContestEvidenceResponse>> response = controller.page(1, 10, null, null, null, null);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().records()).hasSize(1);
        assertThat(response.getData().total()).isEqualTo(1);
    }

    @Test
    void detailReturnsEvidenceResponse() {
        ContestEvidenceApiFacade facade = mock(ContestEvidenceApiFacade.class);
        ContestEvidenceController controller = new ContestEvidenceController(facade);
        when(facade.detail(anyLong())).thenReturn(evidence(2L, "EV-1002"));

        R<ContestEvidenceResponse> response = controller.detail(2L);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().id()).isEqualTo(2L);
    }

    @Test
    void reviewReturnsOk() {
        ContestEvidenceApiFacade facade = mock(ContestEvidenceApiFacade.class);
        ContestEvidenceController controller = new ContestEvidenceController(facade);
        EvidenceReviewDTO dto = new EvidenceReviewDTO();
        dto.setEvidenceStatus("VERIFIED");
        dto.setReviewComment("确认无误");

        R<Void> response = controller.review(1L, dto, 0L);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isNull();
    }

    @Test
    void summaryReturnsMap() {
        ContestEvidenceApiFacade facade = mock(ContestEvidenceApiFacade.class);
        ContestEvidenceController controller = new ContestEvidenceController(facade);
        when(facade.summary()).thenReturn(Map.of("total", 10, "verified", 6));

        R<Map<String, Object>> response = controller.summary();

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).containsEntry("total", 10);
    }

    @Test
    void employeeChainReturnsMap() {
        ContestEvidenceApiFacade facade = mock(ContestEvidenceApiFacade.class);
        ContestEvidenceController controller = new ContestEvidenceController(facade);
        when(facade.employeeChain(anyLong())).thenReturn(Map.of("empId", 100L, "abilityCount", 5));

        R<Map<String, Object>> response = controller.employeeChain(100L);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).containsEntry("abilityCount", 5);
    }

    @Test
    void postChainReturnsMap() {
        ContestEvidenceApiFacade facade = mock(ContestEvidenceApiFacade.class);
        ContestEvidenceController controller = new ContestEvidenceController(facade);
        when(facade.postChain(anyLong())).thenReturn(Map.of("postId", 200L, "requiredCount", 8));

        R<Map<String, Object>> response = controller.postChain(200L);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).containsEntry("requiredCount", 8);
    }

    @Test
    void backfillReturnsCreatedCount() {
        ContestEvidenceApiFacade facade = mock(ContestEvidenceApiFacade.class);
        ContestEvidenceController controller = new ContestEvidenceController(facade);
        when(facade.backfill(anyString(), anyInt())).thenReturn(Map.of("sourceType", "JD_IMPORT", "created", 12));

        R<Map<String, Object>> response = controller.backfill("JD_IMPORT", 100);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).containsEntry("created", 12);
    }
}
