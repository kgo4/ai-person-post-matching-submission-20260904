package com.example.matching.service.assessment;

import com.example.matching.entity.ability.PersonAbilityProfile;
import com.example.matching.entity.workflow.PersonAbilityClaimGroup;
import com.example.matching.entity.workflow.PersonAbilityLevelDecision;

import java.util.List;
import java.util.Map;

/**
 * 能力画像投影服务接口
 * <p>
 * 将正式结论投影为 EmpAbility 和 PersonAbilityProfile；
 * 同时提供待确立能力视图。
 *
 * @author system
 */
public interface AbilityProfileProjectionService {

    /**
     * 将 AUTO_CONFIRMED / HUMAN_CONFIRMED 决策投影为 EmpAbility + 正式 PersonAbilityProfile。
     * 每个投影写入对应 governance_admission（PASS）以满足写守卫。
     *
     * @return 投影的能力数量
     */
    int projectConfirmed(Long workflowId, Long operatorId);

    /**
     * 待确立能力视图（COLLECTED / READY_FOR_AGGREGATE_HARNESS / PENDING_MANUAL_REVIEW / TAG_CANDIDATE_PENDING）。
     */
    List<Map<String, Object>> getProvisionalView(Long empId);

    /**
     * 正式画像视图。
     */
    List<PersonAbilityProfile> getConfirmedProfile(Long empId);

    /**
     * 获取员工待确立聚合组。
     */
    List<PersonAbilityClaimGroup> listProvisionalGroups(Long empId);

    /**
     * 获取工作流内可投影决策（AUTO_CONFIRMED / HUMAN_CONFIRMED）。
     */
    List<PersonAbilityLevelDecision> listProjectableDecisions(Long workflowId);
}
