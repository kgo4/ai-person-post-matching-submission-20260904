package com.example.matching.mapper.learning;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.matching.entity.learning.LearningQuizRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 答题记录 Mapper
 */
@Mapper
public interface LearningQuizRecordMapper extends BaseMapper<LearningQuizRecord> {
}
