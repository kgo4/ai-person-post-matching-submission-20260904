package com.example.matching.application.matching;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.dto.matching.MatchingFeedbackExportDTO;
import com.example.matching.dto.matching.api.FeedbackDatasetRequest;
import com.example.matching.dto.matching.api.FeedbackDatasetResponse;
import com.example.matching.entity.matching.MatchingFeedbackDataset;
import com.example.matching.service.matching.MatchingFeedbackDatasetService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FeedbackApiFacade {

    private final MatchingFeedbackDatasetService matchingFeedbackDatasetService;

    public void submit(FeedbackDatasetRequest req) {
        MatchingFeedbackDataset entity = new MatchingFeedbackDataset();
        entity.setMatchingRecordId(req.matchingRecordId());
        if (req.manualScore() != null) {
            entity.setFinalMatchScore(req.manualScore());
        }
        entity.setFeedbackComment(coalesce(req.feedbackContent()));
        entity.setFeedbackReasons(coalesce(req.feedbackDimensions()));
        entity.setExportEnabled(req.exportEnabled());
        matchingFeedbackDatasetService.submitFeedback(entity);
    }

    public PageResponse<FeedbackDatasetResponse> page(long current, long size, Integer exportEnabled) {
        IPage<MatchingFeedbackDataset> page = matchingFeedbackDatasetService.pageFeedback(
            new Page<>(current, size), exportEnabled);
        return PageResponse.from(page, this::toResponse);
    }

    public Map<String, Object> summary(int limit) {
        return matchingFeedbackDatasetService.getFeedbackSummary(limit);
    }

    public List<String> examples(int limit) {
        return matchingFeedbackDatasetService.getRecentFeedbackExamples(limit);
    }

    public List<MatchingFeedbackExportDTO> export(Integer exportEnabled) {
        return matchingFeedbackDatasetService.exportFeedback(exportEnabled);
    }

    public void batchUpdateExportStatus(List<Long> ids, Integer exportEnabled) {
        matchingFeedbackDatasetService.batchUpdateExportStatus(ids, exportEnabled);
    }

    public Map<String, Object> trend(int days) {
        return matchingFeedbackDatasetService.getFeedbackTrend(days);
    }

    public Map<String, Object> deviationDistribution(int limit) {
        return matchingFeedbackDatasetService.getDeviationDistribution(limit);
    }

    public Map<String, Object> calibrationReplay(int limit) {
        return matchingFeedbackDatasetService.getCalibrationReplaySummary(limit);
    }

    private FeedbackDatasetResponse toResponse(MatchingFeedbackDataset e) {
        return new FeedbackDatasetResponse(
            e.getId(), e.getMatchingRecordId(), e.getEmpId(), e.getPostId(),
            e.getAiMatchScore(), e.getFinalMatchScore(), e.getFinalMatchStatus(),
            e.getAdoptionStatus(), e.getFeedbackReasons(), e.getFeedbackComment(),
            e.getExportEnabled(), e.getFeedbackTime()
        );
    }

    private static String coalesce(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
