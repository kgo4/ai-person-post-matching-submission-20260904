package com.example.matching.entity.interview;

/**
 * 追问类型枚举
 * <p>
 * 第一版只做三类追问，够用且稳定。
 */
public enum FollowUpType {

    /**
     * STAR 维度缺失
     * 缺少背景、任务、行动、结果中的关键项
     * 适合行为面试题、项目经历题
     */
    STAR_MISSING,

    /**
     * 个人贡献不清
     * 学生频繁说"我们""团队"，无法判断个人真实贡献
     */
    PERSONAL_CONTRIBUTION,

    /**
     * 简历声明验证
     * 简历中声明某能力或项目，回答没有展开关键细节
     */
    RESUME_VERIFICATION,

    /**
     * 情景模拟追问：抛出一个与岗位相关的业务场景，让候选人现场分析解决，
     * 用于探测候选人的深层分析能力和临场应变能力。
     */
    SCENARIO_SIMULATION
}
