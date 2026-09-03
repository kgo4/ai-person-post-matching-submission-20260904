package com.example.matching.service.closure.impl;

import com.example.matching.dto.closure.ComprehensiveDiagnosisFactDTO;
import com.example.matching.dto.closure.ComprehensiveDiagnosisResultDTO;
import com.example.matching.ai.service.LangChain4jChatService;
import com.example.matching.ai.service.PromptTemplateService;
import com.example.matching.service.rag.RagRetrievalService;
import com.example.matching.service.rag.RagScenarioEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 综合诊断 AI 分析：RAG 上下文 + Prompt 渲染 + 熔断重试调用 + 接地校验。
 * <p>
 * 从 ComprehensiveDiagnosisServiceImpl（700+ 行）中拆分的 AI 分析组件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiagnosisAiAnalyzer {

    private final LangChain4jChatService langChain4jChatService;
    private final PromptTemplateService promptTemplateService;
    private final ObjectMapper objectMapper;
    private final com.example.matching.infrastructure.llm.LlmResponseParser llmResponseParser;
    private final RagRetrievalService ragRetrievalService;
    public ComprehensiveDiagnosisResultDTO.AiDiagnosisAnalysis buildAiAnalysis(
            ComprehensiveDiagnosisFactDTO fact) {
        Set<String> allowedSourceRefs = buildAllowedSourceRefs(fact);
        // 1. RAG 检索上下文
        String ragContext = retrieveRagContext(fact);

        // 2. 渲染 Prompt
        String prompt = renderPrompt(fact, ragContext, allowedSourceRefs);

        // 3. 调用 AI（带熔断和重试）
        String aiResponse = callAi(prompt);

        // 4. 解析 AI 响应
        ComprehensiveDiagnosisResultDTO.AiDiagnosisAnalysis analysis = parseAiResponse(aiResponse);
        return filterUngroundedAiAnalysis(analysis, allowedSourceRefs);
    }

    public ComprehensiveDiagnosisResultDTO.AiDiagnosisAnalysis filterUngroundedAiAnalysis(
            ComprehensiveDiagnosisResultDTO.AiDiagnosisAnalysis analysis) {
        return filterUngroundedAiAnalysis(analysis, null);
    }

    private ComprehensiveDiagnosisResultDTO.AiDiagnosisAnalysis filterUngroundedAiAnalysis(
            ComprehensiveDiagnosisResultDTO.AiDiagnosisAnalysis analysis, Set<String> allowedSourceRefs) {
        if (analysis == null) {
            return null;
        }
        List<ComprehensiveDiagnosisResultDTO.BlockedClaim> blocked = new ArrayList<>();
        if (analysis.getBlockedClaims() != null) {
            blocked.addAll(analysis.getBlockedClaims());
        }

        List<ComprehensiveDiagnosisResultDTO.DimensionDiagnosis> verifiedDimensions = new ArrayList<>();
        for (ComprehensiveDiagnosisResultDTO.DimensionDiagnosis dimension : safeList(analysis.getDimensions())) {
            if (hasGroundedSourceRefs(dimension.getSourceRefs(), allowedSourceRefs)) {
                verifiedDimensions.add(dimension);
            } else {
                blocked.add(buildGroundedBlockedClaim(dimension.getAnalysis()));
            }
        }
        analysis.setDimensions(verifiedDimensions);

        List<ComprehensiveDiagnosisResultDTO.PriorityAction> verifiedActions = new ArrayList<>();
        for (ComprehensiveDiagnosisResultDTO.PriorityAction action : safeList(analysis.getPriorityActions())) {
            if (hasGroundedSourceRefs(action.getSourceRefs(), allowedSourceRefs)) {
                verifiedActions.add(action);
            } else {
                blocked.add(buildGroundedBlockedClaim(action.getAction()));
            }
        }
        analysis.setPriorityActions(verifiedActions);

        // 整体结论没有单独的引用字段；仅在至少一条维度或行动已通过来源校验时才保留。
        // 这既避免把所有结论静默清空，也不会展示脱离事实包的孤立结论。
        if (hasText(analysis.getOverallConclusion())
                && verifiedDimensions.isEmpty() && verifiedActions.isEmpty()) {
            blocked.add(buildGroundedBlockedClaim(analysis.getOverallConclusion()));
            analysis.setOverallConclusion(null);
        }

        analysis.setBlockedClaims(blocked);
        return analysis;
    }

    private boolean hasGroundedSourceRefs(List<String> sourceRefs, Set<String> allowedSourceRefs) {
        if (sourceRefs == null || sourceRefs.isEmpty()) {
            return false;
        }
        for (String ref : sourceRefs) {
            if (allowedSourceRefs != null && allowedSourceRefs.contains(ref)) {
                return true;
            }
            if (allowedSourceRefs == null && ref != null
                    && (ref.startsWith("fact:") || ref.startsWith("evidence:"))) {
                return true;
            }
        }
        return false;
    }

    private ComprehensiveDiagnosisResultDTO.BlockedClaim buildGroundedBlockedClaim(String claimText) {
        ComprehensiveDiagnosisResultDTO.BlockedClaim blocked = new ComprehensiveDiagnosisResultDTO.BlockedClaim();
        blocked.setClaim(claimText);
        blocked.setReason("Missing grounded source reference");
        blocked.setConfidence("UNSUPPORTED");
        return blocked;
    }

    private <T> List<T> safeList(List<T> values) {
        return values != null ? values : List.of();
    }

    /**
     * RAG 检索上下文
     */
    private String retrieveRagContext(ComprehensiveDiagnosisFactDTO fact) {
        if (fact == null) {
            return "";
        }
        String abilities = safeList(fact.getAbilityGaps()).stream()
                .map(ComprehensiveDiagnosisFactDTO.AbilityGapFact::getAbilityName)
                .filter(this::hasText)
                .limit(8)
                .reduce((left, right) -> left + "、" + right)
                .orElse("");
        String query = String.join(" ", List.of(
                fact.getPostName() != null ? fact.getPostName() : "",
                abilities,
                "岗位能力差距 学习建议 证据"));
        if (query.isBlank()) {
            return "";
        }
        try {
            return ragRetrievalService.retrieveContext(query, RagScenarioEnum.MATCH_GAP_DIAGNOSIS, 5);
        } catch (Exception e) {
            log.warn("综合诊断 RAG 检索失败，继续使用事实包: recordId={}, error={}", fact.getRecordId(), e.getMessage());
            return "";
        }
    }

    /**
     * 渲染 Prompt
     */
    private String renderPrompt(ComprehensiveDiagnosisFactDTO fact, String ragContext, Set<String> allowedSourceRefs) {
        Map<String, Object> dataModel = new HashMap<>();
        dataModel.put("fact", fact);
        dataModel.put("allowedSourceRefs", new ArrayList<>(allowedSourceRefs));
        if (ragContext != null && !ragContext.isBlank()) {
            dataModel.put("ragContext", ragContext);
        }

        return promptTemplateService.render("gap-diagnosis-prompt", dataModel);
    }

    private Set<String> buildAllowedSourceRefs(ComprehensiveDiagnosisFactDTO fact) {
        if (fact == null || fact.getRecordId() == null) {
            return Set.of();
        }
        Set<String> refs = new LinkedHashSet<>();
        refs.add("fact:MATCHING_RECORD:" + fact.getRecordId());
        return refs;
    }

    /**
     * 调用 AI（带熔断和重试）
     */
    private String callAi(String prompt) {
        return langChain4jChatService.chat("gap-diagnosis", prompt,
                () -> null);
    }

    /**
     * 解析 AI 响应
     */
    private ComprehensiveDiagnosisResultDTO.AiDiagnosisAnalysis parseAiResponse(String aiResponse) {
        if (aiResponse == null || aiResponse.isBlank()) {
            return null;
        }
        try {
            String json = llmResponseParser.extractJson(aiResponse);
            return objectMapper.readValue(json,
                    ComprehensiveDiagnosisResultDTO.AiDiagnosisAnalysis.class);
        } catch (Exception e) {
            log.warn("解析AI差距诊断响应失败: {}", e.getMessage());
            return null;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
