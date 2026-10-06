package com.example.matching.agent.service.impl;

import com.example.matching.agent.config.LangChain4jAgentProperties;
import com.example.matching.agent.dto.EvidenceGovernanceAgentRequest;
import com.example.matching.agent.dto.EvidenceGovernanceAgentResult;
import com.example.matching.agent.lc4j.EvidenceGovernanceAiService;
import com.example.matching.agent.service.AgentFallbackService;
import com.example.matching.agent.service.EvidenceGovernanceAgentService;
import com.example.matching.application.agent.AgentMemoryPort;
import com.example.matching.dto.harness.AiHarnessClaimDTO;
import com.example.matching.dto.harness.AiHarnessDecisionDTO;
import com.example.matching.service.harness.AiTrustHarnessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 证据治理Agent服务实现
 * <p>
 * 使用LangChain4j AiServices + Tool编排
 * 关键规则：模型只能补充解释，不能覆盖规则引擎的 BLOCK/REVIEW
 * <p>
 * TODO-WF3: Replace direct AiTrustHarnessService.verify() calls with this Agent in governance entry points
 * (e.g. AiDecisionFlowServiceImpl.callHarnessVerify()). Agent returns EvidenceGovernanceAgentResult
 * {decision, riskLevel, supportScore, selfEvidence, reasons, missingEvidence, suggestedHumanReviewAction}
 * but consumers expect AiHarnessDecisionDTO {decision, riskLevel, supportScore, selfEvidence, reasons,
 * acceptedSourceRefs, invalidSourceRefs, checkCode}. Core fields (decision, riskLevel, supportScore,
 * selfEvidence, reasons) are compatible; additional Agent fields (missingEvidence, suggestedHumanReviewAction)
 * would need mapping into AiHarnessDecisionDTO or consumer adaptation.
 *
 * @author system
 */
@Slf4j
@Service
public class EvidenceGovernanceAgentServiceImpl extends AbstractAgentService implements EvidenceGovernanceAgentService {

    private final LangChain4jAgentProperties properties;
    private final AgentFallbackService fallbackService;
    private final ObjectMapper objectMapper;
    private final EvidenceGovernanceAiService evidenceGovernanceAiService;
    private final AiTrustHarnessService aiTrustHarnessService;
    private final AgentMemoryContextService memoryContextService;
    private final AgentMemoryRuleEnforcer memoryRuleEnforcer;

    public EvidenceGovernanceAgentServiceImpl(
            LangChain4jAgentProperties properties,
            AgentFallbackService fallbackService,
            ObjectMapper objectMapper,
            ObjectProvider<EvidenceGovernanceAiService> aiServiceProvider,
            ObjectProvider<AiTrustHarnessService> harnessServiceProvider,
            AgentRunConfidencePolicy confidencePolicy,
            AgentMemoryContextService memoryContextService,
            AgentMemoryRuleEnforcer memoryRuleEnforcer) {
        super(confidencePolicy);
        this.properties = properties;
        this.fallbackService = fallbackService;
        this.objectMapper = objectMapper;
        this.evidenceGovernanceAiService = aiServiceProvider.getIfAvailable();
        this.aiTrustHarnessService = harnessServiceProvider.getIfAvailable();
        this.memoryContextService = memoryContextService;
        this.memoryRuleEnforcer = memoryRuleEnforcer;
    }

