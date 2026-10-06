package com.example.matching.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 对外开放注册的角色授权码。
 *
 * <p><b>为什么授权码必须来自环境变量，而不是代码里的常量</b>：
 * {@code POST /api/system/user/register} 是 **permitAll**（未登录可访问）的开放端点。
 * 若把「注册成平台管理员」的授权码硬编码在仓库里，任何能看到源码（或反编译前端包）的人
 * 都能直接注册出一个平台管理员账号 —— 这等于给系统开了一个无需凭证的后门。
 * 因此本配置**不提供默认值**：未配置的角色一律不可自助注册。
 *
 * <p>约定：授权码按角色分别配置，环境变量形如
 * <pre>
 *   REGISTER_CODE_HR=xxx
 *   REGISTER_CODE_JOB_ARCHITECT=xxx
 *   REGISTER_CODE_PLATFORM_ADMIN=xxx
 * </pre>
 *
 * <p>EMPLOYEE（员工）**不需要授权码**，它是面向外部求职者的默认注册角色；
 * SUPER_ADMIN 属内部高权限角色，**不开放注册**（连授权码也不接受）。
 */
@Data
@Component
@Slf4j
@ConfigurationProperties(prefix = "auth.register")
public class RegisterRoleCodeProperties {

    /** 员工角色码：不需要授权码。 */
    public static final String ROLE_EMPLOYEE = "EMPLOYEE";
    /** HR 角色码：需要授权码。 */
    public static final String ROLE_HR = "HR_SPECIALIST";
    /** 岗位体系管理员角色码：需要授权码。 */
    public static final String ROLE_JOB_ARCHITECT = "JOB_ARCHITECT";
    /** 平台管理员角色码：需要授权码。 */
    public static final String ROLE_PLATFORM_ADMIN = "PLATFORM_ADMIN";

    /**
     * 角色码 → 授权码。
     *
     * <p>**不在**此表中的角色一律视为「不开放注册」——
     * 这是白名单语义：新增角色时忘了配置，结果是"不能注册"（安全），
     * 而不是"随便注册"（危险）。
     */
    private Map<String, String> roleCodes = new LinkedHashMap<>();

    /**
     * 允许自助注册的角色码集合（配置里出现且授权码非空的那些）。
     * 员工始终允许。
     */
    public boolean isRoleOpenForRegistration(String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            return false;
        }
        if (ROLE_EMPLOYEE.equalsIgnoreCase(roleCode.trim())) {
            return true;
        }
        String code = roleCodes.get(roleCode.trim().toUpperCase());
        return StringUtils.hasText(code);
    }

    /**
     * 该角色**在配置表里占位但授权码为空**（即管理员忘了配环境变量）。
     *
     * <p>为什么要单独判这一种：{@link #isRoleOpenForRegistration} 把
     * 「角色本就不开放注册（如 SUPER_ADMIN）」与「角色开放但没配授权码」
     * 归成同一个 false，调用方只能给出「该角色未开放自助注册」——
     * 而真实原因是**部署侧漏配环境变量**，看提示的人会去翻代码找为什么"不开放"，
     * 完全找错方向（实际踩过：`REGISTER_CODE_HR` 未配置，注册 HR 一直失败）。
     *
     * <p>⚠️ 这里**只影响提示文案**，不放宽任何校验：返回 true 依旧不能注册，
     * 只是告诉运维「去配环境变量」而不是「这个角色不开放」。
     * 也**不泄露授权码本身**，仅说明"这一项是空的"。
     */
    public boolean isRoleConfiguredButBlank(String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            return false;
        }
        String key = roleCode.trim().toUpperCase();
        // EMPLOYEE 本就不需要授权码；完全不在表里的角色（如 SUPER_ADMIN）不属此类。
        if (ROLE_EMPLOYEE.equals(key)) {
            return false;
        }
        return roleCodes.containsKey(key) && !StringUtils.hasText(roleCodes.get(key));
    }

    /** 已开放自助注册（授权码非空）的角色码，用于启动日志自检。 */
    public java.util.List<String> openRoleCodes() {
        return roleCodes.entrySet().stream()
                .filter(e -> StringUtils.hasText(e.getValue()))
                .map(java.util.Map.Entry::getKey)
                .sorted()
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 校验授权码。
     *
     * <p>员工不需要授权码，恒通过。其余角色必须命中配置，且**大小写敏感**
     * （授权码是凭证，不该容忍大小写差异带来的歧义）。
     *
     * <p>用 {@code equals} 而非 {@code contains}：避免"前缀对就算过"这类放大攻击面。
     */
    public boolean matchesCode(String roleCode, String providedCode) {
        if (ROLE_EMPLOYEE.equalsIgnoreCase(StringUtils.trimWhitespace(roleCode))) {
            return true;
        }
        if (!StringUtils.hasText(roleCode) || !StringUtils.hasText(providedCode)) {
            return false;
        }
        String expected = roleCodes.get(roleCode.trim().toUpperCase());
        return StringUtils.hasText(expected) && expected.equals(providedCode.trim());
    }

    /**
     * 启动自检：把「哪些角色能自助注册」打出来，缺配的给出明确告警。
     *
     * <p>为什么需要：授权码取自环境变量且**没有默认值**，漏配时服务照常启动，
     * 直到有人注册管理角色才报「该角色未开放自助注册」——
     * 排查方向容易跑偏去翻代码（实际踩过：`REGISTER_CODE_HR` 没配）。
     * 启动时一条日志就能把问题说清，代价接近零。
     *
     * <p>**只告警不阻断**：员工注册不依赖授权码，服务必须能正常起来。
     */
    @PostConstruct
    public void logRegistrationReadiness() {
        java.util.List<String> open = openRoleCodes();
        java.util.List<String> blank = roleCodes.entrySet().stream()
                .filter(e -> !StringUtils.hasText(e.getValue()))
                .map(java.util.Map.Entry::getKey)
                .sorted()
                .collect(java.util.stream.Collectors.toList());

        log.info("自助注册角色授权码状态：可注册={}，员工始终可注册（无需授权码），"
                + "SUPER_ADMIN 不开放注册", open.isEmpty() ? "仅 EMPLOYEE" : ("EMPLOYEE + " + open));
        if (!blank.isEmpty()) {
            log.warn("以下角色的授权码未配置，这些角色当前**无法自助注册**（注册页会提示"
                    + "「该角色的注册授权码未在服务端配置」）：{}。"
                    + "请配置对应环境变量后重启 —— REGISTER_CODE_HR / REGISTER_CODE_JOB_ARCHITECT"
                    + " / REGISTER_CODE_PLATFORM_ADMIN", blank);
        }
    }
}
