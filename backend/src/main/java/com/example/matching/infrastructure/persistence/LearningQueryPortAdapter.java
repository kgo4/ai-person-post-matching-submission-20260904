package com.example.matching.infrastructure.persistence;

import com.example.matching.entity.learning.LearningMasteryLog;
import com.example.matching.entity.learning.LearningPathPlan;
import com.example.matching.entity.learning.LearningPathStep;
import com.example.matching.entity.learning.LearningProjectTask;
import com.example.matching.entity.learning.LearningResource;
import com.example.matching.mapper.learning.LearningMasteryLogMapper;
import com.example.matching.mapper.learning.LearningPathPlanMapper;
import com.example.matching.mapper.learning.LearningPathStepMapper;
import com.example.matching.mapper.learning.LearningProjectTaskMapper;
import com.example.matching.mapper.learning.LearningResourceMapper;
import com.example.matching.port.learning.LearningQueryPort;
import com.example.matching.port.learning.LearningQueryPort.LearningMasteryLogDTO;
import com.example.matching.port.learning.LearningQueryPort.LearningPathPlanDTO;
import com.example.matching.port.learning.LearningQueryPort.LearningPathStepDTO;
import com.example.matching.port.learning.LearningQueryPort.LearningProjectTaskDTO;
import com.example.matching.port.learning.LearningQueryPort.LearningResourceDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LearningQueryPortAdapter implements LearningQueryPort {

    private final LearningResourceMapper resourceMapper;
    private final LearningPathPlanMapper planMapper;
    private final LearningPathStepMapper stepMapper;
    private final LearningProjectTaskMapper taskMapper;
    private final LearningMasteryLogMapper masteryLogMapper;

    @Override
    public List<LearningResourceDTO> listResourcesByTagIds(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) return List.of();
        return resourceMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LearningResource>()
                        .in(LearningResource::getTagId, tagIds)
                        .eq(LearningResource::getStatus, 1)
        ).stream().map(LearningResourceDTO::from).collect(Collectors.toList());
    }

    @Override
    public List<LearningPathPlanDTO> listPlansByEmpId(Long empId) {
        if (empId == null) return List.of();
        return planMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LearningPathPlan>()
                        .eq(LearningPathPlan::getEmpId, empId)
        ).stream().map(LearningPathPlanDTO::from).collect(Collectors.toList());
    }

    @Override
    public List<LearningPathPlanDTO> listPlansByMatchingRecordId(Long matchingRecordId) {
        if (matchingRecordId == null) return List.of();
        return planMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LearningPathPlan>()
                        .eq(LearningPathPlan::getMatchingRecordId, matchingRecordId)
        ).stream().map(LearningPathPlanDTO::from).collect(Collectors.toList());
    }

    @Override
    public List<LearningPathStepDTO> listStepsByPlanId(Long planId) {
        if (planId == null) return List.of();
        return stepMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LearningPathStep>()
                        .eq(LearningPathStep::getPlanId, planId)
        ).stream().map(LearningPathStepDTO::from).collect(Collectors.toList());
    }

    @Override
    public List<LearningProjectTaskDTO> listProjectTasksByStepId(Long stepId) {
        if (stepId == null) return List.of();
        return taskMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LearningProjectTask>()
                        .eq(LearningProjectTask::getStepId, stepId)
        ).stream().map(LearningProjectTaskDTO::from).collect(Collectors.toList());
    }

    @Override
    public List<LearningMasteryLogDTO> listMasteryByEmpId(Long empId) {
        if (empId == null) return List.of();
        return masteryLogMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LearningMasteryLog>()
                        .eq(LearningMasteryLog::getEmpId, empId)
        ).stream().map(LearningMasteryLogDTO::from).collect(Collectors.toList());
    }

    @Override
    public List<LearningResourceDTO> listActiveResources(int limit) {
        var w = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LearningResource>()
                .eq(LearningResource::getStatus, 1);
        if (limit > 0) w.last("LIMIT " + limit);
        return resourceMapper.selectList(w).stream().map(LearningResourceDTO::from).collect(Collectors.toList());
    }

    @Override
    public List<LearningPathPlanDTO> listPlansPaginated(int page, int size) {
        var p = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<LearningPathPlan>(page, size);
        return planMapper.selectPage(p,
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LearningPathPlan>()
                        .eq(LearningPathPlan::getIsDeleted, 0)
                        .orderByAsc(LearningPathPlan::getId)
        ).getRecords().stream().map(LearningPathPlanDTO::from).collect(Collectors.toList());
    }

    @Override
    public List<LearningPathStepDTO> listStepsPaginated(int page, int size) {
        var p = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<LearningPathStep>(page, size);
        return stepMapper.selectPage(p,
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LearningPathStep>()
                        .eq(LearningPathStep::getIsDeleted, 0)
                        .orderByAsc(LearningPathStep::getId)
        ).getRecords().stream().map(LearningPathStepDTO::from).collect(Collectors.toList());
    }

    @Override
    public List<LearningProjectTaskDTO> listProjectTasksPaginated(int page, int size) {
        var p = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<LearningProjectTask>(page, size);
        return taskMapper.selectPage(p,
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LearningProjectTask>()
                        .eq(LearningProjectTask::getIsDeleted, 0)
                        .orderByAsc(LearningProjectTask::getId)
        ).getRecords().stream().map(LearningProjectTaskDTO::from).collect(Collectors.toList());
    }
}
