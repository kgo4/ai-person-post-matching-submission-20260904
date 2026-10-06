package com.example.matching.controller.evolution;

import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.common.result.R;
import com.example.matching.config.CrawlerSystemProperties;
import com.example.matching.dto.evolution.api.CrawlerTriggerRequest;
import com.example.matching.integration.crawler.CrawlerSystemClient;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 【过渡兼容】爬虫任务反向代理接口。
 * <p>
 * <b>默认已下线</b>。爬虫对接文档 {@code MAIN_SYSTEM_INTEGRATION.md} §1.1 明确
 * 「主系统不能、也不需要访问本地电脑的 8081 端口」，因此这些把请求转发到本地爬虫的端点
 * 受 {@code crawler-system.legacy-proxy-enabled}（默认 {@code false}）门禁；
 * 关闭时统一返回 {@code 410 GONE}，并提示改用「采集命令 + 本地爬虫轮询」。
 * <p>
 * 仅当本地爬虫尚未实现命令轮询时，临时把开关置 {@code true} 过渡。新部署请保持关闭，
 * 以满足上游验收项 6「主系统全程不需要访问本地电脑地址」。
 *
 * @see com.example.matching.controller.evolution.CrawlerCommandController 采集命令（推荐路径）
 * @author system
 */
@Tag(name = "爬虫采集（已下线）", description = "过渡期反向代理，默认返回 410；请使用采集命令接口。")
@RestController
@RequestMapping("/api/post/evolution/crawler")
@RequiredArgsConstructor
public class CrawlerTaskProxyController {

    private static final String DOWN_MESSAGE =
            "该接口已下线：主系统不再反向访问本地爬虫。请使用采集命令（管理端创建命令，本地爬虫主动轮询领取）";

    private final CrawlerSystemClient crawlerSystemClient;
    private final CrawlerSystemProperties crawlerSystemProperties;

    @Operation(summary = "触发爬取（已下线）", description = "默认返回 410；请改用 POST /commands 下发采集命令。")
    @PostMapping("/trigger")
    public R<JsonNode> trigger(@Valid @RequestBody CrawlerTriggerRequest request) {
        assertLegacyProxyEnabled();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sources", request.getSources());
        payload.put("keywords", request.getKeywords());
        payload.put("cities", request.getCities());
        payload.put("maxItems", request.getMaxItems());
        return R.ok(crawlerSystemClient.triggerCrawl(payload));
    }

    @Operation(summary = "查询爬虫任务状态（已下线）", description = "默认返回 410；请改用 GET /commands/{commandId}。")
    @GetMapping("/task/{taskId}")
    public R<JsonNode> taskStatus(
            @Parameter(description = "爬虫任务ID") @PathVariable String taskId) {
        assertLegacyProxyEnabled();
        return R.ok(crawlerSystemClient.getTaskStatus(taskId));
    }

    @Operation(summary = "重新推送待推送数据（已下线）",
            description = "默认返回 410；重新推送由本地爬虫自行重试，主系统按幂等接收。")
    @PostMapping("/task/{taskId}/retry-push")
    public R<JsonNode> retryPush(
            @Parameter(description = "爬虫任务ID") @PathVariable String taskId) {
        assertLegacyProxyEnabled();
        return R.ok(crawlerSystemClient.retryPush(taskId));
    }

    private void assertLegacyProxyEnabled() {
        if (!crawlerSystemProperties.isLegacyProxyEnabled()) {
            throw new BusinessException(ErrorCodeEnum.GONE, DOWN_MESSAGE);
        }
        if (!crawlerSystemProperties.isAvailable()) {
            throw new BusinessException(ErrorCodeEnum.INTERNAL_ERROR,
                    "过渡期反向代理已启用，但未配置 crawler-system.base-url，无法转发");
        }
    }
}
