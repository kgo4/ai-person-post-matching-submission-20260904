package com.example.matching.service.ability;

import com.example.matching.entity.ability.PersonAbilityClaim;
import com.example.matching.entity.ability.PersonAbilityProfile;
import com.example.matching.entity.interview.InterviewAbilityObservation;

import java.util.List;

/**
 * 人员能力画像Agent（顶层编排服务）
 * <p>
 * 职责：综合多来源能力证据，生成最终人员能力画像。
 * <p>
 * 能力来源：
 * 1. 简历解析 - 发现候选人声称的经历和技能，可信度中等
 * 2. AI面试 - 验证简历真实性、能力边界、表达逻辑、问题解决能力
 * 3. AI测评 - 验证知识掌握和标准题表现
 * 4. PMS / 项目系统 - 验证真实工作表现，可信度较高
 * 5. 学习成果 - 验证近期提升和成长轨迹
 * 6. 人工录入 - 人工评估和确认
 * <p>
 * 融合规则：
 * 1. 有其他来源时，AI面试用于验证、补充、发现冲突
 * 2. 无其他来源时，AI面试可构建初步画像，但应标记来源为INTERVIEW_INITIAL
 * 3. 最终画像必须保留来源明细
 * 4. 任何能力等级变化都应能追溯到具体证据
 * 5. 冲突不自动覆盖，进入REVIEW
 *
 * @author system
 */
public interface PersonAbilityProfileAgent {

    /**
     * 构建人员能力画像
     * <p>
     * 完整流程：
     * 1. 通过PersonAbilityExtractionAgent提取各来源能力主张
     * 2. 通过AIInterviewAgent获取面试能力观察（如果有面试记录）
     * 3. 将面试观察转换为能力主张
     * 4. 通过PersonAbilityProfileBuildService融合所有来源
     * 5. 生成最终人员能力画像
     *
     * @param empId 员工ID
     * @return 构建的人员能力画像列表
     */
    List<PersonAbilityProfile> buildProfile(Long empId);

    /**
     * 构建人员能力画像（包含指定面试会话）
     * <p>
     * 当需要将特定面试会话的结果纳入画像时使用。
     *
     * @param empId     员工ID
     * @param sessionId 面试会话ID（可选）
     * @return 构建的人员能力画像列表
     */
    List<PersonAbilityProfile> buildProfileWithInterview(Long empId, Long sessionId);

    /**
     * 刷新人员能力画像
     * <p>
     * 当有新的能力证据（如新的面试、测评结果）时，调用此方法刷新画像。
     *
     * @param empId 员工ID
     * @return 刷新后的人员能力画像列表
     */
    List<PersonAbilityProfile> refreshProfile(Long empId);

    /**
     * 获取人员能力画像
     *
     * @param empId 员工ID
     * @return 当前的人员能力画像列表
     */
    List<PersonAbilityProfile> getProfile(Long empId);

    /**
     * 获取人员特定能力的画像
     *
     * @param empId 员工ID
     * @param tagId 能力标签ID
     * @return 人员能力画像
     */
    PersonAbilityProfile getProfileByTag(Long empId, Long tagId);

    /**
     * 检查是否有待审核的能力画像
     *
     * @param empId 员工ID
     * @return 是否有待审核的画像
     */
    boolean hasPendingReview(Long empId);

    /**
     * 审核人员能力画像
     *
     * @param profileId 画像ID
     * @param reviewerId 审核人ID
     * @param approved 是否通过
     * @param comment 审核意见
     * @return 审核后的画像
     */
    PersonAbilityProfile reviewProfile(Long profileId, Long reviewerId, boolean approved, String comment);
}
