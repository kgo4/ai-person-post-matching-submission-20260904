package com.example.matching.service.system;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.matching.dto.common.ChangePasswordDTO;
import com.example.matching.dto.system.MyProfileUpdateDTO;
import com.example.matching.dto.system.UserSaveDTO;
import com.example.matching.entity.system.SysUser;
import com.example.matching.vo.system.LoginVO;
import com.example.matching.vo.system.MyProfileVO;
import com.example.matching.vo.system.UserVO;

/**
 * 用户 服务接口
 */
public interface SysUserService extends IService<SysUser> {

    /** 根据用户名获取启用的用户（带 Redis 缓存） */
    SysUser getByUsername(String username);

    /** 登录 */
    LoginVO login(String username, String password);

    /** 保存用户（新增/更新），返回用户ID（新增时为新生成的主键） */
    Long saveUser(UserSaveDTO dto);

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

    /**
     * 个人中心：读取本人资料（手机号**不脱敏**，因为本人要编辑它）。
     *
     * @param userId 当前登录用户ID（由 SecurityUtils 解析，不接受前端传参，避免越权读他人资料）
     */
    MyProfileVO getMyProfile(Long userId);

    /**
     * 个人中心：更新本人资料（头像 / 手机号 / 邮箱）。
     *
     * <p>字段为 null 表示不修改；空串表示清空。用户名与真实姓名不在可改范围内，
     * 传了也不会生效 —— 这两项分别属登录凭据与人员档案映射。</p>
     *
     * @param userId 当前登录用户ID
     */
    void updateMyProfile(Long userId, MyProfileUpdateDTO dto);

    /**
     * 个人中心：保存头像文件并返回访问路径。
     *
     * <p>分两步（先上传拿路径、再提交资料）而不是一个 multipart 接口，
     * 是因为头像一旦上传即产生一个新文件，前端可先预览再决定是否保存；
     * 若合成一步，用户取消保存后文件就成了孤儿。</p>
     */
    String uploadAvatar(byte[] content, String contentType);
}
