package com.example.matching.service.system;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.matching.entity.system.SysOperationLog;

import java.time.LocalDateTime;

/**
 * 操作日志 服务接口
 */
public interface SysOperationLogService extends IService<SysOperationLog> {

    /** 分页查询日志 */
    IPage<SysOperationLog> pageLogs(IPage<SysOperationLog> page, String operationModule,
                                    Long userId, LocalDateTime startTime, LocalDateTime endTime);
}
