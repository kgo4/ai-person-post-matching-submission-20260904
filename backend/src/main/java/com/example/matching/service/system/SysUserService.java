package com.example.matching.service.system;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.matching.dto.common.ChangePasswordDTO;
import com.example.matching.dto.system.UserSaveDTO;
import com.example.matching.entity.system.SysUser;
import com.example.matching.vo.system.LoginVO;
import com.example.matching.vo.system.UserVO;

/**
 * 用户 服务接口
 */
public interface SysUserService extends IService<SysUser> {

    /** 根据用户名获取启用的用户（带 Redis 缓存） */
    SysUser getByUsername(String username);

    /** 登录 */
    LoginVO login(String username, String password);

    /** 保存用户（新增/更新） */
    void saveUser(UserSaveDTO dto);

    /** 修改密码 */
    void changePassword(Long userId, ChangePasswordDTO dto);

    /** 分页查询用户 */
    IPage<UserVO> pageUsers(IPage<SysUser> page, String keyword, Integer status);

    /** 根据ID获取用户VO */
    UserVO getUserVOById(Long id);

    /** 修改用户状态 */
    void updateStatus(Long id, Integer status);

    /** 重置密码 */
    void resetPassword(Long id);
}
