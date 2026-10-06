package com.example.matching.mapper.rag;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.matching.entity.rag.RagQueryLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * RAG查询日志 Mapper
 */
@Mapper
public interface RagQueryLogMapper extends BaseMapper<RagQueryLog> {

    /**
     * 按小时聚合的检索延迟趋势（审计图表用）。
     *
     * <p>分组标签只取 `%H:00`（不带日期），与 Prompt 侧的写法一致。
     */
    @Select("SELECT DATE_FORMAT(created_time, '%H:00') AS hourBucket, "
            + "COUNT(*) AS queryCount, "
            + "COALESCE(ROUND(AVG(latency_ms), 1), 0) AS avgLatencyMs, "
            + "COALESCE(MAX(latency_ms), 0) AS maxLatencyMs, "
            + "COALESCE(ROUND(AVG(hit_count), 1), 0) AS avgHitCount, "
            + "COALESCE(SUM(CASE WHEN is_degraded = 1 THEN 1 ELSE 0 END), 0) AS degradedCount "
            + "FROM rag_query_log "
            + "WHERE created_time >= #{since} "
            + "GROUP BY hourBucket ORDER BY hourBucket")
    List<Map<String, Object>> selectLatencyTrend(@Param("since") LocalDateTime since);

    /** 按场景聚合的检索量 / 平均延迟 / 平均命中数。 */
    @Select("SELECT COALESCE(NULLIF(scenario, ''), 'UNKNOWN') AS scenario, "
            + "COUNT(*) AS queryCount, "
            + "COALESCE(ROUND(AVG(latency_ms), 1), 0) AS avgLatencyMs, "
            + "COALESCE(ROUND(AVG(hit_count), 1), 0) AS avgHitCount "
            + "FROM rag_query_log "
            + "WHERE created_time >= #{since} "
            + "GROUP BY scenario ORDER BY queryCount DESC LIMIT 12")
    List<Map<String, Object>> selectScenarioBreakdown(@Param("since") LocalDateTime since);
}
