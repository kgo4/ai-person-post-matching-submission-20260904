package com.example.matching.mapper.workflow;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.matching.entity.workflow.AiTestCoverage;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 测试验证覆盖关系 Mapper 接口
 */
@Mapper
public interface AiTestCoverageMapper extends BaseMapper<AiTestCoverage> {
}
