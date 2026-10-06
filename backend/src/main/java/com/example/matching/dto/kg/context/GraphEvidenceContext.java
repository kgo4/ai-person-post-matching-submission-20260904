package com.example.matching.dto.kg.context;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 能力证据上下文（紧凑 DTO，不暴露原始文档文本）
 */
@Schema(description = "能力证据上下文")
public record GraphEvidenceContext(
        @Schema(description = "证据节点ID") Long evidenceId,
        @Schema(description = "证据标签") String label,
        @Schema(description = "关系类型") String relationType,
        @Schema(description = "置信度") Double confidence,
        @Schema(description = "审核状态") String reviewStatus,
        @Schema(description = "来源引用列表") List<String> sourceRefs,
        @Schema(description = "图谱版本") String graphVersion,
        @Schema(description = "创建时间") LocalDateTime createdTime
) implements Serializable {
}
