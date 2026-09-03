package com.example.matching.infrastructure.persistence;

import com.example.matching.entity.system.SysRole;
import com.example.matching.entity.system.SysUser;
import com.example.matching.entity.system.SysUserRole;
import com.example.matching.mapper.system.SysRoleMapper;
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

    private final SysUserService sysUserService;
    private final SysUserRoleService sysUserRoleService;
    private final SysRoleMapper sysRoleMapper;

    public SystemAuthenticationPortAdapter(@Lazy SysUserService sysUserService,
                                           SysUserRoleService sysUserRoleService,
                                           SysRoleMapper sysRoleMapper) {
        this.sysUserService = sysUserService;
        this.sysUserRoleService = sysUserRoleService;
        this.sysRoleMapper = sysRoleMapper;
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
        authorities.add("ROLE_USER");

        List<Long> roleIds = sysUserRoleService.listByUserId(userId).stream()
                .map(SysUserRole::getRoleId)
                .toList();
        if (roleIds.isEmpty()) {
            return List.copyOf(authorities);
        }

        sysRoleMapper.selectBatchIds(roleIds).stream()
                .filter(role -> role.getStatus() != null && role.getStatus() == 1)
                .map(SysRole::getRoleCode)
                .filter(roleCode -> roleCode != null && !roleCode.isBlank())
                .map(roleCode -> roleCode.startsWith("ROLE_") ? roleCode : "ROLE_" + roleCode)
                .forEach(authorities::add);
        return List.copyOf(authorities);
    }
}
