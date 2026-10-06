package com.example.matching.dto.post.api;

import com.example.matching.dto.post.TrendCandidatePayload;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 趋势候选详情（抽屉用）：摘要 + 完整载荷 + 证据 + 来源 + 治理理由。
 */
@Data
public class PostTrendCandidateDetailResponse {

    private PostTrendCandidateResponse summary;

    /** 完整能力清单、职责、场景、强调度口径说明、既有能力参考清单 */
    private TrendCandidatePayload payload;

    /** 证据原文片段（多段以 --- 分隔） */
    private String evidenceText;

    /** 标准来源引用列表，形如 source:INDUSTRY_WHITEPAPER:11:0 */
    private List<String> sourceRefs = new ArrayList<>();

    /** 来源材料标题，按引用顺序对齐；供界面直接显示「依据是哪几份文件」 */
    private List<String> sourceTitles = new ArrayList<>();

    /** 未归位能力提出的新标签候选ID，前端据此调「采用现有标签 / 忽略」 */
    private List<Long> newTagCandidateIds = new ArrayList<>();
}
