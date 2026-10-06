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

    /** 按角色编码查询启用角色的ID，不存在时返回 null */
    Long getRoleIdByCode(String roleCode);

    /**
     * 清空用户权限缓存（`auth:authorities`）。
     *
     * <p>用于「直接改库」的场景：角色授权/账号角色是手工 SQL 改的，
     * 走不到 {@link #saveRole} / {@link #assignRolesToUser} 上的 {@code @CacheEvict}，
     * 缓存 TTL 30 分钟且按 userId 存 —— **重新登录也读同一份旧值**，
     * 表现为「SQL 执行成功、角色状态正确，却仍进不去功能」。</p>
     */
    void evictAuthoritiesCache();
}
