package com.example.matching.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 爬虫接收通道配置。
 * <p>
 * 前缀：{@code market-jd.crawler}，对应爬虫侧文档 {@code MAIN_SYSTEM_INTEGRATION.md}：
 * <ul>
 *   <li>{@code apiKey} ⇔ 爬虫侧 {@code MAIN_SYSTEM_TOKEN}（文档 §3.1）；</li>
 *   <li>{@code maxBodyBytes} ⇔ 文档 §7.3 请求体大小限制；</li>
 *   <li>{@code maxItems} ⇔ 文档 §7.4 单批条数限制；</li>
 *   <li>{@code signatureEnabled} / {@code sharedSecret} ⇔ 文档 §7.7 时间戳 + HMAC 签名增强。</li>
 * </ul>
 * api-key 留空时内部接收接口一律拒绝（401），但**不影响主系统启动**——爬虫是可选外部系统。
 *
 * @author system
 */
@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "market-jd.crawler")
public class MarketJdCrawlerProperties {

    /** 与爬虫共享的静态密钥；留空表示未接入爬虫，接收接口一律拒绝 */
    private String apiKey = "";

    /** 是否强制校验 X-Crawler-Timestamp + X-Crawler-Signature（文档 §7.7 推荐增强） */
    private boolean signatureEnabled = false;

    /** HMAC-SHA256 共享密钥；signatureEnabled=true 时必填 */
    private String sharedSecret = "";

    /** 时间戳允许的偏移秒数（双向） */
    private long signatureToleranceSeconds = 300L;

    /** 请求体上限（字节），文档 §7.3 建议 5–10 MB */
    private long maxBodyBytes = 10L * 1024 * 1024;

    /** 单批岗位数上限，文档 §7.4 建议不超过 100 */
    private int maxItems = 100;

    /**
     * 接收入库后是否自动触发既有解析链路（治理 → 去重 → 能力提取 → 准入）。
     * <p>
     * <b>默认 false（2026-09-04 起）</b>：爬虫按计划自行推送，自动解析会在无人值守时消耗
     * LLM 配额，且解析结论直接写库（能力标签 / 准入），运营无法先审后跑。改为默认**手动**，
     * 由「市场 JD 采集」页的解析开关或批次行上的「解析」按钮显式触发。
     * <p>
     * 这里只是**启动默认值**；运行期可被 {@code MarketJdAutoAnalyzeSwitch} 临时覆盖（重启回落）。
     */
    private boolean autoAnalyzeEnabled = false;

    /** 是否在启动后回填存量行的 content_hash（避免首次重推被误判为正文变化） */
    private boolean contentHashBackfillEnabled = true;

    /** 回填分批大小 */
    private int contentHashBackfillBatchSize = 500;

    /** 回填单次运行最多处理行数（防止启动后长时间占用 IO） */
    private int contentHashBackfillMaxRows = 20000;

    /** 本地爬虫心跳多久未上报即视为离线（秒） */
    private int agentOnlineThresholdSeconds = 90;

    /** 采集命令未在多久内被领取则自动过期（秒） */
    private int commandExpireSeconds = 1800;

    /** 未配置 api-key 时为 true，接收接口一律拒绝 */
    public boolean isApiKeyConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** 签名密钥是否可用 */
    public boolean isSharedSecretConfigured() {
        return sharedSecret != null && !sharedSecret.isBlank();
    }

    /** 仅供日志使用的密钥指纹，绝不输出密钥本身（文档 §7.6）。 */
    public String apiKeyFingerprint() {
        if (!isApiKeyConfigured()) {
            return "none";
        }
        return sha256Hex(apiKey).substring(0, 8);
    }

    /**
     * 极简 SHA-256 十六进制实现。
     * <p>
     * 之所以在这里内联而不是复用业务层工具：{@code config} 层不得依赖业务 {@code service} 层
     * （见 {@code ArchitectureRulesTest#platformMustNotDependOnBusinessModules}）。
     * 该方法仅用于生成日志指纹，不承载任何业务语义。
     */
    private static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JVM 强制要求的算法，正常环境不可能走到这里
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
        }
    }

    @PostConstruct
    public void logConfigurationState() {
        if (maxItems < 1) {
            throw new IllegalStateException("market-jd.crawler.max-items 必须 >= 1，当前: " + maxItems);
        }
        if (maxBodyBytes < 1024) {
            throw new IllegalStateException("market-jd.crawler.max-body-bytes 必须 >= 1024，当前: " + maxBodyBytes);
        }
        if (contentHashBackfillBatchSize < 1) {
            throw new IllegalStateException(
                    "market-jd.crawler.content-hash-backfill-batch-size 必须 >= 1，当前: " + contentHashBackfillBatchSize);
        }
        if (agentOnlineThresholdSeconds < 10) {
            throw new IllegalStateException(
                    "market-jd.crawler.agent-online-threshold-seconds 必须 >= 10，当前: " + agentOnlineThresholdSeconds);
        }
        if (signatureEnabled && !isSharedSecretConfigured()) {
            log.warn("market-jd.crawler.signature-enabled=true 但未配置 shared-secret，"
                    + "内部接收接口将一律返回 401。请配置 MARKET_JD_CRAWLER_SHARED_SECRET 或关闭签名校验。");
        }
        if (!isApiKeyConfigured()) {
            log.warn("爬虫接收通道未接入：未配置 MARKET_JD_CRAWLER_API_KEY，"
                    + "/api/internal/market-jd/** 一律返回 401（主系统启动与其它功能不受影响）。");
        } else {
            log.info("爬虫接收通道已接入：apiKeyFingerprint={}, maxItems={}, maxBodyBytes={}, "
                            + "signatureEnabled={}, autoAnalyzeEnabled={}",
                    apiKeyFingerprint(), maxItems, maxBodyBytes, signatureEnabled, autoAnalyzeEnabled);
        }
    }
}
