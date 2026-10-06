package com.example.matching.service.ability;

import com.example.matching.application.agent.*;
import com.example.matching.infrastructure.llm.LlmResponseParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for typed domain records and LLM response parsing.
 * Verifies the new application-layer types work correctly.
 */
@DisplayName("Typed Domain Records and LLM Parser")
class TypedDomainRecordsTest {

    @Nested
    @DisplayName("AbilityClaimCandidate validation")
    class ClaimValidation {

        @Test
        @DisplayName("Valid claim passes validation")
        void validClaim() {
            AbilityClaimCandidate claim = new AbilityClaimCandidate(
                    1L, 10L, "Java", "java", 3,
                    ClaimSource.RESUME_PARSE, 100L,
                    EvidenceBundle.of("简历中提到", List.of()),
                    BigDecimal.valueOf(75), null, null, null
            );
            assertTrue(claim.isValid());
        }

        @Test
        @DisplayName("Claim without employeeId fails validation")
        void missingEmployee() {
            AbilityClaimCandidate claim = new AbilityClaimCandidate(
                    null, 10L, "Java", "java", 3,
                    ClaimSource.RESUME_PARSE, 100L,
                    EvidenceBundle.empty(),
                    BigDecimal.valueOf(75), null, null, null
            );
            assertFalse(claim.isValid());
        }

        @Test
        @DisplayName("Claim without abilityName fails validation")
        void missingName() {
            AbilityClaimCandidate claim = new AbilityClaimCandidate(
                    1L, 10L, null, null, 3,
                    ClaimSource.RESUME_PARSE, 100L,
                    EvidenceBundle.empty(),
                    BigDecimal.valueOf(75), null, null, null
            );
            assertFalse(claim.isValid());
        }

        @Test
        @DisplayName("Claim with level out of range fails validation")
        void invalidLevel() {
            AbilityClaimCandidate claim = new AbilityClaimCandidate(
                    1L, 10L, "Java", "java", 6,
                    ClaimSource.RESUME_PARSE, 100L,
                    EvidenceBundle.empty(),
                    BigDecimal.valueOf(75), null, null, null
            );
            assertFalse(claim.isValid());
        }

        @Test
        @DisplayName("hasResolvedTag returns true when tagId is set")
        void hasResolvedTag() {
            AbilityClaimCandidate claim = new AbilityClaimCandidate(
                    1L, 10L, "Java", "java", 3,
                    ClaimSource.RESUME_PARSE, 100L,
                    EvidenceBundle.empty(),
                    BigDecimal.valueOf(75), null, null, null
            );
            assertTrue(claim.hasResolvedTag());
        }

        @Test
        @DisplayName("hasResolvedTag returns false when tagId is null")
        void noResolvedTag() {
            AbilityClaimCandidate claim = new AbilityClaimCandidate(
                    1L, null, "Java", "java", 3,
                    ClaimSource.RESUME_PARSE, 100L,
                    EvidenceBundle.empty(),
                    BigDecimal.valueOf(75), null, null, null
            );
            assertFalse(claim.hasResolvedTag());
        }
    }

    @Nested
    @DisplayName("ClaimSource enum")
    class ClaimSourceTests {

        @Test
        @DisplayName("fromString returns correct enum")
        void fromString() {
            assertEquals(ClaimSource.RESUME_PARSE, ClaimSource.fromString("RESUME_PARSE"));
            assertEquals(ClaimSource.AI_PROJECT, ClaimSource.fromString("PMS"));
        }

        @Test
        @DisplayName("fromString maps unknown legacy values to manual")
        void fromStringUnknown() {
            assertEquals(ClaimSource.MANUAL, ClaimSource.fromString("UNKNOWN"));
            assertEquals(ClaimSource.MANUAL, ClaimSource.fromString(null));
        }

        @Test
        @DisplayName("Each source has a non-zero default weight")
        void defaultWeights() {
            for (ClaimSource source : ClaimSource.values()) {
                assertTrue(source.getDefaultWeight() > 0,
                        source.name() + " should have a positive default weight");
            }
        }
    }

    @Nested
    @DisplayName("EvidenceBundle")
    class EvidenceBundleTests {

        @Test
        @DisplayName("empty() has no evidence")
        void empty() {
            EvidenceBundle empty = EvidenceBundle.empty();
            assertFalse(empty.hasEvidence());
            assertTrue(empty.sourceReferences().isEmpty());
        }

