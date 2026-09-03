package com.example.matching.entity.kg;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识点实体
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("knowledge_node")
public class KnowledgeNode implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 知识点编码 */
    private String nodeCode;

    /** 知识点名称 */
    private String nodeName;

    /** 所属领域ID */
    private Long domainId;

    /** 父知识点ID */
    private Long parentId;

    /** 知识点层级：1一级，2二级，3三级 */
    private Integer nodeLevel;

    /** 知识点描述 */
    private String nodeDescription;

    /** 学习目标 */
    private String learningObjectives;

    /** 前置知识点JSON数组 */
    private String prerequisitesJson;

    /** 排序序号 */
    private Integer sortOrder;

    /** 状态：ACTIVE/INACTIVE */
    private String status;

    /** 逻辑删除：0未删除，1已删除 */
    @TableLogic
    private Integer isDeleted;

    /** 创建人ID */
    private Long createdBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /** 更新人ID */
    private Long updatedBy;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
