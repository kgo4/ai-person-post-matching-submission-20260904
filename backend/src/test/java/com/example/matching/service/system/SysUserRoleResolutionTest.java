package com.example.matching.service.system;

import com.example.matching.entity.system.SysRole;
import com.example.matching.entity.system.SysUser;
import com.example.matching.entity.system.SysUserRole;
import com.example.matching.mapper.system.SysUserMapper;
import com.example.matching.security.JwtTokenProvider;
import com.example.matching.security.TokenInvalidationService;
import com.example.matching.security.UserAuthoritiesService;
import com.example.matching.service.system.impl.AvatarStorage;
import com.example.matching.service.system.impl.SysUserServiceImpl;
import com.example.matching.vo.system.MyProfileVO;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 账号「生效角色」解析契约。
 *
 * <p>背景（2026-09-04）：线上出现「角色状态正常（status=1/is_deleted=0）、账号绑定也正确，
 * 却进不去该角色任何功能」。原因是前端侧边栏把 {@code roles[0]} 当主角色并对菜单声明的 roles
 * 做**精确相等**匹配 —— 只要下发数组里混入停用/废弃角色、又恰好排在首位，
 * 该账号的菜单会被整体过滤为空，**且不报任何错**。</p>
 *
 * <p>本测试锁定两条口径：① 停用角色不得下发；② 顺序稳定（按 roleId），
 * 不依赖 sys_user_role 的物理顺序。</p>
 */
class SysUserRoleResolutionTest {

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

    /** 组装一个只依赖 mock 的 SysUserServiceImpl（构造参数与 CacheInvalidationContractTest 保持一致） */
    private SysUserServiceImpl serviceWithRoles(SysRoleService roleService) {
        SysUserServiceImpl service = new SysUserServiceImpl(
                mock(PasswordEncoder.class),
                mock(JwtTokenProvider.class),
                mock(TokenInvalidationService.class),
                roleService,
                mock(CacheManager.class),
                mock(UserAuthoritiesService.class),
                mock(AvatarStorage.class));
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysUser user = new SysUser();
        user.setId(9L);
        user.setUsername("security-admin");
        when(userMapper.selectById(9L)).thenReturn(user);
        ReflectionTestUtils.setField(service, "baseMapper", userMapper);
        return service;
    }

    @Test
    void disabledRolesMustNotBeResolvedAsActiveRoles() {
        SysRoleService roleService = mock(SysRoleService.class);
        when(roleService.getUserRoleIds(9L)).thenReturn(List.of(1L, 2L, 3L));
        // 1 = 已停用（或已废弃置 status=0）的角色；2/3 正常
        when(roleService.getById(1L)).thenReturn(role(1L, "SECURITY_ADMIN", 0));
        when(roleService.getById(2L)).thenReturn(role(2L, "PLATFORM_ADMIN", 1));
        when(roleService.getById(3L)).thenReturn(role(3L, "EMPLOYEE", 1));

        MyProfileVO vo = serviceWithRoles(roleService).getMyProfile(9L);

        assertThat(vo.getRoles())
                .as("停用角色不得下发，否则前端拿它当主角色会把菜单全部过滤掉")
                .containsExactly("PLATFORM_ADMIN", "EMPLOYEE");
    }

    @Test
    void resolvedRolesAreOrderedByRoleIdRegardlessOfBindingOrder() {
        SysRoleService roleService = mock(SysRoleService.class);
        // 绑定顺序故意与 roleId 顺序相反
        when(roleService.getUserRoleIds(9L)).thenReturn(List.of(7L, 3L));
        when(roleService.getById(7L)).thenReturn(role(7L, "SUPER_ADMIN", 1));
        when(roleService.getById(3L)).thenReturn(role(3L, "EMPLOYEE", 1));

        MyProfileVO vo = serviceWithRoles(roleService).getMyProfile(9L);

        assertThat(vo.getRoles())
                .as("顺序必须稳定（按 roleId），不能依赖 sys_user_role 的物理顺序")
                .containsExactly("EMPLOYEE", "SUPER_ADMIN");
    }
}
