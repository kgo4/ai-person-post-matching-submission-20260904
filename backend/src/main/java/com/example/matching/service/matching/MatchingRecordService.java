package com.example.matching.service.matching;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.matching.common.enums.PublishStatusEnum;
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

    /** 员工本人只读 HR 已确认发布的匹配结果。 */
    IPage<MatchingRecord> pagePublishedRecords(IPage<MatchingRecord> page, Long empId);

    /**
     * 员工本人只读 HR 已确认发布的匹配结果（带岗位 / 匹配状态筛选）。
     * <p>员工侧不接受 empId 入参，人员范围一律由服务端按登录身份固定为本人，
     * 岗位与匹配状态仅收窄本人数据，不扩大范围。</p>
     */
    IPage<MatchingRecord> pagePublishedRecords(IPage<MatchingRecord> page, Long empId, Long postId, Integer matchStatus);

    /**
     * 员工「已通过岗位」列表（闭环设计 P3）。
     *
     * <p>口径与员工侧可见闸门完全一致：{@code approvalStatus=APPROVED} 且
     * {@code publishStatus=PUBLISHED}，并且 {@code matchStatus ∈ {1 强适配, 2 适配}}。
     * 同一岗位存在多条历史记录（重新匹配）时只保留最新一条。</p>
     *
     * <p>返回记录已补全 {@code empName / postName}。</p>
     *
     * @param empId 人员档案ID
     * @return 已通过岗位记录，按更新时间倒序；无数据返回空列表
     */
    List<MatchingRecord> listPassedPosts(Long empId);

    /**
     * 全部「已通过」记录（跨员工），供 P5 视频终面推荐池使用。
     *
     * <p>口径与 {@link #listPassedPosts(Long)} 相同（已推送 + 强适配/适配），
     * 按更新时间倒序，最多返回 {@code limit} 条。</p>
     *
     * @param limit 最大条数（服务层会做上下界保护）
     */
    List<MatchingRecord> listAllPassedRecords(int limit);

    /**
     * 最近的匹配记录（跨员工、**不限匹配状态与推送状态**），供 P5 视频终面选择邀约对象使用。
     *
     * <p>与 {@link #listAllPassedRecords(int)} 的区别是**不做任何结论过滤**：
     * 需求已明确「HR 可以直接选择匹配结果发起邀约，不必等匹配通过；
     * 匹配通过只是会向 HR 推荐」。因此这里返回全部未删除记录（含尚未评分/未推送的），
     * 由调用方用 {@code matchStatus} 自行标记哪些是「推荐」。</p>
     *
     * <p>按更新时间倒序，最多返回 {@code limit} 条；记录已补全 {@code empName / postName}。</p>
     *
     * @param limit 最大条数（服务层会做上下界保护）
     */
    List<MatchingRecord> listRecentRecords(int limit);

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

    /** 获取员工本人已发布的匹配详情。 */
    MatchingRecord getPublishedDetailById(Long id, Long empId);

    /**
     * 设置匹配结果的推送状态（HR 决定是否让员工本人看到）。
     * <p>推送与审批是两个独立动作：只有审批通过（{@code approvalStatus=2}）的记录才允许推送，
     * 已推送的记录可以撤回（回到未推送），撤回后员工侧立即不可见。</p>
     *
     * @param id            匹配记录ID
     * @param publishStatus 目标推送状态
     * @return true=状态已变更；false=记录不存在或已是目标状态
     */
    boolean updatePublishStatus(Long id, PublishStatusEnum publishStatus);

    Map<String, Long> dashboardSummary();

    /**
     * 手动重试 AI 评分：将 FAILED/PENDING 记录重置为 PENDING（attempt=0、nextRetryAt=now），
     * 由 AI 评分恢复调度器自动重投。为 AI_SCORING_FAILED 提供唯一出口。
     *
     * @return true=已重置；false=状态不允许（如已完成/评分中/已锁定）
     */
    boolean retryAiScoring(Long id);
}
