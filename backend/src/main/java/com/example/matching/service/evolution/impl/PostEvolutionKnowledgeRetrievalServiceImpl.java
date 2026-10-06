package com.example.matching.service.evolution.impl;

import com.example.matching.common.constant.SourceRefConstants;
import com.example.matching.dto.rag.KnowledgeChunkResultDTO;
import com.example.matching.dto.rag.KnowledgeChunkSearchDTO;
import com.example.matching.entity.rag.KnowledgeSourceDocument;
import com.example.matching.entity.evolution.MarketJdData;
import com.example.matching.mapper.evolution.MarketJdDataMapper;
import com.example.matching.mapper.rag.KnowledgeSourceDocumentMapper;
import com.example.matching.service.evolution.PostEvolutionKnowledgeRetrievalService;
import com.example.matching.service.rag.KnowledgeDocumentService;
import com.example.matching.service.rag.RagRetrievalRequest;
import com.example.matching.service.rag.RagRetrievalResult;
import com.example.matching.service.rag.RagRetrievalService;
import com.example.matching.service.rag.RagScenarioEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 岗位演化知识检索服务实现
 * <p>
 * 从云知识库和行业白皮书中检索与岗位演化相关的证据片段。
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostEvolutionKnowledgeRetrievalServiceImpl implements PostEvolutionKnowledgeRetrievalService {

    private final RagRetrievalService ragRetrievalService;
    private final KnowledgeDocumentService knowledgeDocumentService;
    private final KnowledgeSourceDocumentMapper sourceDocumentMapper;
    private final MarketJdDataMapper marketJdDataMapper;

    @Override
    public List<RetrievalResult> retrieveForPost(RetrievalRequest request) {
        log.info("检索岗位演化知识片段: postId={}, postName={}, industry={}",
                request.postId(), request.postName(), request.industry());

        List<RetrievalResult> results = new ArrayList<>();

        // 构建查询文本
        String queryText = String.join(" ", request.keywords());
        if (request.postName() != null) {
            queryText = request.postName() + " " + queryText;
        }

        // 1. 使用 RAG 检索服务
        RagRetrievalRequest ragRequest = RagRetrievalRequest.builder()
                .queryText(queryText)
                .scenario(RagScenarioEnum.POST_EVOLUTION)
                .topK(request.maxResults())
                .sourceTypes(request.knowledgeBaseScope())
                .build();

        RagRetrievalResult ragResult = ragRetrievalService.retrieve(ragRequest);

        // 2. 转换检索结果
        if (ragResult.getHits() != null) {
            for (RagRetrievalResult.RagHit hit : ragResult.getHits()) {
                String sourceType = determineSourceType(hit, request.knowledgeBaseScope());
                results.add(new RetrievalResult(
                        String.valueOf(hit.getChunkId()),
                        hit.getContent(),
                        hit.getTitle(),
                        chunkTypeForSource(sourceType),
                        buildSourceRef(sourceType, hit),
                        hit.getScore()
                ));
            }
        }

        log.info("检索完成，共获取 {} 个知识片段", results.size());
        return results;
    }

    @Override
    public List<RetrievalResult> retrieveIndustryTrends(String industry, List<String> keywords, int maxResults) {
        log.info("检索行业趋势: industry={}, keywords={}", industry, keywords);

        String queryText = industry + " " + String.join(" ", keywords) + " 趋势 发展";

        RagRetrievalRequest request = RagRetrievalRequest.builder()
                .queryText(queryText)
                .scenario(RagScenarioEnum.POST_EVOLUTION)
                .topK(maxResults)
                .sourceTypes(List.of(SourceRefConstants.SOURCE_INDUSTRY_WHITEPAPER))
                .build();

        RagRetrievalResult result = ragRetrievalService.retrieve(request);

        return convertResults(result, SourceRefConstants.SOURCE_INDUSTRY_WHITEPAPER, "ABILITY_REQUIREMENT");
    }

    @Override
    public List<RetrievalResult> retrieveBusinessChanges(String businessDomain, List<String> keywords, int maxResults) {
        log.info("检索业务变化: businessDomain={}, keywords={}", businessDomain, keywords);

        String queryText = businessDomain + " " + String.join(" ", keywords) + " 业务 变化 需求";

        RagRetrievalRequest request = RagRetrievalRequest.builder()
                .queryText(queryText)
                .scenario(RagScenarioEnum.POST_EVOLUTION)
                .topK(maxResults)
                .sourceTypes(List.of(SourceRefConstants.SOURCE_CLOUD_KNOWLEDGE_INTERNAL))
                .build();

        RagRetrievalResult result = ragRetrievalService.retrieve(request);

        return convertResults(result, SourceRefConstants.SOURCE_CLOUD_KNOWLEDGE_INTERNAL, "BUSINESS_CHANGE");
    }

    @Override
    public List<RetrievalResult> retrieveMarketEvolutionClues(Long postId, List<Long> abilityTagIds, int maxResults) {
        Set<Long> modelTagIds = abilityTagIds == null ? Set.of() : new LinkedHashSet<>(abilityTagIds);
        if (postId == null || modelTagIds.isEmpty()) return List.of();

        List<MarketJdData> candidates = marketJdDataMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MarketJdData>()
                .eq(MarketJdData::getAnalysisStatus, 1).eq(MarketJdData::getIsDuplicate, 0)
                .isNotNull(MarketJdData::getSkillTags).orderByDesc(MarketJdData::getPublishedTime).last("LIMIT 500"));
        List<RetrievalResult> results = new ArrayList<>();
        for (MarketJdData jd : candidates) {
            Set<Long> jdTagIds = parseTagIds(jd.getSkillTags());
            jdTagIds.retainAll(modelTagIds);
            boolean matchedPost = postId.equals(jd.getMatchedPostId());
            if (!matchedPost && jdTagIds.isEmpty()) continue;
            String evidenceText = joinEvidenceText(jd);
            if (evidenceText.isBlank()) continue;
            double overlap = jdTagIds.size() / (double) modelTagIds.size();
            double relevance = Math.min(1D, (matchedPost ? 0.70D : 0.35D) + overlap);
            results.add(new RetrievalResult(String.valueOf(jd.getId()), evidenceText,
                    String.join(" / ", safe(jd.getPostName()), safe(jd.getCompanyName())),
                    "MARKET_ABILITY_REQUIREMENT", SourceRefConstants.sourceRef(SourceRefConstants.SOURCE_MARKET_JD, jd.getId()), relevance));
            if (results.size() >= Math.max(1, maxResults)) break;
        }
        log.info("检索市场演化线索完成: postId={}, results={}", postId, results.size());
        return results;
    }

    private Set<Long> parseTagIds(String json) {
        try {
            return new LinkedHashSet<>(new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<Long>>() { }));
        } catch (Exception ignored) {
            return new LinkedHashSet<>();
        }
    }

    private String joinEvidenceText(MarketJdData jd) { return String.join("\n", safe(jd.getRequirements()), safe(jd.getJobDescription())).trim(); }

    private String safe(String value) { return value == null ? "" : value.trim(); }

    /**
     * 转换 RAG 检索结果
     */
    private List<RetrievalResult> convertResults(RagRetrievalResult ragResult, String defaultSourceType,
                                                 String chunkType) {
        List<RetrievalResult> results = new ArrayList<>();

        if (ragResult.getHits() != null) {
            for (RagRetrievalResult.RagHit hit : ragResult.getHits()) {
                String sourceType = determineSourceType(hit, List.of(defaultSourceType));
                results.add(new RetrievalResult(
                        String.valueOf(hit.getChunkId()),
                        hit.getContent(),
                        hit.getTitle(),
                        chunkType,
                        buildSourceRef(sourceType, hit),
                        hit.getScore()
                ));
            }
        }

        return results;
    }

    /**
     * 根据命中信息确定来源类型
     */
    private String determineSourceType(RagRetrievalResult.RagHit hit, List<String> allowedSources) {
        if (hit.getSourceType() != null) {
            return hit.getSourceType();
        }
        return allowedSources == null || allowedSources.isEmpty()
                ? SourceRefConstants.SOURCE_RECRUITMENT_JD
                : allowedSources.get(0);
    }

    private String chunkTypeForSource(String sourceType) {
        return switch (sourceType) {
            case SourceRefConstants.SOURCE_INDUSTRY_WHITEPAPER -> "ABILITY_REQUIREMENT";
            case SourceRefConstants.SOURCE_CLOUD_KNOWLEDGE_INTERNAL -> "BUSINESS_CHANGE";
            case SourceRefConstants.SOURCE_RECRUITMENT_JD, SourceRefConstants.SOURCE_MARKET_JD -> "POST_REQUIREMENT";
            default -> "ABILITY_REQUIREMENT";
        };
    }

    /**
     * 构建统一 sourceRef
     */
    private String buildSourceRef(String sourceType, RagRetrievalResult.RagHit hit) {
        return SourceRefConstants.knowledgeSourceRef(
                sourceType,
                hit.getDocumentId() != null ? hit.getDocumentId() : 0L,
                String.valueOf(hit.getChunkId())
        );
    }
}
