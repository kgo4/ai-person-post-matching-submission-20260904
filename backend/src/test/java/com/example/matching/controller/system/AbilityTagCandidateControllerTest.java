package com.example.matching.controller.system;

import com.example.matching.application.system.AbilityTagCandidateApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.system.api.AbilityTagCandidateResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AbilityTagCandidateControllerTest {

    private static AbilityTagCandidateResponse createCandidate(Long id, String name) {
        return new AbilityTagCandidateResponse(
                id, name, "TECHNICAL", "java", "描述", "理由", "证据",
                "MATCH_RESULT", 1L, 2L, 3L, 5, 3, 2,
                null, null, null, "PENDING", null, null, null, null, null, null);
    }

    @Test
    void pageCandidatesReturnsPage() {
        AbilityTagCandidateApiFacade facade = mock(AbilityTagCandidateApiFacade.class);
        AbilityTagCandidateController controller = new AbilityTagCandidateController(facade);

        AbilityTagCandidateResponse candidate = createCandidate(1L, "Java并发");
        PageResponse<AbilityTagCandidateResponse> page = new PageResponse<>(List.of(candidate), 1, 1, 10, 1);
        when(facade.page(1, 10, "PENDING", "MATCH_RESULT", "java")).thenReturn(page);

        R<PageResponse<AbilityTagCandidateResponse>> response =
                controller.pageCandidates(1, 10, "PENDING", "MATCH_RESULT", "java");

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().records()).containsExactly(candidate);
        assertThat(response.getData().total()).isEqualTo(1);
    }

    @Test
    void pageCandidatesWithDefaultFilters() {
        AbilityTagCandidateApiFacade facade = mock(AbilityTagCandidateApiFacade.class);
        AbilityTagCandidateController controller = new AbilityTagCandidateController(facade);

        PageResponse<AbilityTagCandidateResponse> page = new PageResponse<>(List.of(), 0, 1, 10, 0);
        when(facade.page(1, 10, null, null, null)).thenReturn(page);

        R<PageResponse<AbilityTagCandidateResponse>> response =
                controller.pageCandidates(1, 10, null, null, null);

        assertThat(response.getData().records()).isEmpty();
    }

    @Test
    void getHighFrequencyReturnsCandidates() {
        AbilityTagCandidateApiFacade facade = mock(AbilityTagCandidateApiFacade.class);
        AbilityTagCandidateController controller = new AbilityTagCandidateController(facade);

        AbilityTagCandidateResponse candidate = createCandidate(2L, "向量检索");
        when(facade.getHighFrequency(3)).thenReturn(List.of(candidate));

        R<List<AbilityTagCandidateResponse>> response = controller.getHighFrequency(3);

        assertThat(response.getData()).containsExactly(candidate);
    }

    @Test
    void countByStatusReturnsCounts() {
        AbilityTagCandidateApiFacade facade = mock(AbilityTagCandidateApiFacade.class);
        AbilityTagCandidateController controller = new AbilityTagCandidateController(facade);

        when(facade.countByStatus()).thenReturn(Map.of("PENDING", 3L, "APPROVED", 2L));

        R<Map<String, Long>> response = controller.countByStatus();

        assertThat(response.getData())
                .containsEntry("PENDING", 3L)
                .containsEntry("APPROVED", 2L);
    }

    @Test
    void approveReturnsNewTagId() {
        AbilityTagCandidateApiFacade facade = mock(AbilityTagCandidateApiFacade.class);
        AbilityTagCandidateController controller = new AbilityTagCandidateController(facade);

        when(facade.approve(1L, 0L, "同意")).thenReturn(100L);

        R<Long> response = controller.approve(1L, "同意");

        assertThat(response.getData()).isEqualTo(100L);
        verify(facade).approve(1L, 0L, "同意");
    }

    @Test
    void rejectReturnsOk() {
        AbilityTagCandidateApiFacade facade = mock(AbilityTagCandidateApiFacade.class);
        AbilityTagCandidateController controller = new AbilityTagCandidateController(facade);

        R<Void> response = controller.reject(1L, "信息不足");

        assertThat(response.getCode()).isEqualTo(200);
        verify(facade).reject(1L, "信息不足");
    }

    @Test
    void mergeReturnsOk() {
        AbilityTagCandidateApiFacade facade = mock(AbilityTagCandidateApiFacade.class);
        AbilityTagCandidateController controller = new AbilityTagCandidateController(facade);

        R<Void> response = controller.merge(1L, 2L, "合并");

        assertThat(response.getCode()).isEqualTo(200);
        verify(facade).merge(1L, 2L, "合并");
    }

    @Test
    void deleteReturnsOk() {
        AbilityTagCandidateApiFacade facade = mock(AbilityTagCandidateApiFacade.class);
        AbilityTagCandidateController controller = new AbilityTagCandidateController(facade);

        R<Void> response = controller.delete(1L);

        assertThat(response.getCode()).isEqualTo(200);
        verify(facade).delete(1L);
    }
}
