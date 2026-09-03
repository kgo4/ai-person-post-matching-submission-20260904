package com.example.matching.infrastructure.persistence;

import com.example.matching.entity.evolution.PostEvolutionChangeItem;
import com.example.matching.entity.evolution.PostEvolutionTask;
import com.example.matching.mapper.evolution.PostEvolutionChangeItemMapper;
import com.example.matching.mapper.evolution.PostEvolutionTaskMapper;
import com.example.matching.port.evolution.EvolutionQueryPort;
import com.example.matching.port.evolution.EvolutionQueryPort.EvolutionChangeItemDTO;
import com.example.matching.port.evolution.EvolutionQueryPort.EvolutionTaskDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EvolutionQueryPortAdapter implements EvolutionQueryPort {

    private final PostEvolutionTaskMapper taskMapper;
    private final PostEvolutionChangeItemMapper changeItemMapper;

    @Override
    public List<EvolutionTaskDTO> listAllTasks(int limit) {
        var w = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PostEvolutionTask>();
        if (limit > 0) w.last("LIMIT " + limit);
        return taskMapper.selectList(w).stream().map(EvolutionTaskDTO::from).collect(Collectors.toList());
    }

    @Override
    public EvolutionTaskDTO getTaskById(Long taskId) {
        if (taskId == null) return null;
        PostEvolutionTask task = taskMapper.selectById(taskId);
        return task != null ? EvolutionTaskDTO.from(task) : null;
    }

    @Override
    public List<EvolutionChangeItemDTO> listApprovedChangeItems(Long taskId) {
        if (taskId == null) return List.of();
        return changeItemMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PostEvolutionChangeItem>()
                        .eq(PostEvolutionChangeItem::getTaskId, taskId)
                        .eq(PostEvolutionChangeItem::getConfirmStatus, "APPROVED")
        ).stream().map(EvolutionChangeItemDTO::from).collect(Collectors.toList());
    }
}
