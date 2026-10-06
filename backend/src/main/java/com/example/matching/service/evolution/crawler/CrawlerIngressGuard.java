package com.example.matching.service.evolution.crawler;

import com.example.matching.common.exception.CrawlerIngressException;
import com.example.matching.config.MarketJdCrawlerProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * 内部爬虫接口的入站守卫：鉴权 + 有界读取请求体 + JSON 解析。
 * <p>
 * 为什么不用 Spring 的 {@code @RequestBody} + {@code @Valid}：
 * <ol>
 *   <li>HMAC 签名（文档 §7.7）必须对**原始请求体字节**求摘要，框架反序列化后拿不到原字节；</li>
 *   <li>请求体大小限制（文档 §7.3）需要在我们自己读取时生效，Tomcat 对 JSON 请求体没有默认上限；</li>
 *   <li>校验消息必须是 {@code items[i].xxx} 形式（文档 §3.4），由
 *       {@link CrawlerImportValidator} 统一产出，不能让 Bean Validation 抢先报出另一套文案。</li>
 * </ol>
 * 因此这里显式读取、显式校验。失败一律返回「爬虫鉴权失败」，绝不暴露是哪一步不通过，
 * 也绝不把密钥写进日志或异常（文档 §7.6）。
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlerIngressGuard {

    public static final String HEADER_KEY = "X-Crawler-Key";
    public static final String HEADER_TIMESTAMP = "X-Crawler-Timestamp";
    public static final String HEADER_SIGNATURE = "X-Crawler-Signature";

    private static final int READ_BUFFER_SIZE = 8192;

    private final MarketJdCrawlerProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * 校验鉴权并读取请求体。
     *
     * @return 原始请求体字节（长度不超过 {@code market-jd.crawler.max-body-bytes}）
     * @throws CrawlerIngressException 401（鉴权失败）或 413（请求体超限）
     */
    public byte[] readAuthorizedBody(HttpServletRequest request) {
        assertApiKey(request.getHeader(HEADER_KEY));
        byte[] rawBody = readBoundedBody(request);
        if (properties.isSignatureEnabled()) {
            assertSignature(request.getHeader(HEADER_TIMESTAMP), request.getHeader(HEADER_SIGNATURE), rawBody);
        }
        return rawBody;
    }

    /** 反序列化请求体；失败按文档 §3.4 归为 400。 */
    public <T> T parse(byte[] rawBody, Class<T> type) {
        try {
            return objectMapper.readValue(rawBody, type);
        } catch (Exception exception) {
            throw CrawlerIngressException.badRequest("请求体格式错误：无法解析为 JSON");
        }
    }

    /**
     * 只校验 {@code X-Crawler-Key}，不读取请求体。
     * <p>
     * 供使用 {@code @RequestBody} 的内部端点（命令领取 / 心跳 / 任务结果）使用——
     * 这些端点不需要对原始字节做 HMAC 校验，因此不能调用 {@link #readAuthorizedBody}，
     * 否则请求流会被提前消费，框架拿不到 body。
     */
    public void assertAuthorized(HttpServletRequest request) {
        assertApiKey(request.getHeader(HEADER_KEY));
    }

    /**
     * 调用方 IP：部署在 Nginx/网关之后时取 {@code X-Forwarded-For} 首个地址，否则取远端地址。
     * 仅用于日志留痕，不参与任何鉴权判断。
     */
    public static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            String first = comma > 0 ? forwarded.substring(0, comma) : forwarded;
            return trimTo(first, 64);
        }
        return trimTo(request.getRemoteAddr(), 64);
    }

    // ===== 鉴权 =====

    /** 常量时间比较，避免时序侧信道；密钥未配置时一律拒绝（不静默放行）。 */
    private void assertApiKey(String providedKey) {
        if (!properties.isApiKeyConfigured() || providedKey == null) {
            throw CrawlerIngressException.unauthorized();
        }
        boolean matched = MessageDigest.isEqual(
                properties.getApiKey().getBytes(StandardCharsets.UTF_8),
                providedKey.getBytes(StandardCharsets.UTF_8));
        if (!matched) {
            throw CrawlerIngressException.unauthorized();
        }
    }

    /**
     * 校验 {@code X-Crawler-Timestamp} + {@code X-Crawler-Signature}。
     * <p>签名口径与对接文档 §7.7 一致：{@code HMAC-SHA256(timestamp + "." + rawBody, sharedSecret)}。
     */
    private void assertSignature(String timestampHeader, String signatureHeader, byte[] rawBody) {
        if (!properties.isSharedSecretConfigured() || timestampHeader == null || signatureHeader == null) {
            throw CrawlerIngressException.unauthorized();
        }
        String timestamp = timestampHeader.trim();
        long epochSeconds;
        try {
            epochSeconds = Long.parseLong(timestamp);
        } catch (NumberFormatException exception) {
            throw CrawlerIngressException.unauthorized();
        }
        long nowSeconds = System.currentTimeMillis() / 1000L;
        if (Math.abs(nowSeconds - epochSeconds) > properties.getSignatureToleranceSeconds()) {
            throw CrawlerIngressException.unauthorized();
        }
        String payload = timestamp + "." + new String(rawBody, StandardCharsets.UTF_8);
        String expected = hmacSha256Hex(payload, properties.getSharedSecret());
        boolean matched = MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signatureHeader.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
        if (!matched) {
            throw CrawlerIngressException.unauthorized();
        }
    }

    private static String hmacSha256Hex(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw CrawlerIngressException.unavailable("服务暂时不可用");
        }
    }

    // ===== 有界读取 =====

    private byte[] readBoundedBody(HttpServletRequest request) {
        long maxBytes = properties.getMaxBodyBytes();
        try (InputStream inputStream = request.getInputStream()) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[READ_BUFFER_SIZE];
            long total = 0L;
            int read;
            while ((read = inputStream.read(chunk)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw CrawlerIngressException.payloadTooLarge(
                            "请求体超过 " + (maxBytes / 1024 / 1024) + " MB 限制");
                }
                buffer.write(chunk, 0, read);
            }
            return buffer.toByteArray();
        } catch (CrawlerIngressException ingressException) {
            throw ingressException;
        } catch (IOException ioException) {
            log.warn("读取爬虫请求体失败: error={}", ioException.getMessage());
            throw CrawlerIngressException.badRequest("读取请求体失败");
        }
    }

    private static String trimTo(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
