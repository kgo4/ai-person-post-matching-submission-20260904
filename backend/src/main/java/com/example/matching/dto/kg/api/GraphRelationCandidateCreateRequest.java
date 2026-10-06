package com.example.matching.dto.kg.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.math.BigDecimal;

@Schema(description = "图谱关系候选创建请求")
public record GraphRelationCandidateCreateRequest(
        @Schema(description = "源节点Key") String sourceNodeKey,
        @Schema(description = "目标节点Key") String targetNodeKey,
        @Schema(description = "关系类型") String relationType,
        @Schema(description = "发现方法") String discoveryMethod,
        @Schema(description = "语义评分") BigDecimal semanticScore,
        @Schema(description = "来源引用JSON") String sourceRefsJson
) implements Serializable {
}
