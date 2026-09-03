package com.example.matching.entity.employee;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 视频面试能力提取表实体
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("emp_video_interview_ability")
public class EmpVideoInterviewAbility implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 会话ID */
    private Long sessionId;

    /** 能力标签ID */
    private Long tagId;

    /** 掌握等级：1-5 */
    private Integer masteryLevel;

    /** 置信度 */
    private BigDecimal confidenceScore;

    /** 来源权重 */
    private BigDecimal sourceWeight;

    /** 证据摘要 */
    private String evidenceSummary;

    /** 分析评语 */
    private String analysisComment;

    /** 是否已导入：0-否，1-是 */
    private Integer importedFlag;

    /** 导入时间 */
    private LocalDateTime importedTime;
}
