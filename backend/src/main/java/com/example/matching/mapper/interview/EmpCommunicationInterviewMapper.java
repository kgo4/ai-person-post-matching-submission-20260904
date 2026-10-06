package com.example.matching.mapper.interview;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.matching.entity.interview.EmpCommunicationInterview;
import org.apache.ibatis.annotations.Mapper;

/**
 * HR-员工视频终面记录 Mapper（HR 匹配闭环 P5）
 */
@Mapper
public interface EmpCommunicationInterviewMapper extends BaseMapper<EmpCommunicationInterview> {
}
