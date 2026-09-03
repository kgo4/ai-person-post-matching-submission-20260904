package com.example.matching.service.kg.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.common.constant.SourceRefConstants;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.dto.kg.GraphRelationCandidateCreateDTO;
import com.example.matching.dto.kg.GraphRelationCandidateReviewDTO;
import com.example.matching.dto.kg.GraphRelationCandidateRevokeDTO;
import com.example.matching.entity.kg.KgRelationCandidate;
import com.example.matching.mapper.kg.KgRelationCandidateMapper;
import com.example.matching.service.kg.GraphChangeSetService;
import com.example.matching.service.kg.GraphRelationCandidateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GraphRelationCandidateServiceImpl implements GraphRelationCandidateService {

    private static final String RELATED_TO = "RELATED_TO";
    private static final List<String> DISCOVERY_METHODS = List.of("VECTOR", "LLM", "MANUAL");

    private final KgRelationCandidateMapper candidateMapper;
    private final GraphChangeSetService graphChangeSetService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KgRelationCandidate createCandidate(GraphRelationCandidateCreateDTO request, Long createdBy) {
        String sourceNodeKey = normalizeNodeKey(request.getSourceNodeKey());
        String targetNodeKey = normalizeNodeKey(request.getTargetNodeKey());
        if (!sourceNodeKey.startsWith("ABILITY:") || !targetNodeKey.startsWith("ABILITY:")) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "RELATED_TO 仅支持 AbilityTag 能力节点之间的关联");
        }
        if (sourceNodeKey.equals(targetNodeKey)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "关联关系不能指向自身");
        }
        if (request.getDiscoveryMethod() == null
                || !DISCOVERY_METHODS.contains(request.getDiscoveryMethod().trim().toUpperCase())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "discoveryMethod 仅支持 VECTOR、LLM 或 MANUAL");
        }

        List<String> sourceRefs = normalizeSourceRefs(request.getSourceRefs());
        if (sourceRefs.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "关联关系必须提供有效来源引用");
        }
        BigDecimal semanticScore = request.getSemanticScore();
        if (semanticScore == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "关联关系必须提供语义相似度");
        }
        if (semanticScore.compareTo(BigDecimal.ZERO) < 0 || semanticScore.compareTo(BigDecimal.ONE) > 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "语义相似度必须在 0 到 1 之间");
        }

        String[] orderedKeys = List.of(sourceNodeKey, targetNodeKey).stream()
                .sorted(Comparator.naturalOrder())
                .toArray(String[]::new);
        KgRelationCandidate existing = candidateMapper.selectOne(
                Wrappers.<KgRelationCandidate>lambdaQuery()
                        .eq(KgRelationCandidate::getSourceNodeKey, orderedKeys[0])
                        .eq(KgRelationCandidate::getTargetNodeKey, orderedKeys[1])
                        .eq(KgRelationCandidate::getRelationType, RELATED_TO)
                        .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }

        KgRelationCandidate candidate = new KgRelationCandidate();
        candidate.setCandidateCode("KRC_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        candidate.setSourceNodeKey(orderedKeys[0]);
        candidate.setTargetNodeKey(orderedKeys[1]);
        candidate.setRelationType(RELATED_TO);
        candidate.setDiscoveryMethod(request.getDiscoveryMethod().trim().toUpperCase());
        candidate.setSemanticScore(semanticScore);
        candidate.setSourceRefsJson(writeJson(sourceRefs));
        candidate.setReviewStatus("PENDING");
        candidate.setCreatedBy(createdBy);
        candidateMapper.insert(candidate);
        return candidate;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KgRelationCandidate reviewCandidate(Long candidateId, GraphRelationCandidateReviewDTO request, Long reviewedBy) {
        KgRelationCandidate candidate = candidateMapper.selectById(candidateId);
        if (candidate == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "图谱关系候选不存在");
        }
        if (!"PENDING".equals(candidate.getReviewStatus())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "仅待审核的关系候选可以裁决");
        }
        String decision = request.getDecision().trim().toUpperCase();
        if (!List.of("APPROVED", "REJECTED").contains(decision)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "decision 仅支持 APPROVED 或 REJECTED");
        }
        candidate.setReviewStatus(decision);
        candidate.setReviewReason(request.getReviewReason());
        candidate.setReviewedBy(reviewedBy);
        candidate.setReviewedTime(LocalDateTime.now());
        candidateMapper.updateById(candidate);
        graphChangeSetService.requestChange("RELATION_CANDIDATE", "KG_RELATION_CANDIDATE", candidate.getId(),
                "UPSERT", Map.of("reviewStatus", decision), reviewedBy);
        return candidate;
    }

    @Override
    public List<KgRelationCandidate> listCandidates(String reviewStatus) {
        return candidateMapper.selectList(Wrappers.<KgRelationCandidate>lambdaQuery()
                .eq(reviewStatus != null && !reviewStatus.isBlank(), KgRelationCandidate::getReviewStatus, reviewStatus)
                .orderByDesc(KgRelationCandidate::getCreatedTime));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KgRelationCandidate revokeCandidate(Long candidateId, GraphRelationCandidateRevokeDTO request, Long revokedBy) {
        KgRelationCandidate candidate = candidateMapper.selectById(candidateId);
        if (candidate == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "图谱关系候选不存在");
        }
        if (!"APPROVED".equals(candidate.getReviewStatus())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "仅已审核通过的关系可以撤销");
        }
        candidate.setReviewStatus("REVOKED");
        candidate.setReviewReason(request.getRevokeReason());
        candidate.setReviewedBy(revokedBy);
        candidate.setReviewedTime(LocalDateTime.now());
        candidateMapper.updateById(candidate);
        graphChangeSetService.requestChange("RELATION_CANDIDATE", "KG_RELATION_CANDIDATE", candidate.getId(),
                "DISABLE", Map.of("reviewStatus", "REVOKED"), revokedBy);
        return candidate;
    }

    private String normalizeNodeKey(String value) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || !normalized.matches("[A-Z_]+:[0-9]+")) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "节点键必须使用 TYPE:ID 格式");
        }
        return normalized;
    }

    private List<String> normalizeSourceRefs(List<String> sourceRefs) {
        List<String> normalized = new ArrayList<>();
        if (sourceRefs == null) {
            return normalized;
        }
        for (String sourceRef : sourceRefs) {
            if (sourceRef == null || sourceRef.isBlank()) {
                continue;
            }
            String value = sourceRef.trim();
            if (!SourceRefConstants.isStandardFormat(value) || !isAllowedSourceRef(value)) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "存在非标准来源引用: " + value);
            }
            if (!normalized.contains(value)) {
                normalized.add(value);
            }
        }
        return normalized;
    }

    private boolean isAllowedSourceRef(String sourceRef) {
        return sourceRef.startsWith(SourceRefConstants.PREFIX_FACT)
                || sourceRef.startsWith(SourceRefConstants.PREFIX_EVIDENCE)
                || sourceRef.startsWith(SourceRefConstants.PREFIX_MATCHING)
                || sourceRef.startsWith(SourceRefConstants.PREFIX_SOURCE)
                || sourceRef.startsWith(SourceRefConstants.PREFIX_FEEDBACK)
                || sourceRef.startsWith(SourceRefConstants.PREFIX_RAG)
                || sourceRef.startsWith(SourceRefConstants.PREFIX_LEARNING);
    }

    private String writeJson(List<String> sourceRefs) {
        try {
            return objectMapper.writeValueAsString(sourceRefs);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "来源引用序列化失败");
        }
    }
}
