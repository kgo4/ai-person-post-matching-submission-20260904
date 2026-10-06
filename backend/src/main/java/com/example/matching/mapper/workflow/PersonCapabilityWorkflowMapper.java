package com.example.matching.mapper.workflow;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.matching.entity.workflow.PersonCapabilityWorkflow;
import org.apache.ibatis.annotations.Mapper;

/**
 * 人员能力评估工作流 Mapper 接口
 */
@Mapper
public interface PersonCapabilityWorkflowMapper extends BaseMapper<PersonCapabilityWorkflow> {
}
