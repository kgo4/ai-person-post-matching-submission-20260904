package com.example.matching.service.system.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.matching.entity.system.SysOperationLog;
import com.example.matching.mapper.system.SysOperationLogMapper;
import com.example.matching.service.system.SysOperationLogService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class SysOperationLogServiceImpl extends ServiceImpl<SysOperationLogMapper, SysOperationLog> implements SysOperationLogService {

    @Override
    public IPage<SysOperationLog> pageLogs(IPage<SysOperationLog> page, String operationModule,
                                           Long userId, LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<SysOperationLog> wrapper = Wrappers.<SysOperationLog>lambdaQuery();
        if (StringUtils.hasText(operationModule)) {
            wrapper.eq(SysOperationLog::getOperationModule, operationModule);
        }
        if (userId != null) {
            wrapper.eq(SysOperationLog::getUserId, userId);
        }
        if (startTime != null) {
            wrapper.ge(SysOperationLog::getOperationTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(SysOperationLog::getOperationTime, endTime);
        }
        wrapper.orderByDesc(SysOperationLog::getOperationTime);
        return page(page, wrapper);
    }
}
