package com.example.matching.service.matching;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.matching.dto.matching.MatchingFeedbackExportDTO;
import com.example.matching.entity.matching.MatchingFeedbackDataset;

import java.util.List;
import java.util.Map;

public interface MatchingFeedbackDatasetService extends IService<MatchingFeedbackDataset> {

    /** 提交人工反馈 */
    void submitFeedback(MatchingFeedbackDataset feedback);

    /** 分页查询 */
    IPage<MatchingFeedbackDataset> pageFeedback(IPage<MatchingFeedbackDataset> page, Integer exportEnabled);

    /**
     * 获取反馈统计摘要（用于Prompt优化）
     * 返回最近N条反馈的采纳情况和偏差分析
     */
    Map<String, Object> getFeedbackSummary(int limit);

    /**
     * 获取最近的反馈样本（用于few-shot Prompt）
     * 返回格式化的反馈文本列表
     */
    List<String> getRecentFeedbackExamples(int limit);

    /**
     * 导出反馈数据
     * @param exportEnabled 导出授权筛选条件
     * @return 导出数据列表
     */
    List<MatchingFeedbackExportDTO> exportFeedback(Integer exportEnabled);

    /**
     * 批量更新导出授权
     * @param ids 反馈记录ID列表
     * @param exportEnabled 导出授权：0-否，1-是
     */
    void batchUpdateExportStatus(List<Long> ids, Integer exportEnabled);

    /**
     * 获取反馈趋势统计
     * @param days 统计最近N天
     * @return 趋势数据
     */
    Map<String, Object> getFeedbackTrend(int days);

    /**
     * 获取偏差分布统计
     * @param limit 统计最近N条反馈
     * @return 偏差分布数据
     */
    Map<String, Object> getDeviationDistribution(int limit);

    /**
     * 获取校准回放摘要。
     * 对最近反馈样本比较：
     * 1. 当前最终分与人工最终分的偏差
     * 2. 去掉岗位级校准项后的偏差
     */
    Map<String, Object> getCalibrationReplaySummary(int limit);
}
