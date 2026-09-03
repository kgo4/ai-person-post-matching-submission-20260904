package com.example.matching.application.system;

import com.example.matching.service.common.DlqReplayService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * DLQ 管理门面（controller -> application facade -> service）
 */
@Service
@RequiredArgsConstructor
public class DlqAdminApiFacade {

    private final DlqReplayService dlqReplayService;

    public DlqReplayService.DlqSummary summary() {
        return dlqReplayService.summary();
    }

    public int replay(int count) {
        return dlqReplayService.replay(count);
    }

    public int discard(int count, String reason) {
        return dlqReplayService.discard(count, reason);
    }
}
