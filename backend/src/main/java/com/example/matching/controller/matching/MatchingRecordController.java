package com.example.matching.controller.matching;

import com.example.matching.application.matching.MatchingRecordApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.matching.MatchingExecuteDTO;
import com.example.matching.dto.matching.StructuredReviewDTO;
import com.example.matching.dto.matching.api.MatchingExecuteResultResponse;
import com.example.matching.dto.matching.api.MatchingRecordResponse;
import com.example.matching.dto.matching.api.MatchingTaskResponse;
import com.example.matching.dto.matching.api.ModifyResultRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Tag(name = "匹配执行与结果", description = "人岗匹配核心模块")
@RestController
@RequestMapping("/api/matching/record")
@RequiredArgsConstructor
public class MatchingRecordController {

    private final MatchingRecordApiFacade matchingRecordApiFacade;

    @Operation(summary = "执行人岗匹配（同步接口）")
    @PostMapping("/execute")
    public R<MatchingExecuteResultResponse> execute(@Valid @RequestBody MatchingExecuteDTO dto) {
        MatchingExecuteResultResponse result = matchingRecordApiFacade.execute(dto);
        return R.ok("匹配完成，共处理" + result.records().size() + "条记录", result);
    }

    @Operation(summary = "提交异步匹配任务")
    @PostMapping("/execute-async")
    public R<Map<String, String>> executeAsync(@Valid @RequestBody MatchingExecuteDTO dto) {
        Map<String, String> result = matchingRecordApiFacade.executeAsync(dto);
        return R.ok("匹配任务已提交", result);
    }

    @Operation(summary = "查询匹配任务状态")
    @GetMapping("/task/{taskId}")
    public R<MatchingTaskResponse> getTaskStatus(@PathVariable String taskId) {
        MatchingTaskResponse task = matchingRecordApiFacade.getTaskStatus(taskId);
        if (task == null) {
            return R.fail("任务不存在");
        }
        return R.ok(task);
    }

    @Operation(summary = "取消匹配任务（仅待执行/执行中可取消）")
    @PostMapping("/task/{taskId}/cancel")
    public R<Void> cancelTask(@PathVariable String taskId) {
        boolean cancelled = matchingRecordApiFacade.cancelMatchingTask(taskId);
        return cancelled ? R.ok() : R.fail("任务不存在或已处于终态，无法取消");
    }

    @Operation(summary = "删除匹配任务（连带删除该任务产生的匹配记录）")
    @DeleteMapping("/task/{taskId}")
    public R<Void> deleteTask(@PathVariable String taskId) {
        boolean deleted = matchingRecordApiFacade.deleteMatchingTask(taskId);
        return deleted ? R.ok() : R.fail("任务不存在，无法删除");
    }

    @Operation(summary = "分页查询匹配任务列表")
    @GetMapping("/task/page")
    public R<PageResponse<MatchingTaskResponse>> pageTasks(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Integer status) {
        long safeCurrent = Math.max(current, 1);
        long safeSize = Math.min(Math.max(size, 1), 200);
        return R.ok(matchingRecordApiFacade.pageMatchingTasks(safeCurrent, safeSize, status));
    }

    @Operation(summary = "分页查询匹配记录")
    @GetMapping("/page")
    public R<PageResponse<MatchingRecordResponse>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long postId,
            @RequestParam(required = false) Long empId,
            @RequestParam(required = false) Integer matchStatus) {
        return R.ok(matchingRecordApiFacade.page(current, size, postId, empId, matchStatus));
    }

    @GetMapping("/dashboard-summary")
    public R<Map<String, Object>> dashboardSummary() {
        return R.ok(matchingRecordApiFacade.dashboardSummary());
    }

    @Operation(summary = "获取匹配详情")
    @GetMapping("/{id}")
    public R<MatchingRecordResponse> getById(@PathVariable Long id) {
        return R.ok(matchingRecordApiFacade.getById(id));
    }

    @Operation(summary = "人工修改匹配结果")
    @PutMapping("/{id}")
    public R<Void> modifyResult(
            @PathVariable Long id,
            @Valid @RequestBody ModifyResultRequest request) {
        matchingRecordApiFacade.modifyResult(id, request);
        return R.ok();
    }

    @Operation(summary = "结构化复核")
    @PostMapping("/structured-review")
    public R<Void> structuredReview(@RequestBody StructuredReviewDTO request) {
        matchingRecordApiFacade.submitStructuredReview(request);
        return R.ok();
    }

    @Operation(summary = "手动重试AI评分")
    @PostMapping("/{id}/retry-ai-scoring")
    public R<Void> retryAiScoring(@PathVariable Long id) {
        boolean reset = matchingRecordApiFacade.retryAiScoring(id);
        return reset ? R.ok() : R.fail("状态不允许重试（已完成/评分中/已锁定）");
    }

    @Operation(summary = "锁定匹配结果")
    @PutMapping("/{id}/lock")
    public R<Void> lock(@PathVariable Long id) {
        matchingRecordApiFacade.lockResult(id);
        return R.ok();
    }

    @Operation(summary = "解锁匹配结果")
    @PutMapping("/{id}/unlock")
    public R<Void> unlock(@PathVariable Long id) {
        matchingRecordApiFacade.unlockResult(id);
        return R.ok();
    }

    @Operation(summary = "查看量化分析报告")
    @GetMapping("/{id}/report")
    public R<String> report(@PathVariable Long id) {
        return R.ok(matchingRecordApiFacade.generateReport(id));
    }

    @Operation(summary = "AI语义增强分析报告")
    @GetMapping("/{id}/ai-report")
    public R<String> aiReport(@PathVariable Long id) {
        return R.ok(matchingRecordApiFacade.generateAiReport(id));
    }

    @Operation(summary = "删除匹配记录")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        matchingRecordApiFacade.deleteRecord(id);
        return R.ok();
    }

    @Operation(summary = "导出匹配结果 Excel")
    @GetMapping("/export-excel")
    public void exportExcel(
            @RequestParam(required = false) Long postId,
            HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''matching-results.xlsx");
        response.getOutputStream().write(matchingRecordApiFacade.exportExcel(postId));
    }
}
