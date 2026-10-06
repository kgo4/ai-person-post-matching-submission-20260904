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
 * 岗位趋势候选实体。
 * <p>
 * 两类候选共表：
 * <ul>
 *   <li>{@code NEW_POST} —— 与既有岗位相似度低，建议新建岗位；</li>
 *   <li>{@code ABILITY_CHANGE} —— 与既有岗位相似度高，建议作为该岗位的能力变更。</li>
 * </ul>
 * 无论哪一类，都必须由岗位管理员显式确认后才会落地，不存在自动创建。
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("post_trend_candidate")
public class PostTrendCandidate implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 候选类型：新岗位 */
    public static final String TYPE_NEW_POST = "NEW_POST";

    /** 候选类型：既有岗位能力变更 */
    public static final String TYPE_ABILITY_CHANGE = "ABILITY_CHANGE";

    /** 确认状态：待确认 */
    public static final String CONFIRM_PENDING = "PENDING";

    /** 确认状态：已通过 */
    public static final String CONFIRM_APPROVED = "APPROVED";

    /** 确认状态：已驳回 */
    public static final String CONFIRM_REJECTED = "REJECTED";

    /** 治理判定：放行 —— 可作为批量确认的对象 */
    public static final String HARNESS_PASS = "PASS";

    /** 治理判定：待复核 —— 必须逐条人工看过才能落地 */
    public static final String HARNESS_REVIEW = "REVIEW";

    /** 治理判定：拦截 —— 证据不足或来源自证，禁止落地 */
    public static final String HARNESS_BLOCK = "BLOCK";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属趋势任务ID */
    private Long taskId;

    /** NEW_POST / ABILITY_CHANGE */
    private String candidateType;

    /** 解析出的岗位名称 */
    private String postName;

    /** 岗位描述（AI 生成草案） */
    private String postDescription;

    /** 能力清单、职责、业务场景、建议等级与权重、标签归位结果（JSON 字符串） */
    private String candidatePayload;

    /** 最相似的既有岗位；ABILITY_CHANGE 必有 */
    private Long matchedPostId;

    /** 最相似岗位名称快照 */
    private String matchedPostName;

    /** 与 matchedPost 的 COSINE 相似度 0-1；null 表示未取得（检索不可用） */
    private BigDecimal similarityScore;

    /** 材料内出现强度 */
    private BigDecimal emphasisScore;

    /** 不同类别材料的印证数量 */
    private Integer sourceCoverage;

    /** 证据原文片段 */
    private String evidenceText;

    /** 来源文档ID与分块定位（JSON 字符串） */
    private String sourceRefs;

    /** 治理判定：PASS/REVIEW/BLOCK */
    private String harnessDecision;

    /** 风险等级：LOW/MEDIUM/HIGH */
    private String riskLevel;

    /** PENDING/APPROVED/REJECTED */
    private String confirmStatus;

    /** 确认创建后回写的岗位ID */
    private Long createdPostId;

    /** 去重指纹 */
    private String fingerprint;

    /** 审核意见 */
    private String reviewComment;

    private Long reviewedBy;

    private LocalDateTime reviewedAt;

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

    public boolean isNewPost() {
        return TYPE_NEW_POST.equals(candidateType);
    }

    public boolean isPending() {
        return CONFIRM_PENDING.equals(confirmStatus);
    }
}
