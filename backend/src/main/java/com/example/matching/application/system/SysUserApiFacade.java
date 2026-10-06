package com.example.matching.application.system;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.application.employee.EmpEmployeeApiFacade;
import com.example.matching.dto.common.ChangePasswordDTO;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.dto.system.LoginDTO;
import com.example.matching.dto.system.MyProfileUpdateDTO;
import com.example.matching.dto.system.UserSaveDTO;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.security.AuthRateLimitService;
import com.example.matching.security.TokenInvalidationService;
import com.example.matching.service.employee.EmpEmployeeService;
import com.example.matching.service.system.SysRoleService;
import com.example.matching.service.system.SysUserService;
import com.example.matching.vo.system.LoginVO;
import com.example.matching.vo.system.MyProfileVO;
import com.example.matching.vo.system.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SysUserApiFacade {

    private final SysUserService sysUserService;
    private final SysRoleService sysRoleService;
    private final AuthRateLimitService authRateLimitService;
    private final TokenInvalidationService tokenInvalidationService;
    private final EmpEmployeeService empEmployeeService;
    private final EmpEmployeeApiFacade empEmployeeApiFacade;
    private final com.example.matching.service.system.RegisterEmailCodeService registerEmailCodeService;
    private final com.example.matching.config.RegisterRoleCodeProperties registerRoleCodeProperties;

    /**
     * 解析账号关联的人员档案ID。
     * <p>员工角色的“仅本人”链路需要 empId：登录态与 /current 都通过该映射下发，
     * 避免前端再从 URL 传参推断身份。账号未绑定人员档案时返回 null。</p>
     */
    private Long resolveEmpId(Long userId) {
        if (userId == null) {
            return null;
        }
        EmpEmployee employee = empEmployeeService.getByUserId(userId);
        return employee == null ? null : employee.getId();
    }

    public void checkLoginAllowed(String clientIp, String username) {
        authRateLimitService.checkLoginAllowed(clientIp, username);
    }

    public void clearLoginFailures(String clientIp, String username) {
        authRateLimitService.clearLoginFailures(clientIp, username);
    }

    public LoginVO login(String username, String password) {
        LoginVO vo = sysUserService.login(username, password);
        if (vo != null) {
            vo.setEmpId(resolveEmpId(vo.getUserId()));
        }
        return vo;
    }

    public void recordLoginFailure(String clientIp, String username) {
        authRateLimitService.recordLoginFailure(clientIp, username);
    }

    /**
     * 自助注册：创建账号后同步落人员档案并授予员工角色。
     * <p>注册者是自然人，注册即代表其组织身份建立，因此这里必须完成三件事，
     * 且必须在同一事务语义内完成：建账号 → 建人员档案（回写 userId）→ 授 EMPLOYEE 角色。
     * 只靠账号不建档案会让 HR 侧人员档案看不到人、也让“仅本人”链路拿不到 empId；
     * 只建档不授角色会让新账号拿不到 ASSESSMENT:SELF，无法进入能力评估流程。</p>
     * <p>注意与后台管理端建号（{@link #saveUser}）区分：后台建号由管理员指定角色，
     * 不代表自助员工身份，因此不在此自动建档。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    /** 兼容旧调用方（仅员工注册、不带验证码）：内部转成新的注册请求。 */
    public LoginVO register(UserSaveDTO dto) {
        dto.setStatus(1);
        Long userId = sysUserService.saveUser(dto);
        empEmployeeApiFacade.createProfileForRegisteredUser(
                userId, dto.getRealName(), dto.getPhone(), dto.getEmail());
        assignRole(userId, com.example.matching.config.RegisterRoleCodeProperties.ROLE_EMPLOYEE);
        return sysUserService.login(dto.getUsername(), dto.getPassword());
    }

    /**
     * 自助注册（带邮箱验证码 + 可选角色）。
     *
     * <p>校验顺序刻意如此，**先便宜后昂贵、先无副作用后有副作用**：
     * <ol>
     *   <li>角色是否开放注册 —— 不开放时直接拒绝，不必浪费一次验证码校验；</li>
     *   <li>授权码 —— 与角色绑定，属于"凭证"检查，放在验证码之前，
     *       避免攻击者用任意邮箱反复消耗验证码来探测角色是否可注册；</li>
     *   <li>邮箱验证码 —— 一次性，校验通过即作废。</li>
     * </ol>
     *
     * <p>注意：**先校验后建号**。若先建号再校验验证码，失败的注册会留下垃圾账号，
     * 而且会占用用户名（真实用户反而注册不上）。
     */
    public LoginVO register(com.example.matching.dto.system.RegisterRequestDTO dto) {
        String roleCode = resolveRegistrationRole(dto.getRoleCode());

        if (!registerRoleCodeProperties.isRoleOpenForRegistration(roleCode)) {
            // 区分两种 false，否则运维会拿着"未开放"的提示去翻代码找原因，
            // 而真实原因常常是部署时漏配了 REGISTER_CODE_* 环境变量。
            if (registerRoleCodeProperties.isRoleConfiguredButBlank(roleCode)) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                        "该角色的注册授权码未在服务端配置，请联系管理员配置后重试");
            }
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                    "该角色未开放自助注册，请联系管理员开通账号");
        }
        if (!registerRoleCodeProperties.matchesCode(roleCode, dto.getRoleAuthCode())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "角色授权码不正确");
        }

        registerEmailCodeService.verify(dto.getEmail(), dto.getEmailCode());

        UserSaveDTO save = new UserSaveDTO();
        save.setUsername(dto.getUsername());
        save.setPassword(dto.getPassword());
        save.setRealName(dto.getRealName());
        save.setPhone(dto.getPhone());
        save.setEmail(dto.getEmail());
        save.setStatus(1);

        Long userId = sysUserService.saveUser(save);
        empEmployeeApiFacade.createProfileForRegisteredUser(
                userId, save.getRealName(), save.getPhone(), save.getEmail());
        assignRole(userId, roleCode);
        return sysUserService.login(save.getUsername(), save.getPassword());
    }

    /**
     * 归一化注册角色码。
     *
     * <p>留空按 EMPLOYEE 处理（兼容只有用户名密码的老注册页）；
     * **SUPER_ADMIN 直接拒绝** —— 超级管理员可访问全部功能，只允许内部授权，
     * 不能通过公开注册入口获得（连授权码都不接受）。
     */
    private String resolveRegistrationRole(String rawRoleCode) {
        if (!org.springframework.util.StringUtils.hasText(rawRoleCode)) {
            return com.example.matching.config.RegisterRoleCodeProperties.ROLE_EMPLOYEE;
        }
        String normalized = rawRoleCode.trim().toUpperCase(java.util.Locale.ROOT);
        if ("SUPER_ADMIN".equals(normalized)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "超级管理员不支持自助注册");
        }
        return normalized;
    }

    /**
     * 授予注册账号目标角色。
     * <p>角色由 V147 迁移预置；若部署环境的角色种子数据缺失，这里不阻断注册流程，
     * 仅跳过授权（账号仍可用，由管理员后续补授权），避免因种子数据问题导致注册入口整体不可用。</p>
     */
    private void assignRole(Long userId, String roleCode) {
        Long roleId = sysRoleService.getRoleIdByCode(roleCode);
        if (roleId != null) {
            sysRoleService.assignRolesToUser(userId, List.of(roleId));
        }
    }

    public void checkRegistrationAllowed(String clientIp) {
        authRateLimitService.checkRegistrationAllowed(clientIp);
    }

    public PageResponse<UserVO> pageUsers(long current, long size, String keyword, Integer status) {
        IPage<UserVO> page = sysUserService.pageUsers(new Page<>(current, size), keyword, status);
        return new PageResponse<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }

    public UserVO getUserVOById(Long id) {
        UserVO vo = sysUserService.getUserVOById(id);
        if (vo != null) {
            vo.setEmpId(resolveEmpId(id));
        }
        return vo;
    }

    public void saveUser(UserSaveDTO dto) {
        sysUserService.saveUser(dto);
    }

    public void changePassword(Long userId, ChangePasswordDTO dto) {
        sysUserService.changePassword(userId, dto);
    }

    public void resetPassword(Long id) {
        sysUserService.resetPassword(id);
    }

    public void updateStatus(Long id, Integer status) {
        sysUserService.updateStatus(id, status);
    }

    public void removeById(Long id) {
        sysUserService.removeById(id);
    }

    public void invalidateUserTokens(Long userId) {
        tokenInvalidationService.invalidateUserTokens(userId);
    }

    /**
     * 个人中心：读取本人资料。
     * <p>empId 与 /current 走同一解析（emp_employee.user_id 映射），
     * 让个人中心也能显示"是否已绑定人员档案"。</p>
     */
    public MyProfileVO getMyProfile(Long userId) {
        MyProfileVO vo = sysUserService.getMyProfile(userId);
        if (vo != null) {
            vo.setEmpId(resolveEmpId(userId));
        }
        return vo;
    }

    /**
     * 个人中心：更新本人资料（头像/手机号/邮箱）。人员范围由 Service 固定为当前登录人。
     * <p>
     * <b>邮箱需要额外同步到员工档案</b>：{@code sys_user.email} 是登录账号的联系方式，
     * 而视频终面邀请邮件的收件人取自 {@code emp_employee.email}。两者不同步时，
     * 员工在个人中心把邮箱改成自己的个人邮箱、邀请邮件却仍发到档案里的旧地址 ——
     * 表现就是「改了没用」，甚至会一直发到平台发信邮箱上（即「收发件是同一个邮箱」）。
     * </p>
     */
    public void updateMyProfile(Long userId, MyProfileUpdateDTO dto) {
        sysUserService.updateMyProfile(userId, dto);
        syncEmployeeEmail(userId, dto);
    }

    /**
     * 把个人中心改的邮箱同步到员工档案。
     * <p>
     * 只在<b>本次确实传了 email</b> 时同步（{@code null} 表示不改这一项，与
     * {@code sysUserService.updateMyProfile} 的语义一致）；传空串是「清空」，
     * 会一并清掉档案邮箱。
     * <p>
     * 账号未绑定员工档案（如纯管理端账号）时不做任何事 —— 这不是错误，只是没有需要同步的档案。
     */
    private void syncEmployeeEmail(Long userId, MyProfileUpdateDTO dto) {
        if (dto == null || dto.getEmail() == null) {
            return;
        }
        String email = dto.getEmail().trim();
        empEmployeeService.updateEmailByUserId(userId, email.isEmpty() ? null : email);
    }

    /** 个人中心：保存头像并返回访问路径。 */
    public String uploadAvatar(byte[] content, String contentType) {
        return sysUserService.uploadAvatar(content, contentType);
    }
}
