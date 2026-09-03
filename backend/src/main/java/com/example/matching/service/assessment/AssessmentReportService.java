package com.example.matching.service.assessment;

import com.example.matching.dto.interview.CompetencyReport;
import com.example.matching.port.assessment.AssessmentReportPort;

import java.util.List;

/**
 * 评估流程综合报告服务
 * <p>
 * 主体（简历+测试+面试）在面试结束异步分析时生成并落库 READY；
 * 聚合审核 / 等级确认完成后回填对应结论。
 */
public interface AssessmentReportService {

    /** 面试结束后生成报告主体并落库（RESUME_PARSE + AI_TEST + 面试观察/建议）。 */
    void generateAndPersist(Long workflowId, Long sessionId, CompetencyReport report);

    /** 聚合审核完成后回填聚合审核结论。 */
    void refreshAggregateConclusion(Long workflowId);

    /** 等级确认完成后回填等级确认结论。 */
    void refreshLevelConclusion(Long workflowId);

    /** 员工全部评估流程 + 报告状态（倒序）。 */
    List<AssessmentReportPort.WorkflowReportDTO> listByEmpId(Long empId);

    /** 单次评估报告（不存在返回 null）。 */
    AssessmentReportPort.ReportDTO getByWorkflowId(Long workflowId);
}
