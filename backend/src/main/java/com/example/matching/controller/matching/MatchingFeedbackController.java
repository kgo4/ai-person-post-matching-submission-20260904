package com.example.matching.controller.matching;

import com.alibaba.excel.EasyExcel;
import com.example.matching.application.matching.FeedbackApiFacade;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.matching.MatchingFeedbackExportDTO;
import com.example.matching.dto.matching.api.FeedbackDatasetRequest;
import com.example.matching.dto.matching.api.FeedbackDatasetResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Tag(name = "人工校准数据", description = "管理人工对匹配结果的结构化校准数据，并按显式授权导出。")
@RestController
@RequestMapping("/api/matching/feedback")
@RequiredArgsConstructor
public class MatchingFeedbackController {

    private final FeedbackApiFacade feedbackApiFacade;

    @Operation(summary = "提交人工校准数据", description = "提交一条人工对匹配结果的校准记录；是否导出必须显式指定。")
    @PostMapping
    public R<Void> submit(@Parameter(description = "反馈数据集请求对象") @Valid @RequestBody FeedbackDatasetRequest request) {
        feedbackApiFacade.submit(request);
        return R.ok();
    }

    @Operation(summary = "分页查询校准记录", description = "分页查询人工校准记录，支持按是否允许导出筛选。")
    @GetMapping("/page")
    public R<PageResponse<FeedbackDatasetResponse>> page(
            @Parameter(description = "当前页码，从1开始") @RequestParam(defaultValue = "1") long current,
            @Parameter(description = "每页记录数") @RequestParam(defaultValue = "10") long size,
            @Parameter(description = "是否允许导出：0-否、1-是，不传则查询全部") @RequestParam(required = false) Integer exportEnabled) {
        return R.ok(feedbackApiFacade.page(current, size, exportEnabled));
    }

    @Operation(summary = "获取反馈统计摘要", description = "获取最近反馈数据的统计摘要，包括采纳情况和平均偏差")
    @GetMapping("/summary")
    public R<Map<String, Object>> summary(
            @Parameter(description = "统计最近N条反馈") @RequestParam(defaultValue = "100") int limit) {
        return R.ok(feedbackApiFacade.summary(limit));
    }

    @Operation(summary = "获取反馈样本（用于Prompt优化）", description = "获取最近的反馈样本文本，可用于AI Prompt的few-shot优化")
    @GetMapping("/examples")
    public R<List<String>> examples(
            @Parameter(description = "返回最近N条样本") @RequestParam(defaultValue = "5") int limit) {
        return R.ok(feedbackApiFacade.examples(limit));
    }

    @Operation(summary = "导出校准数据", description = "导出校准数据为 Excel 文件，支持按导出授权筛选")
    @GetMapping("/export")
    public void exportFeedback(
            @Parameter(description = "是否允许导出：0-否、1-是，不传则导出全部") @RequestParam(required = false) Integer exportEnabled,
            HttpServletResponse response) {
        try {
            List<MatchingFeedbackExportDTO> dataList = feedbackApiFacade.export(exportEnabled);

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String fileName = URLEncoder.encode("AI反馈数据", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

            EasyExcel.write(response.getOutputStream(), MatchingFeedbackExportDTO.class).sheet("反馈数据").doWrite(dataList);
        } catch (Exception e) {
            throw BusinessException.of(ErrorCodeEnum.EXPORT_ERROR, "导出失败", e).put("operation", "exportFeedback").build();
        }
    }


    @Operation(summary = "获取反馈趋势统计", description = "获取最近N天的反馈数量和偏差趋势")
    @GetMapping("/trend")
    public R<Map<String, Object>> trend(
            @Parameter(description = "统计最近N天") @RequestParam(defaultValue = "30") int days) {
        return R.ok(feedbackApiFacade.trend(days));
    }

    @Operation(summary = "获取偏差分布统计", description = "获取反馈偏差的分布情况")
    @GetMapping("/deviation-distribution")
    public R<Map<String, Object>> deviationDistribution(
            @Parameter(description = "统计最近N条反馈") @RequestParam(defaultValue = "100") int limit) {
        return R.ok(feedbackApiFacade.deviationDistribution(limit));
    }

    @Operation(summary = "获取校准回放摘要", description = "对最近反馈样本回放当前总分与去校准分，观察校准项对最终偏差的影响")
    @PostMapping("/batch-update-export-status")
    public R<Void> batchUpdateExportStatus(
            @RequestBody List<Long> ids,
            @RequestParam Integer exportEnabled) {
        feedbackApiFacade.batchUpdateExportStatus(ids, exportEnabled);
        return R.ok();
    }
    @GetMapping("/calibration-replay")
    public R<Map<String, Object>> calibrationReplay(
            @Parameter(description = "统计最近N条反馈") @RequestParam(defaultValue = "100") int limit) {
        return R.ok(feedbackApiFacade.calibrationReplay(limit));
    }
}
