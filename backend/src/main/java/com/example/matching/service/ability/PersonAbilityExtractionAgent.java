package com.example.matching.service.ability;

import com.example.matching.entity.ability.PersonAbilityClaim;

import java.util.List;

/**
 * 人员能力提取Agent接口
 * <p>
 * 负责从各来源提取能力主张（PersonAbilityClaim）。
 * 来源包括：简历解析、AI测评、PMS、项目系统、学习成果等。
 * <p>
 * 注意：AI面试由独立的AIInterviewAgent负责，输出InterviewAbilityObservation。
 * 本Agent不处理AI面试来源。
 *
 * @author system
 */
public interface PersonAbilityExtractionAgent {

    /**
     * 从简历解析结果提取能力主张
     *
     * @param empId   员工ID
     * @param parseId 简历解析记录ID
     * @return 提取的能力主张列表
     */
    List<PersonAbilityClaim> extractFromResume(Long empId, Long parseId);

    /**
     * 从AI测评结果提取能力主张
     *
     * @param empId  员工ID
     * @param testId AI测评记录ID
     * @return 提取的能力主张列表
     */
    List<PersonAbilityClaim> extractFromAiTest(Long empId, Long testId);

    /**
     * 从PMS系统提取能力主张
     *
     * @param empId 员工ID
     * @return 提取的能力主张列表
     */
    List<PersonAbilityClaim> extractFromPms(Long empId);

    /**
     * 从项目经历提取能力主张
     *
     * @param empId 员工ID
     * @return 提取的能力主张列表
     */
    List<PersonAbilityClaim> extractFromProject(Long empId);

    /**
     * 从学习成果提取能力主张
     *
     * @param empId 员工ID
     * @return 提取的能力主张列表
     */
    List<PersonAbilityClaim> extractFromLearning(Long empId);

    /**
     * 从人工录入提取能力主张
     *
     * @param empId 员工ID
     * @return 提取的能力主张列表
     */
    List<PersonAbilityClaim> extractFromManual(Long empId);

    /**
     * 提取所有来源的能力主张（不含AI面试）
     *
     * @param empId 员工ID
     * @return 所有来源的能力主张列表
     */
    List<PersonAbilityClaim> extractAll(Long empId);
}
