package com.example.matching.entity.post;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 岗位原型表实体
 * <p>
 * 用于沉淀岗位族/岗位模板，支撑新兴岗位快速建模。
 * 当用户输入新兴岗位时，先从原型库召回最像的岗位族，
 * 再结合标签库生成该岗位的能力模型草稿。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("post_prototype")
public class PostPrototype implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 原型名称，如"后端开发工程师" */
    private String prototypeName;

    /** 行业方向，如"互联网/金融/制造" */
    private String industry;

    /** 岗位族分类，如"技术/产品/运营" */
    private String category;

    /** 领域分类：AI/BIG_DATA/IOT/SMART_SYSTEM/CLOUD/BLOCKCHAIN/GENERAL */
    private String domain;

    /** 原型描述，包含典型职责和任职要求 */
    private String description;

    /** 描述文本的向量嵌入（JSON数组），用于语义召回 */
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private List<Float> embeddingVector;

    /** 状态：0停用，1启用 */
    private Integer status;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.UPDATE)
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @Version
    private Integer version;
}
