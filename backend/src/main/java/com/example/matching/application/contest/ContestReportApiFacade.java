package com.example.matching.application.contest;

import com.example.matching.dto.contest.api.ContestReportTaskResponse;
import com.example.matching.dto.contest.api.CreateContestReportRequest;
import com.example.matching.entity.contest.ContestReportTask;
import com.example.matching.entity.contest.ContestReportTypeEnum;
import com.example.matching.service.contest.report.ContestReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContestReportApiFacade {

    private final ContestReportService reportService;

    public ContestReportTaskResponse generateReport(CreateContestReportRequest req, Long createdBy) {
        ContestReportTask task = reportService.generateReport(req.reportType(), req.title(), createdBy);
        return toResponse(task);
    }

    public ContestReportTaskResponse retryReport(Long id, Long createdBy) {
        ContestReportTask task = reportService.retryReport(id, createdBy);
        return toResponse(task);
    }

    public Map<String, Object> getReportTaskPage(String reportType, Integer page, Integer size) {
        return reportService.getReportTaskPage(reportType, page, size);
    }

    public ContestReportTaskResponse getReportTaskById(Long id) {
        ContestReportTask task = reportService.getReportTaskById(id);
        return toResponse(task);
    }

    public List<Map<String, Object>> getReportEvidence(Long id) {
        return reportService.getReportEvidenceRefs(id);
    }

    public String exportReport(Long id, String format) {
        return reportService.exportReport(id, format);
    }

    public Map<String, Object> getSubmissionChecklist() {
        return reportService.getSubmissionChecklist();
    }

    public List<Map<String, Object>> getReportTypes() {
        return Arrays.stream(ContestReportTypeEnum.values())
                .map(e -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("type", e.getType());
                    item.put("label", e.getLabel());
                    item.put("title", e.getTitle());
                    item.put("description", e.getDescription());
                    item.put("scope", e.getScope());
                    item.put("needsAi", e.isNeedsAi());
                    item.put("needsRag", e.isNeedsRag());
                    item.put("exportable", e.isExportable());
                    return item;
                })
                .collect(Collectors.toList());
    }

    static ContestReportTaskResponse toResponse(ContestReportTask e) {
        if (e == null) return null;
        return new ContestReportTaskResponse(
                e.getId(), e.getTaskCode(), e.getReportType(), e.getTaskStatus(),
                e.getReportTitle(), e.getReportMarkdown(), e.getReportJson(),
                e.getErrorMessage(), e.getGenerationMode(), e.getModelName(),
                e.getPromptVersion(), e.getEvidenceSnapshotJson(), e.getValidationStatus(),
                e.getValidationMessage(), e.getDurationMs(), e.getWordCount(),
                e.getRagScenario(), e.getRagHitCount(), e.getCreatedBy(),
                e.getCreatedTime(), e.getFinishedTime()
        );
    }
}
