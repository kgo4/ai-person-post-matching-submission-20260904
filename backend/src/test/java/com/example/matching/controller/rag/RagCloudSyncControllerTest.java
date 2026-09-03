package com.example.matching.controller.rag;

import com.example.matching.application.rag.RagCloudSyncApiFacade;
import com.example.matching.common.result.R;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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
}
