package com.example.matching.service.employee.impl;

import com.example.matching.agent.dto.person.PersonAbilityExtractRequest;
import com.example.matching.agent.dto.person.PersonAbilityExtractionResult;
import com.example.matching.agent.service.EmployeeAbilityAgentService;
import com.example.matching.mapper.employee.EmpResumeParseMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResumeFileParserTest {

    @Test
    void resumeAbilityRequestIncludesItsControlledSourceReference() {
        EmployeeAbilityAgentService agentService = mock(EmployeeAbilityAgentService.class);
        when(agentService.extractAbilities(any())).thenReturn(new PersonAbilityExtractionResult());
        ResumeFileParser parser = new ResumeFileParser(
                mock(EmpResumeParseMapper.class), agentService, new ObjectMapper(),
                mock(ApplicationEventPublisher.class));

        ReflectionTestUtils.invokeMethod(parser, "analyzeWithAi", "Java backend experience", 17L, 33L);

        ArgumentCaptor<PersonAbilityExtractRequest> request = ArgumentCaptor.forClass(PersonAbilityExtractRequest.class);
        verify(agentService).extractAbilities(request.capture());
        assertThat(request.getValue().getSourceText()).isEqualTo("Java backend experience");
        assertThat(request.getValue().getSourceRefId()).isEqualTo(33L);
        assertThat(request.getValue().getSourceRefs()).containsExactly("source:RESUME_PARSE:33");
        assertThat(request.getValue().isOcrDerived()).isFalse();
    }

    @Test
    void ocrResumeAbilityRequestCarriesOnlyTheInternalOcrMarker() {
        EmployeeAbilityAgentService agentService = mock(EmployeeAbilityAgentService.class);
        when(agentService.extractAbilities(any())).thenReturn(new PersonAbilityExtractionResult());
        ResumeFileParser parser = new ResumeFileParser(
                mock(EmpResumeParseMapper.class), agentService, new ObjectMapper(),
                mock(ApplicationEventPublisher.class));

        ReflectionTestUtils.invokeMethod(parser, "analyzeWithAi", "OCR resume text", 17L, 33L, true);

        ArgumentCaptor<PersonAbilityExtractRequest> request = ArgumentCaptor.forClass(PersonAbilityExtractRequest.class);
        verify(agentService).extractAbilities(request.capture());
        assertThat(request.getValue().isOcrDerived()).isTrue();
        assertThat(request.getValue().getSourceType()).isEqualTo("RESUME_PARSE");
    }

    @Test
    void docxTableContentIsExtractedInStableOrder(@TempDir Path tempDir) throws Exception {
        Path docxFile = tempDir.resolve("table-resume.docx");

        try (XWPFDocument doc = new XWPFDocument()) {
            // Normal paragraph before the table
            XWPFParagraph para = doc.createParagraph();
            para.createRun().setText("This is a resume summary paragraph.");

            // Table with Kubernetes and Redis only inside table cells
            XWPFTable table = doc.createTable(2, 2);
            XWPFTableRow row1 = table.getRow(0);
            row1.getCell(0).setText("Skill");
            row1.getCell(1).setText("Level");
            XWPFTableRow row2 = table.getRow(1);
            row2.getCell(0).setText("Kubernetes");
            row2.getCell(1).setText("Redis");

            try (FileOutputStream fos = new FileOutputStream(docxFile.toFile())) {
                doc.write(fos);
            }
        }

        ResumeFileParser parser = new ResumeFileParser(
                mock(EmpResumeParseMapper.class), mock(EmployeeAbilityAgentService.class),
                new ObjectMapper(), mock(ApplicationEventPublisher.class));

        String parsed = (String) ReflectionTestUtils.invokeMethod(parser, "parseDocx", docxFile.toString());

        assertThat(parsed).contains("Kubernetes");
        assertThat(parsed).contains("Redis");
        // 表格内容顺序稳定：段落内容在前，表格内容在后
        int paraIdx = parsed.indexOf("resume summary paragraph");
        int k8sIdx = parsed.indexOf("Kubernetes");
        int redisIdx = parsed.indexOf("Redis");
        assertThat(paraIdx).isLessThan(k8sIdx);
        assertThat(k8sIdx).isLessThan(redisIdx);
    }
}
