package com.example.matching.agent.service.impl;

import com.example.matching.agent.config.AgentMemoryProperties;
import com.example.matching.application.agent.AgentMemoryPort;
import com.example.matching.application.agent.AgentMemoryPort.MemoryEntry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/**
 * Agent 记忆上下文服务
 * <p>
 * 负责：检索、排序、截断、Prompt上下文组装。
 * 在提取请求进入Agent前调用，为LLM提供可读的治理规则摘要。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentMemoryContextService {

    private final AgentMemoryPort agentMemoryPort;
    private final ObjectMapper objectMapper;
    private final AgentMemoryProperties properties;

    private static final int MAX_PER_TYPE = 3;
    private static final int MAX_TOTAL = 8;

    /** Agent scope 常量 */
    public static final String SCOPE_EMPLOYEE = "EMPLOYEE_ABILITY_EXTRACTION";
    public static final String SCOPE_POST = "POST_ABILITY_EXTRACTION";
    public static final String SCOPE_EVIDENCE = "EVIDENCE_GOVERNANCE";
    public static final String SCOPE_MATCHING = "MATCH_EXECUTION_SCORING";

    /**
     * 检索并组装上下文规则。
     *
     * @param rawText 原始文本（简历、JD等）
     * @param scope   场景范围
     * @return ContextRules 包含硬规则和提示规则的文本
     */
    public ContextRules resolveRules(String rawText, String scope) {
        // 全局总开关：关闭时直接短路，不检索、不注入、不执行任何记忆规则
        if (!properties.isEnabled()) {
            return new ContextRules(List.of(), List.of(), "", "");
        }
        String normalized = normalizeText(rawText);
        List<MemoryEntry> all = agentMemoryPort.searchActiveRules(normalized, scope);

        List<MemoryEntry> sorted = all.stream()
                .sorted(Comparator.comparingInt(MemoryEntry::priority).reversed())
                .toList();

        List<MemoryEntry> truncated = new ArrayList<>();
        HashMap<String, Integer> countByType = new HashMap<>();
        for (MemoryEntry entry : sorted) {
            if (truncated.size() >= MAX_TOTAL) break;
            String type = entry.memoryType() != null ? entry.memoryType() : "OTHER";
            if (countByType.getOrDefault(type, 0) >= MAX_PER_TYPE) continue;
            truncated.add(entry);
            countByType.merge(type, 1, Integer::sum);
        }

        List<MemoryEntry> hardRules = truncated.stream()
                .filter(m -> "HARD".equals(m.ruleStrength()))
                .toList();
        List<MemoryEntry> guidanceRules = truncated.stream()
                .filter(m -> !"HARD".equals(m.ruleStrength()))
                .toList();

        String hardSummary = buildHardRuleSummary(hardRules);
        String guidancePrompt = buildGuidancePrompt(guidanceRules);

        return new ContextRules(hardRules, guidanceRules, hardSummary, guidancePrompt);
    }

    /**
     * 构建可读的硬规则摘要（注入Prompt用）。
     */
    private String buildHardRuleSummary(List<MemoryEntry> hardRules) {
        if (hardRules.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("已确认治理规则：\n");
        for (MemoryEntry rule : hardRules) {
            sb.append("- ").append(rule.content()).append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * 构建提示规则段落（注入Prompt用）。
     */
    private String buildGuidancePrompt(List<MemoryEntry> guidanceRules) {
        if (guidanceRules.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("参考治理建议：\n");
        for (MemoryEntry rule : guidanceRules) {
            sb.append("- [").append(rule.memoryType()).append("] ")
                    .append(rule.title()).append(": ").append(rule.content()).append("\n");
        }
        return sb.toString().trim();
    }

    private String normalizeText(String text) {
        if (text == null) return "";
        return text.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    /**
     * 解析 rulePayloadJson 中的 condition.sourceTerms。
     */
    public List<String> extractSourceTerms(MemoryEntry entry) {
        if (entry.rulePayloadJson() == null || entry.rulePayloadJson().isBlank()) {
            return extractFromTrigger(entry);
        }
        try {
            JsonNode root = objectMapper.readTree(entry.rulePayloadJson());
            JsonNode sourceTerms = root.path("condition").path("sourceTerms");
            if (sourceTerms.isArray()) {
                List<String> terms = new ArrayList<>();
                for (JsonNode node : sourceTerms) {
                    if (node.isTextual()) terms.add(node.asText());
                }
                if (!terms.isEmpty()) return terms;
            }
        } catch (Exception e) {
            log.debug("Failed to parse rulePayloadJson sourceTerms: {}", entry.id());
        }
        return extractFromTrigger(entry);
    }

    private List<String> extractFromTrigger(MemoryEntry entry) {
        if (entry.triggerExpressionsJson() == null || entry.triggerExpressionsJson().isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(entry.triggerExpressionsJson());
            if (node.isArray()) {
                List<String> terms = new ArrayList<>();
                for (JsonNode n : node) {
                    if (n.isTextual()) terms.add(n.asText());
                }
                return terms;
            }
        } catch (Exception e) {
            log.debug("Failed to parse triggerExpressionsJson: {}", entry.id());
        }
        return List.of();
    }

    /**
     * 上下文规则结果
     */
    public record ContextRules(
            List<MemoryEntry> hardRules,
            List<MemoryEntry> guidanceRules,
            String hardRuleSummary,
            String guidancePrompt
    ) {}
}
