package com.example.matching.service.governance.impl;

import com.example.matching.ai.context.dto.AiContextSourceRefDTO;
import com.example.matching.ai.context.service.AiContextSourceRefService;
import com.example.matching.agent.dto.EvidenceGovernanceAgentRequest;
import com.example.matching.agent.dto.EvidenceGovernanceAgentResult;
import com.example.matching.agent.service.EvidenceGovernanceAgentService;
import com.example.matching.dto.harness.AiHarnessClaimDTO;
import com.example.matching.dto.harness.AiHarnessDecisionDTO;
import com.example.matching.service.governance.AiDecisionFlowService;
import com.example.matching.service.harness.AiTrustHarnessService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * AI决策分流服务实现
 * <p>
 * 根据以下条件判断风险等级：
 * - sourceRef 有效性（通过 AiContextSourceRefService.resolve 真实校验）
 * - Harness 审核结果（通过 AiTrustHarnessService.verify 真实校验）
 * - 置信度分数
 * - 标签是否存在
 * - 是否与现有能力冲突
 * - 能力等级提升幅度
 * - 来源是否为 AI 自证
 * <p>
 * TODO-WF3: Replace harnessService.verify(claim) in callHarnessVerify() with
 * EvidenceGovernanceAgentService.review(request) once output mapping is defined.
 * Agent returns {decision, riskLevel, supportScore, selfEvidence, reasons, missingEvidence,
 * suggestedHumanReviewAction} but consumer expects AiHarnessDecisionDTO {decision, riskLevel,
 * supportScore, selfEvidence, reasons, acceptedSourceRefs, invalidSourceRefs}. Core fields
 * are compatible; additional Agent fields require mapping into AiHarnessDecisionDTO.
 *
 * @author system
 */
@Slf4j
@Service
public class AiDecisionFlowServiceImpl implements AiDecisionFlowService {

    /** 置信度阈值：高可信 */
    private static final int CONFIDENCE_HIGH = 80;
    /** 置信度阈值：中等 */
    private static final int CONFIDENCE_MEDIUM = 60;

    private final AiContextSourceRefService sourceRefService;
    private final AiTrustHarnessService harnessService;
    private final EvidenceGovernanceAgentService evidenceGovernanceAgentService;

    public AiDecisionFlowServiceImpl(AiContextSourceRefService sourceRefService,
                                     AiTrustHarnessService harnessService,
                                     EvidenceGovernanceAgentService evidenceGovernanceAgentService) {
        this.sourceRefService = sourceRefService;
        this.harnessService = harnessService;
        this.evidenceGovernanceAgentService = evidenceGovernanceAgentService;
    }

    @Override
    public AiDecisionFlowResult evaluate(AiDecisionFlowRequest request) {
        AiDecisionFlowResult result = new AiDecisionFlowResult();
        List<String> reasons = new ArrayList<>();

        // 1. 通过 AiContextSourceRefService.resolve 校验来源有效性
        boolean sourceRefValid = checkSourceRefValidity(request);
        result.setSourceRefValid(sourceRefValid);

        if (!sourceRefValid) {
            reasons.add("来源引用无效或为空");
        }

        // 2. 检查证据文本
        boolean evidenceValid = checkEvidenceValidity(request);
        if (!evidenceValid) {
            reasons.add("证据文本为空或无效");
        }

        // 3. 检查是否为 AI 自证
        boolean selfEvidence = checkSelfEvidence(request);
        if (selfEvidence) {
            reasons.add("来源为AI自证，可信度低");
        }

        // 4. 获取置信度
        Integer confidence = request.getConfidenceScore();
        if (confidence == null) {
            confidence = 0;
            reasons.add("置信度分数缺失");
        }

        // 5. 调用 Harness 真实校验
        AiHarnessDecisionDTO harnessResult = callHarnessVerify(request, confidence);
        if (harnessResult != null) {
            result.setHarnessDecision(harnessResult.getDecision());
            if (harnessResult.isSelfEvidence()) {
                selfEvidence = true;
                if (!reasons.stream().anyMatch(r -> r.contains("AI自证"))) {
                    reasons.add("Harness判定为AI自证");
                }
            }
            if (harnessResult.getReasons() != null) {
                reasons.addAll(harnessResult.getReasons());
            }
        }

        // 6. 综合评估
        RiskLevel riskLevel = assessRiskLevel(sourceRefValid, evidenceValid, selfEvidence, confidence, harnessResult);
        result.setRiskLevel(riskLevel);

        // 7. 确定决策（Harness 决策优先级高于纯规则）
        Decision decision = determineDecision(riskLevel, sourceRefValid, evidenceValid, selfEvidence, confidence, harnessResult);
        result.setDecision(decision);

        // 8. 如果 Harness 已给出判定，以 Harness 为准
        if (harnessResult != null && harnessResult.getDecision() != null) {
            result.setHarnessDecision(harnessResult.getDecision());
        } else {
            result.setHarnessDecision(mapToHarnessDecision(decision, riskLevel));
        }

        // 9. 添加决策原因
        reasons.add("置信度: " + confidence);
        reasons.add("风险等级: " + riskLevel);
        reasons.add("决策: " + decision);
        result.setReasons(reasons);

        log.info("AI决策分流: scenario={}, decision={}, riskLevel={}, confidence={}, harnessDecision={}",
                request.getScenario(), decision, riskLevel, confidence, result.getHarnessDecision());

        return result;
    }

