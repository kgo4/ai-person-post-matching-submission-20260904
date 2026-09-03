package com.example.matching.controller.system;

import com.example.matching.application.system.DlqAdminApiFacade;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.result.R;
import com.example.matching.service.common.DlqReplayService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DlqAdminControllerTest {

    @Test
    void summaryWithoutThresholdReturnsSummaryAsIs() {
        DlqAdminApiFacade facade = mock(DlqAdminApiFacade.class);
        DlqAdminController controller = new DlqAdminController(facade);

        LocalDateTime checkedAt = LocalDateTime.now();
        DlqReplayService.DlqSummary summary = new DlqReplayService.DlqSummary(5, checkedAt);
        when(facade.summary()).thenReturn(summary);

        R<DlqReplayService.DlqSummary> response = controller.summary(null);

        assertThat(response.getData()).isSameAs(summary);
        assertThat(response.getData().messageCount()).isEqualTo(5);
        assertThat(response.getData().alerting()).isFalse();
    }

    @Test
    void summaryWithThresholdAppliesThreshold() {
        DlqAdminApiFacade facade = mock(DlqAdminApiFacade.class);
        DlqAdminController controller = new DlqAdminController(facade);

        LocalDateTime checkedAt = LocalDateTime.now();
        DlqReplayService.DlqSummary summary = new DlqReplayService.DlqSummary(5, checkedAt);
        when(facade.summary()).thenReturn(summary);

        R<DlqReplayService.DlqSummary> response = controller.summary(3L);

        assertThat(response.getData()).isNotSameAs(summary);
        assertThat(response.getData().alertThreshold()).isEqualTo(3);
        assertThat(response.getData().alerting()).isTrue();
    }

    @Test
    void summaryWithThresholdBelowMessageCountNotAlerting() {
        DlqAdminApiFacade facade = mock(DlqAdminApiFacade.class);
        DlqAdminController controller = new DlqAdminController(facade);

        DlqReplayService.DlqSummary summary = new DlqReplayService.DlqSummary(2, LocalDateTime.now());
        when(facade.summary()).thenReturn(summary);

        R<DlqReplayService.DlqSummary> response = controller.summary(5L);

        assertThat(response.getData().alertThreshold()).isEqualTo(5);
        assertThat(response.getData().alerting()).isFalse();
    }

    @Test
    void replayReturnsReplayedCount() {
        DlqAdminApiFacade facade = mock(DlqAdminApiFacade.class);
        DlqAdminController controller = new DlqAdminController(facade);

        when(facade.replay(5)).thenReturn(5);

        R<Integer> response = controller.replay(5);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("已重放 5 条消息");
        assertThat(response.getData()).isEqualTo(5);
    }

    @Test
    void replayRejectsCountOutOfRange() {
        DlqAdminApiFacade facade = mock(DlqAdminApiFacade.class);
        DlqAdminController controller = new DlqAdminController(facade);

        assertThatThrownBy(() -> controller.replay(0))
                .isInstanceOf(BusinessException.class)
                .hasMessage("条数必须在 1..100 之间");
        assertThatThrownBy(() -> controller.replay(101))
                .isInstanceOf(BusinessException.class)
                .hasMessage("条数必须在 1..100 之间");
        verify(facade, never()).replay(anyInt());
    }

    @Test
    void discardReturnsDiscardedCount() {
        DlqAdminApiFacade facade = mock(DlqAdminApiFacade.class);
        DlqAdminController controller = new DlqAdminController(facade);

        when(facade.discard(2, "测试丢弃")).thenReturn(2);

        R<Integer> response = controller.discard(2, "测试丢弃");

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("已丢弃 2 条消息");
        assertThat(response.getData()).isEqualTo(2);
        verify(facade).discard(2, "测试丢弃");
    }

    @Test
    void discardRejectsBlankReason() {
        DlqAdminApiFacade facade = mock(DlqAdminApiFacade.class);
        DlqAdminController controller = new DlqAdminController(facade);

        assertThatThrownBy(() -> controller.discard(1, "   "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("丢弃原因不能为空");
        verify(facade, never()).discard(anyInt(), any());
    }

    @Test
    void discardRejectsNullReason() {
        DlqAdminApiFacade facade = mock(DlqAdminApiFacade.class);
        DlqAdminController controller = new DlqAdminController(facade);

        assertThatThrownBy(() -> controller.discard(1, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("丢弃原因不能为空");
        verify(facade, never()).discard(anyInt(), any());
    }

    @Test
    void discardRejectsCountOutOfRange() {
        DlqAdminApiFacade facade = mock(DlqAdminApiFacade.class);
        DlqAdminController controller = new DlqAdminController(facade);

        assertThatThrownBy(() -> controller.discard(150, "原因"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("条数必须在 1..100 之间");
        verify(facade, never()).discard(anyInt(), any());
    }
}
