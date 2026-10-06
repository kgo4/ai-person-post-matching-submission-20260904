package com.example.matching.infrastructure.persistence;

import com.example.matching.entity.system.SysRole;
import com.example.matching.entity.system.SysUser;
import com.example.matching.entity.system.SysUserRole;
import com.example.matching.mapper.system.SysRoleMapper;
import com.example.matching.mapper.system.SysPermissionMapper;
import com.example.matching.port.system.SystemAuthenticationPort;
import com.example.matching.port.system.SystemAuthenticationPort.AuthenticatedUser;
import com.example.matching.service.system.SysUserRoleService;
import com.example.matching.service.system.SysUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class SystemAuthenticationPortAdapter implements SystemAuthenticationPort {

    /**
     * 超级管理员角色码。
     *
     * <p>【2026-09-04 新增】该角色**不逐条授权**：只要用户持有它（且角色启用未删除），
     * 就把 {@code sys_permission} 里的全部权限码一次性装上，等价于「所有角色的所有功能」。
     * 这么做而不是"给这个角色 INSERT 一堆 sys_role_permission"，是因为后者在
     * 新增权限码时会静默漏授权（人看不到新菜单，也没有任何报错）。</p>
     *
     * <p>前端有同名常量 {@code frontend/src/utils/super-admin.ts}，两处必须保持一致。</p>
     */
    static final String SUPER_ADMIN_ROLE_CODE = "SUPER_ADMIN";

    private final SysUserService sysUserService;
    private final SysUserRoleService sysUserRoleService;
    private final SysRoleMapper sysRoleMapper;
    private final SysPermissionMapper sysPermissionMapper;

    public SystemAuthenticationPortAdapter(@Lazy SysUserService sysUserService,
                                           SysUserRoleService sysUserRoleService,
                                           SysRoleMapper sysRoleMapper,
                                           SysPermissionMapper sysPermissionMapper) {
        this.sysUserService = sysUserService;
        this.sysUserRoleService = sysUserRoleService;
        this.sysRoleMapper = sysRoleMapper;
        this.sysPermissionMapper = sysPermissionMapper;
    }

    @Override
    public AuthenticatedUser getUserByUsername(String username) {
        SysUser user = sysUserService.getByUsername(username);
        if (user == null) {
            return null;
        }
        return new AuthenticatedUser(user.getId(), user.getUsername(), user.getPassword(), user.getStatus());
    }

    @Override
    public List<String> getAuthorities(Long userId) {
        Set<String> authorities = new LinkedHashSet<>();

        List<Long> roleIds = sysUserRoleService.listByUserId(userId).stream()
                .map(SysUserRole::getRoleId)
                .toList();

        // 启用中的角色码（SysRole.isDeleted 带 @TableLogic，selectBatchIds 已自动排除已删除行）
        List<String> activeRoleCodes = sysRoleMapper.selectBatchIds(roleIds).stream()
                .filter(role -> role.getStatus() != null && role.getStatus() == 1)
                .map(SysRole::getRoleCode)
                .filter(roleCode -> roleCode != null && !roleCode.isBlank())
                .toList();

        if (activeRoleCodes.contains(SUPER_ADMIN_ROLE_CODE)) {
            // 超级管理员：全部权限码 + 全部启用角色码，不受 sys_role_permission 约束
            authorities.addAll(sysPermissionMapper.selectAllCodes());
            activeRoleCodes.forEach(roleCode -> authorities.add("ROLE_" + roleCode));
            log.debug("[AUTH] 超级管理员权限装载: userId={}, 权限码={}, 角色={}",
                    userId, authorities.size(), activeRoleCodes);
            return List.copyOf(authorities);
        }

        authorities.addAll(sysPermissionMapper.selectCodesByUserId(userId));
        activeRoleCodes.stream()
                .map(roleCode -> "ROLE_" + roleCode)
                .forEach(authorities::add);
        return List.copyOf(authorities);
    }
}
