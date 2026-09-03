package com.example.matching.service.contest.report;

import com.example.matching.entity.contest.ContestReportTask;

import java.util.List;
import java.util.Map;

/**
 * 竞赛报告服务接口
 *
 * @author system
 */
public interface ContestReportService {

    /**
     * 生成报告
     *
     * @param reportType 报告类型
     * @param title      报告标题
     * @param createdBy  创建人
     * @return 报告任务
     */
    ContestReportTask generateReport(String reportType, String title, Long createdBy);

    /**
     * 重试失败的报告任务
     *
     * @param id        原任务ID
     * @param createdBy 重试人
     * @return 新的报告任务
     */
    ContestReportTask retryReport(Long id, Long createdBy);

    /**
     * 查询报告任务列表
     *
     * @param reportType 报告类型
     * @param page       页码
     * @param size       每页数量
     * @return 分页结果
     */
    Map<String, Object> getReportTaskPage(String reportType, Integer page, Integer size);

    /**
     * 获取报告详情
     *
     * @param id 任务ID
     * @return 报告任务
     */
    ContestReportTask getReportTaskById(Long id);

    /**
     * 获取报告关联的证据列表
     *
     * @param taskId 任务ID
     * @return 证据列表
     */
    List<Map<String, Object>> getReportEvidenceRefs(Long taskId);

    /**
     * 导出报告内容
     *
     * @param id     任务ID
     * @param format 格式：md/json
     * @return 导出内容
     */
    String exportReport(Long id, String format);

    /**
     * 获取提交清单
     *
     * @return 清单数据
     */
    Map<String, Object> getSubmissionChecklist();
}
