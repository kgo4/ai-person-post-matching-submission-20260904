package com.example.matching.controller.evolution;

import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.CrawlerIngressException;
import com.example.matching.dto.evolution.api.CrawlerApiResponse;
import com.example.matching.dto.evolution.api.CrawlerCommandView;
import com.example.matching.dto.evolution.api.CrawlerHeartbeatRequest;
import com.example.matching.dto.evolution.api.CrawlerTaskResultRequest;
import com.example.matching.service.evolution.crawler.CrawlerAgentService;
import com.example.matching.service.evolution.crawler.CrawlerCommandService;
import com.example.matching.service.evolution.crawler.CrawlerIngressGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地爬虫轮询用的内部接口。
 * <p>
 * 依据爬虫对接文档 §9（第二阶段）设计：主系统**不反向访问本地电脑**，改为
 * 「服务器创建命令 → 本地爬虫每 10–30 秒轮询领取 → 本地执行 → 本地推送 JD」。
 * <p>
 * 鉴权与推送入口一致：请求头 {@code X-Crawler-Key: <MAIN_SYSTEM_TOKEN>}；
 * 未配置密钥时一律 401。响应统一使用爬虫信封 {@link CrawlerApiResponse}，
 * 保证爬虫侧只需一套解析逻辑。
 * <p>
 * <b>注意</b>：抓取到的 JD 不经过本控制器，仍走
 * {@code POST /api/internal/market-jd/crawler-import}。
 *
 * @author system
 */
@Slf4j
@Tag(name = "爬虫内部接口", description = "本地爬虫轮询领取命令、上报心跳与执行结果。")
@RestController
@RequestMapping("/api/internal/crawler")
@RequiredArgsConstructor
public class CrawlerInternalController {

    private final CrawlerIngressGuard guard;
    private final CrawlerCommandService commandService;
    private final CrawlerAgentService agentService;

    @Operation(summary = "领取待执行命令",
            description = "原子领取（PENDING → DISPATCHED），多实例并发时同一条命令只会被一个实例拿到；"
                    + "返回 data 为命令数组。")
    @GetMapping("/commands")
    public CrawlerApiResponse<List<CrawlerCommandView>> claimCommands(
            HttpServletRequest request,
            @Parameter(description = "爬虫实例标识") @RequestParam String agentId,
            @Parameter(description = "本次最多领取条数，默认 1，最大 10") @RequestParam(defaultValue = "1") int limit) {
        guard.assertAuthorized(request);
        if (agentId == null || agentId.isBlank()) {
            throw CrawlerIngressException.badRequest("agentId 不能为空");
        }
        List<CrawlerCommandView> commands = commandService.claim(agentId.trim(), limit);
        return CrawlerApiResponse.ok("已返回 " + commands.size() + " 条命令", commands);
    }

    @Operation(summary = "上报心跳", description = "upsert crawler_agent，主系统据此判断本地爬虫是否在线。")
    @PostMapping("/heartbeat")
    public CrawlerApiResponse<Map<String, Object>> heartbeat(HttpServletRequest request,
                                                             @RequestBody CrawlerHeartbeatRequest body) {
        guard.assertAuthorized(request);
        if (body == null || body.getAgentId() == null || body.getAgentId().isBlank()) {
            throw CrawlerIngressException.badRequest("agentId 不能为空");
        }
        boolean firstSeen = agentService.heartbeat(body);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("agentId", body.getAgentId());
        data.put("registered", firstSeen);
        data.put("serverTime", java.time.LocalDateTime.now().toString());
        return CrawlerApiResponse.ok("心跳已记录", data);
    }

    @Operation(summary = "上报命令执行结果",
            description = "status 为 RUNNING / SUCCEEDED / FAILED；重复上报幂等，不会报错。")
    @PostMapping("/task-result")
    public CrawlerApiResponse<CrawlerCommandView> taskResult(HttpServletRequest request,
                                                             @RequestBody CrawlerTaskResultRequest body) {
        guard.assertAuthorized(request);
        if (body == null || body.getCommandId() == null || body.getCommandId().isBlank()) {
            throw CrawlerIngressException.badRequest("commandId 不能为空");
        }
        if (body.getAgentId() == null || body.getAgentId().isBlank()) {
            throw CrawlerIngressException.badRequest("agentId 不能为空");
        }
        CrawlerCommandView view = commandService.applyTaskResult(body);
        return CrawlerApiResponse.ok("命令状态已更新", view);
    }

    // ===== 统一信封：HTTP 状态码与 code 严格一致 =====

    @ExceptionHandler(CrawlerIngressException.class)
    public ResponseEntity<CrawlerApiResponse<Void>> handleIngressException(CrawlerIngressException exception) {
        return ResponseEntity.status(exception.getHttpStatus())
                .body(CrawlerApiResponse.fail(exception.getHttpStatus(), exception.getMessage()));
    }

    /**
     * 业务异常（如命令不存在 404、状态冲突 409）也按爬虫信封返回，
     * 避免爬虫拿到与推送接口不一致的结构而无法解析。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<CrawlerApiResponse<Void>> handleBusinessException(BusinessException exception) {
        int code = exception.getCode();
        int httpStatus = code >= 400 && code < 600 ? code : 400;
        return ResponseEntity.status(httpStatus)
                .body(CrawlerApiResponse.fail(httpStatus, exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CrawlerApiResponse<Void>> handleUnexpectedException(Exception exception) {
        log.error("爬虫内部接口未预期异常: type={}", exception.getClass().getSimpleName(), exception);
        return ResponseEntity.status(503).body(CrawlerApiResponse.fail(503, "服务暂时不可用"));
    }
}


