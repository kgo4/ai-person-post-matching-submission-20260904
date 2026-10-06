package com.example.matching.dto.evolution.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;

/**
 * 内部爬虫接口的响应信封 {@code {code, message, data}}。
 * <p>
 * 为什么不复用全局 {@code R}：
 * <ol>
 *   <li>{@code R} 带 {@code @JsonInclude(NON_NULL)}，会**丢掉 {@code data: null}**，
 *       而对接文档 §3.4 的失败响应明确要求 {@code "data": null}；</li>
 *   <li>{@code R} 的 {@code code} 语义是业务码（含 10000+），而爬虫侧只按
 *       HTTP 状态码 + {@code code=200} 判断，需要「HTTP 状态码 == code」的严格一致。</li>
 * </ol>
 * 因此内部接口使用本信封，对外 {@code /api/**} 的响应结构完全不受影响。
 *
 * @author system
 */
@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
public class CrawlerApiResponse<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private int code;

    private String message;

    private T data;

    public static <T> CrawlerApiResponse<T> ok(T data) {
        return ok("导入完成", data);
    }

    public static <T> CrawlerApiResponse<T> ok(String message, T data) {
        CrawlerApiResponse<T> response = new CrawlerApiResponse<>();
        response.code = 200;
        response.message = message;
        response.data = data;
        return response;
    }

    public static <T> CrawlerApiResponse<T> fail(int code, String message) {
        CrawlerApiResponse<T> response = new CrawlerApiResponse<>();
        response.code = code;
        response.message = message;
        response.data = null;
        return response;
    }
}
