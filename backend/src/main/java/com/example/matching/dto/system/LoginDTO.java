package com.example.matching.dto.system;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 登录请求DTO
 */
@Data
@Schema(description = "登录请求，包含用户名和密码，用于系统用户身份认证")
public class LoginDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "用户名不能为空")
    @Schema(description = "用户名，用于登录认证的唯一标识，通常为系统注册的账号名称", example = "admin")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Schema(description = "密码，与用户名配对的登录凭证，前端应传输明文，后端进行加密校验", example = "123456")
    private String password;
}
