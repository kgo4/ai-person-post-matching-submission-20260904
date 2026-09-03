package com.example.matching.service.system;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.matching.dto.system.RoleSaveDTO;
import com.example.matching.entity.system.SysRole;
import com.example.matching.vo.system.RoleVO;

import java.util.List;

/**
 * 角色 服务接口
 */
public interface SysRoleService extends IService<SysRole> {

    /** 保存角色 */
    void saveRole(RoleSaveDTO dto);

    /** 分页查询 */
    IPage<RoleVO> pageRoles(IPage<SysRole> page, String keyword);

    /** 查询全部启用角色 */
    List<RoleVO> listEnabled();

    /** 为用户分配角色 */
    void assignRolesToUser(Long userId, List<Long> roleIds);

    /** 查询用户的角色ID列表 */
    List<Long> getUserRoleIds(Long userId);
}
