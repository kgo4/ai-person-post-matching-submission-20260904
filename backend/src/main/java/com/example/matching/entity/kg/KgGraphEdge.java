package com.example.matching.entity.kg;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 知识图谱边实体
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("kg_graph_edge")
public class KgGraphEdge implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 稳定边键 */
    private String edgeKey;

    /** 源节点键 */
    private String sourceNodeKey;

    /** 目标节点键 */
    private String targetNodeKey;

    /** 边类型，例如 REQUIRES、HAS_ABILITY、SUPPORTED_BY、PREREQUISITE_OF */
    private String edgeType;

    /** 边权重 */
    private BigDecimal weightValue;

    /** 置信度 0-100 */
    private BigDecimal confidenceScore;

    /** 边元数据JSON */
    private String metadataJson;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
