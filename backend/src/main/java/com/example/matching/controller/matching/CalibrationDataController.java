package com.example.matching.controller.matching;

import com.example.matching.application.matching.CalibrationDataApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.matching.CalibrationRecordVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;

@Tag(name = "匹配校准数据", description = "人工复核产生的标准化校准数据：查询、筛选、导出。")
@RestController
@RequestMapping("/api/matching/calibration")
@RequiredArgsConstructor
public class CalibrationDataController {

    private final CalibrationDataApiFacade facade;

    @Operation(summary = "分页查询校准数据")
    @GetMapping
    public R<PageResponse<CalibrationRecordVO>> pageCalibration(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(required = false) Long postId,
            @RequestParam(required = false) Boolean exportEnabled) {
        return R.ok(facade.pageCalibration(current, size, startTime, endTime, postId, exportEnabled));
    }

    @Operation(summary = "导出校准数据（JSONL/CSV，流式）")
    @GetMapping("/export")
    public void exportCalibration(
            @RequestParam(defaultValue = "jsonl") String format,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(required = false) Long postId,
            @RequestParam(defaultValue = "true") boolean includeDimensions,
            @RequestParam(defaultValue = "true") boolean maskPersonalData,
            HttpServletResponse response) throws IOException {
        String fileFormat = "csv".equalsIgnoreCase(format) ? "csv" : "jsonl";
        String contentType = "csv".equalsIgnoreCase(format)
                ? "text/csv; charset=UTF-8"
                : "application/x-jsonlines; charset=UTF-8";
        response.setContentType(contentType);
        String filename = "matching-calibration-" + java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + "." + fileFormat;
        response.setHeader("Content-Disposition", "attachment; filename=" + filename);
        facade.exportCalibration(fileFormat, startTime, endTime, postId,
                includeDimensions, maskPersonalData, response.getOutputStream());
    }
}
