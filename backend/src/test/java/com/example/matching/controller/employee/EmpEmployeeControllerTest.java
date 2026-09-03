package com.example.matching.controller.employee;

import com.example.matching.application.common.FileContent;
import com.example.matching.application.employee.EmpEmployeeApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.employee.api.EmployeeCreateRequest;
import com.example.matching.dto.employee.api.EmployeeResponse;
import com.example.matching.dto.employee.api.EmployeeUpdateRequest;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmpEmployeeControllerTest {

    private EmpEmployeeApiFacade facade;
    private EmpEmployeeController controller;

    @BeforeEach
    void setUp() {
        facade = mock(EmpEmployeeApiFacade.class);
        controller = new EmpEmployeeController(facade);
    }

    private static EmployeeResponse employeeResponse(Long id) {
        return new EmployeeResponse(
                id, "EMP001", "张三", 1, "13800000000", "zhang@example.com",
                10L, 20L, LocalDate.of(2024, 1, 1), "P4", "{}", 0, 1,
                LocalDateTime.of(2025, 1, 1, 10, 0),
                LocalDateTime.of(2025, 1, 1, 10, 0));
    }

    @Test
    void pageReturnsPageOfEmployees() {
        EmployeeResponse employee = employeeResponse(1L);
        PageResponse<EmployeeResponse> page = new PageResponse<>(List.of(employee), 1, 1, 10, 1);
        when(facade.page(1, 10, "张", 1)).thenReturn(page);

        R<PageResponse<EmployeeResponse>> response = controller.page(1, 10, "张", 1);

        assertThat(response.getData().records()).containsExactly(employee);
        assertThat(response.getData().total()).isEqualTo(1);
    }

    @Test
    void getByIdReturnsEmployee() {
        EmployeeResponse employee = employeeResponse(1L);
        when(facade.getById(1L)).thenReturn(employee);

        R<EmployeeResponse> response = controller.getById(1L);

        assertThat(response.getData()).isSameAs(employee);
    }

    @Test
    void saveDelegatesAndReturnsOk() {
        EmployeeCreateRequest request = new EmployeeCreateRequest(
                null, "张三", 1, "id-card", "13800000000", "zhang@example.com", "{}");

        R<Void> response = controller.save(request);

        verify(facade).save(request);
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    void updateDelegatesAndReturnsOk() {
        EmployeeUpdateRequest request = new EmployeeUpdateRequest(
                "EMP001", "张三", 1, "id-card", "13800000000", "zhang@example.com", "{}");

        R<Void> response = controller.update(1L, request);

        verify(facade).update(1L, request);
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    void batchImportReturnsCount() {
        EmployeeCreateRequest request = new EmployeeCreateRequest(
                null, "张三", 1, null, null, null, null);
        when(facade.batchImport(anyList())).thenReturn(2);

        R<Integer> response = controller.batchImport(List.of(request));

        assertThat(response.getData()).isEqualTo(2);
        assertThat(response.getMessage()).isEqualTo("成功导入2条记录");
    }

    @Test
    void importExcelReturnsCount() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("employees.xlsx");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        when(facade.importExcel(eq("employees.xlsx"), any(InputStream.class))).thenReturn(3);

        R<Integer> response = controller.importExcel(file);

        assertThat(response.getData()).isEqualTo(3);
        assertThat(response.getMessage()).isEqualTo("成功导入3条记录");
    }

    @Test
    void exportExcelWritesFileContentToResponse() throws Exception {
        FileContent content = new FileContent("employees.xlsx", new byte[]{1, 2, 3});
        when(facade.exportExcel()).thenReturn(content);
        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream outputStream = mock(ServletOutputStream.class);
        when(response.getOutputStream()).thenReturn(outputStream);

        controller.exportExcel(response);

        verify(response).setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        verify(response).setHeader(eq("Content-Disposition"), anyString());
        verify(outputStream).write(eq(new byte[]{1, 2, 3}));
    }

    @Test
    void downloadTemplateWritesFileContentToResponse() throws Exception {
        FileContent content = new FileContent("employee-import-template.xlsx", new byte[]{9, 8, 7});
        when(facade.downloadTemplate()).thenReturn(content);
        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream outputStream = mock(ServletOutputStream.class);
        when(response.getOutputStream()).thenReturn(outputStream);

        controller.downloadTemplate(response);

        verify(response).setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        verify(outputStream).write(eq(new byte[]{9, 8, 7}));
    }

    @Test
    void lockDelegatesAndReturnsOk() {
        R<Void> response = controller.lock(1L);

        verify(facade).lock(1L);
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    void unlockDelegatesAndReturnsOk() {
        R<Void> response = controller.unlock(1L);

        verify(facade).unlock(1L);
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    void deleteDelegatesAndReturnsOk() {
        R<Void> response = controller.delete(1L);

        verify(facade).delete(1L);
        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    void statsReturnsCounts() {
        Map<String, Long> stats = Map.of("total", 10L, "enabled", 8L, "locked", 1L);
        when(facade.stats()).thenReturn(stats);

        R<Map<String, Long>> response = controller.stats();

        assertThat(response.getData()).isEqualTo(stats);
    }
}
