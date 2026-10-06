package com.example.matching.infrastructure.persistence;

import com.example.matching.entity.system.SysRole;
import com.example.matching.entity.system.SysUserRole;
import com.example.matching.mapper.system.SysPermissionMapper;
import com.example.matching.mapper.system.SysRoleMapper;
import com.example.matching.service.system.SysUserRoleService;
import com.example.matching.service.system.SysUserService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 超级管理员权限装载契约（2026-09-04 新增）。
 *
 * <p>SUPER_ADMIN 不逐条依赖 {@code sys_role_permission}：只要持有该角色，
 * 就装载 {@code sys_permission} 的**全部**权限码。这样才能做到"可访问所有角色的所有功能"，
 * 且**以后新增权限码自动生效**（否则新模块上线时超管会静默看不到入口）。</p>
 *
 * <p>同时锁定：普通角色仍只拿自己被授权的码，不能被顺带放大。</p>
 */
class SystemAuthenticationPortAdapterTest {

    private static SysRole role(Long id, String code, int status) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setRoleCode(code);
        role.setStatus(status);
        return role;
    }

    private static SysUserRole binding(Long roleId) {
        SysUserRole ur = new SysUserRole();
        ur.setUserId(9L);
        ur.setRoleId(roleId);
        return ur;
    }

    private SystemAuthenticationPortAdapter adapter(SysUserRoleService userRoleService,
                                                    SysRoleMapper roleMapper,
                                                    SysPermissionMapper permissionMapper) {
        return new SystemAuthenticationPortAdapter(
                mock(SysUserService.class), userRoleService, roleMapper, permissionMapper);
    }

    @Test
    void superAdminGetsEveryPermissionCodeEvenWithoutRolePermissionRows() {
        SysUserRoleService userRoleService = mock(SysUserRoleService.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysPermissionMapper permissionMapper = mock(SysPermissionMapper.class);

        when(userRoleService.listByUserId(9L)).thenReturn(List.of(binding(1L)));
        when(roleMapper.selectBatchIds(anyList())).thenReturn(List.of(role(1L, "SUPER_ADMIN", 1)));
        when(permissionMapper.selectAllCodes())
                .thenReturn(List.of("USER:MANAGE", "ROLE:MANAGE", "AUDIT:READ", "AI:CONFIG", "ASSESSMENT:CONFIG"));

        List<String> authorities = adapter(userRoleService, roleMapper, permissionMapper).getAuthorities(9L);

        assertThat(authorities).contains(
                "USER:MANAGE", "ROLE:MANAGE", "AUDIT:READ", "AI:CONFIG", "ASSESSMENT:CONFIG");
        assertThat(authorities).contains("ROLE_SUPER_ADMIN");
    }

    @Test
    void superAdminStillWorksWhenItIsNotTheFirstRole() {
        SysUserRoleService userRoleService = mock(SysUserRoleService.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysPermissionMapper permissionMapper = mock(SysPermissionMapper.class);

        when(userRoleService.listByUserId(9L)).thenReturn(List.of(binding(1L), binding(2L)));
        when(roleMapper.selectBatchIds(anyList()))
                .thenReturn(List.of(role(1L, "EMPLOYEE", 1), role(2L, "SUPER_ADMIN", 1)));
        when(permissionMapper.selectAllCodes()).thenReturn(List.of("POST:MANAGE"));

        List<String> authorities = adapter(userRoleService, roleMapper, permissionMapper).getAuthorities(9L);

        assertThat(authorities).contains("POST:MANAGE", "ROLE_EMPLOYEE", "ROLE_SUPER_ADMIN");
    }

    @Test
    void normalRoleOnlyGetsItsGrantedCodes() {
        SysUserRoleService userRoleService = mock(SysUserRoleService.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysPermissionMapper permissionMapper = mock(SysPermissionMapper.class);

        when(userRoleService.listByUserId(9L)).thenReturn(List.of(binding(1L)));
        when(roleMapper.selectBatchIds(anyList())).thenReturn(List.of(role(1L, "JOB_ARCHITECT", 1)));
        when(permissionMapper.selectCodesByUserId(9L)).thenReturn(List.of("POST:READ", "POST:MANAGE"));

        List<String> authorities = adapter(userRoleService, roleMapper, permissionMapper).getAuthorities(9L);

        assertThat(authorities).containsExactlyInAnyOrder("POST:READ", "POST:MANAGE", "ROLE_JOB_ARCHITECT");
    }

    @Test
    void disabledRolesDoNotContributeRoleAuthorities() {
        SysUserRoleService userRoleService = mock(SysUserRoleService.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysPermissionMapper permissionMapper = mock(SysPermissionMapper.class);

        when(userRoleService.listByUserId(9L)).thenReturn(List.of(binding(1L)));
        when(roleMapper.selectBatchIds(anyList())).thenReturn(List.of(role(1L, "SUPER_ADMIN", 0)));
        when(permissionMapper.selectCodesByUserId(9L)).thenReturn(List.of());

        List<String> authorities = adapter(userRoleService, roleMapper, permissionMapper).getAuthorities(9L);

        assertThat(authorities)
                .as("停用角色的 SUPER_ADMIN 不应获得全量权限")
                .isEmpty();
    }
}
