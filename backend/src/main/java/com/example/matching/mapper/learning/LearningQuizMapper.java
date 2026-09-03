package com.example.matching.mapper.learning;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.matching.entity.learning.LearningQuiz;
import org.apache.ibatis.annotations.Mapper;

/**
 * 测验题目 Mapper
 */
@Mapper
public interface LearningQuizMapper extends BaseMapper<LearningQuiz> {
}
