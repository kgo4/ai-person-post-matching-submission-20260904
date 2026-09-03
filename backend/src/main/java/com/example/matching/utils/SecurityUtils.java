package com.example.matching.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全工具类
 * <p>
 * 基于 ThreadLocal 存储当前请求的用户信息，同时兼容 Spring Security Context
 */
@Slf4j
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static final String TOKEN_HEADER = "Authorization";
    public static final String TOKEN_PREFIX = "Bearer ";

    private static final ThreadLocal<Long> USER_ID_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> USERNAME_HOLDER = new ThreadLocal<>();

    /** 设置当前用户ID（由 JWT Filter 调用） */
    public static void setCurrentUserId(Long userId) {
        USER_ID_HOLDER.set(userId);
    }

    /** 设置当前用户名 */
    public static void setCurrentUsername(String username) {
        USERNAME_HOLDER.set(username);
    }

    /**
     * 获取当前登录用户ID。
     * <p>
     * 优先从 ThreadLocal 获取（由 JwtFilter 设置），回退到 SecurityContext。
     * 注意：SecurityContext 中的 username 可能是字符串（如 "admin"），
     * 只有当 username 为纯数字时才作为 userId 返回，否则返回 null。
     *
     * @return 当前用户ID，未登录或无法解析时返回 null
     */
    public static Long getCurrentUserId() {
        Long userId = USER_ID_HOLDER.get();
        if (userId != null) {
            return userId;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof org.springframework.security.core.userdetails.User user) {
            String username = user.getUsername();
            if (username != null) {
                Long parsed = tryParseLong(username);
                if (parsed != null) {
                    return parsed;
                }
                log.debug("SecurityContext username '{}' 不是数字ID，无法解析为 userId", username);
            }
        }
        return null;
    }

    /** 获取当前登录用户名 */
    public static String getCurrentUsername() {
        String username = USERNAME_HOLDER.get();
        if (username == null) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null) {
                return auth.getName();
            }
        }
        return username != null ? username : "system";
    }

    /** 清除 ThreadLocal */
    public static void clear() {
        USER_ID_HOLDER.remove();
        USERNAME_HOLDER.remove();
    }

    /**
     * 以系统身份设置当前线程上下文（用于 @Scheduled / RabbitListener / 无用户上下文的异步任务）。
     * 调用方应在 finally 中调用 {@link #clear()} 清理。
     */
    public static void setSystemContext() {
        setCurrentUserId(0L);
        setCurrentUsername("system");
    }

    private static Long tryParseLong(String str) {
        try {
            return Long.parseLong(str);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
