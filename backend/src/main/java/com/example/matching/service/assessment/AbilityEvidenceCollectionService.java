package com.example.matching.service.assessment;

import com.example.matching.dto.assessment.ResumeAbilityClaimDTO;
import com.example.matching.entity.ability.PersonAbilityClaim;

import java.util.List;

/**
 * 能力证据收集服务接口
 * <p>
 * 将每阶段输出保存为原始能力证据，不做正式融合。
 *
 * @author system
 */
public interface AbilityEvidenceCollectionService {

    /**
     * 保存简历能力证据（阶段 1）。
     * 确定性校验：能力名称/声明等级/原文证据非空、证据可定位、sourceRef 有效。
     * 输出：COLLECTED + DISPLAY_ONLY。
     *
     * @return 保存的 Claim 数量
     */
    int saveResumeClaims(Long workflowId, Long stageRunId, Long empId,
                         List<ResumeAbilityClaimDTO> claims, Long operatorId);

    /**
     * 保存测试证据 Claim（阶段 2）。
     */
    int saveTestClaims(Long workflowId, Long stageRunId, Long empId,
                       List<PersonAbilityClaim> claims, Long operatorId);

    /**
     * 保存面试证据 Claim（阶段 3）。
     */
    int saveInterviewClaims(Long workflowId, Long stageRunId, Long empId,
                            List<PersonAbilityClaim> claims, Long operatorId);

    /**
     * 将工作流内的 Claim 按能力聚合为 Claim Group。
     * 标签可归一则 RESOLVED，否则 TAG_CANDIDATE_PENDING/UNRESOLVED。
     *
     * @return 聚合组数量
     */
    int groupClaimsByAbility(Long workflowId, Long empId);

    /**
     * 标记聚合组及其 Claim 为 READY_FOR_AGGREGATE_HARNESS。
     */
    void markReadyForAggregateHarness(Long workflowId);

    /**
     * 获取工作流内的 Claim 列表。
     */
    List<PersonAbilityClaim> listClaimsByWorkflow(Long workflowId);

    /**
     * 获取聚合组内全部 Claim。
     */
    List<PersonAbilityClaim> listClaimsByGroup(Long claimGroupId);
}
