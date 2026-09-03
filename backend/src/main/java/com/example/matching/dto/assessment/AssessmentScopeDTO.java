package com.example.matching.dto.assessment;

import java.math.BigDecimal;
import java.util.List;

/**
 * 评估范围（AssessmentScope）：不可变契约。
 * <p>
 * 不可变业务规则（见实施计划 §0）：
 * <ul>
 *   <li>简历解析是唯一允许提出候选能力标签的阶段。</li>
 *   <li>一次评估必须绑定一个 postId；岗位能力要求来自服务端数据库。</li>
 *   <li>评估范围包含简历声明标签。岗位要求只为匹配标签提供目标等级、权重和优先级；
 *       岗位未命中的简历标签仍可被核验，岗位未覆盖的标签仅记录为 uncoveredRequirements。</li>
 *   <li>AI 测试和 AI 面试只能验证本 scope 中的标签，不得新增、改名或输出无法映射的 tagId。</li>
 * </ul>
 * <p>
 * 本 DTO 一经 {@link com.example.matching.service.assessment.AssessmentScopeService#build} 生成即锁定；
 * 阶段重试必须复用同一 {@code scopeHash}，岗位不可中途替换。
 *
 * @param workflowId             能力评估工作流ID
 * @param empId                  员工ID
 * @param postId                 目标岗位ID
 * @param items                  简历能力核验范围（岗位匹配信息可选）
 * @param uncoveredRequirements  岗位未覆盖要求（仅用于岗位差距展示与出题决策，绝不转换为人员 Claim）
 * @param scopeHash              范围内容哈希（稳定排序后计算，用于幂等与重试复用）
 */
public record AssessmentScopeDTO(
        Long workflowId,
        Long empId,
        Long postId,
        List<AssessmentScopeItem> items,
        List<UncoveredPostRequirement> uncoveredRequirements,
        String scopeHash) {

    /**
     * 单个待核验能力：来自简历声明，岗位要求信息可选。
     */
    public record AssessmentScopeItem(
            Long abilityTagId,
            Long assessmentAbilityId,
            Long canonicalTagId,
            String abilityName,
            List<Long> resumeClaimIds,
            Integer claimedLevel,
            Long postRequirementId,
            Integer requiredLevel,
            boolean required,
            boolean core,
            BigDecimal weight,
            List<String> resumeEvidenceRefs) {
        /** Backward-compatible constructor used by legacy callers and tests. */
        public AssessmentScopeItem(Long abilityTagId, String abilityName, List<Long> resumeClaimIds,
                                   Integer claimedLevel, Long postRequirementId, Integer requiredLevel,
                                   boolean required, boolean core, BigDecimal weight,
                                   List<String> resumeEvidenceRefs) {
            this(abilityTagId, abilityTagId, abilityTagId, abilityName, resumeClaimIds, claimedLevel,
                    postRequirementId, requiredLevel, required, core, weight, resumeEvidenceRefs);
        }

        public Long assessmentAbilityId() {
            return assessmentAbilityId;
        }
    }

    /**
     * 岗位未覆盖要求：岗位要求了但简历未声明对应标签的能力。
     * <p>
     * 只用于岗位差距展示和出题决策，绝不能转换为人员 Claim。
     */
    public record UncoveredPostRequirement(
            Long postRequirementId,
            Long abilityTagId,
            String abilityName,
            Integer requiredLevel,
            String reason) {
        public Long assessmentAbilityId() {
            return abilityTagId;
        }
    }
}
