package com.example.matching.mapper.matching;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.matching.entity.matching.MatchingTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 匹配任务 Mapper
 */
@Mapper
public interface MatchingTaskMapper extends BaseMapper<MatchingTask> {
}
