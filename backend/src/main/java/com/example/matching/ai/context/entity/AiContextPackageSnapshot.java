package com.example.matching.ai.context.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI上下文包快照实体
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("ai_context_package_snapshot")
public class AiContextPackageSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** AI使用场景 */
    private String scenario;

    /** 业务键，如 MATCHING_RECORD:1001 */
    private String businessKey;

    /** 上下文内容hash */
    private String contextHash;

    /** 预估token数量 */
    private Integer tokenEstimate;

    /** 来源引用数量 */
    private Integer sourceRefCount;

    /** 上下文包JSON */
    private String packageJson;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
