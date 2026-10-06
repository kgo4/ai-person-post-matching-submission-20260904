package com.example.matching.entity.evolution;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 岗位动态演化定时配置实体
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("post_evolution_schedule_config")
public class PostEvolutionScheduleConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 岗位ID */
    private Long postId;

    /** 是否启用：0否，1是 */
    private Integer enabled;

    /** Cron表达式，默认每天凌晨2点 */
    private String cronExpression;

    /** 行业 */
    private String industry;

    /** 业务领域 */
    private String businessDomain;

    /** 资料范围配置JSON */
    private String sourceScope;

    /** 是否包含行业白皮书 */
    private Integer includeWhitepaper;

    /** 是否包含云知识库 */
    private Integer includeCloudKnowledge;

    /** 是否包含市场JD */
    private Integer includeMarketJd;

    /** 最近执行时间 */
    private LocalDateTime lastRunTime;

    /** 下次执行时间 */
    private LocalDateTime nextRunTime;

    /** 最近生成的任务ID */
    private Long lastTaskId;

    /** 累计执行次数 */
    private Integer runCount;

    /** 创建人ID */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
