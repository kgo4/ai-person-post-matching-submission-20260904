package com.example.matching.controller.employee;

import com.example.matching.application.employee.EmpAbilityApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.employee.EmpAbilitySaveDTO;
import com.example.matching.dto.employee.api.EmployeeAbilityCreateRequest;
import com.example.matching.dto.employee.api.EmployeeAbilityResponse;
import com.example.matching.dto.employee.api.EmployeeAbilityUpdateRequest;
import com.example.matching.dto.employee.api.PendingAbilityClaimResponse;
import com.example.matching.vo.employee.EmpAbilityProfileVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmpAbilityControllerTest {

    private EmpAbilityApiFacade facade;
    private EmpAbilityController controller;

    @BeforeEach
    void setUp() {
        facade = mock(EmpAbilityApiFacade.class);
        controller = new EmpAbilityController(facade);
    }

    private static EmployeeAbilityResponse abilityResponse(Long id) {
        return new EmployeeAbilityResponse(
                id, 100L, 101L, "Java", "Java", null, null, 4, 4, "MANUAL",
                new BigDecimal("0.80"), LocalDate.of(2025, 6, 1), "remark",
                LocalDateTime.of(2025, 6, 1, 10, 0),
                LocalDateTime.of(2025, 6, 1, 10, 0));
    }

    private static PendingAbilityClaimResponse pendingClaimResponse(Long id) {
        return new PendingAbilityClaimResponse(
                id, 100L, 101L, "Java", 4, "AI_TEST", 3L,
                "evidence", new BigDecimal("0.9"), "ACCEPTED", 9L, "PENDING",
                LocalDateTime.of(2025, 6, 1, 10, 0));
    }

    @Test
    void profileReturnsAbilityProfile() {
        EmpAbilityProfileVO profile = new EmpAbilityProfileVO();
        profile.setEmpId(100L);
        profile.setRealName("张三");
        when(facade.getProfile(100L)).thenReturn(profile);

        R<EmpAbilityProfileVO> response = controller.profile(100L);

        assertThat(response.getData()).isSameAs(profile);
        assertThat(response.getData().getRealName()).isEqualTo("张三");
    }

    @Test
    void listByEmpIdReturnsAbilityList() {
        EmployeeAbilityResponse ability = abilityResponse(1L);
        when(facade.listByEmpId(100L)).thenReturn(List.of(ability));

        R<List<EmployeeAbilityResponse>> response = controller.listByEmpId(100L);

        assertThat(response.getData()).containsExactly(ability);
    }

    @Test
    void listPendingClaimsReturnsClaimList() {
        PendingAbilityClaimResponse claim = pendingClaimResponse(1L);
        when(facade.listPendingClaims(100L)).thenReturn(List.of(claim));

        R<List<PendingAbilityClaimResponse>> response = controller.listPendingClaims(100L);

        assertThat(response.getData()).containsExactly(claim);
    }

    @Test
    void saveDelegatesAndReturnsOk() {
        EmployeeAbilityCreateRequest request = new EmployeeAbilityCreateRequest(
                100L, "Java", 101L, 4, "MANUAL", new BigDecimal("0.80"),
                LocalDate.of(2025, 6, 1), "remark");

        R<Void> response = controller.save(request);

        verify(facade).save(same(request));
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    void updateDelegatesAndReturnsOk() {
        EmployeeAbilityUpdateRequest request = new EmployeeAbilityUpdateRequest(
                "Java", 101L, 4, "MANUAL", new BigDecimal("0.80"),
                LocalDate.of(2025, 6, 1), "remark");

        R<Void> response = controller.update(7L, request);

        verify(facade).update(eq(7L), same(request));
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    void batchSaveDelegatesAndReturnsOk() {
        EmpAbilitySaveDTO dto = new EmpAbilitySaveDTO();
        dto.setEmpId(100L);
        dto.setTagId(101L);
        dto.setMasteryLevel(4);
        dto.setEvaluationSource("MANUAL");

        List<EmpAbilitySaveDTO> list = List.of(dto);

        R<Void> response = controller.batchSave(list);

        verify(facade).batchSave(same(list));
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    void deleteDelegatesAndReturnsOk() {
        R<Void> response = controller.delete(9L);

        verify(facade).delete(9L);
        assertThat(response.getCode()).isEqualTo(200);
    }
}