    @Override
    public EvidenceGovernanceAgentResult review(EvidenceGovernanceAgentRequest request) {
        String rawText = buildEvidenceRawText(request);
        AgentMemoryContextService.ContextRules contextRules = memoryContextService.resolveRules(
                rawText, AgentMemoryContextService.SCOPE_EVIDENCE);

        AiHarnessDecisionDTO deterministicDecision = runDeterministicCheck(request);

        if (!properties.isEnabled() || evidenceGovernanceAiService == null) {
            log.info("LangChain4j未启用，使用确定性规则结果");
            EvidenceGovernanceAgentResult merged = mergeResult(request, deterministicDecision, null, true);
            return applyMemoryRules(merged, request, contextRules);
        }

        return runWithFallback(() -> {
            String contextJson = buildEvidenceContextJsonWithMemory(request, contextRules);
            EvidenceGovernanceAgentResult aiResult = com.example.matching.agent.config.AgentToolProvider
                    .withScope(() -> evidenceGovernanceAiService.review(contextJson));
            if (aiResult == null) {
                throw new IllegalStateException("Evidence governance returned no structured result");
            }
            String serializedResult = objectMapper.writeValueAsString(aiResult);
            memoryRuleEnforcer.auditGuidanceResponse(contextRules, serializedResult,
                    "EVIDENCE_GOVERNANCE", request.getSourceType(), request.getSourceRefId(), rawText);
            aiResult.setRawModelOutput(serializedResult);

            EvidenceGovernanceAgentResult merged = mergeResult(request, deterministicDecision, aiResult, false);
            return applyMemoryRules(merged, request, contextRules);
        }, e -> {
            log.error("LangChain4j调用失败，使用确定性规则结果", e);
            EvidenceGovernanceAgentResult merged = mergeResult(request, deterministicDecision, null, true);
            return applyMemoryRules(merged, request, contextRules);
        });
    }

    private EvidenceGovernanceAgentResult applyMemoryRules(
            EvidenceGovernanceAgentResult result,
            EvidenceGovernanceAgentRequest request,
            AgentMemoryContextService.ContextRules contextRules) {
        if (contextRules == null || contextRules.hardRules() == null || contextRules.hardRules().isEmpty()) {
            return result;
        }

        String agentName = "EVIDENCE_GOVERNANCE";
        String hitText = request.getClaimText() != null ? request.getClaimText() : request.getSourceType();

        for (AgentMemoryPort.MemoryEntry rule : contextRules.hardRules()) {
            switch (rule.memoryType()) {
                case "SOURCE_POLICY" -> {
                    String enforced = memoryRuleEnforcer.enforceSourcePolicy(
                            request.getSourceType(), result.getDecision(), rule, agentName,
                            hitText, request.getSourceRefId(), null);
                    if (!enforced.equals(result.getDecision())) {
                        result.setDecision(enforced);
                        if (result.getReasons() == null) result.setReasons(new ArrayList<>());
                        result.getReasons().add("治理规则[SOURCE_POLICY]: " + rule.title());
                    } else {
                        memoryRuleEnforcer.recordRetrievedNotApplied(rule, agentName, request.getSourceType(),
                                request.getSourceRefId(), hitText);
                    }
                }
                case "TAG_REJECT" -> {
                    // 修复：仅当原决策不是 BLOCK/RETRY 时才置 REVIEW。
                    // 原实现无条件覆盖，击穿 mergeResult 的"BLOCK 不可被覆盖"契约，
                    // 使被 harness fail-closed 拒绝的声明进入待复核队列。
                    String before = result.getDecision();
                    if (!"BLOCK".equals(before) && !"RETRY".equals(before)) {
                        result.setDecision("REVIEW");
                        if (result.getReasons() == null) result.setReasons(new ArrayList<>());
                        result.getReasons().add("治理规则[TAG_REJECT]: " + rule.title());
                    } else {
                        log.info("治理规则[TAG_REJECT]不覆盖 {} 决策: {}", before, rule.title());
                    }
                    memoryRuleEnforcer.enforceTagReject(request.getSourceType(), rule, agentName,
                            hitText, request.getSourceRefId(), before);
                }
                case "LEVEL_RULE" -> {
                    if (result.getSupportScore() != null) {
                        int current = result.getSupportScore().intValue();
                        Integer capped = memoryRuleEnforcer.enforceLevelCap(
                                request.getSourceType(), current, rule, agentName,
                                hitText, request.getSourceRefId());
                        if (capped != null && capped < current) {
                            result.setSupportScore(BigDecimal.valueOf(capped));
                        } else {
                            memoryRuleEnforcer.recordRetrievedNotApplied(rule, agentName, request.getSourceType(),
                                    request.getSourceRefId(), hitText);
                        }
                    } else {
                        memoryRuleEnforcer.recordRetrievedNotApplied(rule, agentName, request.getSourceType(),
                                request.getSourceRefId(), hitText);
                    }
                }
            }
        }

        return result;
    }

