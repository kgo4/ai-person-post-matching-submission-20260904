package com.example.matching.controller.rag;

import com.example.matching.application.rag.RagCloudSyncApiFacade;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.result.R;
import com.example.matching.config.VolcengineKnowledgeBaseProperties;
import com.example.matching.service.rag.CloudKnowledgeSyncService;
import com.example.matching.service.rag.RagRetrievalService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagCloudSyncControllerTest {

    @Test
    void statusReturnsCloudKnowledgeBaseStatus() {
        RagCloudSyncApiFacade facade = mock(RagCloudSyncApiFacade.class);
        RagCloudSyncController controller = new RagCloudSyncController(facade);

        Map<String, Object> status = Map.of(
                "enabled", true, "usable", true, "providerMode", "volcengine",
                "resourceId", "ak****xxxx", "collectionName", "person-post",
                "endpoint", "https://example.com", "hasCredentials", true,
                "hasCollectionTarget", true);
        when(facade.getStatus()).thenReturn(status);

        R<Map<String, Object>> response = controller.status();

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData())
                .containsEntry("enabled", true)
                .containsEntry("providerMode", "volcengine")
                .containsEntry("collectionName", "person-post");
    }

    @Test
    void syncDelegatesToFacade() {
        RagCloudSyncApiFacade facade = mock(RagCloudSyncApiFacade.class);
        RagCloudSyncController controller = new RagCloudSyncController(facade);

        Map<String, Object> syncResult = Map.of("synced", 10, "failed", 0, "dryRun", true);
        when(facade.sync("POST", 50, false)).thenReturn(syncResult);

        R<Map<String, Object>> response = controller.sync("POST", 50, false);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).containsEntry("synced", 10).containsEntry("failed", 0);
    }

    @Test
    void searchDelegatesToFacade() {
        RagCloudSyncApiFacade facade = mock(RagCloudSyncApiFacade.class);
        RagCloudSyncController controller = new RagCloudSyncController(facade);

        Map<String, Object> searchResult = Map.of(
                "queryText", "Java工程师", "scenario", "JD_ABILITY_EXTRACT",
                "providerMode", "vector", "fallbackUsed", false,
                "allowCloud", true, "hitCount", 3, "latencyMs", 12L);
        when(facade.search("Java工程师", "JD_ABILITY_EXTRACT")).thenReturn(searchResult);

        R<Map<String, Object>> response = controller.search("Java工程师", "JD_ABILITY_EXTRACT");

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData())
                .containsEntry("queryText", "Java工程师")
                .containsEntry("scenario", "JD_ABILITY_EXTRACT")
                .containsEntry("hitCount", 3);
    }

    /**
     * 配置端点必须**拒绝运行期修改**，而不是「改内存 + 返回 200」。
     *
     * <p>回归背景：原实现只写 `VolcengineKnowledgeBaseProperties` 的字段（不落库、不写配置），
     * 前端据此提示「保存成功」，而重启即回滚 —— 静默失效。
     * 这里直接打真实 facade（只 mock 依赖），锁住"抛异常 + 不返回 200"。
     */
    @Test
    void updateConfigRejectsRuntimeModification() {
        RagCloudSyncApiFacade facade = new RagCloudSyncApiFacade(
                mock(CloudKnowledgeSyncService.class),
                new VolcengineKnowledgeBaseProperties(),
                mock(RagRetrievalService.class));

        assertThatThrownBy(() -> facade.updateConfig(Map.of("enabled", true, "accessKey", "ak")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("启动时")
                .hasMessageContaining("运行期不支持修改");
    }
}
