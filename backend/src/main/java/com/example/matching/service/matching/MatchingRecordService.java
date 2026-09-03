package com.example.matching.service.matching;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.matching.dto.matching.MatchingExecuteDTO;
import com.example.matching.entity.matching.MatchingRecord;

import java.util.List;
import java.util.Map;

public interface MatchingRecordService extends IService<MatchingRecord> {

    /** 执行匹配 */
    List<MatchingRecord> executeMatching(MatchingExecuteDTO dto);

    /** 人工修改匹配结果 */
    void modifyResult(Long id, MatchingRecord record);

    /** 锁定匹配结果 */
    void lockResult(Long id);

    /** 解锁匹配结果 */
    void unlockResult(Long id);

    /** 分页查询匹配记录 */
    IPage<MatchingRecord> pageRecords(IPage<MatchingRecord> page, Long postId, Long empId, Integer matchStatus);

    /** 按创建人（发起者）过滤的分页查询：移动端 HR 归属隔离用 */
    IPage<MatchingRecord> pageRecordsByCreator(IPage<MatchingRecord> page, Long postId,
                                               Integer matchStatus, Long createdBy);

    /** 生成量化分析报告 */
    String generateReport(Long id);

    /** 生成AI语义增强分析报告 */
    String generateAiReport(Long id);

    /** 删除匹配记录（级联删除关联的审批流程和反馈数据） */
    void deleteRecord(Long id);

    /** 按批次号批量删除匹配记录（级联删除关联的审批流程和反馈数据），供删除任务时连带清理 */
    int deleteByBatchNo(String batchNo);

    /**
     * 获取匹配详情（含重新计算的瞬态字段）
     * <p>
     * 由于 evidenceScore、profileSemanticScore 等字段标记为 exist=false，
     * 不会持久化到数据库。本方法在读取记录后重新计算这些字段，确保前端展示完整数据。
     *
     * @param id 匹配记录ID
     * @return 填充了瞬态字段的匹配记录
     */
    MatchingRecord getDetailById(Long id);

    Map<String, Long> dashboardSummary();

    /**
     * 手动重试 AI 评分：将 FAILED/PENDING 记录重置为 PENDING（attempt=0、nextRetryAt=now），
     * 由 AI 评分恢复调度器自动重投。为 AI_SCORING_FAILED 提供唯一出口。
     *
     * @return true=已重置；false=状态不允许（如已完成/评分中/已锁定）
     */
    boolean retryAiScoring(Long id);
}
