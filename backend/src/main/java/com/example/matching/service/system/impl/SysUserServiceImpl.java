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
import com.example.matching.dto.system.UserSaveDTO;
import com.example.matching.entity.system.SysUser;
import com.example.matching.mapper.system.SysUserMapper;
import com.example.matching.security.JwtTokenProvider;
import com.example.matching.security.TokenInvalidationService;
import com.example.matching.security.UserAuthoritiesService;
import com.example.matching.service.system.SysRoleService;
import com.example.matching.service.system.SysUserService;
import com.example.matching.vo.system.LoginVO;
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
    public void saveUser(UserSaveDTO dto) {
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

    private List<String> resolveRoles(Long userId) {
        return sysRoleService.getUserRoleIds(userId).stream()
                .map(sysRoleService::getById)
                .filter(role -> role != null)
                .map(sysRole -> sysRole.getRoleCode())
                .collect(java.util.stream.Collectors.toList());
    }

    private String maskPhone(String phone) {
        if (phone != null && phone.length() >= 7) {
            return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
        }
        return phone;
    }
}
