package com.example.matching.dto.post.api;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 趋势候选摘要（列表卡片）。
 * <p>
 * 刻意只承载「一眼能判断要不要点进去」的字段：岗位名、相似度、两个徽标、前几个能力名。
 * 完整能力清单与证据在详情接口里按需拉取，避免首屏出现巨量信息。
 */
@Data
public class PostTrendCandidateResponse {

    private Long id;

    private Long taskId;

    /** NEW_POST / ABILITY_CHANGE */
    private String candidateType;

    private String postName;

    private String postDescription;

    /** ABILITY_CHANGE 时锚定的既有岗位 */
    private Long matchedPostId;

    private String matchedPostName;

    /** 与既有岗位的向量相似度 0-1；null 表示本次未取得（检索不可用） */
    private BigDecimal similarityScore;

    /** 材料内强调度（已用提及次数交叉校验） */
    private BigDecimal emphasisScore;

    /** 印证的材料类别数，≥2 时前端显示「多来源印证」 */
    private Integer sourceCoverage;

    /** PASS / REVIEW / BLOCK */
    private String harnessDecision;

    private String riskLevel;

    /** PENDING / APPROVED / REJECTED */
    private String confirmStatus;

    private Long createdPostId;

    /** 能力项总数 */
    private int abilityCount;

    /** 前若干个能力名，供卡片展示 */
    private List<String> previewAbilities = new ArrayList<>();

    /** 未归位到既有标签的能力数量，>0 时详情页才显示「新能力标签」区 */
    private int unresolvedAbilityCount;

    /** 需要人工确认的变更项数量（新岗位候选等于 abilityCount） */
    private int changeCount;

    private LocalDateTime createdTime;
}
