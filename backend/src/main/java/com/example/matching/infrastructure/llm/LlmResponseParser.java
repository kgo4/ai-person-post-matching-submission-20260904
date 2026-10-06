package com.example.matching.infrastructure.llm;

import com.example.matching.application.agent.AbilityClaimCandidate;
import com.example.matching.application.agent.ClaimSource;
import com.example.matching.application.agent.EvidenceBundle;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Centralized JSON extraction and parsing for LLM responses.
 * Replaces the duplicated {@code extractJsonFromText} methods across Agent services.
 * <p>
 * Handles: pure JSON, markdown code blocks, and responses with explanatory text.
 */
@Slf4j
@Component
public class LlmResponseParser {

    private final ObjectMapper objectMapper;

    public LlmResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Extract a JSON object or array from an LLM response string.
     * Handles markdown code blocks, leading/trailing text, etc.
     *
     * @param text raw LLM response
     * @return extracted JSON string
     * @throws ModelResponseParseException if no JSON can be extracted
     */
    public String extractJson(String text) {
        if (text == null || text.isBlank()) {
            throw new ModelResponseParseException("Empty LLM response");
        }
        String trimmed = text.trim();

        // 1. A response may start with JSON but still append prose. Extract one balanced value.
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return extractFirstJson(trimmed);
        }

        // 2. Extract ```json ... ``` code block
        int codeBlockStart = trimmed.indexOf("```json");
        if (codeBlockStart >= 0) {
            int jsonStart = codeBlockStart + 7;
            int codeBlockEnd = trimmed.indexOf("```", jsonStart);
            if (codeBlockEnd > jsonStart) {
                return extractFirstJson(trimmed.substring(jsonStart, codeBlockEnd));
            }
        }

        // 3. Extract ``` ... ``` code block without json marker
        codeBlockStart = trimmed.indexOf("```");
        if (codeBlockStart >= 0) {
            int jsonStart = codeBlockStart + 3;
            int codeBlockEnd = trimmed.indexOf("```", jsonStart);
            if (codeBlockEnd > jsonStart) {
                String candidate = trimmed.substring(jsonStart, codeBlockEnd).trim();
                if (candidate.startsWith("{") || candidate.startsWith("[")) {
                    return extractFirstJson(candidate);
                }
            }
        }

