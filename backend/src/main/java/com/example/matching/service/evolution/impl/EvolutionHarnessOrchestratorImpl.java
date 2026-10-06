package com.example.matching.service.evolution.impl;

import com.example.matching.dto.evolution.PostEvolutionAgentResult;
import com.example.matching.dto.evolution.PostEvolutionAgentResult.HarnessSummary;
import com.example.matching.dto.evolution.PostEvolutionAgentResult.PostEvolutionChangeProposal;
import com.example.matching.dto.harness.AiHarnessClaimDTO;
import com.example.matching.dto.harness.AiHarnessDecisionDTO;
import com.example.matching.service.evolution.EvolutionHarnessOrchestrator;
import com.example.matching.service.harness.AiTrustHarnessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 演化 Harness 编排器实现
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvolutionHarnessOrchestratorImpl implements EvolutionHarnessOrchestrator {

    private final AiTrustHarnessService aiTrustHarnessService;

    @Override
    public AiHarnessDecisionDTO verifyProposal(PostEvolutionChangeProposal proposal, Long postId, Long taskId) {
        log.debug("Harness 校验变更建议: ability={}, changeType={}", proposal.getAbilityName(), proposal.getChangeType());

        // 构建 Harness Claim
        AiHarnessClaimDTO claim = buildClaim(proposal, postId, taskId);

        // 调用 Harness 服务
        AiHarnessDecisionDTO decision = aiTrustHarnessService.verify(claim);

        // 更新提案的 Harness 决策
        proposal.setHarnessDecision(decision.getDecision());

        log.debug("Harness 决策: ability={}, decision={}", proposal.getAbilityName(), decision.getDecision());
        return decision;
    }

    @Override
    public HarnessSummary verifyAllProposals(PostEvolutionAgentResult result, Long postId, Long taskId) {
        List<PostEvolutionChangeProposal> proposals = result.getProposals();
        if (proposals == null || proposals.isEmpty()) {
            HarnessSummary summary = new HarnessSummary();
            summary.setPass(0);
            summary.setReview(0);
            summary.setBlock(0);
            summary.setTotal(0);
            return summary;
        }

        int passCount = 0;
        int reviewCount = 0;
        int blockCount = 0;

        for (PostEvolutionChangeProposal proposal : proposals) {
            AiHarnessDecisionDTO decision = verifyProposal(proposal, postId, taskId);

            switch (decision.getDecision()) {
                case "PASS":
                    passCount++;
                    break;
                case "REVIEW":
                    reviewCount++;
                    break;
                case "BLOCK":
                    blockCount++;
                    break;
                default:
                    reviewCount++;
            }
        }

        HarnessSummary summary = new HarnessSummary();
        summary.setPass(passCount);
        summary.setReview(reviewCount);
        summary.setBlock(blockCount);
        summary.setTotal(proposals.size());

        log.info("Harness 校验完成: total={}, pass={}, review={}, block={}",
                summary.getTotal(), summary.getPass(), summary.getReview(), summary.getBlock());

        return summary;
    }

    /**
     * 构建 Harness Claim
     */
    private AiHarnessClaimDTO buildClaim(PostEvolutionChangeProposal proposal, Long postId, Long taskId) {
        AiHarnessClaimDTO claim = new AiHarnessClaimDTO();
        claim.setScenario("POST_EVOLUTION");
        claim.setClaimType("ABILITY_CHANGE");
        claim.setChangeType(resolveHarnessChangeType(proposal));
        claim.setClaimText(buildClaimText(proposal));
        claim.setSourceType("POST_EVOLUTION_AGENT");
        claim.setSourceRefId(taskId);
        claim.setEvidenceText(proposal.getEvidenceText());
        claim.setBusinessTargetType("POST_ABILITY_MODEL");
        claim.setBusinessTargetId(postId);

        // 设置来源引用
        if (proposal.getSourceRefs() != null) {
            claim.setSourceRefs(proposal.getSourceRefs());
        }

        // 设置能力标签ID
        claim.setMatchedTagId(proposal.getAbilityTagId());

        return claim;
    }

    private String resolveHarnessChangeType(PostEvolutionChangeProposal proposal) {
        if (proposal.getChangeType() == null) {
            return "UPDATE_ABILITY";
        }
        return switch (proposal.getChangeType()) {
            case "ADD" -> "ADD_ABILITY";
            case "REMOVE" -> "REMOVE_ABILITY";
            case "UPDATE" -> {
                if (proposal.getOldLevel() != null && proposal.getNewLevel() != null) {
                    yield proposal.getNewLevel() > proposal.getOldLevel()
                            ? "UPGRADE_LEVEL" : "DOWNGRADE_LEVEL";
                }
                yield "UPDATE_ABILITY";
            }
            default -> proposal.getChangeType();
        };
    }


    /**
     * 构建 Claim 文本
     */
    private String buildClaimText(PostEvolutionChangeProposal proposal) {
        StringBuilder sb = new StringBuilder();

        switch (proposal.getChangeType()) {
            case "ADD":
                sb.append("岗位需要新增能力：").append(proposal.getAbilityName());
                if (proposal.getNewLevel() != null) {
                    sb.append("，要求等级").append(proposal.getNewLevel());
                }
                break;
            case "UPDATE":
                sb.append("岗位能力 ").append(proposal.getAbilityName()).append(" 需要调整");
                if (proposal.getNewLevel() != null && !proposal.getNewLevel().equals(proposal.getOldLevel())) {
                    sb.append("，等级从").append(proposal.getOldLevel()).append("调整为").append(proposal.getNewLevel());
                }
                if (proposal.getNewWeight() != null && proposal.getOldWeight() != null
                        && proposal.getNewWeight().subtract(proposal.getOldWeight()).abs().doubleValue() >= 5) {
                    sb.append("，权重从").append(proposal.getOldWeight()).append("调整为").append(proposal.getNewWeight());
                }
                break;
            case "REMOVE":
                sb.append("岗位可以移除能力：").append(proposal.getAbilityName());
                break;
            default:
                sb.append("岗位能力 ").append(proposal.getAbilityName()).append(" 需要变更");
        }

        return sb.toString();
    }
}
