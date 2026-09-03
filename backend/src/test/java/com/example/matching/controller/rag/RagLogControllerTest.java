package com.example.matching.controller.rag;

import com.example.matching.application.rag.RagLogApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.rag.api.RagQueryLogResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagLogControllerTest {

    private static RagQueryLogResponse createLog(Long id) {
        return new RagQueryLogResponse(
                id, "QL_001", "JD_ABILITY_EXTRACT", "Java工程师需要什么能力",
                5, "chunk-1,chunk-2", "上下文", "prompt快照", "响应快照",
                25L, 2, 1L, null);
    }

    @Test
    void pageLogsReturnsPage() {
        RagLogApiFacade facade = mock(RagLogApiFacade.class);
        RagLogController controller = new RagLogController(facade);

        RagQueryLogResponse log = createLog(1L);
        PageResponse<RagQueryLogResponse> page = new PageResponse<>(List.of(log), 1, 1, 10, 1);
        when(facade.pageLogs(1, 10, "JD_ABILITY_EXTRACT")).thenReturn(page);

        R<PageResponse<RagQueryLogResponse>> response =
                controller.pageLogs(1, 10, "JD_ABILITY_EXTRACT");

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().records()).containsExactly(log);
        assertThat(response.getData().total()).isEqualTo(1);
    }

    @Test
    void pageLogsWithoutScenarioReturnsAll() {
        RagLogApiFacade facade = mock(RagLogApiFacade.class);
        RagLogController controller = new RagLogController(facade);

        PageResponse<RagQueryLogResponse> empty = new PageResponse<>(List.of(), 0, 1, 10, 0);
        when(facade.pageLogs(1, 10, null)).thenReturn(empty);

        R<PageResponse<RagQueryLogResponse>> response = controller.pageLogs(1, 10, null);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().records()).isEmpty();
    }

    @Test
    void getLogReturnsLog() {
        RagLogApiFacade facade = mock(RagLogApiFacade.class);
        RagLogController controller = new RagLogController(facade);

        RagQueryLogResponse log = createLog(2L);
        when(facade.getLog(2L)).thenReturn(log);

        R<RagQueryLogResponse> response = controller.getLog(2L);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isEqualTo(log);
    }
}
