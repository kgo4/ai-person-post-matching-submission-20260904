package com.example.matching.infrastructure.llm;

import com.example.matching.agent.lc4j.EmployeeAbilityAiService;
import com.example.matching.application.agent.AbilityClaimCandidate;
import com.example.matching.application.agent.CapabilityWorkflowFacade;
import com.example.matching.application.agent.ClaimExtractionException;
import com.example.matching.application.agent.ClaimExtractor;
import com.example.matching.application.agent.ClaimSource;
import com.example.matching.application.agent.EvidenceBundle;
import com.example.matching.application.agent.SourceReference;
import com.example.matching.agent.dto.person.PersonAbilityExtractionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * LangChain4j implementation of the claim extraction port.
 */
@Component
@RequiredArgsConstructor
public class LangChain4jClaimExtractor implements ClaimExtractor {

    private final ObjectProvider<EmployeeAbilityAiService> employeeAbilityAiServiceProvider;
    private final ObjectMapper objectMapper;
    private final LlmResponseParser responseParser;

    @Override
    public List<AbilityClaimCandidate> extract(CapabilityWorkflowFacade.AdmissionRequest request) {
        EmployeeAbilityAiService aiService = employeeAbilityAiServiceProvider.getIfAvailable();
        if (aiService == null) {
            throw new ClaimExtractionException(
                    "Capability claim extraction is unavailable because LangChain4j is not enabled");
        }

        try {
            String context = objectMapper.writeValueAsString(new ClaimExtractionContext(
                    request.employeeId(),
                    request.sourceType(),
                    request.sourceRefId(),
                    request.sourceText(),
                    request.sources() == null ? List.of() : request.sources()
            ));
            PersonAbilityExtractionResult response = com.example.matching.agent.config.AgentToolProvider
                    .withScope(() -> aiService.extractAbilities(context));
            if (response == null || response.getClaims() == null) {
                throw new ClaimExtractionException("Capability claim extraction returned no structured claims");
            }
            String auditOutput = objectMapper.writeValueAsString(response);
            return response.getClaims().stream()
                    .map(claim -> new AbilityClaimCandidate(
                            request.employeeId(), claim.getAbilityTagId(), claim.getAbilityName(),
                            claim.getNormalizedAbilityName() != null ? claim.getNormalizedAbilityName() : claim.getAbilityName(),
                            claim.getMasteryLevel(), request.sourceType(), request.sourceRefId(),
                            new EvidenceBundle(claim.getEvidenceText(),
                                    request.sources() == null ? List.of() : request.sources(), auditOutput),
                            claim.getConfidenceScore(), null, null, claim.getSimilarTagId()))
                    .toList();
        } catch (ModelResponseParseException e) {
            throw new ClaimExtractionException("Capability claim extraction returned invalid model output", e);
        } catch (ClaimExtractionException e) {
            throw e;
        } catch (Exception e) {
            throw new ClaimExtractionException("Capability claim extraction failed", e);
        }
    }

    private record ClaimExtractionContext(
            Long employeeId,
            ClaimSource sourceType,
            Long sourceRefId,
            String sourceText,
            List<SourceReference> sources
    ) {
    }
}
