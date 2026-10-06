package com.example.matching.entity.learning;

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
import java.time.LocalDateTime;

/**
 * 员工学习成果提交单（HR 匹配闭环 P4）。
 *
 * <p>与 {@code capability_closure_log} 解耦：后者是「已发生事件」的日志，且被
 * {@code CapabilityClosureServiceImpl.findLog} 用作幂等判定依据，不能在其上叠加审批态。
 * 本表只承载「提交 → 复核」的审批状态，复核通过后才回调既有
 * {@code onLearningOutcomeConfirmed} 完成能力证据回写。</p>
 *
 * <p>设计文档：docs/hr-matching-closed-loop-design.md 4.2 节。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("emp_learning_outcome_submission")
public class EmpLearningOutcomeSubmission implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 待复核：员工已提交，尚未回写能力证据 */
    public static final int STATUS_PENDING = 0;
    /** 通过：已回调回写链路，能力画像更新 */
    public static final int STATUS_APPROVED = 1;
    /** 驳回：附理由退回员工，可修改后重新提交 */
    public static final int STATUS_REJECTED = 2;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 员工档案ID */
    private Long empId;

    /** 关联匹配记录（若有） */
    private Long matchingRecordId;

    /** 能力标签ID */
    private Long tagId;

    /** tagId 缺失时按名称匹配 */
    private String abilityName;

    /** 完成的学习资源ID */
    private Long completedResourceId;

    /** 学习前等级 */
    private Integer beforeLevel;

    /** 员工自评达成等级 1-5 */
    private Integer confirmedLevel;

    /** 员工提交说明 */
    private String note;

    /** AI 学习建议追溯ID */
    private Long aiSuggestionId;

    /** RAG 检索 chunkIds（JSON 数组） */
    private String ragChunkIds;

    /** AI 建议版本 */
    private String aiSuggestionVersion;

    /** 0待复核 1通过 2驳回 */
    private Integer reviewStatus;

    /** 复核意见（驳回必填） */
    private String reviewComment;

    /** 复核人用户ID */
    private Long reviewedBy;

    /** 复核时间 */
    private LocalDateTime reviewedTime;

    /** 复核通过后回写产生的闭环日志业务键 */
    private String closureBusinessKey;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.UPDATE)
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @TableLogic
    private Integer isDeleted;

    @Version
    private Integer version;
}
