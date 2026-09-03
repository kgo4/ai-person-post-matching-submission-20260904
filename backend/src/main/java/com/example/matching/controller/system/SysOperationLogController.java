package com.example.matching.controller.system;

import com.example.matching.application.system.OperationLogApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.system.api.OperationLogResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Tag(name = "操作日志", description = "多条件分页查询操作日志，支持按操作模块、操作用户、时间范围筛选")
@RestController
@RequestMapping("/api/system/operation-log")
@RequiredArgsConstructor
public class SysOperationLogController {

    private final OperationLogApiFacade facade;

    @Operation(summary = "分页查询操作日志", description = "按操作模块、操作用户ID、时间范围（开始时间~结束时间）进行组合筛选，支持分页返回操作日志列表")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功")
    })
    @GetMapping("/page")
    public R<PageResponse<OperationLogResponse>> page(
            @Parameter(description = "当前页码，从1开始", example = "1") @RequestParam(defaultValue = "1") long current,
            @Parameter(description = "每页记录数，默认10条", example = "10") @RequestParam(defaultValue = "10") long size,
            @Parameter(description = "操作模块名称，如：用户管理、角色管理") @RequestParam(required = false) String operationModule,
            @Parameter(description = "操作用户ID", example = "1") @RequestParam(required = false) Long userId,
            @Parameter(description = "操作时间起始，格式：yyyy-MM-dd HH:mm:ss", example = "2025-01-01 00:00:00") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "操作时间截止，格式：yyyy-MM-dd HH:mm:ss", example = "2025-12-31 23:59:59") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return R.ok(facade.page(current, size, operationModule, userId, startTime, endTime));
    }

    @Operation(summary = "获取日志详情", description = "根据日志ID查询单条操作日志的完整信息，包括请求参数、返回结果、操作IP等")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "404", description = "日志记录不存在")
    })
    @GetMapping("/{id}")
    public R<OperationLogResponse> getById(
            @Parameter(description = "操作日志ID", required = true, example = "1") @PathVariable Long id) {
        return R.ok(facade.get(id));
    }
}
