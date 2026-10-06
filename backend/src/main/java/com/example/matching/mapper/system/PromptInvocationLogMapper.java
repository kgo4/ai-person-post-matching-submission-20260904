package com.example.matching.mapper.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.matching.entity.system.PromptInvocationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface PromptInvocationLogMapper extends BaseMapper<PromptInvocationLog> {

    /**
     * 按**小时**聚合的调用量与响应时间趋势（审计图表用）。
     *
     * <p>为什么在 SQL 里聚合而不是把行拉到内存：审计窗口内的调用量可能上万行，
     * 图表只需要几十个点；这也是本项目既有的做法（见 {@code ContestEvidenceItemMapper}）。
     *
     * <p>分组标签只取 `%H:00`，**不带日期** —— 窗口是"最近 N 小时"的滚动区间，
     * 标签写成 `09:00` 直接可读，也避免在界面上出现与窗口无关的日期。
     */
    @Select("SELECT DATE_FORMAT(created_time, '%H:00') AS hourBucket, "
            + "COUNT(*) AS callCount, "
            + "COALESCE(ROUND(AVG(latency_ms), 1), 0) AS avgLatencyMs, "
            + "COALESCE(MAX(latency_ms), 0) AS maxLatencyMs, "
            + "COALESCE(SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END), 0) AS failedCount, "
            + "COALESCE(SUM(CASE WHEN cache_hit = 1 THEN 1 ELSE 0 END), 0) AS cacheHitCount "
            + "FROM prompt_invocation_log "
            + "WHERE created_time >= #{since} "
            + "GROUP BY hourBucket ORDER BY hourBucket")
    List<Map<String, Object>> selectLatencyTrend(@Param("since") LocalDateTime since);

    /** 按 Prompt 名称聚合：调用量 / 平均耗时 / 失败数（取调用量前 12 名）。 */
    @Select("SELECT prompt_name AS promptName, "
            + "COUNT(*) AS callCount, "
            + "COALESCE(ROUND(AVG(latency_ms), 1), 0) AS avgLatencyMs, "
            + "COALESCE(MAX(latency_ms), 0) AS maxLatencyMs, "
            + "COALESCE(SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END), 0) AS failedCount "
            + "FROM prompt_invocation_log "
            + "WHERE created_time >= #{since} "
            + "GROUP BY prompt_name ORDER BY callCount DESC LIMIT 12")
    List<Map<String, Object>> selectPromptBreakdown(@Param("since") LocalDateTime since);

    /** 按场景聚合：调用量 / 平均耗时。 */
    @Select("SELECT COALESCE(NULLIF(scenario, ''), 'UNKNOWN') AS scenario, "
            + "COUNT(*) AS callCount, "
            + "COALESCE(ROUND(AVG(latency_ms), 1), 0) AS avgLatencyMs "
            + "FROM prompt_invocation_log "
            + "WHERE created_time >= #{since} "
            + "GROUP BY scenario ORDER BY callCount DESC LIMIT 12")
    List<Map<String, Object>> selectScenarioBreakdown(@Param("since") LocalDateTime since);
}