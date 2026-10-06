package com.example.matching.application.system;

import com.example.matching.dto.system.api.OperationLogResponse;
import com.example.matching.entity.system.SysOperationLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 操作日志的**读取时回填**测试。
 *
 * <p>用户可见症状是「操作日志页的操作类型整列显示 UNKNOWN」。光改切面只能让**新**日志变好；
 * 已经落库的历史行（本地库就有 8000+ 行 {@code ('system','UNKNOWN')}）不会因为你改代码而改变。
 * 因此 {@link OperationLogApiFacade#toResponse} 在读取时按 {@code requestMethod + requestUrl}
 * 重新推导这两个字段 —— 这组用例锁定的就是它，且**不需要跑任何 SQL 修数据**。
 */
class OperationLogApiFacadeTest {

    private static SysOperationLog legacyRow(String method, String url) {
        SysOperationLog entity = new SysOperationLog();
        entity.setId(1L);
        entity.setUserId(7L);
        entity.setRealName("彭超");
        // 历史值：切面写死的
        entity.setOperationModule("system");
        entity.setOperationType("UNKNOWN");
        entity.setOperationDesc("自动记录");
        entity.setRequestMethod(method);
        entity.setRequestUrl(url);
        entity.setCostTime(12L);
        return entity;
    }

    @Test
    @DisplayName("历史 UNKNOWN 行：按 URL + 方法回填出真实模块与类型")
    void backfillsLegacyRowsOnRead() {
        OperationLogResponse created = OperationLogApiFacade.toResponse(
                legacyRow("POST", "/api/system/user"));
        assertEquals("USER", created.operationModule());
        assertEquals("CREATE", created.operationType());

        OperationLogResponse updated = OperationLogApiFacade.toResponse(
                legacyRow("PUT", "/api/system/role/1/permissions"));
        assertEquals("ROLE", updated.operationModule());
        assertEquals("UPDATE", updated.operationType());

        OperationLogResponse deleted = OperationLogApiFacade.toResponse(
                legacyRow("DELETE", "/api/post/model-config/9"));
        assertEquals("POST", deleted.operationModule());
        assertEquals("DELETE", deleted.operationType());
    }

    @Test
    @DisplayName("历史行的描述也从「自动记录」换成可读文案")
    void rewritesPlaceholderDescription() {
        OperationLogResponse response = OperationLogApiFacade.toResponse(
                legacyRow("POST", "/api/system/user"));
        assertEquals("新增 · 用户管理（POST /api/system/user）", response.operationDesc());
    }

    @Test
    @DisplayName("其他写入方的模块/类型必须原样保留，不被回填覆盖")
    void keepsMeaningfulValuesIntact() {
        SysOperationLog dlq = legacyRow(null, null);
        dlq.setOperationModule("DLQ");
        dlq.setOperationType("ALERT");
        dlq.setOperationDesc("死信队列积压告警");
        dlq.setRequestMethod(null);
        dlq.setRequestUrl(null);

        OperationLogResponse response = OperationLogApiFacade.toResponse(dlq);
        assertEquals("DLQ", response.operationModule());
        assertEquals("ALERT", response.operationType());
        assertEquals("死信队列积压告警", response.operationDesc());
    }

    @Test
    @DisplayName("新写入的行已经是对的，回填不改动它")
    void leavesNewRowsUnchanged() {
        SysOperationLog fresh = legacyRow("POST", "/api/matching/record/execute");
        fresh.setOperationModule("MATCHING");
        fresh.setOperationType("CREATE");
        fresh.setOperationDesc("新增 · 人岗匹配（POST /api/matching/record/execute）");

        OperationLogResponse response = OperationLogApiFacade.toResponse(fresh);
        assertEquals("MATCHING", response.operationModule());
        assertEquals("CREATE", response.operationType());
    }
}
