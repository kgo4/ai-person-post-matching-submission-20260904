package com.example.matching.dto.rag.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.time.LocalDateTime;

@Schema(description = "知识文档响应")
public record KnowledgeDocumentResponse(
        @Schema(description = "主键ID") Long id,
        @Schema(description = "文档编码") String docCode,
        @Schema(description = "来源类型") String sourceType,
        @Schema(description = "来源业务ID") Long sourceRefId,
        @Schema(description = "文档标题") String title,
        @Schema(description = "文档完整内容") String content,
        @Schema(description = "结构化元数据JSON") String metadataJson,
        @Schema(description = "文档状态") String docStatus,
        @Schema(description = "分块数量") Integer chunkCount,
        @Schema(description = "最后索引时间") LocalDateTime lastIndexedTime,
        @Schema(description = "创建人ID") Long createdBy,
        @Schema(description = "创建时间") LocalDateTime createdTime,
        @Schema(description = "更新人ID") Long updatedBy,
        @Schema(description = "更新时间") LocalDateTime updatedTime
) implements Serializable {
}
