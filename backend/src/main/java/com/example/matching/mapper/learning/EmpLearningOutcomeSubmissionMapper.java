package com.example.matching.mapper.learning;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.matching.entity.learning.EmpLearningOutcomeSubmission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 员工学习成果提交单 Mapper（HR 匹配闭环 P4）
 */
@Mapper
public interface EmpLearningOutcomeSubmissionMapper extends BaseMapper<EmpLearningOutcomeSubmission> {
}
