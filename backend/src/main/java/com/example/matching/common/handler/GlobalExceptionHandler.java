package com.example.matching.common.handler;

import com.example.matching.common.exception.AiServiceException;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.common.exception.PermanentResumeParseException;
import com.example.matching.common.exception.RetryableResumeParseException;
import com.example.matching.common.result.R;
import com.example.matching.security.RateLimitExceededException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * <p>
 * 异常处理分层：
 * 1. 业务异常（BusinessException 及其子类）→ 根据 error code 返回对应 HTTP 状态码
 * 2. AI 服务异常（AiServiceException）→ 502/503，携带提供方和操作信息
 * 3. 简历解析异常（Permanent/RetryableResumeParseException）→ 422/503
 * 4. 参数校验异常 → 400
 * 5. 认证/授权异常 → 401/403
 * 6. 未知异常 → 500，记录完整堆栈
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RateLimitExceededException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public R<Void> handleRateLimitExceeded(RateLimitExceededException e) {
        return R.fail(HttpStatus.TOO_MANY_REQUESTS.value(), e.getMessage());
    }

    // ==================== 业务异常 ====================

    @ExceptionHandler(BusinessException.class)
    public R<Void> handleBusinessException(BusinessException e, jakarta.servlet.http.HttpServletResponse response) {
        if (e.getDetail() != null && !e.getDetail().isEmpty()) {
            log.warn("业务异常：code={}, message={}, detail={}", e.getCode(), e.getMessage(), e.getDetail(),
                    e.getCause() != null ? e.getCause() : null);
        } else {
            log.warn("业务异常：code={}, message={}", e.getCode(), e.getMessage(),
                    e.getCause() != null ? e.getCause() : null);
        }
        // HTTP 状态码对齐：标准语义码（400-599，如 404 资源不存在）映射到真实 HTTP 状态，
        // 业务码（10000+）保持 HTTP 200 + body 业务码，兼容现有客户端
        int code = e.getCode();
        if (code >= 400 && code < 600) {
            try {
                response.setStatus(code);
            } catch (Exception statusEx) {
                log.warn("Failed to set HTTP status {}: {}", code, statusEx.getMessage());
            }
        }
        return R.fail(e.getCode(), e.getMessage(), e.getDetail());
    }

    // ==================== AI 服务异常 ====================

    /**
     * AI 服务异常：不可重试 → 502 Bad Gateway，可重试 → 503 Service Unavailable
     */
    @ExceptionHandler(AiServiceException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public R<Void> handleAiServiceException(AiServiceException e) {
        log.warn("{}", e.toLogMessage(), e.getCause() != null ? e.getCause() : null);
        int httpStatus = e.isRetryable() ? HttpStatus.SERVICE_UNAVAILABLE.value() : HttpStatus.BAD_GATEWAY.value();
        return R.fail(httpStatus, e.getMessage(), e.getDetail());
    }

    // ==================== 简历解析异常 ====================

    /**
     * 简历解析永久失败 → 422 Unprocessable Entity（内容无法处理，不应重试）
     */
    @ExceptionHandler(PermanentResumeParseException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public R<Void> handlePermanentResumeParseException(PermanentResumeParseException e) {
        log.warn("简历解析永久失败：employeeId={}, filename={}, error={}",
                e.getEmployeeId(), e.getFileName(), e.getMessage());
        return R.fail(ErrorCodeEnum.AI_SERVICE_ERROR.getCode(),
                "简历解析失败：" + e.getMessage());
    }

    /**
     * 简历解析可重试失败 → 503 Service Unavailable（AI 暂时不可用）
     */
    @ExceptionHandler(RetryableResumeParseException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public R<Void> handleRetryableResumeParseException(RetryableResumeParseException e) {
        log.warn("简历解析可重试失败：employeeId={}, filename={}, retryCount={}, error={}",
                e.getEmployeeId(), e.getFileName(), e.getRetryCount(), e.getMessage());
        return R.fail(ErrorCodeEnum.AI_SERVICE_ERROR.getCode(),
                "简历解析暂时失败，系统将自动重试");
    }

    // ==================== 参数校验异常 ====================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        log.warn("参数校验异常：{}", message);
        return R.fail(ErrorCodeEnum.PARAM_ERROR.getCode(), message);
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleBindException(BindException e) {
        String message = e.getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数绑定失败");
        return R.fail(ErrorCodeEnum.PARAM_ERROR.getCode(), message);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleHandlerMethodValidationException(HandlerMethodValidationException e) {
        String message = e.getAllErrors().stream()
                .map(err -> err.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        return R.fail(ErrorCodeEnum.PARAM_ERROR.getCode(), message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleConstraintViolationException(ConstraintViolationException e) {
        return R.fail(ErrorCodeEnum.PARAM_ERROR.getCode(), e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleUnreadableMessage(HttpMessageNotReadableException e) {
        log.warn("请求体无法解析", e);
        return R.fail(ErrorCodeEnum.PARAM_ERROR.getCode(), "请求体格式错误");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleMissingParameter(MissingServletRequestParameterException e) {
        return R.fail(ErrorCodeEnum.PARAM_ERROR.getCode(), "缺少必填参数: " + e.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return R.fail(ErrorCodeEnum.PARAM_ERROR.getCode(), "参数类型错误: " + e.getName());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public R<Void> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        return R.fail(ErrorCodeEnum.PARAM_ERROR.getCode(), "上传文件超过大小限制");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public R<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return R.fail(ErrorCodeEnum.PARAM_ERROR.getCode(), "不支持的请求方法");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public R<Void> handleNoResource(NoResourceFoundException e) {
        return R.fail(ErrorCodeEnum.NOT_FOUND);
    }

    // ==================== 认证/授权异常 ====================

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public R<Void> handleAuthenticationException(AuthenticationException e) {
        log.warn("认证失败：{}", e.getMessage());
        return R.fail(ErrorCodeEnum.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public R<Void> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("权限不足：{}", e.getMessage());
        return R.fail(ErrorCodeEnum.FORBIDDEN);
    }

    // ==================== 兜底处理器 ====================

    /**
     * 其他未知异常 → 500
     * <p>
     * 注意：BusinessException 的子类（AiServiceException 等）会被各自的 handler 捕获，
     * 不会落到此处。此处只处理框架级异常和未分类的运行时异常。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleException(Exception e) {
        log.error("系统异常：type={}, message={}", e.getClass().getSimpleName(), e.getMessage(), e);
        return R.fail(ErrorCodeEnum.INTERNAL_ERROR);
    }
}
