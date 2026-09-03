package com.example.matching.service.matching;

import com.example.matching.entity.matching.MatchingFeedbackDataset;
import com.example.matching.entity.matching.MatchingFeedbackDimension;
import com.example.matching.mapper.matching.MatchingFeedbackDatasetMapper;
import com.example.matching.mapper.matching.MatchingFeedbackDimensionMapper;
import com.example.matching.port.post.PostQueryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CalibrationDataServiceTest {

    private MatchingFeedbackDatasetMapper datasetMapper;
    private MatchingFeedbackDimensionMapper dimensionMapper;
    private CalibrationDataService service;

    @BeforeEach
    void setUp() {
        datasetMapper = mock(MatchingFeedbackDatasetMapper.class);
        dimensionMapper = mock(MatchingFeedbackDimensionMapper.class);
        service = new CalibrationDataService(datasetMapper, dimensionMapper, mock(PostQueryPort.class));
    }

    private MatchingFeedbackDataset sample(Long id, BigDecimal ai, BigDecimal manual, Integer exportEnabled) {
        MatchingFeedbackDataset f = new MatchingFeedbackDataset();
        f.setId(id);
        f.setMatchingRecordId(100L + id);
        f.setEmpId(10L);
        f.setPostId(20L);
        f.setAiMatchScore(ai);
        f.setFinalMatchScore(manual);
        f.setFinalMatchStatus(2);
        f.setFeedbackComment("需要补充高并发项目经验");
        f.setCalibrationTemplateVersion("v1");
        f.setCalibrationSource("STRUCTURED_REVIEW");
        f.setExportEnabled(exportEnabled);
        f.setFeedbackTime(LocalDateTime.of(2026, 8, 2, 10, 0));
        return f;
    }

    @Test
    void exportJsonlExportsOnlyExportEnabledSamplesWithScores() throws Exception {
        MatchingFeedbackDataset ok = sample(1L, new BigDecimal("82.5"), new BigDecimal("76.0"), 1);
        MatchingFeedbackDataset noExport = sample(2L, new BigDecimal("90.0"), new BigDecimal("85.0"), 0);
        MatchingFeedbackDataset noManual = sample(3L, new BigDecimal("80.0"), null, 1);
        when(datasetMapper.selectList(any())).thenReturn(List.of(ok, noExport, noManual), List.of());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.exportCalibration("jsonl", null, null, null, false, true, out);

        String result = out.toString(StandardCharsets.UTF_8);
        String[] lines = result.trim().split("\n");
        // 首行为清单元数据，第二行为样本
        assertThat(lines).hasSize(2);
        assertThat(lines[0]).contains("\"type\":\"manifest\"");
        assertThat(lines[0]).contains("\"schemaVersion\":\"calibration-v1\"");
        assertThat(lines[0]).contains("\"maskingPolicy\":\"EMPLOYEE_ID_MASKED\"");
        assertThat(lines[1]).contains("\"schemaVersion\":\"calibration-v1\"");
        assertThat(lines[1]).contains("\"aiMatchScore\":82.5");
        assertThat(lines[1]).contains("\"finalMatchScore\":76.0");
        assertThat(lines[1]).contains("\"templateVersion\":\"v1\"");
        assertThat(lines[1]).contains("\"employee\":{\"id\":\"****10\"");
        assertThat(lines[1]).doesNotContain("realName").doesNotContain("phone");
    }

    @Test
    void exportWithDimensionsSkipsInvalidReasonCode() throws Exception {
        MatchingFeedbackDataset ok = sample(1L, new BigDecimal("82.5"), new BigDecimal("76.0"), 1);
        when(datasetMapper.selectList(any())).thenReturn(List.of(ok), List.of());

        MatchingFeedbackDimension good = new MatchingFeedbackDimension();
        good.setDimensionKey("ability");
        good.setSystemRawScore(new BigDecimal("88.0"));
        good.setManualRawScore(new BigDecimal("75.0"));
        good.setReasonCode("RESUME_OVERESTIMATED");
        good.setReasonText("项目经验不足");
        MatchingFeedbackDimension bad = new MatchingFeedbackDimension();
        bad.setDimensionKey("semantic");
        bad.setReasonCode("NOT_A_REAL_CODE");
        when(dimensionMapper.selectList(any())).thenReturn(List.of(good, bad));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.exportCalibration("jsonl", null, null, null, true, true, out);

        // 仅清单元数据行，非法原因码样本被剔除
        String[] lines = out.toString(StandardCharsets.UTF_8).trim().split("\n");
        assertThat(lines).hasSize(1);
        assertThat(lines[0]).contains("\"type\":\"manifest\"");
    }

    @Test
    void exportCsvHasHeaderAndRows() throws Exception {
        MatchingFeedbackDataset ok = sample(1L, new BigDecimal("82.5"), new BigDecimal("76.0"), 1);
        when(datasetMapper.selectList(any())).thenReturn(List.of(ok), List.of());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.exportCalibration("csv", null, null, null, false, true, out);

        String result = out.toString(StandardCharsets.UTF_8);
        assertThat(result).startsWith("# manifest:");
        assertThat(result).contains("matchingRecordId,postId,empId,aiMatchScore,finalMatchScore");
        assertThat(result).contains("101,20,****10,82.5,76.0,2");
    }
}
