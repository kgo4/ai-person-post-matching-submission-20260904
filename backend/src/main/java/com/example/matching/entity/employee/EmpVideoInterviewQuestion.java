package com.example.matching.entity.employee;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 视频面试问题表实体
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("emp_video_interview_question")
public class EmpVideoInterviewQuestion implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 会话ID */
    private Long sessionId;

    /** 问题序号 */
    private Integer questionOrder;

    /** 问题类型 */
    private String questionType;

    /** 问题文本 */
    private String questionText;

    /** 题目难度：EASY、MEDIUM、HARD；默认答题时长由服务端时长策略确定 */
    private String difficulty;

    /** 答题时长（秒）；非空时作为服务端已确认的题目时长 */
    private Integer durationSeconds;

    /** 预期能力标签JSON */
    private String expectedTagsJson;

    /** 答案转录文本 */
    private String answerTranscript;

    /** 答案摘要 */
    private String answerSummary;

    /** 答案开始时间（秒） */
    private Integer startSecond;

    /** 答案结束时间（秒） */
    private Integer endSecond;

    /** 结束方式：TIMEOUT-超时切题,MANUAL_NEXT-候选人主动切题 */
    private String endedBy;

    /** 答案得分 */
    private BigDecimal answerScore;

    /** 分析评语 */
    private String analysisComment;
}
