package com.example.matching.service.impl;

import com.example.matching.entity.matching.MatchingFeedbackDataset;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.mapper.matching.MatchingFeedbackDatasetMapper;
import com.example.matching.mapper.matching.MatchingRecordMapper;
import com.example.matching.service.matching.impl.MatchingFeedbackDatasetServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class MatchingFeedbackDatasetServiceImplReplayTest {

    @Test
    void submitFeedbackCopiesOriginalAiScoreAndSubjectIdsFromMatchingRecord() {
        MatchingFeedbackDatasetMapper feedbackMapper = mock(MatchingFeedbackDatasetMapper.class);
        MatchingRecordMapper recordMapper = mock(MatchingRecordMapper.class);
        MatchingFeedbackDatasetServiceImpl service = new MatchingFeedbackDatasetServiceImpl(recordMapper);
        ReflectionTestUtils.setField(service, "baseMapper", feedbackMapper);

        MatchingRecord record = new MatchingRecord();
        record.setId(100L);
        record.setEmpId(11L);
        record.setPostId(12L);
        record.setAiMatchScore(new BigDecimal("88.50"));
        when(recordMapper.selectById(100L)).thenReturn(record);

        MatchingFeedbackDataset feedback = new MatchingFeedbackDataset();
        feedback.setMatchingRecordId(100L);
        feedback.setFinalMatchScore(new BigDecimal("80"));

        service.submitFeedback(feedback);

        assertThat(feedback.getAiMatchScore()).isEqualByComparingTo("88.50");
        assertThat(feedback.getEmpId()).isEqualTo(11L);
        assertThat(feedback.getPostId()).isEqualTo(12L);
        verify(feedbackMapper).insert(feedback);
    }

    @Test
    void submitFeedbackCreatesExportControlledManualCalibrationRecord() {
        MatchingFeedbackDatasetMapper feedbackMapper = mock(MatchingFeedbackDatasetMapper.class);
        MatchingRecordMapper recordMapper = mock(MatchingRecordMapper.class);
        MatchingFeedbackDatasetServiceImpl service = new MatchingFeedbackDatasetServiceImpl(recordMapper);
        ReflectionTestUtils.setField(service, "baseMapper", feedbackMapper);

        MatchingFeedbackDataset feedback = new MatchingFeedbackDataset();
        feedback.setMatchingRecordId(100L);

        service.submitFeedback(feedback);

        assertThat(feedback.getExportEnabled()).isEqualTo(0);
        assertThat(feedback.getCalibrationSource()).isEqualTo("MANUAL_FEEDBACK");
        assertThat(feedback.getCalibrationTemplateVersion()).isEqualTo("v1");
        assertThat(feedback.getFeedbackTime()).isNotNull();
    }

    @Test
    void replaySummaryReportsCurrentAndDecalibratedDeviation() {
        MatchingFeedbackDatasetMapper feedbackMapper = mock(MatchingFeedbackDatasetMapper.class);
        MatchingRecordMapper recordMapper = mock(MatchingRecordMapper.class);
        MatchingFeedbackDatasetServiceImpl service = new MatchingFeedbackDatasetServiceImpl(recordMapper);
        ReflectionTestUtils.setField(service, "baseMapper", feedbackMapper);

        MatchingFeedbackDataset sample = new MatchingFeedbackDataset();
        sample.setId(1L);
        sample.setMatchingRecordId(100L);
        sample.setAiMatchScore(new BigDecimal("90"));
        sample.setFinalMatchScore(new BigDecimal("80"));
        sample.setAdoptionStatus(3);
        sample.setFeedbackReasons("[\"POST_MODEL_INACCURATE\",\"BUSINESS_MISMATCH\"]");
        sample.setFeedbackTime(LocalDateTime.now());

        MatchingRecord record = new MatchingRecord();
        record.setId(100L);
        record.setModelQualityCoefficient(new BigDecimal("80"));
        record.setFeedbackCalibration(new BigDecimal("5"));
        record.setLlmScore(null);

        when(feedbackMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(sample));
        when(recordMapper.selectById(100L)).thenReturn(record);

        Map<String, Object> summary = service.getCalibrationReplaySummary(20);

        assertThat(summary.get("totalSamples")).isEqualTo(1);
        assertThat(summary.get("currentAverageDeviation")).isEqualTo(10.0d);
        assertThat(summary.get("decalibratedAverageDeviation")).isEqualTo(10.0d);
        assertThat(summary.get("currentAverageBias")).isEqualTo(10.0d);
        assertThat(summary.get("decalibratedAverageBias")).isEqualTo(10.0d);

        @SuppressWarnings("unchecked")
        Map<String, Long> targetCounts = (Map<String, Long>) summary.get("calibrationTargetCounts");
        assertThat(targetCounts)
                .containsEntry("岗位侧", 1L)
                .containsEntry("AI侧", 1L);
    }
}
