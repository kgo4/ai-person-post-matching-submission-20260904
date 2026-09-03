package com.example.matching.service.system;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.matching.entity.system.SysUserRole;

import java.util.List;

/**
 * 用户角色关联 服务接口
 */
public interface SysUserRoleService extends IService<SysUserRole> {

    /** 根据用户ID查询关联 */
    List<SysUserRole> listByUserId(Long userId);

    /** 删除用户的所有角色关联 */
    void removeByUserId(Long userId);
}
