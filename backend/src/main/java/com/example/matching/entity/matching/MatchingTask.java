package com.example.matching.entity.matching;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 匹配任务表实体
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("matching_task")
public class MatchingTask implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 任务ID，UUID */
    private String taskId;

    /** 关联的匹配记录批次号（16位，异步任务消费端写入记录使用；删除任务可连带删除该批次记录） */
    private String batchNo;

    /** 岗位ID */
    private Long postId;

    /** 员工ID列表，JSON数组格式 */
    private String empIds;

    /** 状态：0待执行，1执行中，2已完成，3失败 */
    private Integer status;

    /** 进度百分比，0-100 */
    private Integer progress;

    /** 总记录数 */
    private Integer totalCount;

    /** 已处理数 */
    private Integer processedCount;

    /** 结果消息 */
    private String resultMessage;

    /** 错误信息 */
    private String errorMessage;

    /** 消费端重试次数 */
    private Integer retryCount;

    /** 下次重试时间 */
    private LocalDateTime nextRetryTime;

    /** 匹配配置，JSON格式 */
    private String matchingConfig;

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
