package com.example.matching.entity.contest;

import lombok.Getter;

/**
 * 竞赛报告类型枚举
 * <p>
 * 统一管理报告类型元数据，前端通过 /types 接口动态获取，
 * 不再手写两套类型枚举。
 */
@Getter
public enum ContestReportTypeEnum {

    SUMMARY("SUMMARY", "摘要报告", "项目概述报告",
            "包含项目核心功能、系统规模和关键指标概览",
            "适合快速了解项目全貌", false, false, true),

    GRAPH("GRAPH", "图谱报告", "知识图谱报告",
            "包含知识图谱节点分布、边类型统计和图谱规模",
            "适合展示知识图谱建设成果", false, false, true),

    EVIDENCE("EVIDENCE", "证据报告", "证据中心报告",
            "包含证据来源分布、审核状态和可信度统计",
            "适合展示证据链完整性", false, false, true),

    SUBMISSION_CHECKLIST("SUBMISSION_CHECKLIST", "提交清单", "竞赛提交清单",
            "包含所有竞赛提交材料的检查项和状态",
            "适合提交前逐项核对", false, false, false),

    MATCHING_OVERVIEW("MATCHING_OVERVIEW", "匹配全景分析", "人岗匹配全景分析报告",
            "基于全部匹配记录，AI 分析整体匹配度、岗位缺口、能力缺失及改进建议",
            "适合管理层汇报和人才战略决策", true, true, true);

    /** 类型标识 */
    private final String type;

    /** 显示标签 */
    private final String label;

    /** 默认标题 */
    private final String title;

    /** 描述 */
    private final String description;

    /** 适用范围 */
    private final String scope;

    /** 是否需要 AI */
    private final boolean needsAi;

    /** 是否需要 RAG */
    private final boolean needsRag;

    /** 是否可导出 */
    private final boolean exportable;

    ContestReportTypeEnum(String type, String label, String title, String description,
                          String scope, boolean needsAi, boolean needsRag, boolean exportable) {
        this.type = type;
        this.label = label;
        this.title = title;
        this.description = description;
        this.scope = scope;
        this.needsAi = needsAi;
        this.needsRag = needsRag;
        this.exportable = exportable;
    }

    /**
     * 根据类型标识查找枚举
     */
    public static ContestReportTypeEnum findByType(String type) {
        if (type == null) return null;
        for (ContestReportTypeEnum e : values()) {
            if (e.type.equals(type)) return e;
        }
        return null;
    }
}
