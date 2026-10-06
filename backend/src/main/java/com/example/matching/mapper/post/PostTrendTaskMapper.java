package com.example.matching.mapper.post;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.matching.entity.post.PostTrendTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 岗位趋势解析任务 Mapper。
 * <p>
 * 状态流转一律走条件更新（CAS）：只有满足前置状态的语句才会命中，
 * 以此保证「抢占执行」「置终态」「取消」三者并发时不会互相覆盖。
 */
@Mapper
public interface PostTrendTaskMapper extends BaseMapper<PostTrendTask> {

    /** 抢占待执行任务：仅 PENDING 可转 RUNNING，返回 1 表示抢占成功。 */
    @Update("UPDATE post_trend_task SET task_status = #{runningStatus}, error_message = NULL, "
            + "started_at = NOW(), progress_status = 'RETRIEVING', progress_percent = 5 "
            + "WHERE id = #{taskId} AND task_status = #{pendingStatus} AND is_deleted = 0")
    int claimPendingTask(@Param("taskId") Long taskId,
                         @Param("pendingStatus") String pendingStatus,
                         @Param("runningStatus") String runningStatus);

    /**
     * 把运行中的任务置为终态。
     * <p>
     * 必须带 {@code task_status = RUNNING} 条件：否则被取消（CANCELLED）的任务
     * 会在执行线程收尾时被重新写成 COMPLETED，取消操作形同虚设。
     */
    @Update("UPDATE post_trend_task SET task_status = #{terminalStatus}, progress_status = #{progressStatus}, "
            + "progress_percent = #{progressPercent}, finished_at = NOW() "
            + "WHERE id = #{taskId} AND task_status = #{runningStatus} AND is_deleted = 0")
    int finishRunningTask(@Param("taskId") Long taskId,
                          @Param("runningStatus") String runningStatus,
                          @Param("terminalStatus") String terminalStatus,
                          @Param("progressStatus") String progressStatus,
                          @Param("progressPercent") Integer progressPercent);

    /** 取消：PENDING 与 RUNNING 均可取消。 */
    @Update("UPDATE post_trend_task SET task_status = #{cancelledStatus}, progress_status = 'CANCELLED', "
            + "finished_at = NOW() "
            + "WHERE id = #{taskId} AND task_status IN ('PENDING', 'RUNNING') AND is_deleted = 0")
    int cancelActiveTask(@Param("taskId") Long taskId,
                         @Param("cancelledStatus") String cancelledStatus);

    /** 失败：仅 RUNNING 可转 FAILED，避免覆盖已取消状态。 */
    @Update("UPDATE post_trend_task SET task_status = #{failedStatus}, progress_status = 'FAILED', "
            + "error_message = #{errorMessage}, finished_at = NOW() "
            + "WHERE id = #{taskId} AND task_status = #{runningStatus} AND is_deleted = 0")
    int failRunningTask(@Param("taskId") Long taskId,
                        @Param("runningStatus") String runningStatus,
                        @Param("failedStatus") String failedStatus,
                        @Param("errorMessage") String errorMessage);

    /**
     * 审核结束后的状态收敛：从 WAIT_CONFIRM 转到应用态。
     * <p>
     * 只允许从 {@code WAIT_CONFIRM} 出发，避免「解析还没结束就有人点确认」或
     * 「任务已被取消」这两种情况下把状态写成 APPLIED。
     */
    @Update("UPDATE post_trend_task SET task_status = #{terminalStatus}, finished_at = NOW() "
            + "WHERE id = #{taskId} AND task_status = #{waitConfirmStatus} AND is_deleted = 0")
    int finishConfirmation(@Param("taskId") Long taskId,
                           @Param("waitConfirmStatus") String waitConfirmStatus,
                           @Param("terminalStatus") String terminalStatus);

    /** 更新执行进度（仅在 RUNNING 期间有效，避免给已终态任务写回进度）。 */
    @Update("UPDATE post_trend_task SET progress_status = #{progressStatus}, progress_percent = #{progressPercent} "
            + "WHERE id = #{taskId} AND task_status = #{runningStatus} AND is_deleted = 0")
    int updateProgress(@Param("taskId") Long taskId,
                       @Param("runningStatus") String runningStatus,
                       @Param("progressStatus") String progressStatus,
                       @Param("progressPercent") Integer progressPercent);

    /**
     * 查找僵尸任务：长时间停留在 RUNNING 且启动时间早于阈值的任务。
     * <p>
     * 进程重启或执行线程意外退出时任务会卡在 RUNNING，没有任何人会推进它；
     * 由定时扫描统一置为 FAILED，让用户能重新发起而不是永远等待。
     */
    @Select("SELECT * FROM post_trend_task WHERE task_status = #{runningStatus} AND is_deleted = 0 "
            + "AND started_at IS NOT NULL AND started_at < #{before} ORDER BY id LIMIT #{limit}")
    List<PostTrendTask> findZombieTasks(@Param("runningStatus") String runningStatus,
                                        @Param("before") LocalDateTime before,
                                        @Param("limit") int limit);
}
