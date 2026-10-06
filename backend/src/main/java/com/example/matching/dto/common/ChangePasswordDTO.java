package com.example.matching.dto.common;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 修改密码请求DTO
 */
@Data
@Schema(description = "修改密码请求")
public class ChangePasswordDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "旧密码不能为空")
    @Schema(description = "旧密码", required = true)
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Schema(description = "新密码", required = true)
    private String newPassword;
}
