package com.example.matching.vo.post;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 趋势候选列表项（摘要卡）。
 * <p>
 * 刻意只放「一眼能判断要不要点开」的信息：岗位名、类型、相似度、强调度、治理结论、
 * 前几个能力名与变更摘要。完整能力清单 / 证据 / 来源在详情里。
 */
@Data
public class PostTrendCandidateVO {

    private Long id;

    private Long taskId;

    /** NEW_POST / ABILITY_CHANGE */
    private String candidateType;

    private String postName;

    private String postDescription;

    /** ABILITY_CHANGE 时的锚定岗位 */
    private Long matchedPostId;

    private String matchedPostName;

    /** 与锚定岗位的相似度 0-1；null 表示检索不可用，不代表不相似 */
    private BigDecimal similarityScore;

    private BigDecimal emphasisScore;

    /** 提及该岗位的材料类别数，≥2 表示多来源印证 */
    private Integer sourceCoverage;

    /** PASS / REVIEW / BLOCK / RETRY */
    private String harnessDecision;

    private String riskLevel;

    /** PENDING / APPROVED / REJECTED */
    private String confirmStatus;

    /** 已落地时回写的岗位ID */
    private Long createdPostId;

    private Integer abilityCount;

    /** 尚未归位到既有标签的能力数量 */
    private Integer unresolvedAbilityCount;

    /** 前几个能力名，供卡片 chip 展示 */
    private List<String> abilityPreview = new ArrayList<>();

    /** 前几条变更摘要，如「大模型微调 由 2 升到 5」 */
    private List<String> changePreview = new ArrayList<>();

    private LocalDateTime createdTime;
}
