package com.example.matching.controller.system;

import com.example.matching.application.system.AdminRuntimeAuditApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.system.api.PromptInvocationLogResponse;
import com.example.matching.dto.system.api.RagAuditLogResponse;
import com.example.matching.dto.system.api.RuntimeAuditAggregateResponse;
import com.example.matching.dto.system.api.RuntimeMetricsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运行审计（AI 基础设施）——只读。
 *
 * <p>归 **PLATFORM_ADMIN**：路由前缀 {@code /api/admin/**} 由 SecurityConfig 统一收口到
 * {@code AUDIT:READ}，与「操作日志」「死信队列」同属审计域。
 *
 * <p>本控制器**只新增端点、不改动既有端点**：
 * <ul>
 *   <li>{@code /api/rag/logs/**} 仍由 {@code POST:MANAGE}（岗位体系管理员）持有，
 *       岗位侧的页面不受影响；</li>
 *   <li>这里给出的是**审计精简版**（不含 longtext 快照字段），面向平台侧。</li>
 * </ul>
 */
@Tag(name = "运行审计", description = "AI 运行时指标（token 消耗、LLM/工具响应时间）、Prompt 调用明细、RAG 检索日志")
@RestController
@RequestMapping("/api/admin/runtime-audit")
@RequiredArgsConstructor
public class AdminRuntimeAuditController {

    private final AdminRuntimeAuditApiFacade facade;

    @Operation(summary = "运行时指标快照",
            description = "token 消耗（输入/输出/总计）、LLM 与工具的调用次数、失败次数、平均与最大响应时间。"
                    + "⚠️ 进程级累计，应用重启归零。")
    @GetMapping("/metrics")
    public R<RuntimeMetricsResponse> metrics() {
        return R.ok(facade.metrics());
    }

    @Operation(summary = "Prompt 调用明细分页",
            description = "逐次 LLM 调用的耗时、工具耗时、排队等待、模型轮次、重试次数、缓存命中、降级情况。")
    @GetMapping("/prompt-logs/page")
    public R<PageResponse<PromptInvocationLogResponse>> pagePromptLogs(
            @Parameter(description = "当前页码") @RequestParam(defaultValue = "1") long current,
            @Parameter(description = "每页记录数") @RequestParam(defaultValue = "10") long size,
            @Parameter(description = "Prompt 名称") @RequestParam(required = false) String promptName,
            @Parameter(description = "是否成功") @RequestParam(required = false) Boolean success) {
        return R.ok(facade.pagePromptLogs(current, size, promptName, success));
    }

    @Operation(summary = "审计统计图表聚合",
            description = "返回调用量与响应时间的时段趋势、按 Prompt 聚合、RAG 检索趋势与场景聚合，"
                    + "供页面直接画图（服务端聚合，不把明细行交给前端）。")
    @GetMapping("/aggregate")
    public R<RuntimeAuditAggregateResponse> aggregate(
            @Parameter(description = "统计窗口（小时），默认 24，上限 720")
            @RequestParam(defaultValue = "24") int hours) {
        return R.ok(facade.aggregate(hours));
    }

    @Operation(summary = "RAG 检索日志分页",
            description = "按场景分页查询 RAG 检索记录（审计精简版：不含 prompt/上下文/响应长文本）")
    @GetMapping("/rag-logs/page")
    public R<PageResponse<RagAuditLogResponse>> pageRagLogs(
            @Parameter(description = "当前页码") @RequestParam(defaultValue = "1") long current,
            @Parameter(description = "每页记录数") @RequestParam(defaultValue = "10") long size,
            @Parameter(description = "RAG 场景") @RequestParam(required = false) String scenario) {
        return R.ok(facade.pageRagLogs(current, size, scenario));
    }
}
