package com.example.matching.application.system;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.audit.OperationLogClassifier;
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
        // toResponse 是静态方法，方法引用必须用类名（`this::toResponse` 对静态方法非法）
        return PageResponse.from(page, OperationLogApiFacade::toResponse);
    }

    public OperationLogResponse get(Long id) {
        return toResponse(sysOperationLogService.getById(id));
    }

    /**
     * 实体 → 响应，**带历史行回填**。
     *
     * <p>包级可见（而非 private）是为了能被单测直接断言：历史行回填是「整列 UNKNOWN」
     * 这个用户可见症状的修复手段，不能只靠肉眼看代码。
     */
    static OperationLogResponse toResponse(SysOperationLog entity) {
        if (entity == null) return null;

        /*
         * 【2026-09-04】历史行回填：切面过去把 operationModule 写死成小写 `system`、
         * operationType 写死成 `UNKNOWN`，已经落库的行（本地库就有 8000+ 行）不会因为改代码而变好，
         * 但如果照原样返回，这一列对用户来说仍然是「整列 UNKNOWN」。
         *
         * 因此**在读取时按 requestMethod + requestUrl 重新推导**：
         * 这不需要跑任何 SQL 修数据，历史行与新行在页面上表现一致。
         * 只在该字段「确实没有信息量」时才覆盖，不碰 DLQ/OUTBOX/SCHEDULER 等
         * 由其他写入方给出的既有取值。
         */
        String module = entity.getOperationModule();
        String type = entity.getOperationType();
        if (OperationLogClassifier.needsModuleBackfill(module)) {
            module = OperationLogClassifier.classifyModule(entity.getRequestUrl());
        }
        if (!OperationLogClassifier.isMeaningfulType(type)) {
            type = OperationLogClassifier.classifyType(entity.getRequestMethod());
        }
        String desc = entity.getOperationDesc();
        if (desc == null || desc.isBlank() || "自动记录".equals(desc)) {
            desc = OperationLogClassifier.describe(module, type, entity.getRequestMethod(), entity.getRequestUrl());
        }

        return new OperationLogResponse(
            entity.getId(), entity.getUserId(), entity.getRealName(),
            module, type, desc,
            entity.getRequestMethod(), entity.getRequestUrl(), entity.getRequestParams(),
            entity.getResponseResult(), entity.getOperationIp(),
            entity.getOperationTime(), entity.getCostTime()
        );
    }
}
