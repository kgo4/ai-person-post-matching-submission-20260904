package com.example.matching.service.matching;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.matching.common.constant.AiConstant;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.mapper.matching.MatchingRecordMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchingAiScoringStateMachineTest {

    @Test
    void stateTransitionWritesAreTransactional() {
        for (Method method : MatchingAiScoringStateMachine.class.getDeclaredMethods()) {
            if (List.of("claimForProcessing", "complete", "fail", "failPending").contains(method.getName())) {
                assertThat(method.isAnnotationPresent(Transactional.class))
                        .as("%s must run inside a transaction", method.getName())
                        .isTrue();
            }
        }
    }

    @Test
    void failSchedulesRetryBeforeMaximumAttempts() {
        MatchingRecordMapper mapper = mock(MatchingRecordMapper.class);
        when(mapper.update(any(MatchingRecord.class), any(LambdaUpdateWrapper.class))).thenReturn(1);
        MatchingAiScoringStateMachine stateMachine = new MatchingAiScoringStateMachine(mapper);

        stateMachine.failIfProcessing(12L, "provider timeout", 0);

        ArgumentCaptor<MatchingRecord> updateCaptor = ArgumentCaptor.forClass(MatchingRecord.class);
        verify(mapper).update(updateCaptor.capture(), any(LambdaUpdateWrapper.class));
        MatchingRecord update = updateCaptor.getValue();
        assertThat(update.getAiScoringStatus()).isEqualTo(AiConstant.AI_SCORING_PENDING);
        assertThat(update.getAiScoringAttemptCount()).isEqualTo(1);
        assertThat(update.getAiScoringNextRetryAt()).isAfter(LocalDateTime.now().minusSeconds(1));
    }

    @Test
    void failStopsRetryingAtMaximumAttempts() {
        MatchingRecordMapper mapper = mock(MatchingRecordMapper.class);
        when(mapper.update(any(MatchingRecord.class), any(LambdaUpdateWrapper.class))).thenReturn(1);
        MatchingAiScoringStateMachine stateMachine = new MatchingAiScoringStateMachine(mapper);

        stateMachine.failIfProcessing(12L, "provider timeout", AiConstant.AI_SCORING_MAX_RETRIES - 1);

        ArgumentCaptor<MatchingRecord> updateCaptor = ArgumentCaptor.forClass(MatchingRecord.class);
        verify(mapper).update(updateCaptor.capture(), any(LambdaUpdateWrapper.class));
        MatchingRecord update = updateCaptor.getValue();
        assertThat(update.getAiScoringStatus()).isEqualTo(AiConstant.AI_SCORING_FAILED);
        assertThat(update.getAiScoringNextRetryAt()).isNull();
    }

    @Test
    void failUsesExponentialBackoff() {
        MatchingRecordMapper mapper = mock(MatchingRecordMapper.class);
        when(mapper.update(any(MatchingRecord.class), any(LambdaUpdateWrapper.class))).thenReturn(1);
        MatchingAiScoringStateMachine stateMachine = new MatchingAiScoringStateMachine(mapper);
        stateMachine.failIfProcessing(12L, "provider timeout", 1);
        ArgumentCaptor<MatchingRecord> captor = ArgumentCaptor.forClass(MatchingRecord.class);
        verify(mapper).update(captor.capture(), any(LambdaUpdateWrapper.class));
        assertThat(captor.getValue().getAiScoringNextRetryAt())
                .isAfter(LocalDateTime.now().plusSeconds(25));
    }
}
