package com.example.matching.mapper.learning;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.matching.entity.learning.LearningProgressLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 学习进度日志 Mapper
 */
@Mapper
public interface LearningProgressLogMapper extends BaseMapper<LearningProgressLog> {
}
