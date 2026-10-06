package com.example.matching.dto.system;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

/**
 * 自助注册请求。
 *
 * <p>与「管理员新增用户」的 {@link UserSaveDTO} 分开，是因为两者规则不同：
 * 注册是**未登录**的开放入口，必须自带验证码与角色授权码校验；
 * 而管理员新增走的是后台权限，不该被"注册"的冷却、授权码等规则牵连。
 *
 * <p>校验点都写在这里（而不是只靠前端）：前端校验只是体验，服务端才是边界。
 */
@Data
@Schema(description = "自助注册请求")
public class RegisterRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_]{3,20}$", message = "用户名需为 3-20 位字母、数字或下划线")
    @Schema(description = "登录用户名", example = "zhangsan")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Pattern(regexp = "^\\S{6,32}$", message = "密码需为 6-32 位且不含空格")
    @Schema(description = "登录密码", example = "Abc@12345")
    private String password;

    @NotBlank(message = "真实姓名不能为空")
    @Schema(description = "真实姓名", example = "张三")
    private String realName;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入正确的 11 位手机号")
    @Schema(description = "手机号", example = "13800138000")
    private String phone;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "请输入正确的邮箱格式")
    @Schema(description = "邮箱，用于接收注册验证码", example = "zhangsan@company.com")
    private String email;

    @NotBlank(message = "邮箱验证码不能为空")
    @Schema(description = "邮箱验证码，6 位数字", example = "123456")
    private String emailCode;

    /**
     * 期望注册的角色码。
     *
     * <p>只接受 {@code EMPLOYEE} / {@code HR_SPECIALIST} / {@code JOB_ARCHITECT} /
     * {@code PLATFORM_ADMIN}；**SUPER_ADMIN 一律拒绝**（不开放注册）。
     * 留空按 {@code EMPLOYEE} 处理，兼容老的注册调用方。
     */
    @Schema(description = "注册角色码；留空默认 EMPLOYEE", example = "HR_SPECIALIST")
    private String roleCode;

    /**
     * 角色授权码。
     *
     * <p>除 EMPLOYEE 外**必填**，用于防止任意人自助注册成管理角色。
     * 校验规则见 {@code RegisterRoleCodeProperties}。不在日志与响应里回显。
     */
    @Schema(description = "角色授权码（EMPLOYEE 无需填写）", example = "****")
    private String roleAuthCode;
}
