package com.example.matching.controller.system;

import com.example.matching.application.system.SourceWeightConfigApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.system.api.SourceWeightConfigRequest;
import com.example.matching.dto.system.api.SourceWeightConfigResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "来源权重配置", description = "管理各证据来源在画像融合中的权重")
@RestController
@RequestMapping("/api/system/source-weight")
@RequiredArgsConstructor
public class SourceWeightConfigController {

    private final SourceWeightConfigApiFacade facade;

    @Operation(summary = "获取所有权重配置")
    @ApiResponse(responseCode = "200", description = "返回按 sortOrder 排序的配置列表")
    @GetMapping("/list")
    public R<List<SourceWeightConfigResponse>> list() {
        return R.ok(facade.listAll());
    }

    @Operation(summary = "批量更新权重配置")
    @ApiResponse(responseCode = "200", description = "更新成功，返回最新配置列表")
    @PutMapping("/batch-update")
    public R<List<SourceWeightConfigResponse>> batchUpdate(@RequestBody List<SourceWeightConfigRequest> requests) {
        return R.ok(facade.batchUpdate(requests));
    }
}
