package com.example.matching.service.system.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.matching.entity.system.SysUserRole;
import com.example.matching.mapper.system.SysUserRoleMapper;
import com.example.matching.service.system.SysUserRoleService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SysUserRoleServiceImpl extends ServiceImpl<SysUserRoleMapper, SysUserRole> implements SysUserRoleService {

    @Override
    public List<SysUserRole> listByUserId(Long userId) {
        return list(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, userId));
    }

    @Override
    public void removeByUserId(Long userId) {
        remove(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, userId));
    }
}
