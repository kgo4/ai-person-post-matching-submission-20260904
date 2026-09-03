package com.example.matching.application.closure;

import com.example.matching.dto.closure.CapabilityClosureResult;
import com.example.matching.dto.closure.ComprehensiveDiagnosisResultDTO;
import com.example.matching.dto.closure.LearningOutcomeConfirmDTO;
import com.example.matching.dto.closure.MatchDiagnosisResult;
import com.example.matching.service.closure.CapabilityClosureService;
import com.example.matching.service.closure.ComprehensiveDiagnosisService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CapabilityClosureApiFacade {

    private final CapabilityClosureService capabilityClosureService;
    private final ComprehensiveDiagnosisService comprehensiveDiagnosisService;

    public MatchDiagnosisResult diagnoseMatchingRecord(Long recordId) {
        return capabilityClosureService.diagnoseMatchingRecord(recordId);
    }

    public ComprehensiveDiagnosisResultDTO comprehensiveDiagnosis(Long recordId) {
        return comprehensiveDiagnosisService.diagnose(recordId);
    }

    public CapabilityClosureResult confirmLearningOutcome(LearningOutcomeConfirmDTO dto) {
        return capabilityClosureService.onLearningOutcomeConfirmed(dto);
    }

    public CapabilityClosureResult getLatestByBusinessKey(String businessKey) {
        return capabilityClosureService.getLatestByBusinessKey(businessKey);
    }
}
