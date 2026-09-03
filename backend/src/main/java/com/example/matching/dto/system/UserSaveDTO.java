package com.example.matching.dto.system;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户保存请求DTO
 */
@Data
@Schema(description = "用户保存请求，用于新增或更新系统用户信息，包括基本信息、联系方式及部门归属")
public class UserSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "用户ID，更新时必传，新增时不传（留空由后端自动生成）", example = "1")
    private Long id;

    @NotBlank(message = "用户名不能为空")
    @Schema(description = "用户名，登录账号的唯一标识，新增时不可与已有用户名重复", example = "zhangsan")
    private String username;

    @Schema(description = "密码，新增用户时必填，更新时留空表示不修改密码，存储前会经过BCrypt加密", example = "Abc@12345")
    private String password;

    @NotBlank(message = "真实姓名不能为空")
    @Schema(description = "用户真实姓名，用于系统内显示和业务记录", example = "张三")
    private String realName;

    @Schema(description = "手机号，用于接收系统通知和验证", example = "13800138000")
    private String phone;

    @Schema(description = "邮箱地址，用于接收邮件通知和密码找回", example = "zhangsan@company.com")
    private String email;

    @Schema(description = "所属部门ID，关联系统部门表的主键，用于限定数据可见范围", example = "101")
    private Long departmentId;

    @Schema(description = "用户状态：0-禁用（无法登录），1-启用（正常使用）", example = "1")
    private Integer status;
}
