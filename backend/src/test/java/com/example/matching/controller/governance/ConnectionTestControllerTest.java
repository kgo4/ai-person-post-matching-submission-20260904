package com.example.matching.controller.governance;

import com.example.matching.application.system.HealthApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.controller.ConnectionTestController;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConnectionTestControllerTest {

    @Test
    void testMilvusReturnsHealthMap() {
        HealthApiFacade facade = mock(HealthApiFacade.class);
        ConnectionTestController controller = new ConnectionTestController(facade);
        when(facade.checkMilvus()).thenReturn(Map.of("status", "OK", "connected", true, "collections", 3));

        R<Map<String, Object>> response = controller.testMilvus();

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).containsEntry("status", "OK").containsEntry("connected", true);
    }

    @Test
    void testAllIncludesMysqlAndMilvusStatus() {
        HealthApiFacade facade = mock(HealthApiFacade.class);
        ConnectionTestController controller = new ConnectionTestController(facade);
        when(facade.checkMilvus()).thenReturn(Map.of("status", "OK"));

        R<Map<String, Object>> response = controller.testAll();

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).containsEntry("mysql", "OK").containsEntry("milvus", "OK");
    }

    @Test
    void testAllFallsBackToUnknownWhenMilvusStatusMissing() {
        HealthApiFacade facade = mock(HealthApiFacade.class);
        ConnectionTestController controller = new ConnectionTestController(facade);
        when(facade.checkMilvus()).thenReturn(Map.of("connected", false));

        R<Map<String, Object>> response = controller.testAll();

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).containsEntry("mysql", "OK").containsEntry("milvus", "UNKNOWN");
    }
}
