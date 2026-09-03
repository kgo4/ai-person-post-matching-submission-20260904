package com.example.matching.common.aspect;

import com.example.matching.entity.system.SysOperationLog;
import com.example.matching.mapper.system.SysOperationLogMapper;
import com.example.matching.utils.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 操作日志 AOP 切面
 * <p>
 * 自动记录 Controller 层的增删改操作
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final SysOperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper;
    private final OperationLogRedactor redactor;

    /** 切点：所有非 GET 请求 */
    @Pointcut("execution(* com.example.matching.controller..*.*(..)) && (@annotation(org.springframework.web.bind.annotation.PostMapping) || @annotation(org.springframework.web.bind.annotation.PutMapping) || @annotation(org.springframework.web.bind.annotation.DeleteMapping))")
    public void operationLogPointcut() {
    }

    @Around("operationLogPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        SysOperationLog logEntity = buildBaseLog();
        Object result = null;

        try {
            // 记录请求参数（敏感端点不记录 body）
            if (redactor.isSensitiveEndpoint(logEntity.getRequestUrl())) {
                logEntity.setRequestParams("[body omitted: sensitive endpoint]");
            } else {
                logEntity.setRequestParams(truncate(redactor.redactRequestBody(joinPoint.getArgs())));
            }
            result = joinPoint.proceed();
            // 记录响应结果
            if (redactor.isSensitiveEndpoint(logEntity.getRequestUrl())) {
                logEntity.setResponseResult("[body omitted: sensitive endpoint]");
            } else if (shouldOmitResponseBody(logEntity.getRequestUrl())) {
                logEntity.setResponseResult("[response omitted]");
            } else {
                logEntity.setResponseResult(truncate(redactor.redactResponseBody(result)));
            }
        } catch (Throwable e) {
            logEntity.setResponseResult("异常: " + e.getMessage());
            throw e;
        } finally {
            logEntity.setCostTime(System.currentTimeMillis() - startTime);
            try {
                operationLogMapper.insert(logEntity);
            } catch (Exception e) {
                log.error("保存操作日志失败", e);
            }
        }
        return result;
    }

    private SysOperationLog buildBaseLog() {
        SysOperationLog log = new SysOperationLog();
        Long userId = Optional.ofNullable(SecurityUtils.getCurrentUserId()).orElse(0L);
        log.setUserId(userId);
        log.setRealName(Optional.ofNullable(SecurityUtils.getCurrentUsername()).orElse("system"));

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            log.setRequestMethod(request.getMethod());
            log.setRequestUrl(request.getRequestURI());
            log.setOperationIp(getClientIp(request));
        }

        log.setOperationTime(LocalDateTime.now());
        log.setOperationModule("system");
        log.setOperationType("UNKNOWN");
        log.setOperationDesc("自动记录");
        return log;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    /**
     * 将方法参数中的 MultipartFile 替换为可序列化的摘要信息
     */
    private Object[] sanitizeArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return args;
        }
        Object[] sanitized = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof MultipartFile file) {
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("fileName", file.getOriginalFilename());
                summary.put("size", file.getSize());
                summary.put("contentType", file.getContentType());
                sanitized[i] = summary;
            } else {
                sanitized[i] = args[i];
            }
        }
        return sanitized;
    }

    private String truncate(String text) {
        if (text != null && text.length() > 4000) {
            return text.substring(0, 4000) + "...";
        }
        return text;
    }

    private boolean shouldOmitResponseBody(String requestUrl) {
        return requestUrl != null && requestUrl.startsWith("/api/matching/recommend/");
    }
}
