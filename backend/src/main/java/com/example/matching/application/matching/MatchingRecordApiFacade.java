package com.example.matching.application.matching;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.dto.matching.MatchingExecuteDTO;
import com.example.matching.dto.matching.api.MatchingExecuteResultResponse;
import com.example.matching.dto.matching.StructuredReviewDTO;
import com.example.matching.dto.matching.api.MatchingRecordResponse;
import com.example.matching.dto.matching.api.MatchingTaskResponse;
import com.example.matching.dto.matching.api.ModifyResultRequest;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.entity.matching.MatchingTask;
import com.example.matching.service.matching.MatchingExecuteService;
import com.example.matching.service.matching.MatchingExecuteResult;
import com.example.matching.service.matching.MatchingRecordService;
import com.example.matching.service.matching.MatchingTaskService;
import com.example.matching.service.matching.StructuredReviewService;
import com.example.matching.service.common.ExcelService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MatchingRecordApiFacade {

    private final MatchingRecordService matchingRecordService;
    private final MatchingExecuteService matchingExecuteService;
    private final MatchingTaskService matchingTaskService;
    private final StructuredReviewService structuredReviewService;
    private final ExcelService excelService;
    private final com.example.matching.converter.matching.MatchingRecordConverter matchingRecordConverter;

    public MatchingExecuteResultResponse execute(MatchingExecuteDTO dto) {
        MatchingExecuteResult result = matchingExecuteService.execute(dto);
        List<MatchingRecordResponse> records = result.records().stream().map(this::toResponse).toList();
        return new MatchingExecuteResultResponse(
                records,
                result.candidateScope().name(),
                result.candidateCount(),
                result.totalActiveCount(),
                result.truncated(),
                result.taskId(),
                result.isAsync(),
                result.excludedCount()
        );
    }

    public Map<String, String> executeAsync(MatchingExecuteDTO dto) {
        String taskId = matchingTaskService.submitTask(dto);
        Map<String, String> result = new HashMap<>();
        result.put("taskId", taskId);
        return result;
    }

    public MatchingTaskResponse getTaskStatus(String taskId) {
        MatchingTask task = matchingTaskService.getTaskStatus(taskId);
        if (task == null) {
            return null;
        }
        return toTaskResponse(task);
    }

    public boolean cancelMatchingTask(String taskId) {
        return matchingTaskService.cancelTask(taskId);
    }

    public boolean deleteMatchingTask(String taskId) {
        return matchingTaskService.deleteTask(taskId);
    }

    public PageResponse<MatchingTaskResponse> pageMatchingTasks(long current, long size, Integer status) {
        IPage<MatchingTask> page = matchingTaskService.pageTasks(current, size, status);
        return PageResponse.from(page, this::toTaskResponse);
    }

    public PageResponse<MatchingRecordResponse> page(long current, long size, Long postId, Long empId, Integer matchStatus) {
        IPage<MatchingRecord> page = matchingRecordService.pageRecords(
            new Page<>(current, size), postId, empId, matchStatus);
        return PageResponse.from(page, this::toResponse);
    }

    public Map<String, Object> dashboardSummary() {
        Map<String, Object> summary = new HashMap<>();
        Map<String, Long> matchingSummary = matchingRecordService.dashboardSummary();
        summary.put("total", matchingSummary.getOrDefault("totalCount", 0L));
        summary.put("score90", matchingSummary.getOrDefault("score90", 0L));
        summary.put("score75", matchingSummary.getOrDefault("score75", 0L));
        summary.put("score60", matchingSummary.getOrDefault("score60", 0L));
        summary.put("scoreBelow60", matchingSummary.getOrDefault("scoreBelow60", 0L));
        for (int status = 0; status <= 4; status++) {
            summary.put("status" + status, matchingSummary.getOrDefault("status" + status, 0L));
        }
        summary.put("recent", page(1, 10, null, null, null).records());
        return summary;
    }

    public MatchingRecordResponse getById(Long id) {
        MatchingRecord record = matchingRecordService.getDetailById(id);
        if (record == null) {
            return null;
        }
        return toResponse(record);
    }

    public void modifyResult(Long id, ModifyResultRequest req) {
        MatchingRecord record = new MatchingRecord();
        record.setFinalMatchScore(req.matchScore());
        record.setMatchStatus(req.matchStatus());
        record.setManualRemark(req.remark());
        record.setFeedbackComment(req.remark());
        matchingRecordService.modifyResult(id, record);
    }

    public void lockResult(Long id) {
        matchingRecordService.lockResult(id);
    }

    public void unlockResult(Long id) {
        matchingRecordService.unlockResult(id);
    }

    public String generateReport(Long id) {
        return matchingRecordService.generateReport(id);
    }

    public String generateAiReport(Long id) {
        return matchingRecordService.generateAiReport(id);
    }

    public void deleteRecord(Long id) {
        matchingRecordService.deleteRecord(id);
    }

    public void submitStructuredReview(StructuredReviewDTO request) {
        structuredReviewService.submitStructuredReview(request);
    }

    public boolean retryAiScoring(Long id) {
        return matchingRecordService.retryAiScoring(id);
    }

    public byte[] exportExcel(Long postId) {
        return excelService.buildMatchResultsExcel(postId);
    }

    private MatchingRecordResponse toResponse(MatchingRecord e) {
        // M17：DTO 收口——字段映射由 MapStruct 生成的 MatchingRecordConverter 承担
        return matchingRecordConverter.toResponse(e);
    }

    private MatchingTaskResponse toTaskResponse(MatchingTask e) {
        return new MatchingTaskResponse(
            e.getId(), e.getTaskId(), e.getPostId(), e.getEmpIds(),
            e.getStatus(), e.getProgress(),
            e.getTotalCount(), e.getProcessedCount(),
            e.getResultMessage(), e.getErrorMessage(),
            e.getCreatedTime(), e.getUpdatedTime()
        );
    }
}
