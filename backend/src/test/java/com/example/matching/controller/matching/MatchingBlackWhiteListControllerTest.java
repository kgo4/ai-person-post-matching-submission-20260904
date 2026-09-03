package com.example.matching.controller.matching;

import com.example.matching.application.matching.BlackWhiteListApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.matching.api.BlackWhiteListEntryRequest;
import com.example.matching.dto.matching.api.BlackWhiteListEntryResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchingBlackWhiteListControllerTest {

    @Test
    void pageReturnsFacadePage() {
        BlackWhiteListApiFacade facade = mock(BlackWhiteListApiFacade.class);
        MatchingBlackWhiteListController controller = new MatchingBlackWhiteListController(facade);

        BlackWhiteListEntryResponse entry = new BlackWhiteListEntryResponse(
                1L, 10001L, 20001L, 1, "强制匹配", 1, 9001L, LocalDateTime.of(2024, 1, 10, 9, 0));
        PageResponse<BlackWhiteListEntryResponse> page =
                new PageResponse<>(List.of(entry), 1, 1, 10, 1);
        when(facade.page(1, 10, 10001L, 20001L)).thenReturn(page);

        R<PageResponse<BlackWhiteListEntryResponse>> response =
                controller.page(1, 10, 10001L, 20001L);

        assertThat(response.getData()).isEqualTo(page);
        assertThat(response.getData().records()).containsExactly(entry);
    }

    @Test
    void saveCallsFacadeCreateAndReturnsOk() {
        BlackWhiteListApiFacade facade = mock(BlackWhiteListApiFacade.class);
        MatchingBlackWhiteListController controller = new MatchingBlackWhiteListController(facade);

        BlackWhiteListEntryRequest request = new BlackWhiteListEntryRequest(10001L, 20001L, 1, "强制匹配");

        R<Void> response = controller.save(request);

        verify(facade).create(request);
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    void updateCallsFacadeUpdateAndReturnsOk() {
        BlackWhiteListApiFacade facade = mock(BlackWhiteListApiFacade.class);
        MatchingBlackWhiteListController controller = new MatchingBlackWhiteListController(facade);

        BlackWhiteListEntryRequest request = new BlackWhiteListEntryRequest(10001L, 20001L, 2, "禁止匹配");

        R<Void> response = controller.update(1L, request);

        verify(facade).update(1L, request);
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    void deleteCallsFacadeDeleteAndReturnsOk() {
        BlackWhiteListApiFacade facade = mock(BlackWhiteListApiFacade.class);
        MatchingBlackWhiteListController controller = new MatchingBlackWhiteListController(facade);

        R<Void> response = controller.delete(1L);

        verify(facade).delete(1L);
        assertThat(response.getCode()).isEqualTo(200);
    }
}
