package com.example.matching.mapper.harness;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.matching.entity.harness.AiHarnessCheckLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiHarnessCheckLogMapper extends BaseMapper<AiHarnessCheckLog> {

    @Select("SELECT COUNT(1) FROM ability_harness_batch_item item "
            + "JOIN person_ability_level_decision decision "
            + "ON decision.claim_group_id = item.claim_group_id "
            + "WHERE item.harness_log_id = #{harnessLogId} "
            + "AND decision.decision_status = 'PENDING_MANUAL_REVIEW'")
    int countPendingLevelDecision(@Param("harnessLogId") Long harnessLogId);
}
