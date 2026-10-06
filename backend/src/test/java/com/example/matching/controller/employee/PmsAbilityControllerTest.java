package com.example.matching.controller.employee;

import com.example.matching.application.employee.PmsAbilityApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.employee.api.PmsAnalysisTaskResponse;
import com.example.matching.dto.employee.api.PmsRosterItemResponse;
import com.example.matching.dto.employee.api.PmsRosterResponse;
import com.example.matching.dto.employee.api.PmsUserMappingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PmsAbilityControllerTest {

    private PmsAbilityApiFacade facade;
    private PmsAbilityController controller;

    @BeforeEach
    void setUp() {
        facade = mock(PmsAbilityApiFacade.class);
        controller = new PmsAbilityController(facade);
    }

    private static PmsUserMappingResponse mappingResponse(Long id) {
        return new PmsUserMappingResponse(
                id, 100L, 500L, "pms_user", "PMS用户", "PMS-001",
                LocalDateTime.of(2025, 1, 1, 10, 0));
    }

    private static PmsAnalysisTaskResponse taskResponse(Long id) {
        return new PmsAnalysisTaskResponse(
                id, 100L, 500L, 1, 6, 10, 2, 5, 3, 8, null,
                LocalDateTime.of(2025, 1, 1, 10, 0),
                LocalDateTime.of(2025, 1, 1, 10, 0));
    }

    @Test
    void autoMapReturnsMappingWhenFound() {
        PmsUserMappingResponse mapping = mappingResponse(1L);
        when(facade.autoMapUser(100L)).thenReturn(mapping);

        R<PmsUserMappingResponse> response = controller.autoMap(100L);

        assertThat(response.getData()).isSameAs(mapping);
        assertThat(response.getMessage()).isEqualTo("映射成功");
    }

    @Test
    void autoMapReturnsFailWhenNoMappingFound() {
        when(facade.autoMapUser(100L)).thenReturn(null);

        R<PmsUserMappingResponse> response = controller.autoMap(100L);

        assertThat(response.getMessage()).isEqualTo("未找到匹配的PMS用户，请手动映射");
        assertThat(response.getData()).isNull();
    }

    @Test
    void manualMapReturnsMapping() {
        PmsUserMappingResponse mapping = mappingResponse(2L);
        when(facade.manualMapUser(100L, 500L)).thenReturn(mapping);

        R<PmsUserMappingResponse> response = controller.manualMap(100L, 500L);

        assertThat(response.getData()).isSameAs(mapping);
        assertThat(response.getMessage()).isEqualTo("映射成功");
    }

    @Test
    void getMappingReturnsMapping() {
        PmsUserMappingResponse mapping = mappingResponse(3L);
        when(facade.getMapping(100L)).thenReturn(mapping);

        R<PmsUserMappingResponse> response = controller.getMapping(100L);

        assertThat(response.getData()).isSameAs(mapping);
    }

    @Test
    void analyzeReturnsTask() {
        PmsAnalysisTaskResponse task = taskResponse(1L);
        when(facade.analyze(100L, 6)).thenReturn(task);

        R<PmsAnalysisTaskResponse> response = controller.analyze(100L, 6);

        assertThat(response.getData()).isSameAs(task);
        assertThat(response.getMessage()).isEqualTo("分析完成");
    }

    @Test
    void getHistoryReturnsTaskList() {
        PmsAnalysisTaskResponse task = taskResponse(1L);
        when(facade.getHistory(100L)).thenReturn(List.of(task));

        R<List<PmsAnalysisTaskResponse>> response = controller.getHistory(100L);

        assertThat(response.getData()).containsExactly(task);
    }

    @Test
    void listPmsUsersReturnsUserList() {
        Map<String, Object> user = Map.of("id", 500L, "username", "pms_user");
        when(facade.listPmsUsers()).thenReturn(List.of(user));

        R<List<Map<String, Object>>> response = controller.listPmsUsers();

        assertThat(response.getData()).containsExactly(user);
    }

    @Test
    void testConnectionReturnsBoolean() {
        when(facade.testConnection()).thenReturn(true);

        R<Boolean> response = controller.testConnection();

        assertThat(response.getData()).isTrue();
    }

    @Test
    void syncPmsUsersReturnsSyncData() {
        Map<String, Object> data = Map.of("newMapped", 2, "totalPmsUsers", 10, "alreadyMapped", 1, "unmatched", 7);
        when(facade.syncPmsUsers()).thenReturn(data);

        R<Map<String, Object>> response = controller.syncPmsUsers();

        assertThat(response.getData()).isEqualTo(data);
        assertThat(response.getMessage()).isEqualTo("同步完成");
    }

    @Test
    void getDetailReturnsDetailMap() {
        Map<String, Object> detail = Map.of("abilities", List.of("Java", "Spring"));
        when(facade.getDetail(9L)).thenReturn(detail);

        R<Map<String, Object>> response = controller.getDetail(9L);

        assertThat(response.getData()).isEqualTo(detail);
    }

    @Test
    void importAbilitiesReturnsImportedCount() {
        when(facade.importAbilities(eq(100L), eq(9L), anyList())).thenReturn(5);

        R<Map<String, Object>> response = controller.importAbilities(100L, 9L, List.of(0, 1));

        assertThat(response.getData().get("importedCount")).isEqualTo(5);
        assertThat(response.getMessage()).isEqualTo("导入成功");
    }

    // ------------------------------------------------------------------
    // PMS 人员花名册（HR 独立功能）
    // ------------------------------------------------------------------

    private static PmsRosterItemResponse rosterItem(long pmsUserId, boolean bound, Long empId) {
        return new PmsRosterItemResponse(
                pmsUserId, "pms_user_" + pmsUserId, "PMS用户" + pmsUserId, "PMS-00" + pmsUserId,
                "pms" + pmsUserId + "@example.com", "1380000000" + pmsUserId, "DEV",
                bound, empId, bound ? "张三" : null, bound ? "EMP001" : null,
                bound ? Boolean.TRUE : null, bound ? 2 : 0, bound ? 2 : null,
                bound ? LocalDateTime.of(2026, 10, 1, 9, 0) : null);
    }

    @Test
    void rosterReturnsRosterPayloadIncludingUnboundPeople() {
        PmsRosterResponse roster = new PmsRosterResponse(
                true, 2, 1, 1, 1, List.of(100L),
                List.of(rosterItem(5L, false, null), rosterItem(6L, true, 100L)),
                null);
        when(facade.roster()).thenReturn(roster);

        R<PmsRosterResponse> response = controller.roster();

        assertThat(response.getData()).isSameAs(roster);
        assertThat(response.getData().unboundCount()).isEqualTo(1);
        assertThat(response.getData().items()).hasSize(2);
    }

    @Test
    void rosterPassesThroughEmptyDiagnosticsMessage() {
        PmsRosterResponse empty = PmsRosterResponse.empty("PMS 数据源未配置或不可用");
        when(facade.roster()).thenReturn(empty);

        R<PmsRosterResponse> response = controller.roster();

        assertThat(response.getData().message()).contains("未配置或不可用");
        assertThat(response.getData().items()).isEmpty();
    }

    @Test
    void bindRosterUserReturnsMapping() {
        PmsUserMappingResponse mapping = mappingResponse(7L);
        when(facade.bindRosterUser(500L, 100L)).thenReturn(mapping);

        R<PmsUserMappingResponse> response = controller.bindRosterUser(500L, 100L);

        assertThat(response.getData()).isSameAs(mapping);
        assertThat(response.getMessage()).isEqualTo("绑定成功");
    }

    @Test
    void unbindRosterUserReturnsSuccess() {
        R<Void> response = controller.unbindRosterUser(500L);

        assertThat(response.getMessage()).isEqualTo("已解绑");
        assertThat(response.getData()).isNull();
        verify(facade).unbindRosterUser(500L);
    }

    @Test
    void analyzeByPmsUserReturnsTask() {
        PmsAnalysisTaskResponse task = taskResponse(2L);
        when(facade.analyzeByPmsUser(500L, 6)).thenReturn(task);

        R<PmsAnalysisTaskResponse> response = controller.analyzeByPmsUser(500L, 6);

        assertThat(response.getData()).isSameAs(task);
        assertThat(response.getMessage()).isEqualTo("分析完成");
    }

    @Test
    void historyByPmsUserReturnsTaskList() {
        PmsAnalysisTaskResponse task = taskResponse(3L);
        when(facade.getHistoryByPmsUser(500L)).thenReturn(List.of(task));

        R<List<PmsAnalysisTaskResponse>> response = controller.historyByPmsUser(500L);

        assertThat(response.getData()).containsExactly(task);
    }
}
