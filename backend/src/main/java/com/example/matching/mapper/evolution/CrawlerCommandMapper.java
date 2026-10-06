package com.example.matching.mapper.evolution;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.matching.entity.evolution.CrawlerCommand;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 采集命令队列 Mapper
 *
 * @author system
 */
@Mapper
public interface CrawlerCommandMapper extends BaseMapper<CrawlerCommand> {

    /**
     * 原子领取一条待执行命令：仅当仍为 {@code PENDING} 时置为 {@code DISPATCHED}。
     * <p>
     * 用「条件更新 + 影响行数」避免多个 agent 抢到同一条命令——先更新，行数为 1 才算领取成功。
     * 返回 0 表示已被其它 agent 领走或状态已变，调用方应重新选取候选。
     *
     * @return 受影响行数（1 = 领取成功）
     */
    @Update("UPDATE crawler_command SET status = 'DISPATCHED', agent_id = #{agentId}, "
            + "dispatched_time = NOW() WHERE id = #{id} AND status = 'PENDING'")
    int claimIfPending(@Param("id") Long id, @Param("agentId") String agentId);
}
