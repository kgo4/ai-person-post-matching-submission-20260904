package com.example.matching.dto.system;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 「个人中心」自助更新资料请求。
 *
 * <p>刻意只开放三个字段：头像 / 手机号 / 邮箱。
 * 用户名与真实姓名不在其列 —— 用户名是账号主键语义（登录凭据），
 * 真实姓名与人员档案（emp_employee）存在映射，允许账号侧改写会让两侧不一致。
 * 需要改这两项请走管理端用户管理（USER:MANAGE），保持「自助只能改自描述信息」的边界。</p>
 *
 * <p>三个字段都允许为 null：null 表示「本次不改这一项」，
 * 空串表示「清空这一项」（用于删掉已设置的头像/邮箱）。</p>
 */
@Data
@Schema(description = "个人中心资料更新请求")
public class MyProfileUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "手机号；null 表示不修改，空串表示清空", example = "13800000000")
    private String phone;

    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过128")
    @Schema(description = "邮箱；null 表示不修改，空串表示清空", example = "zhangsan@example.com")
    private String email;

    @Size(max = 500, message = "头像路径长度不能超过500")
    @Schema(description = "头像访问路径（先调用头像上传接口拿到路径再提交）；null 表示不修改，空串表示恢复默认头像")
    private String avatar;
}
