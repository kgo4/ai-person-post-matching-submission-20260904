package com.example.matching.vo.system;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 登录响应VO
 */
@Data
@Builder
@Schema(description = "登录响应，用户认证成功后返回的凭据信息，包含JWT令牌和用户基本资料")
public class LoginVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "JWT访问令牌，后续请求需在Authorization头中携带（Bearer格式），默认有效期为24小时", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String token;

    @Schema(description = "当前登录用户的唯一ID，用于后续接口调用的用户身份关联", example = "1")
    private Long userId;

    @Schema(description = "当前登录用户的账号名称，与登录时输入的用户名一致", example = "admin")
    private String username;

    @Schema(description = "当前登录用户的真实姓名，用于前端页面显示欢迎信息和操作留痕", example = "系统管理员")
    private String realName;

    @Schema(description = "用户角色编码列表，供前端进行权限判断和菜单展示", example = "[\"admin\", \"user\"]")
    private java.util.List<String> roles;

    @Schema(description = "用户权限标识列表")
    private java.util.List<String> permissions;
}
