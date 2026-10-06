package com.example.matching.application.employee;

import com.example.matching.dto.employee.api.ResumeParseResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeParseApiFacadeTest {

    @Test
    void responseIncludesTheStoredResumeAnalysisPayload() {
        Set<String> fields = Arrays.stream(ResumeParseResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(fields).contains("parsedContent", "aiAnalysisResult");
    }
}
