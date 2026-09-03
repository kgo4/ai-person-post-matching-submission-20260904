package com.example.matching.controller.contest;

import com.example.matching.application.contest.ContestReportApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.contest.api.ContestReportTaskResponse;
import com.example.matching.dto.contest.api.CreateContestReportRequest;
import com.example.matching.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/contest/report")
@RequiredArgsConstructor
public class ContestReportController {

    private final ContestReportApiFacade facade;

    @GetMapping("/types")
    public R<List<Map<String, Object>>> getReportTypes() {
        return R.ok(facade.getReportTypes());
    }

    @PostMapping("/tasks")
    public R<ContestReportTaskResponse> generateReport(@RequestBody CreateContestReportRequest req) {
        Long createdBy = SecurityUtils.getCurrentUserId();
        return R.ok(facade.generateReport(req, createdBy));
    }

    @PostMapping("/tasks/{id}/retry")
    public R<ContestReportTaskResponse> retryReport(@PathVariable Long id) {
        Long createdBy = SecurityUtils.getCurrentUserId();
        return R.ok(facade.retryReport(id, createdBy));
    }

    @GetMapping("/tasks/page")
    public R<Map<String, Object>> getReportTaskPage(
            @RequestParam(required = false) String reportType,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Map<String, Object> result = facade.getReportTaskPage(reportType, page, size);
        return R.ok(result);
    }

    @GetMapping("/tasks/{id}")
    public R<ContestReportTaskResponse> getReportTaskById(@PathVariable Long id) {
        return R.ok(facade.getReportTaskById(id));
    }

    @GetMapping("/tasks/{id}/evidence")
    public R<List<Map<String, Object>>> getReportEvidence(@PathVariable Long id) {
        return R.ok(facade.getReportEvidence(id));
    }

    @GetMapping("/tasks/{id}/export")
    public R<String> exportReport(@PathVariable Long id, @RequestParam(defaultValue = "md") String format) {
        return R.ok(facade.exportReport(id, format));
    }

    @GetMapping("/submission-checklist")
    public R<Map<String, Object>> getSubmissionChecklist() {
        return R.ok(facade.getSubmissionChecklist());
    }
}
