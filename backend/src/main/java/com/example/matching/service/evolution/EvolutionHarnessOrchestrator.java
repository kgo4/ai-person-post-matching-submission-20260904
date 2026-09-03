package com.example.matching.service.evolution;

import com.example.matching.dto.evolution.PostEvolutionAgentResult;
import com.example.matching.dto.harness.AiHarnessDecisionDTO;

/**
 * 演化 Harness 编排器接口
 * <p>
 * 负责对每条变更建议逐条进行 Harness 校验。
 *
 * @author system
 */
public interface EvolutionHarnessOrchestrator {

    /**
     * 对单条变更建议进行 Harness 校验
     *
     * @param proposal 变更建议
     * @param postId   岗位ID
     * @param taskId   任务ID（可为null）
     * @return Harness 决策
     */
    AiHarnessDecisionDTO verifyProposal(PostEvolutionAgentResult.PostEvolutionChangeProposal proposal,
                                         Long postId, Long taskId);

    /**
     * 对 Agent 结果中的所有变更建议进行 Harness 校验
     *
     * @param result Agent 结果
     * @param postId 岗位ID
     * @param taskId 任务ID（可为null）
     * @return 校验后的 Harness 摘要
     */
    PostEvolutionAgentResult.HarnessSummary verifyAllProposals(PostEvolutionAgentResult result,
                                                                Long postId, Long taskId);
}
