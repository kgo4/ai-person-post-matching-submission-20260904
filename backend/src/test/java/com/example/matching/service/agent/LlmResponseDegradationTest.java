package com.example.matching.service.agent;

import com.example.matching.application.agent.AbilityClaimCandidate;
import com.example.matching.application.agent.ClaimSource;
import com.example.matching.infrastructure.llm.LlmResponseParser;
import com.example.matching.infrastructure.llm.ModelResponseParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests LLM response handling degradation in {@link LlmResponseParser}.
 * <p>
 * Covers:
 * <ul>
 *   <li>Pure JSON response -> parsed correctly</li>
 *   <li>Markdown-wrapped JSON (```json ... ```) -> extracted and parsed correctly</li>
 *   <li>Generic code block (``` ... ```) -> extracted if content is JSON</li>
 *   <li>Response with surrounding text -> JSON extracted from middle</li>
 *   <li>Invalid/non-JSON response -> graceful fallback (ModelResponseParseException)</li>
 *   <li>Null response -> handled without NPE (ModelResponseParseException)</li>
 *   <li>Empty string response -> handled</li>
 * </ul>
 */
@DisplayName("LLM Response Degradation")
class LlmResponseDegradationTest {

    private LlmResponseParser parser;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        parser = new LlmResponseParser(objectMapper);
    }

    // ---- extractJson tests ----

    @Nested
    @DisplayName("extractJson")
    class ExtractJsonTests {

        @Test
        @DisplayName("Pure JSON object -> returned as-is")
        void pureJsonObject_returnedAsIs() {
            String json = "{\"claims\": []}";
            String result = parser.extractJson(json);
            assertThat(result).isEqualTo(json);
        }

        @Test
        @DisplayName("Pure JSON array -> returned as-is")
        void pureJsonArray_returnedAsIs() {
            String json = "[{\"name\": \"Java\"}]";
            String result = parser.extractJson(json);
            assertThat(result).isEqualTo(json);
        }

        @Test
        @DisplayName("Markdown-wrapped JSON (```json ... ```) -> extracted correctly")
        void markdownWrappedJson_extracted() {
            String wrapped = "Here are the extracted claims:\n```json\n{\"claims\": [{\"abilityName\": \"Java\", \"masteryLevel\": 4}]}\n```\nLet me know if you need more.";
            String result = parser.extractJson(wrapped);
            assertThat(result).isEqualTo("{\"claims\": [{\"abilityName\": \"Java\", \"masteryLevel\": 4}]}");
        }

        @Test
        @DisplayName("Generic code block (``` ... ```) with JSON content -> extracted")
        void genericCodeBlockWithJson_extracted() {
            String wrapped = "```{\"claims\": []}\n```";
            String result = parser.extractJson(wrapped);
            assertThat(result).isEqualTo("{\"claims\": []}");
        }

        @Test
        @DisplayName("Response with explanatory text before JSON -> JSON extracted")
        void textBeforeJson_extracted() {
            String response = "Based on the resume, I found the following abilities:\n{\"claims\": [{\"abilityName\": \"Python\"}]}";
            String result = parser.extractJson(response);
            assertThat(result).isEqualTo("{\"claims\": [{\"abilityName\": \"Python\"}]}");
        }

        @Test
        @DisplayName("JSON array in markdown code block -> extracted correctly")
        void jsonArrayInMarkdownBlock_extracted() {
            String wrapped = "```json\n[1, 2, 3]\n```";
            String result = parser.extractJson(wrapped);
            assertThat(result).isEqualTo("[1, 2, 3]");
        }

        @Test
        @DisplayName("Null response -> ModelResponseParseException")
        void nullResponse_throwsException() {
            assertThatThrownBy(() -> parser.extractJson(null))
                    .isInstanceOf(ModelResponseParseException.class)
                    .hasMessageContaining("Empty LLM response");
        }

        @Test
        @DisplayName("Empty string response -> ModelResponseParseException")
        void emptyStringResponse_throwsException() {
            assertThatThrownBy(() -> parser.extractJson(""))
                    .isInstanceOf(ModelResponseParseException.class)
                    .hasMessageContaining("Empty LLM response");
        }

        @Test
        @DisplayName("Blank string response -> ModelResponseParseException")
        void blankStringResponse_throwsException() {
            assertThatThrownBy(() -> parser.extractJson("   \n\t  "))
                    .isInstanceOf(ModelResponseParseException.class)
                    .hasMessageContaining("Empty LLM response");
        }

        @Test
        @DisplayName("Non-JSON response (plain text) -> ModelResponseParseException")
        void nonJsonResponse_throwsException() {
            assertThatThrownBy(() -> parser.extractJson("I'm sorry, I cannot process this request."))
                    .isInstanceOf(ModelResponseParseException.class)
                    .hasMessageContaining("No JSON found in LLM response");
        }

        @Test
        @DisplayName("Code block with non-JSON content -> ModelResponseParseException")
        void codeBlockWithNonJson_throwsException() {
            String wrapped = "```\nThis is not JSON at all\n```";
            assertThatThrownBy(() -> parser.extractJson(wrapped))
                    .isInstanceOf(ModelResponseParseException.class)
                    .hasMessageContaining("No JSON found in LLM response");
        }
    }

    // ---- parseResponse tests ----

    @Nested
    @DisplayName("parseResponse")
    class ParseResponseTests {

        @Test
        @DisplayName("Pure JSON -> parsed into target type")
        void pureJson_parsed() {
            String json = "{\"name\": \"test\", \"value\": 42}";
            TestDto result = parser.parseResponse(json, TestDto.class);
            assertThat(result.name).isEqualTo("test");
            assertThat(result.value).isEqualTo(42);
        }

        @Test
        @DisplayName("Markdown-wrapped JSON -> extracted and parsed")
        void markdownWrapped_parsed() {
            String wrapped = "```json\n{\"name\": \"wrapped\", \"value\": 99}\n```";
            TestDto result = parser.parseResponse(wrapped, TestDto.class);
            assertThat(result.name).isEqualTo("wrapped");
            assertThat(result.value).isEqualTo(99);
        }

        @Test
        @DisplayName("Null response -> ModelResponseParseException")
        void nullResponse_throwsException() {
            assertThatThrownBy(() -> parser.parseResponse(null, TestDto.class))
                    .isInstanceOf(ModelResponseParseException.class);
        }

        @Test
        @DisplayName("Invalid JSON structure -> ModelResponseParseException wrapping parse error")
        void invalidJson_throwsWrappedException() {
            assertThatThrownBy(() -> parser.parseResponse("{\"broken\": }", TestDto.class))
                    .isInstanceOf(ModelResponseParseException.class)
                    .hasMessageContaining("Failed to parse LLM response");
        }

        @Test
        @DisplayName("Non-JSON text -> ModelResponseParseException")
        void nonJsonText_throwsException() {
            assertThatThrownBy(() -> parser.parseResponse("not json at all", TestDto.class))
                    .isInstanceOf(ModelResponseParseException.class)
                    .hasMessageContaining("No JSON found");
        }
    }

    // ---- parseAbilityClaims tests ----

    @Nested
    @DisplayName("parseAbilityClaims")
    class ParseAbilityClaimsTests {

        @Test
        @DisplayName("Valid claims JSON -> parsed correctly")
        void validClaimsJson_parsed() {
            String json = """
                    {"claims": [
                        {"abilityName": "Java", "masteryLevel": 4, "confidenceScore": 85, "evidenceText": "5 years experience"},
                        {"abilityName": "Python", "masteryLevel": 3, "confidenceScore": 70, "evidenceText": "Used in projects"}
                    ]}""";

            List<AbilityClaimCandidate> claims = parser.parseAbilityClaims(json, 1L, ClaimSource.RESUME_PARSE, 11L);

            assertThat(claims).hasSize(2);
            assertThat(claims.get(0).employeeId()).isEqualTo(1L);
            assertThat(claims.get(0).abilityName()).isEqualTo("Java");
            assertThat(claims.get(0).claimedLevel()).isEqualTo(4);
            assertThat(claims.get(0).confidence()).isEqualByComparingTo(new BigDecimal("85"));
            assertThat(claims.get(0).source()).isEqualTo(ClaimSource.RESUME_PARSE);
            assertThat(claims.get(0).sourceRefId()).isEqualTo(11L);
            assertThat(claims.get(0).evidence().evidenceText()).isEqualTo("5 years experience");
            assertThat(claims.get(1).abilityName()).isEqualTo("Python");
        }

        @Test
        @DisplayName("Markdown-wrapped claims JSON -> extracted and parsed")
        void markdownWrappedClaims_parsed() {
            String wrapped = """
                    Here are the extracted claims:
                    ```json
                    {"claims": [{"abilityName": "Java", "masteryLevel": 4}]}
                    ```
                    """;

            List<AbilityClaimCandidate> claims = parser.parseAbilityClaims(wrapped, 1L, ClaimSource.AI_PROJECT, 22L);

            assertThat(claims).hasSize(1);
            assertThat(claims.get(0).abilityName()).isEqualTo("Java");
            assertThat(claims.get(0).source()).isEqualTo(ClaimSource.AI_PROJECT);
        }

        @Test
        @DisplayName("Response without claims array -> ModelResponseParseException")
        void noClaimsArray_throwsException() {
            String json = "{\"data\": \"no claims here\"}";
            assertThatThrownBy(() -> parser.parseAbilityClaims(json, 1L, ClaimSource.RESUME_PARSE, 11L))
                    .isInstanceOf(ModelResponseParseException.class)
                    .hasMessageContaining("does not contain a claims array");
        }

        @Test
        @DisplayName("Null response -> ModelResponseParseException")
        void nullResponse_throwsException() {
            assertThatThrownBy(() -> parser.parseAbilityClaims(null, 1L, ClaimSource.RESUME_PARSE, 11L))
                    .isInstanceOf(ModelResponseParseException.class)
                    .hasMessageContaining("Empty LLM response");
        }

        @Test
        @DisplayName("Empty response -> ModelResponseParseException")
        void emptyResponse_throwsException() {
            assertThatThrownBy(() -> parser.parseAbilityClaims("", 1L, ClaimSource.RESUME_PARSE, 11L))
                    .isInstanceOf(ModelResponseParseException.class)
                    .hasMessageContaining("Empty LLM response");
        }

        @Test
        @DisplayName("Claims with null optional fields -> parsed without NPE")
        void claimsWithNullOptionalFields_parsedWithoutNPE() {
            String json = """
                    {"claims": [
                        {"abilityName": "Java", "masteryLevel": 4}
                    ]}""";

            List<AbilityClaimCandidate> claims = parser.parseAbilityClaims(json, 1L, ClaimSource.RESUME_PARSE, 11L);

            assertThat(claims).hasSize(1);
            assertThat(claims.get(0).abilityTagId()).isNull();
            assertThat(claims.get(0).confidence()).isNull();
            assertThat(claims.get(0).similarTagId()).isNull();
            assertThat(claims.get(0).evidence().evidenceText()).isNull();
        }

        @Test
        @DisplayName("Empty claims array -> empty list returned")
        void emptyClaimsArray_returnsEmptyList() {
            String json = "{\"claims\": []}";

            List<AbilityClaimCandidate> claims = parser.parseAbilityClaims(json, 1L, ClaimSource.RESUME_PARSE, 11L);

            assertThat(claims).isEmpty();
        }

        @Test
        @DisplayName("Claims with abilityTagId and similarTagId -> parsed correctly")
        void claimsWithTagIds_parsedCorrectly() {
            String json = """
                    {"claims": [
                        {"abilityName": "Java", "masteryLevel": 4, "abilityTagId": 7, "similarTagId": 15}
                    ]}""";

            List<AbilityClaimCandidate> claims = parser.parseAbilityClaims(json, 1L, ClaimSource.RESUME_PARSE, 11L);

            assertThat(claims.get(0).abilityTagId()).isEqualTo(7L);
            assertThat(claims.get(0).similarTagId()).isEqualTo(15L);
        }

        @Test
        @DisplayName("Invalid JSON for claims -> ModelResponseParseException wrapping parse error")
        void invalidJson_throwsWrappedException() {
            assertThatThrownBy(() -> parser.parseAbilityClaims("{invalid json}", 1L, ClaimSource.RESUME_PARSE, 11L))
                    .isInstanceOf(ModelResponseParseException.class);
        }
    }

    // ---- ModelResponseParseException tests ----

    @Nested
    @DisplayName("ModelResponseParseException")
    class ExceptionTests {

        @Test
        @DisplayName("Exception with message -> message preserved")
        void exceptionWithMessage_preserved() {
            ModelResponseParseException ex = new ModelResponseParseException("test error");
            assertThat(ex.getMessage()).isEqualTo("test error");
            assertThat(ex.getCause()).isNull();
        }

        @Test
        @DisplayName("Exception with message and cause -> both preserved")
        void exceptionWithMessageAndCause_preserved() {
            RuntimeException cause = new RuntimeException("root cause");
            ModelResponseParseException ex = new ModelResponseParseException("test error", cause);
            assertThat(ex.getMessage()).isEqualTo("test error");
            assertThat(ex.getCause()).isEqualTo(cause);
        }

        @Test
        @DisplayName("Exception is RuntimeException -> catchable as such")
        void exceptionIsRuntimeException() {
            assertThatThrownBy(() -> {
                throw new ModelResponseParseException("test");
            }).isInstanceOf(RuntimeException.class);
        }
    }

    // ---- Test DTO for parseResponse tests ----

    static class TestDto {
        public String name;
        public int value;
    }
}