    /**
     * 通过 AiContextSourceRefService.resolve 校验来源引用有效性
     */
    private boolean checkSourceRefValidity(AiDecisionFlowRequest request) {
        List<String> sourceRefs = request.getSourceRefs();
        if (sourceRefs == null || sourceRefs.isEmpty()) {
            return false;
        }

        for (String ref : sourceRefs) {
            if (ref == null || ref.trim().isEmpty()) {
                continue;
            }
            try {
                AiContextSourceRefDTO resolved = sourceRefService.resolve(ref);
                if (resolved != null) {
                    return true;
                }
            } catch (Exception e) {
                log.debug("来源引用解析失败: ref={}, error={}", ref, e.getMessage());
            }
        }

        return false;
    }

    /**
     * 调用 AiTrustHarnessService.verify 进行真实 Harness 校验
     * <p>
     * AI辅助审核: Agent review() 结果作为规则引擎 verify() 的初始参考，最终决定仍由规则引擎做出
     */
    private AiHarnessDecisionDTO callHarnessVerify(AiDecisionFlowRequest request, int confidence) {
        // AI辅助审核: Agent review() 结果作为规则引擎 verify() 的初始参考，最终决定仍由规则引擎做出
        EvidenceGovernanceAgentResult agentResult = null;
        try {
            EvidenceGovernanceAgentRequest agentReq = buildEvidenceGovernanceRequest(request);
            agentResult = evidenceGovernanceAgentService.review(agentReq);
            log.info("Agent审核完成: decision={}, riskLevel={}, supportScore={}",
                    agentResult.getDecision(), agentResult.getRiskLevel(), agentResult.getSupportScore());
        } catch (Exception e) {
            log.warn("Agent审核调用失败，静默降级为纯规则引擎: {}", e.getMessage());
        }

        try {
            AiHarnessClaimDTO claim = new AiHarnessClaimDTO();
            claim.setScenario(request.getScenario());
            claim.setClaimType(request.getClaimType());
            claim.setClaimText(request.getClaimText());
            claim.setEvidenceText(request.getEvidenceText());
            claim.setSourceRefs(request.getSourceRefs() != null ? request.getSourceRefs() : new ArrayList<>());

            // 将Agent结果作为初始参考权重（取均值）
            if (agentResult != null && agentResult.getSupportScore() != null) {
                double adjustedConfidence = (confidence + agentResult.getSupportScore().doubleValue()) / 2.0;
                claim.setConfidence(Math.round(adjustedConfidence * 100.0) / 100.0);
            } else {
                claim.setConfidence((double) confidence);
            }

            AiHarnessDecisionDTO harnessResult = harnessService.verify(claim);

            // 将Agent的审核原因合并到规则引擎结果中
            if (agentResult != null && agentResult.getReasons() != null && !agentResult.getReasons().isEmpty()) {
                if (harnessResult.getReasons() == null) {
                    harnessResult.setReasons(new ArrayList<>());
                }
                harnessResult.getReasons().add(0,
                        "[AgentAI] " + String.join("; ", agentResult.getReasons()));
            }

            return harnessResult;
        } catch (Exception e) {
            log.warn("Harness校验调用失败，降级为规则判断: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 构建证据治理Agent请求
     */
    private EvidenceGovernanceAgentRequest buildEvidenceGovernanceRequest(AiDecisionFlowRequest request) {
        EvidenceGovernanceAgentRequest req = new EvidenceGovernanceAgentRequest();
        req.setScenario(request.getScenario());
        req.setClaimType(request.getClaimType());
        req.setClaimText(request.getClaimText());
        req.setEvidenceText(request.getEvidenceText());
        req.setSourceRefs(request.getSourceRefs());
        return req;
    }

    /**
     * 检查证据有效性
     */
    private boolean checkEvidenceValidity(AiDecisionFlowRequest request) {
        String evidenceText = request.getEvidenceText();
        return evidenceText != null && !evidenceText.trim().isEmpty();
    }

    /**
     * 检查是否为 AI 自证
     */
    private boolean checkSelfEvidence(AiDecisionFlowRequest request) {
        List<String> sourceRefs = request.getSourceRefs();
        if (sourceRefs == null) {
            return false;
        }

        // 检查来源是否全部为 AI 生成
        for (String ref : sourceRefs) {
            if (ref != null && !ref.contains("AI_") && !ref.contains("LLM_")) {
                return false;
            }
        }

        return !sourceRefs.isEmpty();
    }

    /**
     * 评估风险等级（综合 Harness 结果）
     */
    private RiskLevel assessRiskLevel(boolean sourceRefValid, boolean evidenceValid,
                                       boolean selfEvidence, int confidence,
                                       AiHarnessDecisionDTO harnessResult) {
        // 如果 Harness 已判定 BLOCK，直接高风险
        if (harnessResult != null && AiHarnessDecisionDTO.BLOCK.equals(harnessResult.getDecision())) {
            return RiskLevel.HIGH;
        }

        // 高风险条件
        if (!sourceRefValid || selfEvidence || confidence < CONFIDENCE_MEDIUM) {
            return RiskLevel.HIGH;
        }

        // 如果 Harness 判定 REVIEW，至少中风险
        if (harnessResult != null && AiHarnessDecisionDTO.REVIEW.equals(harnessResult.getDecision())) {
            return RiskLevel.MEDIUM;
        }

        // 中风险条件
        if (!evidenceValid || confidence < CONFIDENCE_HIGH) {
            return RiskLevel.MEDIUM;
        }

        // 低风险
        return RiskLevel.LOW;
    }

    /**
     * 确定决策（Harness 决策优先）
     */
    private Decision determineDecision(RiskLevel riskLevel, boolean sourceRefValid,
                                        boolean evidenceValid, boolean selfEvidence,
                                        int confidence, AiHarnessDecisionDTO harnessResult) {
        // Harness BLOCK → 拒绝
        if (harnessResult != null && AiHarnessDecisionDTO.BLOCK.equals(harnessResult.getDecision())) {
            return Decision.REJECT;
        }

        // 拒绝条件
        if (!sourceRefValid || selfEvidence) {
            return Decision.REJECT;
        }

        // 高风险：拒绝
        if (riskLevel == RiskLevel.HIGH) {
            return Decision.REJECT;
        }

        // Harness REVIEW → 人工确认
        if (harnessResult != null && AiHarnessDecisionDTO.REVIEW.equals(harnessResult.getDecision())) {
            return Decision.HUMAN_REVIEW;
        }

        // 中风险：人工确认
        if (riskLevel == RiskLevel.MEDIUM) {
            return Decision.HUMAN_REVIEW;
        }

        // 低风险且高置信度：自动写入
        if (confidence >= CONFIDENCE_HIGH) {
            return Decision.AUTO_APPLY;
        }

        // 其他情况：人工确认
        return Decision.HUMAN_REVIEW;
    }

    /**
     * 映射到 Harness 判定
     */
    private String mapToHarnessDecision(Decision decision, RiskLevel riskLevel) {
        switch (decision) {
            case AUTO_APPLY:
                return "PASS";
            case HUMAN_REVIEW:
                return "REVIEW";
            case REJECT:
                return "BLOCK";
            default:
                return "REVIEW";
        }
    }
}
