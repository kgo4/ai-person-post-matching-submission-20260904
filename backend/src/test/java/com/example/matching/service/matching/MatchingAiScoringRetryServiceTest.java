package com.example.matching.service.matching;

import com.example.matching.common.constant.AiConstant;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.mapper.matching.MatchingRecordMapper;
import org.junit.jupiter.api.Test;

import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MatchingAiScoringRetryServiceTest {

    @Test
    void submitRetryReturnsRecordToRetryStateWhenExecutorRejectsIt() {
        MatchingRecordMapper mapper = mock(MatchingRecordMapper.class);
        MatchingAiScoringStateMachine stateMachine = mock(MatchingAiScoringStateMachine.class);
        MatchingRecord record = new MatchingRecord();
        record.setId(9L);
        record.setAiScoringStatus(AiConstant.AI_SCORING_PENDING);
        record.setAiScoringAttemptCount(1);
        when(mapper.selectById(9L)).thenReturn(record);
        when(stateMachine.claimForProcessing(9L)).thenReturn(true);

        MatchingAiScoringRetryService service = new MatchingAiScoringRetryService(
                mapper, stateMachine, mock(MatchingDataQueryService.class), mock(MatchingAiAnalysisService.class),
                mock(MatchingAlgorithmService.class), mock(MatchingScoreService.class),
                mock(MatchingTrainingWeightProfileStore.class), mock(MatchingEvidenceScoreCalculator.class),
                mock(MatchingCacheInvalidator.class),
                command -> { throw new RejectedExecutionException("full"); });

        assertThat(service.submitRetry(9L)).isFalse();
        verify(stateMachine).failIfProcessing(eq(9L), contains("rejected"), anyInt());
    }
}
