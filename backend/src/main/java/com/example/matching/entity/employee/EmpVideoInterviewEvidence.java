package com.example.matching.entity.employee;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 视频面试证据表实体
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("emp_video_interview_evidence")
public class EmpVideoInterviewEvidence implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 会话ID */
    private Long sessionId;

    /** 问题ID */
    private Long questionId;

    /** 证据类型：TEXT/AUDIO/VISUAL/MULTIMODAL */
    private String evidenceType;

    /** 开始时间（秒） */
    private Integer startSecond;

    /** 结束时间（秒） */
    private Integer endSecond;

    /** 证据文本 */
    private String evidenceText;

    /** 帧引用JSON */
    private String frameRefsJson;

    /** 置信度 */
    private BigDecimal confidenceScore;

    /** 原始得分 */
    private BigDecimal rawScore;
}
