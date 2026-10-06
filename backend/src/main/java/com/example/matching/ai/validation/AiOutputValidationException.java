package com.example.matching.ai.validation;

import lombok.Getter;

/**
 * AI 输出校验失败异常
 * <p>
 * 所有 AI 输出在写入业务数据前必须通过对应校验器；校验失败时抛出本异常，
 * 禁止写库。场景（scenario）、字段（field）和失败原因（reason）用于日志定位。
 * <p>
 * 处理约定：
 * <ul>
 *   <li>同步接口：由调用方捕获后走确定性降级，返回明确的 degraded 结构</li>
 *   <li>异步任务：捕获后记为 FAILED，错误类型 AI_OUTPUT_INVALID</li>
 * </ul>
 */
@Getter
public class AiOutputValidationException extends RuntimeException {

    /** 场景：如 INTERVIEW_ANSWER_QUALITY / AI_TEST_QUESTION_SET */
    private final String scenario;

    /** 校验失败的字段路径，如 starCompleteness.result */
    private final String field;

    /** 失败原因 */
    private final String reason;

    public AiOutputValidationException(String scenario, String field, String reason) {
        super("AI输出校验失败 scenario=" + scenario + ", field=" + field + ", reason=" + reason);
        this.scenario = scenario;
        this.field = field;
        this.reason = reason;
    }

    public AiOutputValidationException(String scenario, String field, String reason, Throwable cause) {
        super("AI输出校验失败 scenario=" + scenario + ", field=" + field + ", reason=" + reason, cause);
        this.scenario = scenario;
        this.field = field;
        this.reason = reason;
    }
}
