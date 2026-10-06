package com.example.matching.application.rag;

import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.config.VolcengineKnowledgeBaseProperties;
import com.example.matching.service.rag.CloudKnowledgeSyncService;
import com.example.matching.service.rag.RagRetrievalService;
import com.example.matching.service.rag.RagScenarioEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class RagCloudSyncApiFacade {

    private final CloudKnowledgeSyncService cloudKnowledgeSyncService;
    private final VolcengineKnowledgeBaseProperties kbProperties;
    private final RagRetrievalService ragRetrievalService;

    public Map<String, Object> getStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", kbProperties.isEnabled());
        result.put("usable", kbProperties.isUsable());
        result.put("providerMode", kbProperties.getProviderMode());
        result.put("resourceId", mask(kbProperties.getResourceId()));
        result.put("collectionName", kbProperties.getCollectionName());
        result.put("endpoint", kbProperties.getEndpoint());
        result.put("hasCredentials", kbProperties.hasCredentials());
        result.put("hasCollectionTarget", kbProperties.hasCollectionTarget());

        List<Map<String, Object>> scenarios = new ArrayList<>();
        for (RagScenarioEnum s : RagScenarioEnum.values()) {
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("key", s.name());
            sm.put("name", s.getName());
            sm.put("allowCloud", s.isAllowCloud());
            scenarios.add(sm);
        }
        result.put("scenarios", scenarios);

        return result;
    }

    public Map<String, Object> sync(String sourceType, int limit, boolean dryRun) {
        return cloudKnowledgeSyncService.syncSystemKnowledge(sourceType, limit, dryRun);
    }

    /**
     * 更新云端知识库配置。
     *
     * <p>⚠️ **刻意做成「拒绝运行期修改」**，而不是真的写进 {@code kbProperties}。
     *
     * <p>原因：{@code VolcengineKnowledgeBaseProperties} 是启动期绑定的配置 Bean，
     * 改写它的字段**既不会落库、也不会写回配置文件** → 重启即回滚。而接口原先返回 200，
     * 前端据此提示「保存成功」，用户会以为配置已生效 —— 这是典型的静默失效，
     * 比"改不了"更糟。既然运行期无法真正配置，就如实拒绝并说明唯一可行的做法。
     *
     * <p>顺带规避一个安全问题：本端点不在 {@code OperationLogRedactor.SENSITIVE_ENDPOINTS}
     * 名单内，若继续接受写入，请求体里的 {@code accessKey}/{@code secretKey}
     * 会被操作日志切面原样落进 {@code sys_operation_log}。
     *
     * @throws BusinessException 恒定抛出，说明「连接信息只支持系统启动时配置」
     */
    public Map<String, Object> updateConfig(Map<String, Object> request) {
        throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                "云知识库连接信息只支持系统启动时由服务端配置提供，运行期不支持修改。"
                        + "请通过服务端环境变量或配置文件设置后重启应用。");
    }

    public Map<String, Object> search(String queryText, String scenario) {
        RagScenarioEnum scenarioEnum;
        try {
            scenarioEnum = RagScenarioEnum.valueOf(scenario);
        } catch (IllegalArgumentException e) {
            scenarioEnum = RagScenarioEnum.JD_ABILITY_EXTRACT;
        }

        var retrievalResult = ragRetrievalService.retrieve(queryText, scenarioEnum);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("queryText", queryText);
        result.put("scenario", scenarioEnum.getName());
        result.put("providerMode", retrievalResult.getProviderMode());
        result.put("fallbackUsed", retrievalResult.isFallbackUsed());
        result.put("allowCloud", scenarioEnum.isAllowCloud());
        result.put("hits", retrievalResult.getHits());
        result.put("hitCount", retrievalResult.getHitCount());
        result.put("latencyMs", retrievalResult.getLatencyMs());
        return result;
    }

    private static String mask(String value) {
        if (value == null || value.isBlank()) return "";
        if (value.length() <= 8) return "****";
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }
}
