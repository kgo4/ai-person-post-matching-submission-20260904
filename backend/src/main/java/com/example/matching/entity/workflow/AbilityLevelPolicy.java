package com.example.matching.entity.workflow;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 能力等级确认策略配置实体
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("ability_level_policy")
public class AbilityLevelPolicy implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 策略版本号 */
    private String policyVersion;

    /** 策略名称 */
    private String policyName;

    /** 策略配置JSON */
    private String configJson;

    /** 是否启用 */
    private Integer enabled;

    /** 生效时间 */
    private LocalDateTime effectiveFrom;

    /** 创建人ID */
    private Long createdBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
