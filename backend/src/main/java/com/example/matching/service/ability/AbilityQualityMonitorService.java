package com.example.matching.service.ability;

import java.util.List;

/**
 * 能力质量监控服务
 * <p>
 * 基于RAG检索和数据分析，监控能力数据质量，识别问题数据，
 * 生成质量报告，实现能力数据的持续改进闭环。
 *
 * @author system
 */
public interface AbilityQualityMonitorService {

    /**
     * 质量问题类型
     */
    String ISSUE_ISOLATED = "ISOLATED";           // 孤立能力：无来源支撑
    String ISSUE_EXPIRED = "EXPIRED";             // 过期能力：超过1年未更新
    String ISSUE_INCONSISTENT = "INCONSISTENT";   // 不一致能力：多来源差异大
    String ISSUE_LOW_CREDIBILITY = "LOW_CREDIBILITY"; // 低可信度：来源可信度低
    String ISSUE_NO_EVIDENCE = "NO_EVIDENCE";     // 无证据：缺少RAG证据

    /**
     * 质量问题记录
     */
    record QualityIssue(
            String issueType,
            Long empId,
            String employeeName,
            Long tagId,
            String abilityName,
            String description,
            String severity,  // HIGH/MEDIUM/LOW
            String suggestion
    ) {}

    /**
     * 质量报告
     */
    record QualityReport(
            String reportId,
            int totalAbilities,
            int issueCount,
            int highSeverityCount,
            int mediumSeverityCount,
            int lowSeverityCount,
            List<QualityIssue> issues,
            String generatedTime
    ) {}

    /**
     * 扫描指定员工的能力数据质量
     *
     * @param empId 员工ID
     * @return 质量问题列表
     */
    List<QualityIssue> scanEmployeeAbilities(Long empId);

    /**
     * 扫描所有员工的能力数据质量
     *
     * @param limit 最大扫描员工数量
     * @return 质量报告
     */
    QualityReport scanAllAbilities(int limit);

    /**
     * 检测孤立能力（无来源支撑的能力记录）
     *
     * @param empId 员工ID
     * @return 孤立能力列表
     */
    List<QualityIssue> detectIsolatedAbilities(Long empId);

    /**
     * 检测过期能力（超过指定时间未更新）
     *
     * @param empId         员工ID
     * @param expiryMonths  过期月数
     * @return 过期能力列表
     */
    List<QualityIssue> detectExpiredAbilities(Long empId, int expiryMonths);

    /**
     * 检测不一致能力（多来源差异大）
     *
     * @param empId 员工ID
     * @return 不一致能力列表
     */
    List<QualityIssue> detectInconsistentAbilities(Long empId);

    /**
     * 基于RAG检索验证能力数据
     * <p>
     * 检索相关知识文档，验证能力记录是否有证据支撑
     *
     * @param empId 员工ID
     * @return 缺少证据的能力列表
     */
    List<QualityIssue> detectMissingEvidence(Long empId);

    /**
     * 获取质量改进建议
     *
     * @param issue 质量问题
     * @return 改进建议
     */
    String getImprovementSuggestion(QualityIssue issue);
}
