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
 * 数据初始化：首次启动时自动创建管理员账号
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
        initAdminUser();
    }

    public void initAdminUser() {
        // 检查admin是否存在
        long count = userMapper.selectCount(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, "admin"));
        if (count > 0) {
            log.info("管理员账号已存在，跳过初始化");
            return;
        }

        // 创建管理员
        SysUser admin = new SysUser();
        admin.setUsername("admin");
        admin.setPassword(passwordEncoder.encode("admin123"));
        admin.setRealName("系统管理员");
        admin.setStatus(1);
        admin.setCreatedBy(0L);
        userMapper.insert(admin);
        log.info("管理员账号已创建");

        // 创建管理员角色
        SysRole role = roleMapper.selectOne(
                Wrappers.<SysRole>lambdaQuery().eq(SysRole::getRoleCode, "ADMIN"));
        if (role == null) {
            role = new SysRole();
            role.setRoleCode("ADMIN");
            role.setRoleName("超级管理员");
            role.setDescription("拥有系统全部权限");
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
