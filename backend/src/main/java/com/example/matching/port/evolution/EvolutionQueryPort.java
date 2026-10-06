package com.example.matching.port.evolution;

import java.util.List;

/**
 * 岗位演化域查询端口 — 公开只读接口。
 */
public interface EvolutionQueryPort {

    record EvolutionTaskDTO(
            Long id,
            String taskCode,
            String taskName,
            Long postId,
            String taskStatus,
            String businessDomain
    ) {
        public static EvolutionTaskDTO from(com.example.matching.entity.evolution.PostEvolutionTask t) {
            return new EvolutionTaskDTO(t.getId(), t.getTaskCode(), t.getTaskName(),
                    t.getPostId(), t.getTaskStatus(), t.getBusinessDomain());
        }
    }

    List<EvolutionTaskDTO> listAllTasks(int limit);

    /** 按 ID 查询演化任务，未找到返回 null */
    EvolutionTaskDTO getTaskById(Long taskId);

    /** 演化变更项 DTO */
    record EvolutionChangeItemDTO(
            Long id,
            Long taskId,
            Long tagId
    ) {
        public static EvolutionChangeItemDTO from(com.example.matching.entity.evolution.PostEvolutionChangeItem i) {
            return new EvolutionChangeItemDTO(i.getId(), i.getTaskId(), i.getTagId());
        }
    }

    /** 列出任务下已确认（APPROVED）的演化变更项 */
    List<EvolutionChangeItemDTO> listApprovedChangeItems(Long taskId);
}
