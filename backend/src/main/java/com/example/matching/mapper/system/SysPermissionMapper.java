package com.example.matching.mapper.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.matching.entity.system.SysPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermission> {
    @Select("SELECT DISTINCT p.permission_code FROM sys_permission p " +
            "JOIN sys_role_permission rp ON rp.permission_id = p.id " +
            "JOIN sys_user_role ur ON ur.role_id = rp.role_id " +
            "JOIN sys_role r ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND r.status = 1 AND r.is_deleted = 0")
    List<String> selectCodesByUserId(Long userId);

    /**
     * 全部权限码（超级管理员专用）。
     *
     * <p>超级管理员不靠逐条 {@code sys_role_permission} 授权，而是按角色码动态取全集：
     * 这样以后新增权限码（新模块上线）无需再回头补授权，否则超管会出现
     * 「新功能看不见、且没有任何提示」的静默失效。</p>
     */
    @Select("SELECT DISTINCT p.permission_code FROM sys_permission p WHERE p.permission_code IS NOT NULL")
    List<String> selectAllCodes();
}
