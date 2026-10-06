package com.example.matching.controller.evolution;

import com.example.matching.common.exception.CrawlerIngressException;
import com.example.matching.dto.evolution.api.CrawlerApiResponse;
import com.example.matching.dto.evolution.api.CrawlerMarketJdImportRequest;
import com.example.matching.dto.evolution.api.CrawlerMarketJdImportResponse;
import com.example.matching.service.evolution.crawler.CrawlerIngressGuard;
import com.example.matching.service.evolution.crawler.CrawlerMarketJdIngestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 独立爬虫推送市场 JD 的内部入口。
 * <p>
 * 契约依据 {@code crawler-system/docs/MAIN_SYSTEM_INTEGRATION.md}（2026-09-04）：
 * <ul>
 *   <li>唯一入站通道，爬虫**主动出站**调用；主系统不访问本地电脑（§1）；</li>
 *   <li>鉴权 {@code X-Crawler-Key}，可选 {@code X-Crawler-Timestamp} + {@code X-Crawler-Signature}（§3.1、§7.7）；</li>
 *   <li>成功 {@code code=200} + {@code data.imported/duplicate/updated}（§3.3）；失败 401/400/413/503
 *       一律返回 {@code {code,message,data}} 信封（§3.4）；</li>
 *   <li>请求体上限、单批 ≤100 条、请求 IP 与批次结果留痕（§7）。</li>
 * </ul>
 * <b>本 Controller 刻意不使用 {@code @RequestBody}/{@code @Valid}</b>：签名要对原始字节求摘要、
 * 大小限制要在读取时生效、校验文案必须带 {@code items[i]} 下标，这三点都由
 * {@link CrawlerIngressGuard} 与 {@code CrawlerImportValidator} 显式处理。
 *
 * @author system
 */
@Slf4j
@Tag(name = "爬虫推送入口", description = "独立爬虫主动推送市场 JD 的内部接口，仅服务间调用。")
@RestController
@RequestMapping("/api/internal/market-jd")
public class CrawlerMarketJdController {

    private final CrawlerMarketJdIngestService ingestService;
    private final CrawlerIngressGuard guard;

    public CrawlerMarketJdController(CrawlerMarketJdIngestService ingestService,
                                     CrawlerIngressGuard guard) {
        this.ingestService = ingestService;
        this.guard = guard;
    }

    @Operation(summary = "接收爬虫推送的市场JD批次",
            description = "校验 X-Crawler-Key（可选签名）、限制体积与条数，逐条幂等入库，"
                    + "并在有新增/变更时自动排队进入解析链路。")
    @PostMapping("/crawler-import")
    public CrawlerApiResponse<CrawlerMarketJdImportResponse> importFromCrawler(HttpServletRequest request) {
        long startedAt = System.currentTimeMillis();
        String clientIp = CrawlerIngressGuard.resolveClientIp(request);
        byte[] rawBody;
        try {
            rawBody = guard.readAuthorizedBody(request);
        } catch (CrawlerIngressException ingressException) {
            // 任何被拒的入站尝试都留痕（文档 §7.5），但绝不记录请求头与密钥。
            ingestService.recordRejectedIngress(null, null, clientIp, 0,
                    ingressException.getMessage(), ingressException.getHttpStatus(), elapsed(startedAt));
            log.warn("爬虫请求被拒: ip={}, status={}, reason={}",
                    clientIp, ingressException.getHttpStatus(), ingressException.getMessage());
            throw ingressException;
        }
        CrawlerMarketJdImportRequest payload = guard.parse(rawBody, CrawlerMarketJdImportRequest.class);
        CrawlerMarketJdIngestService.IngestReport report = ingestService.ingest(payload, clientIp);
        return CrawlerApiResponse.ok(report.response());
    }

    /** 协议级失败（401/400/413/503）统一返回信封，且 HTTP 状态码与 {@code code} 严格一致。 */
    @ExceptionHandler(CrawlerIngressException.class)
    public ResponseEntity<CrawlerApiResponse<Void>> handleIngressException(CrawlerIngressException exception) {
        return ResponseEntity.status(exception.getHttpStatus())
                .body(CrawlerApiResponse.fail(exception.getHttpStatus(), exception.getMessage()));
    }

    /**
     * 兜底：任何未预期异常都按文档 §3.4 返回 503 信封，避免爬虫拿到框架默认错误体而无法解析。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<CrawlerApiResponse<Void>> handleUnexpectedException(Exception exception) {
        log.error("爬虫接收接口未预期异常: type={}", exception.getClass().getSimpleName(), exception);
        return ResponseEntity.status(503).body(CrawlerApiResponse.fail(503, "服务暂时不可用"));
    }

    private static int elapsed(long startedAt) {
        return (int) Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - startedAt);
    }
}
