package com.example.matching.entity.kg;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 知识图谱节点实体
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("kg_graph_node")
public class KgGraphNode implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 稳定节点键，如 POST:1 */
    private String nodeKey;

    /** 节点类型：POST/POST_FAMILY/ABILITY/TECH_STACK/EMPLOYEE/EVIDENCE/RAG_DOCUMENT/LEARNING_RESOURCE/EVOLUTION_EVENT */
    private String nodeType;

    /** 来源业务ID */
    private Long refId;

    /** 显示标签 */
    private String label;

    /** 分类或领域 */
    private String category;

    /** 能力或岗位等级值 */
    private Integer levelValue;

    /** 状态：ACTIVE/INACTIVE */
    private String status;

    /** 可视化权重 */
    private BigDecimal weightValue;

    /** 节点元数据JSON */
    private String metadataJson;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