        // 4. Find and extract the first complete JSON value in prose.
        return extractFirstJson(trimmed);
    }

    private String extractFirstJson(String text) {
        int searchFrom = 0;
        while (searchFrom < text.length()) {
            int objectStart = text.indexOf('{', searchFrom);
            int arrayStart = text.indexOf('[', searchFrom);
            int start = objectStart < 0 ? arrayStart
                    : arrayStart < 0 ? objectStart : Math.min(objectStart, arrayStart);
            if (start < 0) {
                break;
            }
            try {
                String candidate = extractBalancedJson(text, start);
                objectMapper.readTree(candidate);
                return candidate;
            } catch (ModelResponseParseException | com.fasterxml.jackson.core.JsonProcessingException ignored) {
                searchFrom = start + 1;
            }
        }
        throw new ModelResponseParseException("No JSON found in LLM response");
    }

    private String extractBalancedJson(String text, int start) {
        Deque<Character> stack = new ArrayDeque<>();
        boolean inString = false;
        boolean escaped = false;

        for (int index = start; index < text.length(); index++) {
            char character = text.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == '"') {
                    inString = false;
                }
                continue;
            }
            if (character == '"') {
                inString = true;
            } else if (character == '{' || character == '[') {
                stack.push(character);
            } else if (character == '}' || character == ']') {
                if (stack.isEmpty() || !matches(stack.pop(), character)) {
                    throw new ModelResponseParseException("Malformed JSON in LLM response");
                }
                if (stack.isEmpty()) {
                    return text.substring(start, index + 1).trim();
                }
            }
        }
        throw new ModelResponseParseException("Incomplete JSON in LLM response");
    }

    private boolean matches(char open, char close) {
        return (open == '{' && close == '}') || (open == '[' && close == ']');
    }

    /**
     * Parse an LLM response into a typed object.
     *
     * @param response  raw LLM response
     * @param valueType target type
     * @return parsed object
     * @throws ModelResponseParseException if parsing fails
     */
    public <T> T parseResponse(String response, Class<T> valueType) {
        try {
            String json = extractJson(response);
            return objectMapper.readValue(json, valueType);
        } catch (ModelResponseParseException e) {
            if (response != null && response.trim().matches("^[\\[{].*")) {
                throw new ModelResponseParseException("Failed to parse LLM response: invalid JSON", e);
            }
            throw e;
        } catch (Exception e) {
            throw new ModelResponseParseException("Failed to parse LLM response: " + e.getMessage(), e);
        }
    }

    /**
     * 从 LLM 响应中提取并解析 JSON 对象树。
     *
     * @throws ModelResponseParseException 提取或解析失败时抛出
     */
    public JsonNode parseObject(String response) {
        try {
            return objectMapper.readTree(extractJson(response));
        } catch (ModelResponseParseException e) {
            throw e;
        } catch (Exception e) {
            throw new ModelResponseParseException("Failed to parse LLM JSON object: " + e.getMessage(), e);
        }
    }

    /**
     * 从 LLM 响应中提取并解析 JSON 数组。
     *
     * @throws ModelResponseParseException 提取或解析失败，或根节点不是数组时抛出
     */
    public JsonNode parseArray(String response) {
        JsonNode node = parseObject(response);
        if (!node.isArray()) {
            throw new ModelResponseParseException("LLM response root is not a JSON array");
        }
        return node;
    }

    /**
     * Extract ability claims from an LLM extraction response.
     * Parses the "claims" array from the response JSON.
     *
     * @param response   raw LLM response
     * @param employeeId the employee ID to set on claims
     * @param source     the claim source
     * @param sourceRefId the source reference ID
     * @return list of parsed claim candidates
     */
    public List<AbilityClaimCandidate> parseAbilityClaims(
            String response, Long employeeId, ClaimSource source, Long sourceRefId) {
        try {
            String json = extractJson(response);
            JsonNode root = objectMapper.readTree(json);
            JsonNode claimsNode = root.get("claims");
            if (claimsNode == null || !claimsNode.isArray()) {
                throw new ModelResponseParseException("LLM response does not contain a claims array");
            }

            List<AbilityClaimCandidate> claims = new ArrayList<>();
            for (JsonNode claimNode : claimsNode) {
                AbilityClaimCandidate claim = new AbilityClaimCandidate(
                        employeeId,
                        getLongValue(claimNode, "abilityTagId"),
                        getTextValue(claimNode, "abilityName"),
                        getTextValue(claimNode, "abilityName"), // normalized = raw initially
                        getIntValue(claimNode, "masteryLevel"),
                        source,
                        sourceRefId,
                        EvidenceBundle.of(
                                getTextValue(claimNode, "evidenceText"),
                                List.of()
                        ),
                        getBigDecimalValue(claimNode, "confidenceScore"),
                        null, // freshness
                        null, // authority
                        getLongValue(claimNode, "similarTagId")
                );
                claims.add(claim);
            }
            return claims;
        } catch (ModelResponseParseException e) {
            throw e;
        } catch (Exception e) {
            throw new ModelResponseParseException("Failed to parse ability claims from LLM response", e);
        }
    }

    // --- Helper methods ---

    private String getTextValue(JsonNode node, String field) {
        return JsonNodeValueReader.getText(node, field);
    }

    private Integer getIntValue(JsonNode node, String field) {
        return JsonNodeValueReader.getInt(node, field);
    }

    private Long getLongValue(JsonNode node, String field) {
        return JsonNodeValueReader.getLong(node, field);
    }

    private BigDecimal getBigDecimalValue(JsonNode node, String field) {
        return JsonNodeValueReader.getBigDecimal(node, field);
    }
}
