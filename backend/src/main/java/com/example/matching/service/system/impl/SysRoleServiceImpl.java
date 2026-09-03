package com.example.matching.service.system.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.config.RedisCacheNames;
import com.example.matching.dto.system.RoleSaveDTO;
import com.example.matching.entity.system.SysRole;
import com.example.matching.entity.system.SysUserRole;
import com.example.matching.mapper.system.SysRoleMapper;
import com.example.matching.security.TokenInvalidationService;
import com.example.matching.service.system.SysRoleService;
import com.example.matching.service.system.SysUserRoleService;
import com.example.matching.vo.system.RoleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {

    private final SysUserRoleService sysUserRoleService;
    private final TokenInvalidationService tokenInvalidationService;

    @Override
    @Transactional
    @CacheEvict(cacheNames = RedisCacheNames.AUTH_AUTHORITIES, allEntries = true)
    public void saveRole(RoleSaveDTO dto) {
        if (dto.getId() == null) {
            long count = count(Wrappers.<SysRole>lambdaQuery().eq(SysRole::getRoleCode, dto.getRoleCode()));
            if (count > 0) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR.getCode(), "角色编码已存在");
            }
            SysRole role = new SysRole();
            BeanUtils.copyProperties(dto, role);
            if (role.getStatus() == null) {
                role.setStatus(1);
            }
            save(role);
        } else {
            SysRole role = getById(dto.getId());
            if (role == null) {
                throw new BusinessException(ErrorCodeEnum.NOT_FOUND.getCode(), "角色不存在");
            }
            BeanUtils.copyProperties(dto, role, "roleCode");
            updateById(role);
        }
    }

    @Override
    public IPage<RoleVO> pageRoles(IPage<SysRole> page, String keyword) {
        LambdaQueryWrapper<SysRole> wrapper = Wrappers.<SysRole>lambdaQuery();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SysRole::getRoleCode, keyword).or().like(SysRole::getRoleName, keyword));
        }
        wrapper.orderByAsc(SysRole::getCreatedTime);
        return page(page, wrapper).convert(this::convertToVO);
    }

    @Override
    public List<RoleVO> listEnabled() {
        return list(Wrappers.<SysRole>lambdaQuery().eq(SysRole::getStatus, 1))
                .stream().map(this::convertToVO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = RedisCacheNames.AUTH_AUTHORITIES, key = "#userId")
    public void assignRolesToUser(Long userId, List<Long> roleIds) {
        sysUserRoleService.removeByUserId(userId);
        if (roleIds != null && !roleIds.isEmpty()) {
            List<SysUserRole> userRoles = roleIds.stream().map(roleId -> {
                SysUserRole ur = new SysUserRole();
                ur.setUserId(userId);
                ur.setRoleId(roleId);
                return ur;
            }).collect(Collectors.toList());
            sysUserRoleService.saveBatch(userRoles);
        }
        tokenInvalidationService.invalidateUserTokens(userId);
    }

    @Override
    public List<Long> getUserRoleIds(Long userId) {
        return sysUserRoleService.listByUserId(userId).stream()
                .map(SysUserRole::getRoleId).collect(Collectors.toList());
    }

    private RoleVO convertToVO(SysRole role) {
        RoleVO vo = new RoleVO();
        BeanUtils.copyProperties(role, vo);
        return vo;
    }
}
