package com.example.matching.controller.ability;

import com.example.matching.application.ability.AgentMemoryGovernanceApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.ability.api.AgentMemoryResponse;
import com.example.matching.dto.ability.api.AgentMemoryUpdateRequest;
import com.example.matching.dto.ability.api.GovernanceEventQuery;
import com.example.matching.dto.ability.api.GovernanceEventResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentMemoryGovernanceControllerTest {

    private AgentMemoryGovernanceApiFacade facade;
    private AgentMemoryGovernanceController controller;

    @BeforeEach
    void setUp() {
        facade = mock(AgentMemoryGovernanceApiFacade.class);
        controller = new AgentMemoryGovernanceController(facade);
    }

    private static AgentMemoryResponse memoryResponse(Long id) {
        return new AgentMemoryResponse(
                id, "ABILITY", "记忆标题", "记忆内容", null, null, "GUIDANCE", "rule-key",
                "ALL", 5, "ACTIVE", 1L, 3,
                LocalDateTime.of(2025, 1, 1, 10, 0), null, 7L,
                LocalDateTime.of(2025, 1, 1, 10, 0),
                LocalDateTime.of(2025, 1, 1, 10, 0));
    }

    private static GovernanceEventResponse eventResponse(Long id) {
        return new GovernanceEventResponse(
                id, 100L, 10L, "旧标签", 11L, "新标签",
                3, 4, new BigDecimal("0.6"), new BigDecimal("0.9"),
                "{}", "{}", "REPLACE", "修改原因", "{}", 1, 5L, 7L,
                LocalDateTime.of(2025, 1, 1, 10, 0));
    }

    @Test
    void pageReturnsPageOfMemories() {
        AgentMemoryResponse memory = memoryResponse(1L);
        PageResponse<AgentMemoryResponse> page = new PageResponse<>(List.of(memory), 1, 1, 10, 1);
        when(facade.pageMemories(any(), any(), any(), any(), any(), any())).thenReturn(page);

        R<PageResponse<AgentMemoryResponse>> response = controller.page(1, 10, "ACTIVE", "ABILITY", "ALL", "记忆");

        assertThat(response.getData().records()).containsExactly(memory);
        assertThat(response.getData().total()).isEqualTo(1);
    }

    @Test
    void getByIdReturnsMemory() {
        AgentMemoryResponse memory = memoryResponse(1L);
        when(facade.getById(1L)).thenReturn(memory);

        R<AgentMemoryResponse> response = controller.getById(1L);

        assertThat(response.getData()).isSameAs(memory);
    }

    @Test
    void updateDelegatesAndReturnsOk() {
        AgentMemoryUpdateRequest request = new AgentMemoryUpdateRequest("新标题", "新内容", 4, "ALL");

        R<Void> response = controller.update(1L, request);

        verify(facade).update(1L, request);
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    void enableDelegatesAndReturnsOk() {
        R<Void> response = controller.enable(1L);

        verify(facade).enable(1L);
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    void disableDelegatesAndReturnsOk() {
        R<Void> response = controller.disable(1L);

        verify(facade).disable(1L);
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    void expireDelegatesAndReturnsOk() {
        R<Void> response = controller.expire(1L);

        verify(facade).expire(1L);
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    void getSourceEventReturnsEvent() {
        GovernanceEventResponse event = eventResponse(1L);
        when(facade.getSourceEvent(1L)).thenReturn(event);

        R<GovernanceEventResponse> response = controller.getSourceEvent(1L);

        assertThat(response.getData()).isSameAs(event);
    }

    @Test
    void pageEventsReturnsPageOfEvents() {
        GovernanceEventResponse event = eventResponse(1L);
        PageResponse<GovernanceEventResponse> page = new PageResponse<>(List.of(event), 1, 1, 10, 1);
        when(facade.pageEvents(any(GovernanceEventQuery.class))).thenReturn(page);

        R<PageResponse<GovernanceEventResponse>> response = controller.pageEvents(1, 10, "REPLACE", 100L, 10L);

        assertThat(response.getData().records()).containsExactly(event);
        assertThat(response.getData().total()).isEqualTo(1);
    }

    @Test
    void getEventByIdReturnsEvent() {
        GovernanceEventResponse event = eventResponse(2L);
        when(facade.getEventById(2L)).thenReturn(event);

        R<GovernanceEventResponse> response = controller.getEventById(2L);

        assertThat(response.getData()).isSameAs(event);
    }
}
