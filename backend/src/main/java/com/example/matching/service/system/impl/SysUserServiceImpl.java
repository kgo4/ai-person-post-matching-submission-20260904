package com.example.matching.service.system.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.matching.common.constant.CommonConstant;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.config.RedisCacheNames;
import com.example.matching.dto.common.ChangePasswordDTO;
import com.example.matching.dto.system.MyProfileUpdateDTO;
import com.example.matching.dto.system.UserSaveDTO;
import com.example.matching.entity.system.SysRole;
import com.example.matching.entity.system.SysUser;
import com.example.matching.mapper.system.SysUserMapper;
import com.example.matching.security.JwtTokenProvider;
import com.example.matching.security.TokenInvalidationService;
import com.example.matching.security.UserAuthoritiesService;
import com.example.matching.service.system.SysRoleService;
import com.example.matching.service.system.SysUserService;
import com.example.matching.vo.system.LoginVO;
import com.example.matching.vo.system.MyProfileVO;
import com.example.matching.vo.system.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户 服务实现
 */
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenInvalidationService tokenInvalidationService;
    private final SysRoleService sysRoleService;
    private final CacheManager cacheManager;
    private final UserAuthoritiesService userAuthoritiesService;
    /** 头像文件存储（基础设施，与账号逻辑正交） */
    private final AvatarStorage avatarStorage;

    @Override
    public LoginVO login(String username, String password) {
        SysUser user = getOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username));

        if (user == null) {
            throw new BusinessException(ErrorCodeEnum.USER_NOT_FOUND);
        }
        if (user.getStatus() == 0) {
            throw new BusinessException(ErrorCodeEnum.USER_ACCOUNT_DISABLED);
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(ErrorCodeEnum.USER_PASSWORD_ERROR);
        }

        // 更新最后登录时间
        user.setLastLoginTime(LocalDateTime.now());
        updateById(user);

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());

        List<String> roles = resolveRoles(user.getId());
        List<String> permissions = userAuthoritiesService.getAuthorities(user.getId());

        return LoginVO.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                // 头像随登录一并下发：否则顶栏要等下一次 /current 才显示出来
                .avatar(user.getAvatar())
                .roles(roles)
                .permissions(permissions)
                .build();
    }

    @Override
    @Cacheable(cacheNames = RedisCacheNames.AUTH_SYSUSER, key = "#username", unless = "#result == null")
    public SysUser getByUsername(String username) {
        return getOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username)
                .eq(SysUser::getStatus, 1));
    }

    @Override
    @Transactional
    public Long saveUser(UserSaveDTO dto) {
        if (dto.getId() == null) {
            // 新增
            if (!StringUtils.hasText(dto.getPassword())) {
                dto.setPassword(CommonConstant.DEFAULT_PASSWORD);
            }
            // 检查用户名唯一性
            long count = count(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, dto.getUsername()));
            if (count > 0) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR.getCode(), "用户名已存在");
            }
            SysUser user = new SysUser();
            BeanUtils.copyProperties(dto, user);
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
            if (user.getStatus() == null) {
                user.setStatus(1);
            }
            save(user);
            return user.getId();
        } else {
            // 更新
            SysUser user = getById(dto.getId());
            if (user == null) {
                throw new BusinessException(ErrorCodeEnum.USER_NOT_FOUND);
            }
            BeanUtils.copyProperties(dto, user, "password", "username");
            // 用户名不允许修改
            if (StringUtils.hasText(dto.getPassword())) {
                user.setPassword(passwordEncoder.encode(dto.getPassword()));
            }
            updateById(user);
            evictAuthenticatedUser(user.getUsername());
            return user.getId();
        }
    }

    @Override
    public void changePassword(Long userId, ChangePasswordDTO dto) {
        SysUser user = getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCodeEnum.USER_NOT_FOUND);
        }
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCodeEnum.USER_PASSWORD_ERROR);
        }
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        updateById(user);
        evictAuthenticatedUser(user.getUsername());
        tokenInvalidationService.invalidateUserTokens(userId);
    }

    @Override
    public IPage<UserVO> pageUsers(IPage<SysUser> page, String keyword, Integer status) {
        LambdaQueryWrapper<SysUser> wrapper = Wrappers.<SysUser>lambdaQuery();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getRealName, keyword));
        }
        if (status != null) {
            wrapper.eq(SysUser::getStatus, status);
        }
        wrapper.orderByDesc(SysUser::getCreatedTime);
        IPage<SysUser> userPage = page(page, wrapper);
        return userPage.convert(this::convertToVO);
    }

    @Override
    public UserVO getUserVOById(Long id) {
        SysUser user = getById(id);
        return user != null ? convertToVO(user) : null;
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        SysUser user = getById(id);
        if (user == null) {
            throw new BusinessException(ErrorCodeEnum.USER_NOT_FOUND);
        }
        user.setStatus(status);
        updateById(user);
        evictAuthenticatedUser(user.getUsername());
        tokenInvalidationService.invalidateUserTokens(id);
    }

    @Override
    public void resetPassword(Long id) {
        SysUser user = getById(id);
        if (user == null) {
            throw new BusinessException(ErrorCodeEnum.USER_NOT_FOUND);
        }
        user.setPassword(passwordEncoder.encode(CommonConstant.DEFAULT_PASSWORD));
        updateById(user);
        evictAuthenticatedUser(user.getUsername());
        tokenInvalidationService.invalidateUserTokens(id);
    }

    @Override
    public MyProfileVO getMyProfile(Long userId) {
        SysUser user = requireUser(userId);
        MyProfileVO vo = new MyProfileVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        // 与 UserVO 的关键差别：这里**不脱敏**。本人编辑自己的手机号，
        // 打码后（138****0000）填不回原值。
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setAvatar(user.getAvatar());
        vo.setLastLoginTime(user.getLastLoginTime());
        vo.setRoles(resolveRoles(user.getId()));
        return vo;
    }

    @Override
    @Transactional
    public void updateMyProfile(Long userId, MyProfileUpdateDTO dto) {
        if (dto == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "更新内容不能为空");
        }
        SysUser user = requireUser(userId);
        // null = 本次不改这一项；空串 = 清空。用「是否 null」区分“没传”与“传了空值”，
        // 否则清空手机号/邮箱这类合法诉求会被当成"未修改"而静默失败。
        if (dto.getPhone() != null) {
            user.setPhone(trimToNull(dto.getPhone()));
        }
        if (dto.getEmail() != null) {
            user.setEmail(trimToNull(dto.getEmail()));
        }
        if (dto.getAvatar() != null) {
            String newAvatar = trimToNull(dto.getAvatar());
            String oldAvatar = user.getAvatar();
            user.setAvatar(newAvatar);
            // 换头像后清掉旧文件，避免 uploads/avatars 无限增长。
            // 仅当确实是本站托管路径且与旧值不同才删（deleteQuietly 内部还做了路径穿越防护）。
            if (oldAvatar != null && !oldAvatar.equals(newAvatar)) {
                avatarStorage.deleteQuietly(oldAvatar);
            }
        }
        updateById(user);
        // 账号资料进过认证缓存（AUTH_SYSUSER），改完必须失效，
        // 否则下次登录前读到的仍是旧资料。
        evictAuthenticatedUser(user.getUsername());
    }

    @Override
    public String uploadAvatar(byte[] content, String contentType) {
        return avatarStorage.store(content, contentType);
    }

    private SysUser requireUser(Long userId) {
        SysUser user = userId == null ? null : getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCodeEnum.USER_NOT_FOUND);
        }
        return user;
    }

    /** 去空白；去掉后为空则归一为 null（"没设置"比"空字符串"更明确） */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void evictAuthenticatedUser(String username) {
        if (!StringUtils.hasText(username)) {
            return;
        }
        Cache cache = cacheManager.getCache(RedisCacheNames.AUTH_SYSUSER);
        if (cache != null) {
            cache.evict(username);
        }
    }

    private UserVO convertToVO(SysUser user) {
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        // 脱敏处理
        vo.setPhone(maskPhone(user.getPhone()));
        vo.setRoles(resolveRoles(user.getId()));
        vo.setPermissions(userAuthoritiesService.getAuthorities(user.getId()));
        return vo;
    }

    /**
     * 解析账号当前**生效**的角色码。
     *
     * <p>【2026-09-04 修】必须过滤「停用」角色（status=0）：已删除角色由
     * {@code SysRole.isDeleted} 上的 {@code @TableLogic} 自动排除，但**停用角色不会**被自动过滤。</p>
     *
     * <p>为什么这条过滤很关键：前端侧边栏把 {@code roles[0]} 当主角色，并对菜单声明的 roles 做
     * **精确相等**匹配（见 {@code config/sidebar-menu.ts} 与 {@code AppSidebar.vue}）。
     * 一旦把停用/废弃角色下发到数组里、又恰好排在首位，该账号所有菜单会被过滤成空，
     * 表现为「角色状态正常、却进不去任何功能」且**不报任何错**。同时按 roleId 排序，
     * 保证多次登录拿到的主角色一致（原实现依赖 sys_user_role 的物理顺序，不稳定）。</p>
     */
    private List<String> resolveRoles(Long userId) {
        return sysRoleService.getUserRoleIds(userId).stream()
                .map(sysRoleService::getById)
                .filter(role -> role != null)
                .filter(role -> role.getStatus() != null && role.getStatus() == 1)
                .sorted(java.util.Comparator.comparing(SysRole::getId))
                .map(SysRole::getRoleCode)
                .filter(code -> code != null && !code.isBlank())
                .collect(java.util.stream.Collectors.toList());
    }

    private String maskPhone(String phone) {
        if (phone != null && phone.length() >= 7) {
            return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
        }
        return phone;
    }
}


