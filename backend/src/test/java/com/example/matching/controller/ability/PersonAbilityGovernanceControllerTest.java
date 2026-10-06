package com.example.matching.controller.ability;

import com.example.matching.application.ability.PersonAbilityGovernanceApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.ability.api.AgentMemoryResponse;
import com.example.matching.dto.ability.api.ChangeLevelRequest;
import com.example.matching.dto.ability.api.GovernanceEventResponse;
import com.example.matching.dto.ability.api.RenameTagRequest;
import com.example.matching.dto.ability.api.ReplaceTagRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PersonAbilityGovernanceControllerTest {

    private PersonAbilityGovernanceApiFacade facade;
    private PersonAbilityGovernanceController controller;

    @BeforeEach
    void setUp() {
        facade = mock(PersonAbilityGovernanceApiFacade.class);
        controller = new PersonAbilityGovernanceController(facade);
    }

    private static GovernanceEventResponse eventResponse(Long id) {
        return new GovernanceEventResponse(
                id, 100L, 10L, "旧标签", 11L, "新标签",
                3, 4, new BigDecimal("0.6"), new BigDecimal("0.9"),
                "{}", "{}", "REPLACE", "修改原因", "{}", 1, 5L, 7L,
                LocalDateTime.of(2025, 1, 1, 10, 0));
    }

    private static AgentMemoryResponse memoryResponse(Long id) {
        return new AgentMemoryResponse(
                id, "ABILITY", "记忆标题", "记忆内容", null, null, "GUIDANCE", "rule-key",
                "ALL", 5, "ACTIVE", 1L, 3,
                LocalDateTime.of(2025, 1, 1, 10, 0), null, 7L,
                LocalDateTime.of(2025, 1, 1, 10, 0),
                LocalDateTime.of(2025, 1, 1, 10, 0));
    }

    @Test
    void replaceTagReturnsEvent() {
        ReplaceTagRequest request = new ReplaceTagRequest(100L, 10L, 11L, "人工校正", true);
        GovernanceEventResponse event = eventResponse(1L);
        when(facade.replaceTag(request)).thenReturn(event);

        R<GovernanceEventResponse> response = controller.replaceTag(request);

        assertThat(response.getData()).isSameAs(event);
        assertThat(response.getMessage()).isEqualTo("标签替换成功");
    }

    @Test
    void changeLevelReturnsEvent() {
        ChangeLevelRequest request = new ChangeLevelRequest(100L, 10L, 4, "绩效提升", true, 5);
        GovernanceEventResponse event = eventResponse(2L);
        when(facade.changeLevel(request)).thenReturn(event);

        R<GovernanceEventResponse> response = controller.changeLevel(request);

        assertThat(response.getData()).isSameAs(event);
        assertThat(response.getMessage()).isEqualTo("等级修改成功");
    }

    @Test
    void removeTagReturnsEvent() {
        GovernanceEventResponse event = eventResponse(3L);
        when(facade.removeTag(100L, 10L, "岗位调整", true)).thenReturn(event);

        R<GovernanceEventResponse> response = controller.removeTag(100L, 10L, "岗位调整", true);

        assertThat(response.getData()).isSameAs(event);
        assertThat(response.getMessage()).isEqualTo("标签删除成功");
    }

    @Test
    void renameTagReturnsAffectedEventList() {
        RenameTagRequest request = new RenameTagRequest(10L, "Java精通", "规范命名");
        GovernanceEventResponse first = eventResponse(1L);
        GovernanceEventResponse second = eventResponse(2L);
        when(facade.renameTag(request)).thenReturn(List.of(first, second));

        R<List<GovernanceEventResponse>> response = controller.renameTag(request);

        assertThat(response.getData()).containsExactly(first, second);
        assertThat(response.getMessage()).isEqualTo("标签重命名成功，影响2条能力记录");
    }

    @Test
    void getGovernanceHistoryReturnsEventList() {
        GovernanceEventResponse event = eventResponse(1L);
        when(facade.getGovernanceHistory(100L)).thenReturn(List.of(event));

        R<List<GovernanceEventResponse>> response = controller.getGovernanceHistory(100L);

        assertThat(response.getData()).containsExactly(event);
    }

    @Test
    void getGovernanceByTagReturnsEventList() {
        GovernanceEventResponse event = eventResponse(1L);
        when(facade.getGovernanceByTag(10L)).thenReturn(List.of(event));

        R<List<GovernanceEventResponse>> response = controller.getGovernanceByTag(10L);

        assertThat(response.getData()).containsExactly(event);
    }

    @Test
    void getMemoriesReturnsMemoryList() {
        AgentMemoryResponse memory = memoryResponse(1L);
        when(facade.getMemories("ALL")).thenReturn(List.of(memory));

        R<List<AgentMemoryResponse>> response = controller.getMemories("ALL");

        assertThat(response.getData()).containsExactly(memory);
    }

    @Test
    void searchMemoriesReturnsMemoryList() {
        AgentMemoryResponse memory = memoryResponse(1L);
        when(facade.searchMemories("java", "ALL")).thenReturn(List.of(memory));

        R<List<AgentMemoryResponse>> response = controller.searchMemories("java", "ALL");

        assertThat(response.getData()).containsExactly(memory);
    }
}
