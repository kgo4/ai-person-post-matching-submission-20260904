package com.example.matching.service.post;

import com.example.matching.dto.post.PostTrendAnalysisSummary;

import java.util.List;

/**
 * 岗位趋势解析：一次材料 → 一组候选（新岗位 / 既有岗位能力变更）。
 * <p>
 * 只负责「读材料 → LLM 抽取 → 标签归位 → 相似度分流 → 治理判定 → 落候选」，
 * 不负责落地（建岗位 / 改能力），落地由确认接口在同一事务内完成。
 * <p>
 * <b>任何岗位都不会被自动创建</b>：本服务产出的全部是 {@code confirm_status=PENDING} 的候选。
 */
public interface PostTrendAnalysisService {

    /**
     * 执行一次趋势解析。
     * <p>
     * AI 能力未启用、材料无法索引等**配置/环境问题**直接抛异常，由任务监听器置任务为 FAILED，
     * 便于使用者看到确切原因；LLM 正常但没抽出岗位属于**正常空结果**，返回带诊断的 summary。
     *
     * @param taskId            趋势任务ID，用于回填候选、进度与诊断
     * @param sourceDocumentIds 参与解析的知识源文档ID
     * @param sourceCategories  材料类别（与文档自身类别取并集，仅用于诊断展示）
     */
    PostTrendAnalysisSummary analyze(Long taskId, List<Long> sourceDocumentIds, List<String> sourceCategories);
}
