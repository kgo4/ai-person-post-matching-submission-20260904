package com.example.matching.dto.rag.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

@Schema(description = "创建知识文档请求")
public record KnowledgeDocumentCreateRequest(
        @Schema(description = "来源类型") String sourceType,
        @Schema(description = "来源业务ID") Long sourceRefId,
        @Schema(description = "文档标题") String title,
        @Schema(description = "文档内容") String content,
        @Schema(description = "结构化元数据JSON") String metadataJson
) implements Serializable {
}
