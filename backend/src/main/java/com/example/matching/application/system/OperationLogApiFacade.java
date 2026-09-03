package com.example.matching.application.system;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.dto.system.api.OperationLogResponse;
import com.example.matching.entity.system.SysOperationLog;
import com.example.matching.service.system.SysOperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OperationLogApiFacade {

    private final SysOperationLogService sysOperationLogService;

    public PageResponse<OperationLogResponse> page(long current, long size, String operationModule,
                                                    Long userId, LocalDateTime startTime, LocalDateTime endTime) {
        IPage<SysOperationLog> page = sysOperationLogService.pageLogs(
                new Page<>(current, size), operationModule, userId, startTime, endTime);
        return PageResponse.from(page, this::toResponse);
    }

    public OperationLogResponse get(Long id) {
        return toResponse(sysOperationLogService.getById(id));
    }

    private OperationLogResponse toResponse(SysOperationLog entity) {
        if (entity == null) return null;
        return new OperationLogResponse(
            entity.getId(), entity.getUserId(), entity.getRealName(),
            entity.getOperationModule(), entity.getOperationType(), entity.getOperationDesc(),
            entity.getRequestMethod(), entity.getRequestUrl(), entity.getRequestParams(),
            entity.getResponseResult(), entity.getOperationIp(),
            entity.getOperationTime(), entity.getCostTime()
        );
    }
}
