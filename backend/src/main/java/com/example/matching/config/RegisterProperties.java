package com.example.matching.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 注册相关开关。
 *
 * <p>只放**非凭证类**的开关；角色授权码属敏感凭证，单独放在
 * {@link RegisterRoleCodeProperties}（从环境变量注入、无默认值）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "auth.register")
public class RegisterProperties {

    /**
     * 是否在「获取验证码」的响应里回传验证码。
     *
     * <p>**仅用于本地联调**：未配置邮件通道时，前端拿不到验证码就没法继续注册。
     * 生产环境必须保持 {@code false} —— 回传验证码等于验证环节失效。
     */
    private boolean codeExposeInResponse = false;
}
