package com.example.matching.mapper.evolution;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.matching.entity.evolution.PostEvolutionTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 岗位演化任务 Mapper
 */
@Mapper
public interface PostEvolutionTaskMapper extends BaseMapper<PostEvolutionTask> {

    @Update("UPDATE post_evolution_task "
            + "SET task_status = #{runningStatus}, error_message = NULL "
            + "WHERE id = #{taskId} AND task_status = #{pendingStatus}")
    int claimPendingTask(@Param("taskId") Long taskId,
                         @Param("pendingStatus") String pendingStatus,
                         @Param("runningStatus") String runningStatus);
}
