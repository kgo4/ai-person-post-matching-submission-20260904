package com.example.matching.controller.system;

import com.example.matching.application.system.OperationLogApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.system.api.OperationLogResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SysOperationLogControllerTest {

    private static OperationLogResponse createLog(Long id) {
        return new OperationLogResponse(
                id, 1L, "管理员", "用户管理", "CREATE", "新增用户",
                "POST", "/api/system/user", "{}", "{}",
                "127.0.0.1", LocalDateTime.now(), 15L);
    }

    @Test
    void pageReturnsLogPage() {
        OperationLogApiFacade facade = mock(OperationLogApiFacade.class);
        SysOperationLogController controller = new SysOperationLogController(facade);

        OperationLogResponse log = createLog(1L);
        LocalDateTime start = LocalDateTime.of(2025, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2025, 12, 31, 23, 59);
        PageResponse<OperationLogResponse> page = new PageResponse<>(List.of(log), 1, 1, 10, 1);
        when(facade.page(1, 10, "用户管理", 1L, start, end)).thenReturn(page);

        R<PageResponse<OperationLogResponse>> response =
                controller.page(1, 10, "用户管理", 1L, start, end);

        assertThat(response.getData().records()).containsExactly(log);
        assertThat(response.getData().total()).isEqualTo(1);
    }

    @Test
    void pageWithNullFiltersReturnsLogPage() {
        OperationLogApiFacade facade = mock(OperationLogApiFacade.class);
        SysOperationLogController controller = new SysOperationLogController(facade);

        PageResponse<OperationLogResponse> page = new PageResponse<>(List.of(), 0, 1, 10, 0);
        when(facade.page(1, 10, null, null, null, null)).thenReturn(page);

        R<PageResponse<OperationLogResponse>> response = controller.page(1, 10, null, null, null, null);

        assertThat(response.getData().records()).isEmpty();
    }

    @Test
    void getByIdReturnsLogDetail() {
        OperationLogApiFacade facade = mock(OperationLogApiFacade.class);
        SysOperationLogController controller = new SysOperationLogController(facade);

        OperationLogResponse log = createLog(1L);
        when(facade.get(1L)).thenReturn(log);

        R<OperationLogResponse> response = controller.getById(1L);

        assertThat(response.getData()).isEqualTo(log);
    }
}
