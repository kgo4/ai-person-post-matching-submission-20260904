package com.example.matching.service.governance;

/**
 * AI 治理 - 人工采纳后业务应用服务
 * <p>
 * 职责：将人工采纳的 Harness 记录转换为业务数据写入。
 * 只处理 ACCEPTED 状态的记录，根据 claimType 决定写入目标。
 *
 * @author system
 */
public interface AiGovernanceApplyService {

    /**
     * 将 Harness 记录应用到业务数据
     * <p>
     * 注意：此方法只更新治理状态，不真正写入业务表。
     * 业务写入由 AgentBusinessApplyService 在 Agent 输出时完成。
     *
     * @param harnessLogId Harness 日志 ID
     * @param reviewComment 人工处理说明
     * @return 是否成功应用
     * @deprecated 使用 acceptReview 替代
     */
    @Deprecated
    boolean applyToBusiness(Long harnessLogId, String reviewComment);

    /**
     * 接受审核（人工采纳）
     * <p>
     * 表示人工采纳了 Harness 的 REVIEW 结论。
     * 不声称已经写入业务表，只更新治理状态。
     *
     * @param harnessLogId Harness 日志 ID
     * @param reviewComment 人工处理说明
     * @return 是否成功接受
     */
    boolean acceptReview(Long harnessLogId, String reviewComment);

    boolean rejectReview(Long harnessLogId, String reviewComment);

    /**
     * 按 ID 查询 Harness 检查日志（供应用层判断决策类型/是否聚合记录）。
     * <p>
     * 收敛到本域：避免应用层直接依赖 harness Mapper（架构规则：跨域 Mapper 必须走服务/Port）。
     */
    com.example.matching.entity.harness.AiHarnessCheckLog getCheckLog(Long harnessLogId);
}
