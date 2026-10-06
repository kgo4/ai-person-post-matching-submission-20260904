package com.example.matching.service.assessment;

import com.example.matching.entity.workflow.PersonCapabilityStageRun;
import com.example.matching.entity.workflow.PersonCapabilityWorkflow;

import java.util.List;

/**
 * 人员能力评估工作流服务接口
 * <p>
 * 负责流程状态机、阶段依赖、幂等、重试、状态查询。
 *
 * @author system
 */
public interface CapabilityAssessmentWorkflowService {

    /**
     * 获取或创建员工的活跃工作流（同一时间只允许一个活跃流程）。
     */
    PersonCapabilityWorkflow getOrCreateActiveWorkflow(Long empId, Long operatorId);

    /**
     * 获取员工活跃工作流，不存在返回 null。
     */
    PersonCapabilityWorkflow getActiveWorkflow(Long empId);

    /**
     * 获取员工最近一次已完成（COMPLETED）的工作流 ID，不存在返回 null。
     *
     * <p>用于员工侧评估结果的可见性判定：能力评估流程走完并完成人工审核后员工才能
     * 查看自己的评估结果、能力信息与面试报告。此处提供该判定的唯一数据来源，
     * 避免各处重复拼查询。</p>
     */
    Long getLatestCompletedWorkflowId(Long empId);

    /**
     * 该员工最近一次能力评估流程（不限状态，CANCELLED 除外）；从未发起过返回 null。
     *
     * <p>用于区分员工侧的「从未评估」「评估进行中」「等待 HR 审核」三种处境 ——
     * 它们的界面文案完全不同，而 {@link #getLatestCompletedWorkflowId(Long)} 只认 COMPLETED，
     * 对前两种都返回 null、无法区分。历史上正是因此把「从未评估」误报成了
     * 「你的评估已提交，HR 正在审核」，凭空捏造了一个不存在的审核进度。</p>
     *
     * <p>CANCELLED 排除在外：取消掉的流程没有任何结果可等，员工视角等同于没做过，
     * 应引导他重新发起，而不是让他等一个不存在的审核。</p>
     */
    PersonCapabilityWorkflow getLatestWorkflow(Long empId);

    /**
     * 按 ID 获取工作流，不存在抛异常。
     */
    PersonCapabilityWorkflow getWorkflow(Long workflowId);

    /**
     * 绑定目标岗位到工作流（单一真相源）。测试选岗时写入；是否允许更换由调用方校验。
     */
    void bindPost(Long workflowId, Long postId);

    /**
     * CAS 推进工作流状态：仅当当前状态等于 expect 时才更新为 target。
     *
     * @return true 表示推进成功
     */
    boolean transition(Long workflowId, String expectStatus, String targetStatus, String currentStage);

    /**
     * 幂等创建阶段运行（workflowId + stageType + inputHash 唯一）。
     * 已存在时返回既有记录。
     */
    PersonCapabilityStageRun createStageRun(Long workflowId, String stageType, String inputHash,
                                            String inputSnapshotJson, String sourceRefType, Long sourceRefId);

    /**
     * 查询工作流下某阶段最近一次阶段运行。
     */
    PersonCapabilityStageRun getLatestStageRun(Long workflowId, String stageType);

    /**
     * 按 ID 查询阶段运行。
     */
    PersonCapabilityStageRun getStageRun(Long stageRunId);

    /**
     * 查询工作流的所有阶段运行（按创建时间升序）。
     */
    List<PersonCapabilityStageRun> listStageRuns(Long workflowId);

    /**
     * 抢占阶段运行：PENDING -> RUNNING，仅一次成功。
     */
    boolean claimStageRun(Long stageRunId);

    /**
     * 阶段运行成功：记录输出并 CAS 推进。
     */
    void markStageSucceeded(Long stageRunId, String outputSnapshotJson);

    /**
     * 阶段运行失败：可重试失败或最终失败。
     */
    void markStageFailed(Long stageRunId, String failureCode, String failureMessage, boolean finalFailure);

    /**
     * 工作流整体失败。
     */
    void failWorkflow(Long workflowId, String reason);

    /**
     * 工作流完成。
     */
    void completeWorkflow(Long workflowId);

    /**
     * 从失败阶段恢复：重新创建该阶段的 StageRun 并投递任务。
     */
    void retryStage(Long workflowId, String stageType, Long operatorId);

    /**
     * 校验前置阶段是否已完成。
     */
    void assertStagePrerequisite(Long workflowId, String stageType);

    /**
     * 推进工作流到下一阶段并创建 StageRun（内部做前置校验）。
     */
    PersonCapabilityStageRun startNextStage(Long workflowId, String stageType, String inputHash,
                                            String inputSnapshotJson, Long operatorId);

    // ==================== 仅 CapabilityAssessmentLifecycleCoordinator 调用的底层 CAS 方法 ====================

    /**
     * 按 workflowId + stageType（+ sourceRef）解析最近一条活跃阶段运行（供协调器解析事件目标）。
     */
    PersonCapabilityStageRun resolveActiveStageRun(Long workflowId, String stageType,
                                                   String sourceRefType, Long sourceRefId);

    /**
     * 阶段运行状态 CAS 推进：仅当前状态等于 expectStatus 时更新为 targetStatus。
     *
     * @return true 表示推进成功
     */
    boolean casStageRunStatus(Long stageRunId, String expectStatus, String targetStatus,
                              String failureCode, String failureMessage);

    /**
     * 工作流活跃阶段运行同步（供协调器更新 activeStageRunId）。
     */
    void syncActiveStageRun(Long workflowId, Long stageRunId);

    /**
     * 工作流最终失败（CAS：非终态才允许进入 FAILED），写入 failedReason。
     */
    void markWorkflowFinalFailed(Long workflowId, String failedReason);

    /**
     * 幂等写入生命周期事件审计日志（eventId 冲突时返回 false，表示重复）。
     */
    boolean recordLifecycleEventLog(com.example.matching.entity.workflow.CapabilityStageLifecycleEventLog logRecord);

    /**
     * Atomically claims an event ID before lifecycle side effects are applied.
     * The unique event_id index is the cross-node idempotency boundary.
     */
    boolean claimLifecycleEventLog(com.example.matching.entity.workflow.CapabilityStageLifecycleEventLog logRecord);

    /** Updates the log row that was previously claimed for the event. */
    boolean completeLifecycleEventLog(com.example.matching.entity.workflow.CapabilityStageLifecycleEventLog logRecord);

    /**
     * 判断生命周期事件是否已处理（eventId 幂等键）。
     */
    boolean existsLifecycleEvent(String eventId);

    /**
     * 判断指定阶段运行是否已记录过该事件类型（发布端幂等查重，防止补偿重放重复发布）。
     */
    boolean hasRecordedLifecycleEvent(Long stageRunId, String eventType);
}
