package com.example.matching.service.assessment;

import com.example.matching.entity.workflow.PersonAbilityLevelDecision;

import java.util.List;

/**
 * 最终能力等级确认中心服务接口
 * <p>
 * 读取权重策略和 Harness 结果，生成正式结论。
 *
 * @author system
 */
public interface AbilityLevelConfirmationService {

    /**
     * 执行等级确认（LEVEL_CONFIRMATION 阶段）。
     * 仅 Harness PASS 的 Claim Group 参与自动等级计算。
     * REVIEW/BLOCK 进入 PENDING_MANUAL_REVIEW / 不参与。
     *
     * @return 生成的决策记录
     */
    List<PersonAbilityLevelDecision> confirmLevels(Long workflowId, Long stageRunId);

    /**
     * 按新策略重算：AUTO_CONFIRMED 标记待重算，PENDING_MANUAL_REVIEW 立即重算建议，
     * HUMAN_CONFIRMED 不静默改写。
     *
     * @param workflowId    工作流ID
     * @param newPolicyVersion 新策略版本
     */
    void recalculateByPolicy(Long workflowId, String newPolicyVersion);

    /**
     * 人工确认等级。
     */
    PersonAbilityLevelDecision humanConfirm(Long decisionId, Integer finalLevel, Integer finalConfidence,
                                            String reason, Long reviewerId);

    /**
     * 人工拒绝。
     */
    PersonAbilityLevelDecision humanReject(Long decisionId, String reason, Long reviewerId);

    /**
     * 查询工作流的决策记录。
     */
    List<PersonAbilityLevelDecision> listDecisions(Long workflowId);
}
