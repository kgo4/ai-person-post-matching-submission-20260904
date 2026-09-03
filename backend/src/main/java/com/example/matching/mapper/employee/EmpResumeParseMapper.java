package com.example.matching.mapper.employee;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.matching.entity.employee.EmpResumeParse;
import org.apache.ibatis.annotations.Mapper;

/**
 * 简历解析记录Mapper
 */
@Mapper
public interface EmpResumeParseMapper extends BaseMapper<EmpResumeParse> {
}
