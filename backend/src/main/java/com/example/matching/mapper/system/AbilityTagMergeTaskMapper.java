package com.example.matching.mapper.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.matching.entity.system.AbilityTagMergeTask;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AbilityTagMergeTaskMapper extends BaseMapper<AbilityTagMergeTask> {
    @Select("SELECT * FROM ability_tag_merge_task WHERE status = 'PENDING' AND scheduled_time <= #{now} ORDER BY scheduled_time ASC LIMIT 20")
    List<AbilityTagMergeTask> selectDueTasks(@Param("now") LocalDateTime now);
    @Update("UPDATE ability_tag_merge_task SET status='RUNNING', started_time=NOW(), error_message=NULL WHERE id=#{id} AND status='PENDING'")
    int claimPendingTask(@Param("id") Long id);
    @Update("UPDATE ability_tag_merge_task SET status='COMPLETED', completed_time=NOW(), result_summary=#{summary} WHERE id=#{id} AND status='RUNNING'")
    int markCompleted(@Param("id") Long id, @Param("summary") String summary);
    @Update("UPDATE ability_tag_merge_task SET status='FAILED', completed_time=NOW(), error_message=#{error} WHERE id=#{id} AND status='RUNNING'")
    int markFailed(@Param("id") Long id, @Param("error") String error);
    @Update("UPDATE ability_tag_merge_task SET status='CANCELLED', completed_time=NOW() WHERE task_code=#{taskCode} AND status='PENDING'")
    int cancelPendingTask(@Param("taskCode") String taskCode);
    @Select("SELECT * FROM ability_tag_merge_task WHERE created_by=#{userId} AND status IN ('COMPLETED', 'FAILED') ORDER BY completed_time DESC LIMIT #{limit}")
    List<AbilityTagMergeTask> selectRecentTerminalTasks(@Param("userId") Long userId, @Param("limit") int limit);
}
