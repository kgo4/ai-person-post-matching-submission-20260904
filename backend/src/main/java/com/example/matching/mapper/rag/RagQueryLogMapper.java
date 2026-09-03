package com.example.matching.mapper.rag;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.matching.entity.rag.RagQueryLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * RAG查询日志 Mapper
 */
@Mapper
public interface RagQueryLogMapper extends BaseMapper<RagQueryLog> {
}
