package com.example.matching.entity.interview;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 面试追问实体
 * <p>
 * 记录面试过程中的追问链路，用于证明能力观察是基于多轮追问得出的结论。
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("interview_follow_up_question")
public class InterviewFollowUpQuestion implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 面试会话ID */
    private Long sessionId;

    /** 父问题ID（触发追问的原始问题） */
    private Long parentQuestionId;

    /** 追问序号 */
    private Integer followUpOrder;

    /** 追问文本 */
    private String questionText;

    /** 服务端确定的追问答题时长（秒） */
    private Integer durationSeconds;

    /** 触发原因：INSUFFICIENT_DETAIL/UNCLEAR_ANSWER/ABILITY_BOUNDARY/VERIFICATION */
    private String triggerReason;

    /** 目标能力标签ID */
    private Long targetAbilityTagId;

    /** 期望的证据类型：EXAMPLE/DETAIL/VERIFICATION */
    private String expectedEvidenceType;

    /** 追问状态：SUGGESTED-系统生成的追问建议, ASKED-已向候选人提出, ANSWERED-候选人已回答 */
    private String followUpStatus;

    /** 候选人回答文本 */
    private String answerText;

    /** 回答质量评分：0-100 */
    private BigDecimal answerQualityScore;

    /** 能力边界判断：CONFIRMED/PARTIAL/INSUFFICIENT/NOT_DEMONSTRATED */
    private String boundaryJudgement;

    /** 追问结论 */
    private String followUpConclusion;

    /** 回答质量评估JSON */
    private String qualityEvaluationJson;

    /** 追问目标维度 */
    private String targetDimension;

    /** 追问类型：STAR_MISSING/PERSONAL_CONTRIBUTION/RESUME_VERIFICATION */
    private String followUpType;

    /** 父追问ID（追问的追问） */
    private Long parentFollowUpId;

    /** 逻辑删除：0未删除，1已删除 */
    @TableLogic
    private Integer isDeleted;

    /** 创建人ID */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /** 更新人ID */
    @TableField(fill = FieldFill.UPDATE)
    private Long updatedBy;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
