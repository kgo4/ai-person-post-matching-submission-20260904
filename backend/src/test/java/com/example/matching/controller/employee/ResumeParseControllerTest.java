package com.example.matching.controller.employee;

import com.example.matching.application.common.FileContent;
import com.example.matching.application.employee.ResumeParseApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.employee.api.ResumeParseResponse;
import com.example.matching.dto.system.AbilityImportResultDTO;
import com.example.matching.utils.SecurityUtils;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResumeParseControllerTest {

    private ResumeParseApiFacade facade;
    private ResumeParseController controller;

    @BeforeEach
    void setUp() {
        SecurityUtils.clear();
        facade = mock(ResumeParseApiFacade.class);
        controller = new ResumeParseController(facade);
    }

    private static ResumeParseResponse parseResponse(Long id) {
        return new ResumeParseResponse(
                id, 100L, "cv.pdf", "pdf", "parsed content", "ai analysis",
                2, null, 0,
                LocalDateTime.of(2025, 1, 1, 10, 0),
                LocalDateTime.of(2025, 1, 1, 10, 0));
    }

    @Test
    void uploadAndParseReturnsResult() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("cv.pdf");
        when(file.getBytes()).thenReturn(new byte[]{1, 2, 3});
        ResumeParseResponse parsed = parseResponse(1L);
        when(facade.uploadAndParse(eq(100L), eq("cv.pdf"), any(), any())).thenReturn(parsed);

        R<ResumeParseResponse> response = controller.uploadAndParse(100L, file);

        assertThat(response.getData()).isSameAs(parsed);
        assertThat(response.getMessage()).isEqualTo("简历上传成功，正在解析");
    }

    @Test
    void uploadAndParseThrowsIllegalArgumentWhenFileCannotBeRead() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getBytes()).thenThrow(new IOException("boom"));

        assertThatThrownBy(() -> controller.uploadAndParse(100L, file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unable to read uploaded resume");
    }

    @Test
    void listByEmpIdReturnsParseList() {
        ResumeParseResponse parsed = parseResponse(1L);
        when(facade.listByEmpId(100L)).thenReturn(List.of(parsed));

        R<List<ResumeParseResponse>> response = controller.listByEmpId(100L);

        assertThat(response.getData()).containsExactly(parsed);
    }

    @Test
    void getByIdReturnsDetail() {
        ResumeParseResponse parsed = parseResponse(1L);
        when(facade.getById(1L)).thenReturn(parsed);

        R<ResumeParseResponse> response = controller.getById(1L);

        assertThat(response.getData()).isSameAs(parsed);
    }

    @Test
    void importToAbilityProfileReturnsImportResult() {
        AbilityImportResultDTO result = AbilityImportResultDTO.builder()
                .total(3).imported(2).candidate(1).rejected(0).message("成功导入2条")
                .build();
        when(facade.importToAbilityProfile(1L)).thenReturn(result);

        R<AbilityImportResultDTO> response = controller.importToAbilityProfile(1L);

        assertThat(response.getData()).isSameAs(result);
        assertThat(response.getData().getImported()).isEqualTo(2);
        assertThat(response.getMessage()).isEqualTo("成功导入2条");
    }

    @Test
    void viewFileWritesFileToResponse() throws Exception {
        FileContent content = new FileContent("cv.pdf", new byte[]{1, 2, 3});
        when(facade.viewFile(1L)).thenReturn(content);
        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream outputStream = mock(ServletOutputStream.class);
        when(response.getOutputStream()).thenReturn(outputStream);

        controller.viewFile(1L, response);

        verify(response).setContentType("application/octet-stream");
        verify(response).setHeader(eq("Content-Disposition"), anyString());
        verify(outputStream).write(eq(new byte[]{1, 2, 3}));
    }

    @Test
    void reparseReturnsResult() {
        ResumeParseResponse parsed = parseResponse(1L);
        when(facade.reparse(1L)).thenReturn(parsed);

        R<ResumeParseResponse> response = controller.reparse(1L);

        assertThat(response.getData()).isSameAs(parsed);
        assertThat(response.getMessage()).isEqualTo("重新解析任务已提交");
    }

    @Test
    void retryFailedTaskReturnsResult() {
        ResumeParseResponse parsed = parseResponse(1L);
        when(facade.retryFailedTask(1L)).thenReturn(parsed);

        R<ResumeParseResponse> response = controller.retryFailedTask(1L);

        assertThat(response.getData()).isSameAs(parsed);
        assertThat(response.getMessage()).isEqualTo("任务已重新投递");
    }
}
