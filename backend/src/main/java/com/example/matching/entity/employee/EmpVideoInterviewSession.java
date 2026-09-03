package com.example.matching.entity.employee;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 视频面试会话表实体
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("emp_video_interview_session")
public class EmpVideoInterviewSession implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 员工ID */
    private Long empId;

    /** 岗位ID（POST_BASED模式时必填） */
    private Long postId;

    /** 关联的能力评估工作流ID */
    private Long workflowId;

    /** 会话名称 */
    private String sessionName;

    /** 面试模式：POST_BASED/GENERAL */
    private String interviewMode;

    /** 视频文件路径 */
    private String videoFilePath;

    /** 音频文件路径 */
    private String audioFilePath;

    /** 转录文本 */
    private String transcriptText;

    /** 转录JSON（含时间戳） */
    private String transcriptJson;

    /** 总结报告 */
    private String summaryReport;

    /** 综合得分 */
    private BigDecimal overallScore;

    /** 状态：0-已创建,1-问题已生成,2-视频已上传,3-转录中,4-分析中,5-已完成,6-已导入,7-失败 */
    private Integer status;

    /** 对话状态机状态：PRESET_QUESTION/ANSWERING_PRESET/EVALUATING_ANSWER/FOLLOW_UP_QUESTION/ANSWERING_FOLLOW_UP/NEXT_OR_FINISH/FINISHED */
    private String conversationState;

    /** Active preset question order, starting at 1. */
    private Integer currentQuestionOrder;

    /** Active answer window start time. */
    private LocalDateTime questionStartedAt;

    /** Active answer window deadline. */
    private LocalDateTime questionDeadlineAt;

    /** Interview flow start time. */
    private LocalDateTime interviewStartedAt;

    /** Session optimistic lock version. */
    private Long sessionVersion;

    /** 视频时长（秒） */
    private Integer durationSeconds;

    /** 问题数量 */
    private Integer questionCount;

    /** 错误信息 */
    private String errorMessage;

    /** 面试后 AI 分析重试次数（调度器恢复用） */
    private Integer analysisRetryCount;

    /** 面试后 AI 分析失败原因 */
    private String analysisFailedReason;

    /** 创建人ID */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /** 更新人ID */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
