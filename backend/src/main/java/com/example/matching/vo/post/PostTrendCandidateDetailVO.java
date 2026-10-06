package com.example.matching.vo.post;

import com.example.matching.dto.post.TrendCandidatePayload;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 趋势候选详情（抽屉用）。
 * <p>
 * 这里是「全量查看」的唯一出口：完整能力清单、逐条变更、证据原文、来源材料、
 * 以及本次解析提出的新能力标签候选（供内联确认）。
 */
@Data
public class PostTrendCandidateDetailVO {

    private Long id;

    private Long taskId;

    private String taskName;

    private String candidateType;

    private String postName;

    private String postDescription;

    private Long matchedPostId;

    private String matchedPostName;

    private BigDecimal similarityScore;

    private String harnessDecision;

    private String riskLevel;

    private String confirmStatus;

    private String reviewComment;

    private Long createdPostId;

    /** 完整载荷：职责、场景、能力清单（含归位状态与变更类型）、治理理由 */
    private TrendCandidatePayload payload;

    /** 证据原文片段 */
    private String evidenceText;

    /** 标准来源引用 */
    private List<String> sourceRefs = new ArrayList<>();

    /** 来源引用的可读标题，与 sourceRefs 一一对应 */
    private List<String> sourceTitles = new ArrayList<>();

    /** 本次解析提出的新能力标签候选，仅当存在未归位能力时非空 */
    private List<TagCandidateBriefVO> newTagCandidates = new ArrayList<>();

    /** Harness 治理判定理由（已并入 payload.harnessReason，此处保留便于前端直取） */
    private String harnessReason;

    private LocalDateTime createdTime;

    private LocalDateTime reviewedAt;

    @Data
    public static class TagCandidateBriefVO {
        private Long id;
        private String candidateName;
        private String status;
        private Long similarTagId;
        private String similarTagName;
        private BigDecimal similarityScore;
    }
}
