package com.example.matching.service.closure;

import com.example.matching.dto.closure.ComprehensiveDiagnosisResultDTO;
import com.example.matching.dto.closure.ComprehensiveDiagnosisFactDTO;
import com.example.matching.ai.service.LangChain4jChatService;
import com.example.matching.ai.service.PromptTemplateService;
import com.example.matching.service.closure.impl.ComprehensiveDiagnosisServiceImpl;
import com.example.matching.service.closure.impl.DiagnosisAiAnalyzer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ComprehensiveDiagnosisGroundingTest {

    @Test
    void filterUngroundedAiAnalysisKeepsConclusionWhenSupportingDiagnosisIsGrounded() {
        DiagnosisAiAnalyzer analyzer = new DiagnosisAiAnalyzer(null, null, null,
                new com.example.matching.infrastructure.llm.LlmResponseParser(new com.fasterxml.jackson.databind.ObjectMapper()),
                null);

        ComprehensiveDiagnosisResultDTO.AiDiagnosisAnalysis analysis =
                new ComprehensiveDiagnosisResultDTO.AiDiagnosisAnalysis();
        analysis.setOverallConclusion("The match risk is mainly Java capability gap.");

        ComprehensiveDiagnosisResultDTO.DimensionDiagnosis groundedDimension =
                new ComprehensiveDiagnosisResultDTO.DimensionDiagnosis();
        groundedDimension.setAnalysis("Java gap is supported by the fact package.");
        groundedDimension.setSourceRefs(List.of("fact:ABILITY"));

        ComprehensiveDiagnosisResultDTO.DimensionDiagnosis ungroundedDimension =
                new ComprehensiveDiagnosisResultDTO.DimensionDiagnosis();
        ungroundedDimension.setAnalysis("The candidate lacks architecture ownership.");

        ComprehensiveDiagnosisResultDTO.PriorityAction groundedAction =
                new ComprehensiveDiagnosisResultDTO.PriorityAction();
        groundedAction.setAction("Arrange Java upskilling.");
        groundedAction.setSourceRefs(List.of("fact:ABILITY"));

        ComprehensiveDiagnosisResultDTO.PriorityAction ungroundedAction =
                new ComprehensiveDiagnosisResultDTO.PriorityAction();
        ungroundedAction.setAction("Assign an architecture mentor immediately.");

        analysis.setDimensions(List.of(groundedDimension, ungroundedDimension));
        analysis.setPriorityActions(List.of(groundedAction, ungroundedAction));

        ComprehensiveDiagnosisResultDTO.AiDiagnosisAnalysis filtered =
                analyzer.filterUngroundedAiAnalysis(analysis);

        assertThat(filtered).isNotNull();
        assertThat(filtered.getOverallConclusion())
                .isEqualTo("The match risk is mainly Java capability gap.");
        assertThat(filtered.getDimensions()).containsExactly(groundedDimension);
        assertThat(filtered.getPriorityActions()).containsExactly(groundedAction);
        assertThat(filtered.getBlockedClaims())
                .extracting(ComprehensiveDiagnosisResultDTO.BlockedClaim::getClaim)
                .contains(
                        "The candidate lacks architecture ownership.",
                        "Assign an architecture mentor immediately.");
        assertThat(filtered.getBlockedClaims())
                .extracting(ComprehensiveDiagnosisResultDTO.BlockedClaim::getConfidence)
                .allMatch("UNSUPPORTED"::equals);
        assertThat(filtered.getBlockedClaims())
                .extracting(ComprehensiveDiagnosisResultDTO.BlockedClaim::getReason)
                .allMatch("Missing grounded source reference"::equals);
    }

    @Test
    void constructorHasNoHarnessArgument() {
        var ctors = ComprehensiveDiagnosisServiceImpl.class.getDeclaredConstructors();
        for (var ctor : ctors) {
            for (var param : ctor.getParameterTypes()) {
                assertThat(param.getName())
                        .doesNotContain("AiTrustHarnessService");
            }
        }
    }

    @Test
    void buildAiAnalysisOnlyAcceptsTheFactReferenceProvidedToTheModel() {
        LangChain4jChatService chatService = mock(LangChain4jChatService.class);
        PromptTemplateService promptTemplateService = mock(PromptTemplateService.class);
        ArgumentCaptor<java.util.Map<String, Object>> promptModel = ArgumentCaptor.forClass(java.util.Map.class);
        when(promptTemplateService.render(eq("gap-diagnosis-prompt"), promptModel.capture())).thenReturn("prompt");
        when(chatService.chat(eq("gap-diagnosis"), eq("prompt"), any())).thenReturn("""
                {"overallConclusion":"Unsupported conclusion","dimensions":[{"dimension":"ABILITY","analysis":"Unsupported dimension","sourceRefs":["fact:UNRELATED:99"]}],"priorityActions":[]}
                """);

        DiagnosisAiAnalyzer analyzer = new DiagnosisAiAnalyzer(chatService, promptTemplateService,
                new ObjectMapper(), new com.example.matching.infrastructure.llm.LlmResponseParser(new ObjectMapper()), null);
        ComprehensiveDiagnosisFactDTO fact = new ComprehensiveDiagnosisFactDTO();
        fact.setRecordId(88L);

        ComprehensiveDiagnosisResultDTO.AiDiagnosisAnalysis result = analyzer.buildAiAnalysis(fact);

        assertThat(promptModel.getValue().get("allowedSourceRefs"))
                .isEqualTo(List.of("fact:MATCHING_RECORD:88"));
        assertThat(result.getOverallConclusion()).isNull();
        assertThat(result.getDimensions()).isEmpty();
    }
}
