package com.example.matching.dto.post;

/**
 * 岗位趋势解析统计与诊断。
 * <p>
 * 「无候选必须带原因」是本项目的既定约定（见趋势发现的词表换源修复）：
 * 前端拿到空结果时，必须能从这个对象里读到**可执行的中文说明**，而不是只能反复重传材料。
 */
public record PostTrendAnalysisSummary(
        /** 参与解析且已完成索引的知识文档数量 */
        int indexedDocumentCount,
        /** LLM 从材料中抽取出的岗位数量（未经过滤） */
        int extractedPostCount,
        /** 最终落库的候选数量 */
        int candidateCount,
        /** 新岗位候选数 */
        int newPostCount,
        /** 能力变更候选数 */
        int changeCount,
        /** 因材料强调度不足被丢弃的岗位数 */
        int filteredByEmphasisCount,
        /** 岗位相似度检索是否可用；false 表示分流结果不可信，候选一律按待复核处理 */
        boolean similarityServiceAvailable,
        /** 面向使用者的诊断说明；无候选时必填 */
        String diagnostics) {

    public static PostTrendAnalysisSummary empty(String diagnostics) {
        return new PostTrendAnalysisSummary(0, 0, 0, 0, 0, 0, false, diagnostics);
    }
}
