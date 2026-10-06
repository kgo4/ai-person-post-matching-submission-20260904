package com.example.matching.common.exception;

/**
 * 爬虫内部接收接口的协议级异常。
 * <p>
 * 与 {@link BusinessException} 分开的原因：爬虫侧依赖**精确的 HTTP 状态码 + 固定文案**
 * （对接文档 §3.4），且响应体必须是 {@code {code,message,data}} 信封而不是通用业务错误结构。
 * 本异常永远由内部接口处理，消息中不得包含任何密钥或密钥片段（文档 §7.6）。
 *
 * @author system
 */
public class CrawlerIngressException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int httpStatus;

    public CrawlerIngressException(int httpStatus, String message) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    /** 鉴权失败（未配置密钥 / 未携带 / 不匹配 / 签名不合法），文档 §3.4。 */
    public static CrawlerIngressException unauthorized() {
        return new CrawlerIngressException(401, "爬虫鉴权失败");
    }

    /** 参数或业务失败，message 需带 {@code items[i].xxx} 下标，文档 §3.4。 */
    public static CrawlerIngressException badRequest(String message) {
        return new CrawlerIngressException(400, message);
    }

    /** 请求体超过大小限制，文档 §7.3。 */
    public static CrawlerIngressException payloadTooLarge(String message) {
        return new CrawlerIngressException(413, message);
    }

    /** 服务器临时故障，文档 §3.4。 */
    public static CrawlerIngressException unavailable(String message) {
        return new CrawlerIngressException(503, message);
    }

    /** 接口已下线（如旧反向代理端点），返回 410 并引导改用采集命令。 */
    public static CrawlerIngressException gone(String message) {
        return new CrawlerIngressException(410, message);
    }
}
