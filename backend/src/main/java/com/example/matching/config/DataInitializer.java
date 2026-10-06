package com.example.matching.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.entity.system.SysUser;
import com.example.matching.entity.system.SysRole;
import com.example.matching.entity.system.SysUserRole;
import com.example.matching.mapper.system.SysUserMapper;
import com.example.matching.mapper.system.SysRoleMapper;
import com.example.matching.mapper.system.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 数据初始化：首次启动时创建平台安全管理员账号。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        initSecurityAdminUser();
    }

    public void initSecurityAdminUser() {
        // 不再创建万能 ADMIN 角色；安全管理员只负责账号、角色和审计。
        long count = userMapper.selectCount(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, "security-admin"));
        if (count > 0) {
            log.info("管理员账号已存在，跳过初始化");
            return;
        }

        // 创建管理员
        SysUser admin = new SysUser();
        admin.setUsername("security-admin");
        admin.setPassword(passwordEncoder.encode("admin123"));
        admin.setRealName("平台管理员");
        admin.setStatus(1);
        admin.setCreatedBy(0L);
        userMapper.insert(admin);
        log.info("管理员账号已创建");

        // 创建管理员角色
        // 【2026-09-04】原为 SECURITY_ADMIN。该角色已与 AI_CONFIG_MANAGER 合并为
        // PLATFORM_ADMIN（见 V162 / docs/role-restructure-design.md），
        // 这里若继续用旧码，全新部署的库会建出一个已废弃的角色、且登录后权限为空。
        SysRole role = roleMapper.selectOne(
                Wrappers.<SysRole>lambdaQuery().eq(SysRole::getRoleCode, "PLATFORM_ADMIN"));
        if (role == null) {
            role = new SysRole();
            role.setRoleCode("PLATFORM_ADMIN");
            role.setRoleName("平台管理员");
            role.setDescription("账号与角色、操作审计、企业AI模型、Agent 记忆与系统设置；不参与任何业务数据");
            role.setDataScope(1);
            role.setStatus(1);
            role.setCreatedBy(0L);
            roleMapper.insert(role);
        }

        // 分配角色
        SysUserRole ur = new SysUserRole();
        ur.setUserId(admin.getId());
        ur.setRoleId(role.getId());
        userRoleMapper.insert(ur);
        log.info("管理员角色已分配");
    }
}
