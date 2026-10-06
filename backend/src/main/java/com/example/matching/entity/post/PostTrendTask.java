package com.example.matching.entity.post;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 岗位趋势解析任务实体。
 * <p>
 * 一次任务 = 一批权威材料（政策文件 / 市场职业报告等）的一次解析，
 * 产出一组「新岗位候选」与「能力变更候选」（见 {@link PostTrendCandidate}）。
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("post_trend_task")
public class PostTrendTask implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 可读任务编码 TREND_xxx */
    private String taskCode;

    /** 任务名称 */
    private String taskName;

    /** 参与解析的知识源文档ID列表 JSON */
    private String sourceDocumentIds;

    /** 参与解析的材料类别 JSON：POLICY/REPORT/STANDARD */
    private String sourceCategories;

    /** 任务状态：PENDING/RUNNING/WAIT_CONFIRM/APPLIED/PARTIALLY_APPLIED/FAILED/CANCELLED */
    private String taskStatus;

    /** 执行阶段：RETRIEVING/EXTRACTING/MATCHING/HARNESS/COMPLETED/FAILED */
    private String progressStatus;

    /** 执行进度 0-100 */
    private Integer progressPercent;

    /** 候选总数 */
    private Integer candidateCount;

    /** 新岗位候选数 */
    private Integer newPostCount;

    /** 能力变更候选数 */
    private Integer changeCount;

    /** 本次解析使用的分流阈值快照，便于事后复核误分 */
    private BigDecimal similarityThreshold;

    /** 解析统计 JSON 字符串 */
    private String resultSummary;

    /** 面向使用者的解析诊断；无候选时必须说明原因 */
    private String diagnostics;

    private String errorMessage;

    /** 触发人用户ID */
    private Long operatorId;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableLogic
    private Integer isDeleted;

    @Version
    private Integer version;
}
