package com.example.matching.dto.kg.context;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.util.List;

/**
 * 学习路径前置条件上下文（紧凑 DTO）
 */
@Schema(description = "学习路径前置条件上下文")
public record GraphLearningPrerequisiteContext(
        @Schema(description = "查询的能力ID列表") List<Long> abilityIds,
        @Schema(description = "前置条件节点列表") List<PrerequisiteNode> prerequisites
) implements Serializable {

    /**
     * 前置条件节点
     */
    @Schema(description = "前置条件节点")
    public record PrerequisiteNode(
            @Schema(description = "能力ID") Long abilityId,
            @Schema(description = "能力名称") String abilityName,
            @Schema(description = "前置能力ID") Long prerequisiteAbilityId,
            @Schema(description = "前置能力名称") String prerequisiteAbilityName,
            @Schema(description = "关系类型") String relationType,
            @Schema(description = "来源引用") List<String> sourceRefs,
            @Schema(description = "图谱版本") String graphVersion
    ) implements Serializable {
    }
}
