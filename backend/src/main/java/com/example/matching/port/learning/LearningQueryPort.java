package com.example.matching.port.learning;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 学习域查询端口 — 公开只读接口。
 * <p>
 * 其他域只能通过此接口查询学习数据，禁止直接注入 learning 包的 Mapper。
 */
public interface LearningQueryPort {

    /** 学习资源 DTO */
    record LearningResourceDTO(
            Long id,
            String resourceCode,
            String abilityName,
            Long tagId,
            String title,
            String resourceType,
            Integer difficultyLevel,
            String url,
            String description,
            String platform,
            Integer status
    ) {
        public static LearningResourceDTO from(com.example.matching.entity.learning.LearningResource r) {
            return new LearningResourceDTO(r.getId(), r.getResourceCode(), r.getAbilityName(),
                    r.getTagId(), r.getTitle(), r.getResourceType(), r.getDifficultyLevel(),
                    r.getUrl(), r.getDescription(), r.getPlatform(), r.getStatus());
        }
    }

    /** 学习计划 DTO */
    record LearningPathPlanDTO(
            Long id,
            Long empId,
            Long postId,
            Long matchingRecordId,
            String planTitle,
            String planStatus,
            BigDecimal currentScore,
            BigDecimal targetScore
    ) {
        public static LearningPathPlanDTO from(com.example.matching.entity.learning.LearningPathPlan p) {
            return new LearningPathPlanDTO(p.getId(), p.getEmpId(), p.getPostId(),
                    p.getMatchingRecordId(), p.getPlanTitle(), p.getPlanStatus(),
                    p.getCurrentScore(), p.getTargetScore());
        }
    }

    /** 学习步骤 DTO */
    record LearningPathStepDTO(
            Long id,
            Long planId,
            Long abilityTagId,
            String abilityName,
            Integer currentLevel,
            Integer targetLevel,
            String gapType,
            String priority,
            String stepTitle,
            String status
    ) {
        public static LearningPathStepDTO from(com.example.matching.entity.learning.LearningPathStep s) {
            return new LearningPathStepDTO(s.getId(), s.getPlanId(), s.getAbilityTagId(),
                    s.getAbilityName(), s.getCurrentLevel(), s.getTargetLevel(),
                    s.getGapType(), s.getPriority(), s.getStepTitle(), s.getStatus());
        }
    }

    /** 学习项目任务 DTO */
    record LearningProjectTaskDTO(
            Long id,
            Long planId,
            Long stepId,
            Long abilityTagId,
            String taskTitle,
            String difficultyLevel,
            String status
    ) {
        public static LearningProjectTaskDTO from(com.example.matching.entity.learning.LearningProjectTask t) {
            return new LearningProjectTaskDTO(t.getId(), t.getPlanId(), t.getStepId(),
                    t.getAbilityTagId(), t.getTaskTitle(), t.getDifficultyLevel(), t.getStatus());
        }
    }

    /** 掌握度日志 DTO */
    record LearningMasteryLogDTO(
            Long id,
            Long empId,
            Long tagId,
            BigDecimal masteryScore,
            Integer masteredCount,
            String calculationSource
    ) {
        public static LearningMasteryLogDTO from(com.example.matching.entity.learning.LearningMasteryLog m) {
            return new LearningMasteryLogDTO(m.getId(), m.getEmpId(), m.getTagId(),
                    m.getMasteryScore(), m.getMasteredCount(), m.getCalculationSource());
        }
    }

    // --- 查询方法 ---

    List<LearningResourceDTO> listResourcesByTagIds(List<Long> tagIds);

    List<LearningPathPlanDTO> listPlansByEmpId(Long empId);

    List<LearningPathPlanDTO> listPlansByMatchingRecordId(Long matchingRecordId);

    List<LearningPathStepDTO> listStepsByPlanId(Long planId);

    List<LearningProjectTaskDTO> listProjectTasksByStepId(Long stepId);

    List<LearningMasteryLogDTO> listMasteryByEmpId(Long empId);

    /** 分页列出活跃的学习资源（用于批量回填） */
    List<LearningResourceDTO> listActiveResources(int limit);

    /** 分页列出学习计划 */
    List<LearningPathPlanDTO> listPlansPaginated(int page, int size);

    /** 分页列出学习步骤 */
    List<LearningPathStepDTO> listStepsPaginated(int page, int size);

    /** 分页列出学习项目任务 */
    List<LearningProjectTaskDTO> listProjectTasksPaginated(int page, int size);
}
