package com.example.matching.common.result;

import com.example.matching.common.exception.ErrorCodeEnum;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 统一响应结果封装
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class R<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private int code;

    private String message;

    private T data;

    private long timestamp;

    /** 错误上下文（仅异常响应时填充，用于调试和问题定位） */
    private Map<String, Object> errorDetail;

    private R() {
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.code = ErrorCodeEnum.SUCCESS.getCode();
        r.message = ErrorCodeEnum.SUCCESS.getMessage();
        r.data = data;
        return r;
    }

    public static <T> R<T> ok(String message, T data) {
        R<T> r = new R<>();
        r.code = ErrorCodeEnum.SUCCESS.getCode();
        r.message = message;
        r.data = data;
        return r;
    }

    public static <T> R<T> fail(int code, String message) {
        R<T> r = new R<>();
        r.code = code;
        r.message = message;
        return r;
    }

    public static <T> R<T> fail(ErrorCodeEnum errorCode) {
        R<T> r = new R<>();
        r.code = errorCode.getCode();
        r.message = errorCode.getMessage();
        return r;
    }

    public static <T> R<T> fail(String message) {
        R<T> r = new R<>();
        r.code = ErrorCodeEnum.INTERNAL_ERROR.getCode();
        r.message = message;
        return r;
    }

    public static <T> R<T> fail(int code, String message, Map<String, Object> errorDetail) {
        R<T> r = new R<>();
        r.code = code;
        r.message = message;
        r.errorDetail = errorDetail;
        return r;
    }
}
