package com.example.matching.dto.system;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/** 获取注册邮箱验证码的请求。 */
@Data
@Schema(description = "获取注册邮箱验证码请求")
public class RegisterEmailCodeRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "请输入正确的邮箱格式")
    @Schema(description = "接收验证码的邮箱", example = "zhangsan@company.com")
    private String email;
}
