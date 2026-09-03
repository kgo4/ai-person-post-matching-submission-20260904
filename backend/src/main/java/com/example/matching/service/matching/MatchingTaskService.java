package com.example.matching.service.matching;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.matching.dto.matching.MatchingExecuteDTO;
import com.example.matching.entity.matching.MatchingTask;

/**
 * 匹配任务服务接口
 */
public interface MatchingTaskService extends IService<MatchingTask> {

    /** 消费端最大重试次数（超过后置 FAILED 终态） */
    int MAX_CONSUME_RETRIES = 3;

    /**
     * 提交异步匹配任务
     * @param dto 匹配执行参数
     * @return 任务ID
     */
    String submitTask(MatchingExecuteDTO dto);

    /**
     * 查询任务状态
     * @param taskId 任务ID
     * @return 任务信息
     */
    MatchingTask getTaskStatus(String taskId);

    boolean claimTask(String taskId);

    /**
     * 更新任务进度
     * @param taskId 任务ID
     * @param processedCount 已处理数
     * @param totalCount 总数
     */
    void updateProgress(String taskId, int processedCount, int totalCount);

    /**
     * 完成任务
     * @param taskId 任务ID
     * @param resultMessage 结果消息
     * @return true 表示任务确实由 RUNNING 置为 COMPLETED（未被取消/其他终态覆盖）
     */
    boolean completeTask(String taskId, String resultMessage);

    /**
     * 取消任务：仅 PENDING/RUNNING 可取消，置为 CANCELLED（终态）。
     * 已取消任务不会被消费、不会被 completeTask/failTask 覆盖。
     * @return true 表示取消成功（状态被更新）
     */
    boolean cancelTask(String taskId);

    /**
     * 分页查询任务列表
     * @param current 页码（从 1 开始）
     * @param size 每页条数
     * @param status 状态过滤（可空：PENDING=0/RUNNING=1/COMPLETED=2/FAILED=3/CANCELLED=4）
     */
    com.baomidou.mybatisplus.core.metadata.IPage<MatchingTask> pageTasks(long current, long size, Integer status);

    /**
     * 删除任务（连带删除该任务的匹配记录与子表数据）：
     * 进行中任务先置 CANCELLED 再删；删除 outbox/rematch_validation → 按 batch_no 删匹配记录 → 物理删任务。
     * @return true 表示任务被删除
     */
    boolean deleteTask(String taskId);
    /**
     * 任务失败
     * @param taskId 任务ID
     * @param errorMessage 错误信息
     */
    void failTask(String taskId, String errorMessage);

    /**
     * 消费失败重试：未达上限时把任务从 RUNNING 置回 PENDING 并递增 retry_count
     * （调用方负责重新入 outbox 驱动重投），达到上限则置 FAILED 终态。
     *
     * @return true=已调度重试；false=重试耗尽已置 FAILED
     */
    boolean retryTask(String taskId, String errorMessage);

    /**
     * 僵尸恢复：将 RUNNING 且超过 {@code olderThan} 无任何状态变更（updatedTime 未刷新）的任务
     * CAS 置为 FAILED。返回本次恢复的任务数。
     */
    int recoverZombieTasks(java.time.Duration olderThan);

    /**
     * 心跳：刷新 RUNNING 任务的 updatedTime，防止长任务被僵尸扫描器误判。
     */
    void touchTask(String taskId);

    boolean markDispatchFailed(String taskId, String errorMessage);

    boolean requeueAfterDispatchFailure(String taskId);
}