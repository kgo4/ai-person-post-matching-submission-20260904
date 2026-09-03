package com.example.matching.controller.rag;

import com.example.matching.application.rag.RagLogApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.rag.api.RagQueryLogResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "RAG查询日志", description = "RAG查询日志管理，记录和查询RAG检索历史。")
@RestController
@RequestMapping("/api/rag/logs")
@RequiredArgsConstructor
public class RagLogController {

    private final RagLogApiFacade facade;

    @Operation(summary = "分页查询日志", description = "按场景分页查询RAG查询日志。")
    @GetMapping("/page")
    public R<PageResponse<RagQueryLogResponse>> pageLogs(
            @Parameter(description = "当前页码") @RequestParam(defaultValue = "1") long current,
            @Parameter(description = "每页记录数") @RequestParam(defaultValue = "10") long size,
            @Parameter(description = "RAG场景") @RequestParam(required = false) String scenario) {
        return R.ok(facade.pageLogs(current, size, scenario));
    }

    @Operation(summary = "获取日志详情", description = "根据ID获取RAG查询日志详情。")
    @GetMapping("/{id}")
    public R<RagQueryLogResponse> getLog(@PathVariable Long id) {
        return R.ok(facade.getLog(id));
    }
}
