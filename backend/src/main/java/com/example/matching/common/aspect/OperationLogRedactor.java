package com.example.matching.common.aspect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class OperationLogRedactor {

    private static final Set<String> REDACTED_FIELDS = Set.of(
            "password", "oldPassword", "newPassword", "confirmPassword",
            "token", "refreshToken", "authorization", "secret",
            "apiKey", "accessKey", "credential"
    );

    private static final Set<String> SENSITIVE_ENDPOINTS = Set.of(
            "/api/system/user/login", "/api/system/user/register",
            "/api/system/user/change-password", "/api/system/user/logout"
    );

    private final ObjectMapper objectMapper;

    public boolean isSensitiveEndpoint(String requestUrl) {
        if (requestUrl == null) return false;
        return SENSITIVE_ENDPOINTS.stream().anyMatch(requestUrl::startsWith);
    }

    public String redactRequestBody(Object[] args) {
        try {
            JsonNode root = objectMapper.valueToTree(args);
            redactNode(root);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            return "[serialization error]";
        }
    }

    public String redactResponseBody(Object result) {
        try {
            JsonNode root = objectMapper.valueToTree(result);
            redactNode(root);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            return "[serialization error]";
        }
    }

    private void redactNode(JsonNode node) {
        if (node == null) return;
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = obj.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (REDACTED_FIELDS.contains(field.getKey().toLowerCase())) {
                    field.setValue(new TextNode("[REDACTED]"));
                } else {
                    redactNode(field.getValue());
                }
            }
        } else if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (JsonNode item : arr) {
                redactNode(item);
            }
        }
    }
}
