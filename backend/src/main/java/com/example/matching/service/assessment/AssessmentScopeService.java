package com.example.matching.service.assessment;

import com.example.matching.dto.assessment.AssessmentScopeDTO;

/**
 * 评估范围服务。
 * <p>
 * 在测试/面试生成前，依据简历能力声明与目标岗位能力模型（服务端数据库）构建不可变
 * {@link AssessmentScopeDTO}，锁定「简历提出能力、岗位定义验证目标、AI 只核验既有能力」
 * 的单一路径。
 *
 * @author system
 */
public interface AssessmentScopeService {

    /**
     * 构建评估范围。
     * <p>
     * 规则：
     * <ul>
     *   <li>校验 workflow 存在且归属员工（workflow.empId == empId）；</li>
     *   <li>岗位要求与简历 Claim 按 abilityTagId 取交集（同名不同 ID 不合并）；</li>
     *   <li>无岗位要求的简历标签不进入本次 scope；</li>
     *   <li>岗位要求了但简历未声明的能力进入 uncoveredRequirements；</li>
     *   <li>items 与 uncoveredRequirements 均按 abilityTagId 稳定排序，并计算 scopeHash。</li>
     * </ul>
     *
     * @param workflowId 工作流ID
     * @param empId      员工ID（用于归属校验）
     * @param postId     目标岗位ID
     * @return 评估范围（确定性：相同输入产生相同 scopeHash）
     */
    AssessmentScopeDTO build(Long workflowId, Long empId, Long postId);
}
