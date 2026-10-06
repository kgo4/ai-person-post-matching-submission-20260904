package com.example.matching.mapper.ability;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.matching.entity.ability.AgentMemoryHitLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;

import java.util.List;

/**
 * Agent 记忆命中日志 Mapper
 *
 * @author system
 */
@Mapper
public interface AgentMemoryHitLogMapper extends BaseMapper<AgentMemoryHitLog> {

    @Insert("""
            <script>
            INSERT IGNORE INTO agent_memory_hit_log_archive
                (id, memory_id, agent_name, source_type, source_ref_id, hit_text,
                 hit_context_json, outcome, hit_time, is_deleted)
            SELECT id, memory_id, agent_name, source_type, source_ref_id, hit_text,
                   hit_context_json, outcome, hit_time, is_deleted
            FROM agent_memory_hit_log
            WHERE id IN
            <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>
            </script>
            """)
    int archiveByIds(@Param("ids") List<Long> ids);

    @Select("""
            <script>
            SELECT COUNT(*) FROM agent_memory_hit_log_archive
            WHERE id IN
            <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>
            </script>
            """)
    long countArchivedByIds(@Param("ids") List<Long> ids);

    @Delete("""
            <script>
            DELETE FROM agent_memory_hit_log
            WHERE id IN
            <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>
            </script>
            """)
    int physicalDeleteByIds(@Param("ids") List<Long> ids);
}
