package com.example.matching.service.system;

import com.example.matching.config.ResilientMilvusClient;
import io.milvus.param.collection.ShowCollectionsParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查服务 — Controller 不直接访问基础设施客户端。
 */
@Slf4j
@Service
public class HealthService {

    @Autowired(required = false)
    private ResilientMilvusClient resilientMilvusClient;

    public Map<String, Object> checkMilvus() {
        Map<String, Object> result = new LinkedHashMap<>();
        if (resilientMilvusClient == null) {
            result.put("status", "FAIL");
            result.put("message", "Milvus not configured");
            return result;
        }
        var milvusServiceClient = resilientMilvusClient.getClient();
        if (milvusServiceClient == null) {
            result.put("status", "FAIL");
            result.put("message", "Milvus not connected");
            return result;
        }
        try {
            var resp = milvusServiceClient.showCollections(ShowCollectionsParam.newBuilder().build());
            result.put("status", resp.getStatus() == 0 ? "OK" : "FAIL");
            result.put("connected", resp.getStatus() == 0);
            result.put("collections", resp.getData() != null ? resp.getData().getCollectionNamesCount() : 0);
        } catch (Exception e) {
            log.warn("Milvus health check failed: {}", e.getMessage());
            result.put("status", "FAIL");
            result.put("message", e.getMessage());
        }
        return result;
    }
}
