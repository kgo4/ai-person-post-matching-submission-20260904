package com.example.matching.service.assessment;

import com.example.matching.dto.assessment.report.ComprehensiveAssessmentReportDetail;
import com.example.matching.dto.interview.CompetencyReport;
import com.example.matching.port.assessment.AssessmentReportPort;

import java.util.List;

/**
 * 评估流程综合报告服务
 * <p>
 * 主体（简历+测试+面试）在面试结束异步分析时生成并落库 READY；
 * 聚合审核 / 等级确认完成后回填对应结论；
 * 全部人工审核清空后生成 **AI 综合洞察**（只有文字，不打分、不改分）。
 *
 * @see <a href="file:../../../../../../../docs/comprehensive-assessment-report-design.md">设计文档</a>
 */
public interface AssessmentReportService {

    /** 面试结束后生成报告主体并落库（RESUME_PARSE + AI_TEST + 面试观察/建议）。 */
    void generateAndPersist(Long workflowId, Long sessionId, CompetencyReport report);

    /** 聚合审核完成后回填聚合审核结论。 */
    void refreshAggregateConclusion(Long workflowId);

    /** 等级确认完成后回填等级确认结论。 */
    void refreshLevelConclusion(Long workflowId);

    /**
     * 把报告的各「结论区块」一次性补齐（聚合审核结论 + 等级确认结论）。
     *
     * <p><b>【2026-09-04 修复】</b>此前 {@code refreshLevelConclusion} **没有任何生产调用点**
     * —— {@code refreshAggregateConclusion} 由聚合阶段运行器调用，而等级结论只在测试里被调过。
     * 于是 {@code assessment_report.level_summary_json} 永远是 null，
     * 报告里的「最终能力等级」区块整片空白。
     *
     * <p>调用时机：HR 把该次评估的待人工复核项**全部**审完的那一刻（{@code pending == 0}），
     * 以及任何需要重算报告数值的场合。两个子步骤都是单字段 upsert，可重复调用。
     */
    void refreshReportSections(Long workflowId);

    /** 员工全部评估流程 + 报告状态（倒序）。 */
    List<AssessmentReportPort.WorkflowReportDTO> listByEmpId(Long empId);

    /** 单次评估报告（不存在返回 null）。 */
    AssessmentReportPort.ReportDTO getByWorkflowId(Long workflowId);

    /**
     * 生成（或重算）AI 综合洞察并落库。
     *
     * <p><b>幂等</b>：输入事实指纹未变化且 {@code force=false} 时直接跳过，不重复调用模型。
     * <b>降级</b>：AI 不可用时落模板文字（{@code insight_source=TEMPLATE}），
     * 数值部分不受任何影响（本就来自既有链路）。
     *
     * @param force true 表示 HR 手动「重新生成报告」，忽略指纹强制重算
     * @return 是否实际写入（跳过 / 报告行不存在时为 false）
     */
    boolean generateInsights(Long workflowId, boolean force);

    /**
     * 报告的完整视图（四部分 + AI 洞察 + 来源权重）。
     *
     * <p>闸门：评估流程未完成、或仍有待人工审核的能力项时，返回
     * {@code available=false} + 原因说明（而不是抛错，也不是返回半成品数据）——
     * 这保证了「能力项只有 HR 审核全部完成后才对外可见」。
     *
     * @return 报告视图；工作流不存在时返回 null
     */
    ComprehensiveAssessmentReportDetail getReportDetail(Long workflowId);
}
