package com.example.matching.mapper.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.matching.entity.system.SysUserRole;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户角色关联 Mapper
 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {
}
