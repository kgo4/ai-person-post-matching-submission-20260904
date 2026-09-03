package com.example.matching.service.ability;

import com.example.matching.entity.ability.PersonAbilityClaim;
import com.example.matching.entity.ability.PersonAbilityProfile;
import com.example.matching.entity.interview.InterviewAbilityObservation;

import java.util.List;

/**
 * 人员能力画像构建服务接口
 * <p>
 * 职责：转换、融合、冲突处理、画像写入。
 * 各来源的能力提取由 PersonAbilityExtractionAgent 负责。
 * AI面试观察由 AIInterviewAgent 负责。
 *
 * @author system
 */
public interface PersonAbilityProfileBuildService {

    /**
     * 将面试能力观察转换为能力主张
     *
     * @param observations 面试能力观察列表
     * @return 转换后的能力主张列表
     */
    List<PersonAbilityClaim> convertObservationsToClaims(List<InterviewAbilityObservation> observations);

    /**
     * 融合多来源能力主张，构建最终人员能力画像
     * <p>
     * 融合规则：
     * 1. PMS / 项目系统：0.30
     * 2. AI 面试：0.25
     * 3. AI 测评：0.20
     * 4. 简历解析：0.15
     * 5. 学习成果：0.10
     *
     * @param empId  员工ID
     * @param claims 多来源能力主张列表
     * @return 融合后的人员能力画像列表
     */
    List<PersonAbilityProfile> buildProfile(Long empId, List<PersonAbilityClaim> claims);

    /**
     * 处理能力冲突
     * <p>
     * 当不同来源的能力主张存在冲突时，进入REVIEW状态。
     *
     * @param empId              员工ID
     * @param conflictingClaims 冲突的能力主张列表
     * @return 冲突处理结果
     */
    ConflictResolution resolveConflicts(Long empId, List<PersonAbilityClaim> conflictingClaims);

    /**
     * 获取员工的最新能力画像
     *
     * @param empId 员工ID
     * @return 人员能力画像列表
     */
    List<PersonAbilityProfile> getLatestProfile(Long empId);

    /**
     * 获取员工特定能力的画像
     *
     * @param empId 员工ID
     * @param tagId 能力标签ID
     * @return 人员能力画像
     */
    PersonAbilityProfile getProfileByTag(Long empId, Long tagId);

    /**
     * 冲突处理结果
     */
    record ConflictResolution(
        /** 是否存在冲突 */
        boolean hasConflict,
        /** 冲突说明 */
        String conflictDescription,
        /** 建议的处理方式 */
        String suggestedAction,
        /** 需要人工审核的能力标签ID列表 */
        List<Long> tagsNeedingReview
    ) {}
}