    private String buildEvidenceContextJsonWithMemory(
            EvidenceGovernanceAgentRequest request,
            AgentMemoryContextService.ContextRules contextRules) {
        try {
            Map<String, Object> context = new java.util.LinkedHashMap<>();
            context.put("request", request);

            if (contextRules.guidancePrompt() != null && !contextRules.guidancePrompt().isBlank()) {
                context.put("governanceGuidance", contextRules.guidancePrompt());
            }
            if (contextRules.hardRuleSummary() != null && !contextRules.hardRuleSummary().isBlank()) {
                context.put("hardRuleSummary", contextRules.hardRuleSummary());
            }

            return objectMapper.writeValueAsString(context);
        } catch (Exception e) {
            log.error("构建证据治理上下文失败", e);
            return "{}";
        }
    }

    private String buildEvidenceRawText(EvidenceGovernanceAgentRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            return (request.getClaimText() != null ? request.getClaimText() : "")
                    + " " + (request.getSourceType() != null ? request.getSourceType() : "");
        }
    }

    /**
     * 执行确定性规则审核
     */
    private AiHarnessDecisionDTO runDeterministicCheck(EvidenceGovernanceAgentRequest request) {
        if (aiTrustHarnessService == null) {
            return null;
        }

        try {
            AiHarnessClaimDTO claim = new AiHarnessClaimDTO();
            claim.setScenario(request.getScenario());
            claim.setClaimType(request.getClaimType());
            claim.setClaimText(request.getClaimText());
            claim.setEvidenceText(request.getEvidenceText());
            claim.setSourceType(request.getSourceType());
            claim.setSourceRefId(request.getSourceRefId());
            claim.setSourceRefs(request.getSourceRefs());
            claim.setRagChunkIds(request.getRagChunkIds());
            claim.setMatchedTagId(request.getMatchedTagId());
            claim.setSimilarTagId(request.getSimilarTagId());

            return aiTrustHarnessService.verify(claim);
        } catch (Exception e) {
            log.warn("确定性规则审核失败", e);
            return null;
        }
    }

    /**
     * 合并确定性规则结果和AI结果
     * 关键规则：模型只能补充解释，不能覆盖规则引擎的 BLOCK/REVIEW
     */
    private EvidenceGovernanceAgentResult mergeResult(
            EvidenceGovernanceAgentRequest request,
            AiHarnessDecisionDTO deterministic,
            EvidenceGovernanceAgentResult aiResult,
            boolean fallbackUsed) {

        EvidenceGovernanceAgentResult result = new EvidenceGovernanceAgentResult();
        result.setFallbackUsed(fallbackUsed);

        // 如果没有确定性结果，使用AI结果或降级方案
        if (deterministic == null) {
            return fallbackService.fallbackEvidenceGovernance(request);
        }

        // 确定性结果的决策
        String deterministicDecision = deterministic.getDecision();
        String deterministicRiskLevel = deterministic.getRiskLevel();
        BigDecimal deterministicScore = deterministic.getSupportScore();
        boolean deterministicSelfEvidence = deterministic.isSelfEvidence();

        // 如果有AI结果，使用AI的解释，但决策不能降级
        if (aiResult != null && aiResult.getDecision() != null) {
            // 规则：BLOCK 不能被改成 PASS 或 REVIEW
            if ("BLOCK".equals(deterministicDecision)) {
                result.setDecision("BLOCK");
                result.setRiskLevel("HIGH");
                result.setReasons(mergeReasons(
                        deterministic.getReasons(),
                        aiResult.getReasons(),
                        "确定性规则判定为BLOCK，AI判定为" + aiResult.getDecision()));
            }
            // 规则：REVIEW 不能被模型改成 PASS；模型只能保持或收紧确定性决策。
            else if ("REVIEW".equals(deterministicDecision)) {
                if ("PASS".equals(aiResult.getDecision())) {
                    result.setDecision("REVIEW");
                    result.setRiskLevel("MEDIUM");
                    result.setReasons(mergeReasons(
                            deterministic.getReasons(),
                            aiResult.getReasons(),
                            "确定性规则判定为REVIEW，AI判定为PASS被覆盖"));
                } else {
                    result.setDecision(aiResult.getDecision());
                    result.setRiskLevel(aiResult.getRiskLevel());
                    result.setReasons(mergeReasons(deterministic.getReasons(), aiResult.getReasons(), null));
                }
            }
            // 规则：RETRY（引用无法验证，fail-closed）不能被模型降级为 PASS/REVIEW；
            // 模型只能收紧为 BLOCK，否则保持 RETRY 等待重试。
            else if ("RETRY".equals(deterministicDecision)) {
                if ("BLOCK".equals(aiResult.getDecision())) {
                    result.setDecision("BLOCK");
                    result.setRiskLevel("HIGH");
                    result.setReasons(mergeReasons(
                            deterministic.getReasons(),
                            aiResult.getReasons(),
                            "确定性规则判定为RETRY（来源引用无法验证），AI收紧为BLOCK"));
                } else {
                    result.setDecision("RETRY");
                    result.setRiskLevel(deterministicRiskLevel != null ? deterministicRiskLevel : "HIGH");
                    result.setReasons(mergeReasons(
                            deterministic.getReasons(),
                            aiResult.getReasons(),
                            "确定性规则判定为RETRY：来源引用当前无法验证，fail-closed，禁止降级"));
                }
            }
            // 确定性是PASS，AI可以提供更严格的判定；未知确定性决策按 fail-closed 处理
            else {
                if ("BLOCK".equals(aiResult.getDecision()) || "REVIEW".equals(aiResult.getDecision())) {
                    result.setDecision(aiResult.getDecision());
                    result.setRiskLevel(aiResult.getRiskLevel());
                    result.setReasons(mergeReasons(deterministic.getReasons(), aiResult.getReasons(),
                            "AI提供了更严格的判定"));
                } else if ("PASS".equals(deterministicDecision)) {
                    result.setDecision("PASS");
                    result.setRiskLevel(aiResult.getRiskLevel() != null ? aiResult.getRiskLevel() : "LOW");
                    result.setReasons(mergeReasons(deterministic.getReasons(), aiResult.getReasons(), null));
                } else {
                    result.setDecision("BLOCK");
                    result.setRiskLevel("HIGH");
                    result.setReasons(mergeReasons(deterministic.getReasons(), aiResult.getReasons(),
                            "未知的确定性决策: " + deterministicDecision + "，fail-closed 按 BLOCK 处理"));
                }
            }

            result.setSupportScore(normalizeSupportScore(
                    "BLOCK".equals(result.getDecision()) || "RETRY".equals(result.getDecision())
                            ? (deterministicScore != null ? deterministicScore : BigDecimal.ZERO)
                            : (aiResult.getSupportScore() != null ? aiResult.getSupportScore() : deterministicScore)));
            result.setSelfEvidence(deterministicSelfEvidence || Boolean.TRUE.equals(aiResult.getSelfEvidence()));
            result.setMissingEvidence(aiResult.getMissingEvidence());
            result.setSuggestedHumanReviewAction(aiResult.getSuggestedHumanReviewAction());
            result.setRawModelOutput(aiResult.getRawModelOutput());
        } else {
            // 没有AI结果，直接使用确定性结果
            result.setDecision(deterministicDecision);
            result.setRiskLevel(deterministicRiskLevel);
            result.setSupportScore(normalizeSupportScore(deterministicScore));
            result.setSelfEvidence(deterministicSelfEvidence);
            result.setReasons(deterministic.getReasons() != null ? deterministic.getReasons() : List.of());
            result.setMissingEvidence(List.of());
        }

        log.info("证据治理审核完成: decision={}, riskLevel={}, selfEvidence={}, deterministic={}, ai={}",
                result.getDecision(), result.getRiskLevel(), result.getSelfEvidence(),
                deterministicDecision, aiResult != null ? aiResult.getDecision() : "null");

        return finalizeRun(result, result.getSourceRefs(), fallbackUsed, result.getRawModelOutput());
    }

    private BigDecimal normalizeSupportScore(BigDecimal score) {
        if (score == null) {
            return BigDecimal.ZERO;
        }
        return score.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100));
    }

    private List<String> mergeReasons(List<String> deterministicReasons, List<String> aiReasons, String overrideNote) {
        List<String> merged = new ArrayList<>();
        if (deterministicReasons != null) {
            merged.addAll(deterministicReasons);
        }
        if (aiReasons != null) {
            merged.addAll(aiReasons);
        }
        if (overrideNote != null) {
            merged.add(overrideNote);
        }
        return merged;
    }

}
