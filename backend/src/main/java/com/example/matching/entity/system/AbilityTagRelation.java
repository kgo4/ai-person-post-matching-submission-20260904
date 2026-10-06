package com.example.matching.entity.system;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 能力标签关系表实体
 * <p>
 * 用于表达标签之间的已确认或待审核关系。
 * <p>
 * 关系类型：
 * - ALIAS: 别名（如 SpringBoot -> Spring Boot）
 * - SYNONYM: 同义词（如 RAG工程化 -> 检索增强生成）
 * - PARENT_CHILD: 父子关系（如 Java -> Java并发编程）
 * - SIMILAR: 相似能力（如 React -> Vue）
 * - MERGE: 合并关系（如 Prompt Engineering 合并到 大语言模型应用）
 * - SAME_AS: 语义等价（旧类型，兼容保留）
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("ability_tag_relation")
public class AbilityTagRelation implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 源标签ID */
    private Long sourceTagId;

    /** 目标标签ID */
    private Long targetTagId;

    /** 别名/同义词名称（仅ALIAS/SYNONYM类型使用） */
    private String aliasName;

    /** 关系类型：ALIAS/SYNONYM/PARENT_CHILD/SIMILAR/MERGE/SAME_AS */
    private String relationType;

    /** 相似度分数（0-1） */
    private BigDecimal similarityScore;

    /** 状态：PENDING-待审核，CONFIRMED-已确认，REJECTED-已拒绝 */
    private String status;

    /** 证据来源：MANUAL-人工，AI_DISCOVERY-AI发现，VECTOR_DISCOVERY-向量发现 */
    private String evidenceSource;

    /** 来源类型（同evidenceSource，语义更清晰） */
    private String sourceType;

    /** 备注 */
    private String remark;

    /** 创建人ID */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /** 更新人ID */
    @TableField(fill = FieldFill.UPDATE)
    private Long updatedBy;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
