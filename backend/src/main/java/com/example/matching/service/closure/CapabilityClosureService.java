package com.example.matching.service.closure;

import com.example.matching.dto.closure.CapabilityClosureResult;
import com.example.matching.dto.closure.LearningOutcomeConfirmDTO;
import com.example.matching.dto.closure.MatchDiagnosisResult;
import com.example.matching.port.closure.MatchDiagnosisQueryPort;

public interface CapabilityClosureService extends MatchDiagnosisQueryPort {

    CapabilityClosureResult onEmergingPostConfirmed(Long postId);

    CapabilityClosureResult onPostEvolutionApplied(Long taskId);

    MatchDiagnosisResult diagnoseMatchingRecord(Long matchingRecordId);

    CapabilityClosureResult onLearningOutcomeConfirmed(LearningOutcomeConfirmDTO dto);

    CapabilityClosureResult getLatestByBusinessKey(String businessKey);
}
