package com.example.matching.vo.system;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 「个人中心」自助资料视图。
 *
 * <p>与 {@link UserVO} 的差别只有一点，但是关键一点：<b>手机号不脱敏</b>。
 * UserVO 是给管理端列表看的（手机号打码），而个人中心要让人**编辑自己**的手机号，
 * 打码后填不回原值。因此单开一个 VO，而不是给 UserVO 加个"是否脱敏"开关 ——
 * 脱敏与否应由「谁在看」决定，用一个专门的视图表达最不容易被误用。</p>
 */
@Data
@Schema(description = "个人中心资料")
public class MyProfileVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "账号ID")
    private Long id;

    @Schema(description = "用户名（只读：登录凭据，不可自助修改）")
    private String username;

    @Schema(description = "真实姓名（只读：与人员档案映射，需管理端修改）")
    private String realName;

    @Schema(description = "手机号（未脱敏，供本人编辑）")
    private String phone;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "头像访问路径；为空时前端按姓名首字展示")
    private String avatar;

    @Schema(description = "关联的人员档案ID；为 null 表示该账号未绑定人员档案")
    private Long empId;

    @Schema(description = "角色编码列表（只读）")
    private java.util.List<String> roles;

    @Schema(description = "最后登录时间（只读）")
    private LocalDateTime lastLoginTime;
}
