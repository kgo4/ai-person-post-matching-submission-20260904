package com.example.matching.controller.rag;

import com.example.matching.application.rag.AiHarnessAuditApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.rag.api.AiHarnessCheckLogResponse;
import com.example.matching.dto.rag.api.HarnessCheckRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "AI Harness审计", description = "AI生成声明的Harness判定日志和风险分布。")
@RestController
@RequestMapping("/api/rag/harness")
@RequiredArgsConstructor
public class AiHarnessAuditController {

    private final AiHarnessAuditApiFacade facade;

    @Operation(summary = "分页查询Harness判定日志", description = "按场景、判定分页查询AI Harness日志。")
    @GetMapping("/checks/page")
    public R<PageResponse<AiHarnessCheckLogResponse>> pageChecks(
            @Parameter(description = "当前页码") @RequestParam(defaultValue = "1") long current,
            @Parameter(description = "每页记录数") @RequestParam(defaultValue = "10") long size,
            @Parameter(description = "场景") @RequestParam(required = false) String scenario,
            @Parameter(description = "判定") @RequestParam(required = false) String decision) {
        return R.ok(facade.pageChecks(current, size, scenario, decision));
    }

    @Operation(summary = "获取Harness审计摘要", description = "返回判定分布、风险分布和自证据拦截数量。")
    @GetMapping("/checks/summary")
    public R<Map<String, Object>> summary() {
        return R.ok(facade.summary());
    }

    @Operation(summary = "更新Harness处理状态", description = "轻量人工复核。")
    @PostMapping("/checks/{id}/review")
    public R<Boolean> updateReviewStatus(@PathVariable Long id, @RequestBody HarnessCheckRequest req) {
        if (req.reviewStatus() == null || req.reviewStatus().isBlank()) {
            return R.fail("reviewStatus不能为空");
        }
        boolean ok = facade.updateReviewStatus(id, req);
        if (!ok) {
            return R.fail("Harness日志不存在");
        }
        return R.ok(Boolean.TRUE);
    }
}
