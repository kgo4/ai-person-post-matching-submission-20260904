package com.example.matching.vo.system;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 角色视图VO
 */
@Data
@Schema(description = "角色视图")
public class RoleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "角色ID")
    private Long id;

    @Schema(description = "角色编码")
    private String roleCode;

    @Schema(description = "角色名称")
    private String roleName;

    @Schema(description = "角色描述")
    private String description;

    @Schema(description = "数据权限范围")
    private Integer dataScope;

    @Schema(description = "状态：0停用，1启用")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createdTime;
}