        @Test
        @DisplayName("of() creates bundle with text")
        void ofText() {
            EvidenceBundle bundle = EvidenceBundle.of("test evidence", null);
            assertTrue(bundle.hasEvidence());
            assertNotNull(bundle.sourceReferences());
        }
    }

    @Nested
    @DisplayName("MatchingAgentContext predicates")
    class ContextTests {

        @Test
        @DisplayName("isComplete returns true with all fields")
        void complete() {
            MatchingAgentContext ctx = new MatchingAgentContext(
                    1L, 2L, 3L,
                    List.of(new EmployeeAbilitySnapshot(1L, "Java", 3, "EMP_ABILITY", BigDecimal.valueOf(80), 2)),
                    List.of(new PostRequirementSnapshot(1L, "Java", 4, BigDecimal.valueOf(25), true, true)),
                    List.of(),
                    BigDecimal.valueOf(70)
            );
            assertTrue(ctx.isComplete());
        }

        @Test
        @DisplayName("isComplete returns false without abilities")
        void incompleteNoAbilities() {
            MatchingAgentContext ctx = new MatchingAgentContext(
                    1L, 2L, 3L,
                    List.of(),
                    List.of(new PostRequirementSnapshot(1L, "Java", 4, BigDecimal.valueOf(25), true, true)),
                    List.of(),
                    BigDecimal.valueOf(70)
            );
            assertFalse(ctx.isComplete());
        }
    }

    @Nested
    @DisplayName("LlmResponseParser")
    class ParserTests {

        private LlmResponseParser parser;

        @BeforeEach
        void setUp() {
            parser = new LlmResponseParser(new ObjectMapper());
        }

        @Test
        @DisplayName("Extracts JSON from markdown code block")
        void markdownCodeBlock() {
            String response = "Here is the result:\n```json\n{\"key\": \"value\"}\n```";
            String json = parser.extractJson(response);
            assertEquals("{\"key\": \"value\"}", json);
        }

        @Test
        @DisplayName("Extracts bare JSON object")
        void bareJson() {
            String response = "{\"key\": \"value\"}";
            String json = parser.extractJson(response);
            assertEquals("{\"key\": \"value\"}", json);
        }

        @Test
        @DisplayName("Extracts JSON with leading text")
        void leadingText() {
            String response = "Based on my analysis, the result is: {\"key\": \"value\"}. I hope this helps.";
            String json = parser.extractJson(response);
            assertEquals("{\"key\": \"value\"}", json);
        }

        @Test
        @DisplayName("Throws on empty response")
        void emptyResponse() {
            assertThrows(com.example.matching.infrastructure.llm.ModelResponseParseException.class,
                    () -> parser.extractJson(""));
        }

        @Test
        @DisplayName("Throws on null response")
        void nullResponse() {
            assertThrows(com.example.matching.infrastructure.llm.ModelResponseParseException.class,
                    () -> parser.extractJson(null));
        }

        @Test
        @DisplayName("Throws on non-JSON response")
        void nonJson() {
            assertThrows(com.example.matching.infrastructure.llm.ModelResponseParseException.class,
                    () -> parser.extractJson("I cannot provide that information."));
        }

        @Test
        @DisplayName("Rejects claim extraction response without claims array")
        void claimExtractionWithoutClaimsArray() {
            assertThrows(com.example.matching.infrastructure.llm.ModelResponseParseException.class,
                    () -> parser.parseAbilityClaims("{\"summary\":\"no claims\"}",
                            1L, ClaimSource.RESUME_PARSE, 2L));
        }

        @Test
        @DisplayName("Rejects non-JSON claim extraction response")
        void nonJsonClaimExtractionResponse() {
            assertThrows(com.example.matching.infrastructure.llm.ModelResponseParseException.class,
                    () -> parser.parseAbilityClaims("I cannot provide that information.",
                            1L, ClaimSource.RESUME_PARSE, 2L));
        }

        @Test
        @DisplayName("Extracts JSON array from markdown")
        void markdownArray() {
            String response = "```json\n[1, 2, 3]\n```";
            String json = parser.extractJson(response);
            assertEquals("[1, 2, 3]", json);
        }
    }
}
