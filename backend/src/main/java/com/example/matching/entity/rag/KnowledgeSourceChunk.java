package com.example.matching.entity.rag;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 知识来源切片实体
 * <p>
 * 文档经过解析和切片后的最小证据单元。
 * 每个切片携带统一 sourceRef，供 Agent 和 Harness 使用。
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("knowledge_source_chunk")
public class KnowledgeSourceChunk implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 文档ID */
    private Long documentId;

    /** 切片编码（文档内唯一） */
    private String chunkCode;

    /** 章节标题 */
    private String sectionTitle;

    /** 切片原文 */
    private String chunkText;

    /** 清洗后文本 */
    private String cleanedText;

    /** 切片类型：TREND/POST_REQUIREMENT/BUSINESS_CHANGE/ABILITY_REQUIREMENT/TASK_DESCRIPTION/TOOL_TECH/NOISE/UNKNOWN */
    private String chunkType;

    /** 统一 sourceRef 格式：source:{sourceType}:{documentId}:{chunkCode} */
    private String sourceRef;

    /** 页码 */
    private Integer pageNo;

    /** 段落号 */
    private Integer paragraphNo;

    /** Token 数量 */
    private Integer tokenCount;

    /** 质量评分：0-100 */
    private BigDecimal qualityScore;

    /** 向量嵌入状态：PENDING/DONE/FAILED */
    private String embeddingStatus;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
