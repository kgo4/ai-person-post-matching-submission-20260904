package com.example.matching.port.closure;

import com.example.matching.dto.closure.MatchDiagnosisResult;

public interface MatchDiagnosisQueryPort {

    MatchDiagnosisResult diagnoseMatchingRecord(Long matchingRecordId);
}
