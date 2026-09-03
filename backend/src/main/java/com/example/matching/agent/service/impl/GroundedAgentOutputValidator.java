package com.example.matching.agent.service.impl;

import com.example.matching.agent.dto.AgentContextPackage;
import com.example.matching.agent.dto.AgentSourceRef;
import com.example.matching.agent.dto.LearningPathAgentResult;
import com.example.matching.agent.dto.MatchingAnalysisAgentResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class GroundedAgentOutputValidator {

    private final com.example.matching.ai.context.service.AiContextSourceRefService sourceRefService;

    public GroundedAgentOutputValidator(
            com.example.matching.ai.context.service.AiContextSourceRefService sourceRefService) {
        this.sourceRefService = sourceRefService;
    }

    public Optional<MatchingAnalysisAgentResult> validateMatching(
            MatchingAnalysisAgentResult result, AgentContextPackage context) {
        if (result == null) {
            log.warn("Matching analysis result is null, rejecting");
            return Optional.empty();
        }

        if (result.getSuggestedLlmScore() != null) {
            BigDecimal score = result.getSuggestedLlmScore();
            if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(new BigDecimal("100")) > 0) {
                log.warn("Matching analysis score {} out of 0-100 range, rejecting", score);
                return Optional.empty();
            }
        }

        if (result.getConclusion() == null || result.getConclusion().isBlank()) {
            log.warn("Matching analysis conclusion is blank, rejecting");
            return Optional.empty();
        }

        Set<String> contextDimensions = context.getScoreBreakdown() != null
                ? context.getScoreBreakdown().stream()
                        .map(b -> b.dimension())
                        .collect(Collectors.toSet())
                : Set.of();

        if (result.getDimensionScores() != null && !result.getDimensionScores().isEmpty()) {
            if (contextDimensions.isEmpty()) {
                log.warn("Matching analysis contains dimensions without a deterministic breakdown, rejecting");
                return Optional.empty();
            }
            for (Map<String, Object> dimScore : result.getDimensionScores()) {
                Object dimObj = dimScore.get("dimension");
                if (!(dimObj instanceof String dim) || !contextDimensions.contains(dim)) {
                    log.warn("Matching analysis dimension '{}' not in context breakdown, rejecting", dimObj);
                    return Optional.empty();
                }
            }
        }

        List<AgentSourceRef> serverRefs = context.getSourceRefs();
        if (serverRefs != null && !serverRefs.isEmpty()) {
            // 服务端引用非空时覆盖 LLM 引用（服务端为权威）
            result.setSourceRefs(serverRefs);
        } else {
            // M24：服务端引用为空时，逐条校验 LLM 自报引用（解析/存在性）；
            // 无任何有效引用时拒绝结果，不能采纳自报置信度
            List<AgentSourceRef> llmRefs = result.getSourceRefs();
            List<AgentSourceRef> validRefs = new java.util.ArrayList<>();
            if (llmRefs != null) {
                for (AgentSourceRef ref : llmRefs) {
                    if (ref != null && isValidServerSourceRef(ref)) {
                        validRefs.add(ref);
                    } else {
                        log.warn("Matching analysis LLM sourceRef 无法解析/不存在，剔除: ref={}",
                                ref != null ? ref.getRef() : "null");
                    }
                }
            }
            if (validRefs.isEmpty()) {
                log.warn("Matching analysis has no valid source refs, rejecting");
                return Optional.empty();
            }
            result.setSourceRefs(validRefs);
        }

        // 方案第十三章：结构化 finding 回链校验（仅当子图可用 FRESH 时启用）。
        // abilityTagId ∈ allowedAbilityTagIds、sourceRefs ⊆ allowedSourceRefs、
        // graphNodeKeys ⊆ nodes；校验通过后由服务端生成 strengths/gaps/riskSignals/humanAttentionPoints。
        com.example.matching.agent.dto.graph.AgentGraphContext graphContext = context.getGraphContext();
        if (result.getFindings() == null || result.getFindings().isEmpty()) {
            clearModelDerivedFindings(result);
        } else {
            if (graphContext == null || !graphContext.isUsable()) {
                log.warn("Matching analysis findings cannot be accepted without a fresh graph context");
                return Optional.empty();
            }
            if (!applyFindings(result, graphContext)) {
                return Optional.empty();
            }
        }

        return Optional.of(result);
    }

    private void clearModelDerivedFindings(MatchingAnalysisAgentResult result) {
        result.setStrengths(List.of());
        result.setGaps(List.of());
        result.setRiskSignals(List.of());
        result.setHumanAttentionPoints(List.of());
    }

    /**
     * 校验并应用结构化 findings：
     * 1. 逐条校验回链（能力标签/引用/节点键），剔除不合法条目；
     * 2. 由校验通过的 findings 生成 strengths/gaps/riskSignals/humanAttentionPoints；
     * 3. 没有任何合法 finding 时拒绝（不能采纳无引用结论）。
     */
    private boolean applyFindings(MatchingAnalysisAgentResult result,
                                  com.example.matching.agent.dto.graph.AgentGraphContext graphContext) {
        Set<Long> allowedTagIds = graphContext.getAllowedAbilityTagIds();
        Set<String> allowedRefs = graphContext.getAllowedSourceRefs();
        Set<String> nodeKeys = graphContext.getNodes().stream()
                .map(com.example.matching.agent.dto.graph.AgentGraphNode::getNodeKey)
                .collect(Collectors.toSet());

        List<com.example.matching.agent.dto.AgentFinding> valid = new java.util.ArrayList<>();
        for (com.example.matching.agent.dto.AgentFinding finding : result.getFindings()) {
            if (finding == null || finding.getText() == null || finding.getText().isBlank()) {
                continue;
            }
            boolean tagOk = finding.getAbilityTagId() != null
                    && allowedTagIds.contains(finding.getAbilityTagId());
            boolean refsOk = finding.getSourceRefs() != null && !finding.getSourceRefs().isEmpty()
                    && allowedRefs.containsAll(finding.getSourceRefs());
            boolean nodesOk = finding.getGraphNodeKeys() != null && !finding.getGraphNodeKeys().isEmpty()
                    && nodeKeys.containsAll(finding.getGraphNodeKeys());
            if (!tagOk || !refsOk || !nodesOk) {
                log.warn("Matching analysis finding 回链校验失败，剔除: text={}, tagId={}, refs={}, nodes={}",
                        finding.getText(), finding.getAbilityTagId(),
                        finding.getSourceRefs(), finding.getGraphNodeKeys());
                continue;
            }
            valid.add(finding);
        }

        if (valid.isEmpty()) {
            log.warn("Matching analysis has no valid structured findings, rejecting");
            return false;
        }

        // 服务端转换：strengths/gaps/riskSignals/humanAttentionPoints 由 findings 生成
        result.setFindings(valid);
        result.setStrengths(valid.stream()
                .filter(f -> "STRENGTH".equals(f.getType())).map(com.example.matching.agent.dto.AgentFinding::getText)
                .collect(Collectors.toList()));
        result.setGaps(valid.stream()
                .filter(f -> "GAP".equals(f.getType())).map(com.example.matching.agent.dto.AgentFinding::getText)
                .collect(Collectors.toList()));
        result.setRiskSignals(valid.stream()
                .filter(f -> "RISK".equals(f.getType())).map(com.example.matching.agent.dto.AgentFinding::getText)
                .collect(Collectors.toList()));
        result.setHumanAttentionPoints(valid.stream()
                .filter(f -> "HUMAN_ATTENTION".equals(f.getType())).map(com.example.matching.agent.dto.AgentFinding::getText)
                .collect(Collectors.toList()));
        return true;
    }

    /**
     * M24：服务端校验 LLM 自报引用是否可解析且真实存在。
     */
    private boolean isValidServerSourceRef(AgentSourceRef ref) {
        try {
            if (ref.getRef() != null && !ref.getRef().isBlank()) {
                return sourceRefService.resolve(ref.getRef()) != null;
            }
            if (ref.getSourceType() != null && ref.getRefId() != null) {
                return sourceRefService.resolve(ref.getSourceType() + ":" + ref.getRefId()) != null;
            }
            return false;
        } catch (Exception e) {
            log.warn("Matching analysis sourceRef 解析异常，按无效处理: ref={}, error={}", ref.getRef(), e.getMessage());
            return false;
        }
    }

    public Optional<LearningPathAgentResult> validateLearningPath(
            LearningPathAgentResult result, AgentContextPackage context,
            Set<Long> allowedGapTagIds) {
        if (result == null) {
            log.warn("Learning path result is null, rejecting");
            return Optional.empty();
        }

        if (result.getSteps() == null || result.getSteps().isEmpty()) {
            log.warn("Learning path result has no steps, rejecting for deterministic fallback");
            return Optional.empty();
        }
        for (LearningPathAgentResult.LearningStepSuggestion step : result.getSteps()) {
            if (step.getAbilityTagId() == null || allowedGapTagIds == null
                    || !allowedGapTagIds.contains(step.getAbilityTagId())) {
                log.warn("Learning path step has abilityTagId {} outside verified gaps, rejecting", step.getAbilityTagId());
                return Optional.empty();
            }
        }

        List<AgentSourceRef> serverRefs = context.getSourceRefs();
        if (serverRefs != null && !serverRefs.isEmpty()) {
            result.setSourceRefs(serverRefs);
        }

        return Optional.of(result);
    }
}
