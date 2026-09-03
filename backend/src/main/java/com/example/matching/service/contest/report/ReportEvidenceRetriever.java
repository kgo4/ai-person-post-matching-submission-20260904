package com.example.matching.service.contest.report;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.entity.contest.ContestEvidenceItem;
import com.example.matching.mapper.contest.ContestEvidenceItemMapper;
import com.example.matching.service.rag.RagRetrievalRequest;
import com.example.matching.service.rag.RagRetrievalResult;
import com.example.matching.service.rag.RagRetrievalService;
import com.example.matching.service.rag.RagScenarioEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 报告证据检索器
 * <p>
 * 负责从证据中心和 RAG 检索可信来源，为报告生成提供上下文。
 * 只检索 VERIFIED 状态的证据和未删除的记录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportEvidenceRetriever {

    private final ContestEvidenceItemMapper evidenceItemMapper;
    private final RagRetrievalService ragRetrievalService;

    /**
     * 检索报告生成所需的证据上下文
     *
     * @param reportType 报告类型
     * @param queryText  查询文本（用于 RAG 检索）
     * @return 证据检索结果
     */
    public ReportEvidenceSnapshot retrieve(String reportType, String queryText) {
        ReportEvidenceSnapshot snapshot = new ReportEvidenceSnapshot();

        // 1. 从证据中心检索 VERIFIED 状态的证据
        List<ContestEvidenceItem> verifiedEvidence = evidenceItemMapper.selectList(
                new LambdaQueryWrapper<ContestEvidenceItem>()
                        .eq(ContestEvidenceItem::getEvidenceStatus, "VERIFIED")
                        .eq(ContestEvidenceItem::getIsDeleted, 0)
                        .orderByDesc(ContestEvidenceItem::getCredibilityScore)
                        .last("LIMIT 20"));
        snapshot.setVerifiedEvidence(verifiedEvidence);

        // 2. RAG 检索（如果需要）
        if (queryText != null && !queryText.isEmpty()) {
            try {
                RagRetrievalResult ragResult = ragRetrievalService.retrieve(
                        RagRetrievalRequest.builder()
                                .queryText(queryText)
                                .scenario(RagScenarioEnum.REPORT_GENERATION)
                                .build());
                snapshot.setRagHits(ragResult.getHits());
                snapshot.setRagContextText(ragResult.getContextText());
                snapshot.setRagHitCount(ragResult.getHitCount());
            } catch (Exception e) {
                log.warn("报告 RAG 检索失败，降级为纯证据模式: {}", e.getMessage());
                snapshot.setRagHits(Collections.emptyList());
                snapshot.setRagContextText("");
                snapshot.setRagHitCount(0);
            }
        }

        // 3. 构建证据快照 JSON
        snapshot.setSnapshotJson(buildSnapshotJson(snapshot));

        return snapshot;
    }

    private String buildSnapshotJson(ReportEvidenceSnapshot snapshot) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"verifiedEvidence\":[");

        List<ContestEvidenceItem> evidences = snapshot.getVerifiedEvidence();
        for (int i = 0; i < evidences.size(); i++) {
            ContestEvidenceItem e = evidences.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"id\":").append(e.getId());
            sb.append(",\"code\":\"").append(escapeJson(e.getEvidenceCode())).append("\"");
            sb.append(",\"source\":\"").append(escapeJson(e.getSourceType())).append("\"");
            sb.append(",\"ability\":\"").append(escapeJson(e.getAbilityName())).append("\"");
            sb.append(",\"confidence\":").append(e.getConfidenceScore());
            sb.append(",\"credibility\":").append(e.getCredibilityScore());
            sb.append("}");
        }

        sb.append("],\"ragHitCount\":").append(snapshot.getRagHitCount());
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * 证据检索快照
     */
    @lombok.Data
    public static class ReportEvidenceSnapshot {
        private List<ContestEvidenceItem> verifiedEvidence = Collections.emptyList();
        private List<RagRetrievalResult.RagHit> ragHits = Collections.emptyList();
        private String ragContextText = "";
        private int ragHitCount = 0;
        private String snapshotJson = "{}";
    }
}
