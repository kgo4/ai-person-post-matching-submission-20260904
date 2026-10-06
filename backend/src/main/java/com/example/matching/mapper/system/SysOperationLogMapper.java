package com.example.matching.mapper.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.matching.entity.system.SysOperationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志 Mapper
 */
@Mapper
public interface SysOperationLogMapper extends BaseMapper<SysOperationLog> {
}
