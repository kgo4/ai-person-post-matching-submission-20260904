package com.example.matching.dto.system;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 角色保存请求DTO
 */
@Data
@Schema(description = "角色保存请求，用于新增或更新系统角色信息，包含角色编码、名称、数据权限范围等配置")
public class RoleSaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "角色ID，更新时必传，新增时不传（留空由后端自动生成）", example = "1")
    private Long id;

    @NotBlank(message = "角色编码不能为空")
    @Schema(description = "角色唯一编码，用于程序内部权限判断，建议使用大写英文加下划线格式", example = "ROLE_ADMIN")
    private String roleCode;

    @NotBlank(message = "角色名称不能为空")
    @Schema(description = "角色名称，用于前端页面显示和角色选择", example = "系统管理员")
    private String roleName;

    @Schema(description = "角色描述，说明该角色的职责和用途范围，便于管理员理解和维护", example = "拥有系统全部权限的超级管理员角色")
    private String description;

    @Schema(description = "数据权限范围：1-全部数据可见，2-仅本部门数据可见，3-本部门及下级部门数据可见，4-仅本人数据可见", example = "3")
    private Integer dataScope;

    @Schema(description = "角色状态：0-停用（拥有该角色的用户无对应权限），1-启用（正常生效）", example = "1")
    private Integer status;
}
