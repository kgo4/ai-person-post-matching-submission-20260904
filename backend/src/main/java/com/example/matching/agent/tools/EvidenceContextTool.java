package com.example.matching.agent.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.entity.contest.ContestEvidenceItem;
import com.example.matching.mapper.contest.ContestEvidenceItemMapper;
import com.example.matching.service.rag.RagRetrievalService;
import com.example.matching.service.rag.RagScenarioEnum;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 证据上下文工具 - 供 LangChain4j Agent 调用
 * <p>
 * 当 MySQL 竞赛证据不足时，自动降级检索 RAG 知识库获取补充参考。
 * RAG 引用与已验证证据严格分离：引用不可独立支持 PASS/通过决策。
 */
@Slf4j
@Component
public class EvidenceContextTool {

    private final ContestEvidenceItemMapper evidenceItemMapper;

    @Autowired(required = false)
    private RagRetrievalService ragRetrievalService;

    public EvidenceContextTool(ContestEvidenceItemMapper evidenceItemMapper) {
        this.evidenceItemMapper = evidenceItemMapper;
    }

    private static final int MYSQL_EVIDENCE_MIN = 3;

    @Tool("根据能力标签ID获取同标签公共参考证据（注意：这是同标签相关证据，不等于当前员工个人证据；ragReferences 仅为参考，不可独立支持通过决策）")
    public Map<String, Object> getPublicEvidenceByTagId(Long tagId) {
        Optional<String> validation = AgentToolInputValidator.validatePositive("tagId", tagId);
        if (validation.isPresent()) {
            log.warn("getPublicEvidenceByTagId invalid input: {}", validation.get());
            return Map.of("available", false, "reason", validation.get());
        }

        log.info("Agent调用: getPublicEvidenceByTagId(tagId={})", tagId);
        List<Map<String, Object>> verified = new ArrayList<>();
        List<Map<String, Object>> ragReferences = new ArrayList<>();

        try {
            LambdaQueryWrapper<ContestEvidenceItem> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ContestEvidenceItem::getTagId, tagId)
                    .eq(ContestEvidenceItem::getIsDeleted, 0)
                    .orderByDesc(ContestEvidenceItem::getCredibilityScore)
                    .last("LIMIT 10");

            List<ContestEvidenceItem> items = evidenceItemMapper.selectList(wrapper);
            for (ContestEvidenceItem item : items) {
                verified.add(toVerifiedMap(item));
            }

            if (verified.size() < MYSQL_EVIDENCE_MIN && ragRetrievalService != null) {
                try {
                    String query = "能力标签ID:" + tagId;
                    String ragContext = ragRetrievalService.retrieveContext(query, RagScenarioEnum.EVIDENCE_TRACE, MYSQL_EVIDENCE_MIN);
                    if (ragContext != null && !ragContext.isBlank()) {
                        Map<String, Object> ragRef = new HashMap<>();
                        ragRef.put("sourceType", "RAG_KNOWLEDGE");
                        ragRef.put("title", "RAG知识库补充参考");
                        ragRef.put("content", ragContext);
                        ragReferences.add(ragRef);
                    }
                } catch (Exception e) {
                    log.warn("RAG 证据补充检索失败 tagId={}: {}", tagId, e.getMessage());
                }
            }

            boolean degraded = verified.isEmpty();
            Map<String, Object> result = new HashMap<>();
            result.put("available", true);
            result.put("found", !degraded);
            result.put("verifiedEvidence", verified);
            result.put("ragReferences", ragReferences);
            result.put("degraded", degraded);
            result.put("notice", "RAG 引用仅为参考，不可作为已验证证据支持通过决策");
            return result;
        } catch (Exception e) {
            log.error("getPublicEvidenceByTagId 查询失败: tagId={}", tagId, e);
            return Map.of("available", false, "found", false, "reason", "evidence_unavailable",
                    "verifiedEvidence", List.of(), "ragReferences", List.of());
        }
    }

    @Tool("根据员工能力ID获取该员工的专属证据（targetType=EMP_ABILITY, targetRefId=empAbilityId）")
    public Map<String, Object> getEvidenceByEmpAbilityId(Long empAbilityId) {
        Optional<String> validation = AgentToolInputValidator.validatePositive("empAbilityId", empAbilityId);
        if (validation.isPresent()) {
            log.warn("getEvidenceByEmpAbilityId invalid input: {}", validation.get());
            return Map.of("available", false, "items", List.of(), "reason", validation.get());
        }

        log.info("Agent调用: getEvidenceByEmpAbilityId(empAbilityId={})", empAbilityId);
        try {
            LambdaQueryWrapper<ContestEvidenceItem> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ContestEvidenceItem::getTargetType, "EMP_ABILITY")
                    .eq(ContestEvidenceItem::getTargetRefId, empAbilityId)
                    .eq(ContestEvidenceItem::getIsDeleted, 0)
                    .orderByDesc(ContestEvidenceItem::getCredibilityScore)
                    .last("LIMIT 10");

            List<Map<String, Object>> items = evidenceItemMapper.selectList(wrapper).stream()
                    .map(this::toVerifiedMap)
                    .toList();

            return Map.of("available", true, "items", items);
        } catch (Exception e) {
            log.error("getEvidenceByEmpAbilityId 查询失败: empAbilityId={}", empAbilityId, e);
            return Map.of("available", false, "items", List.of(), "reason", "evidence_unavailable");
        }
    }

    @Tool("根据证据ID获取证据详情")
    public Map<String, Object> getEvidenceDetail(Long evidenceId) {
        Optional<String> validation = AgentToolInputValidator.validatePositive("evidenceId", evidenceId);
        if (validation.isPresent()) {
            log.warn("getEvidenceDetail invalid input: {}", validation.get());
            return Map.of("available", false, "found", false, "reason", validation.get());
        }

        log.info("Agent调用: getEvidenceDetail(evidenceId={})", evidenceId);
        try {
            ContestEvidenceItem evidence = evidenceItemMapper.selectById(evidenceId);
            if (evidence == null || evidence.getIsDeleted() == 1) {
                return Map.of("available", true, "found", false, "reason", "evidence not found");
            }
            return Map.of("available", true, "found", true, "item", toVerifiedMap(evidence));
        } catch (Exception e) {
            log.error("getEvidenceDetail 查询失败: evidenceId={}", evidenceId, e);
            return Map.of("available", false, "found", false, "reason", "evidence_unavailable");
        }
    }

    private Map<String, Object> toVerifiedMap(ContestEvidenceItem evidence) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", evidence.getId());
        map.put("evidenceCode", evidence.getEvidenceCode());
        map.put("sourceType", evidence.getSourceType());
        map.put("sourceTitle", evidence.getSourceTitle());
        map.put("sourceText", evidence.getSourceText());
        map.put("abilityName", evidence.getAbilityName());
        map.put("tagId", evidence.getTagId());
        map.put("targetType", evidence.getTargetType());
        map.put("targetRefId", evidence.getTargetRefId());
        map.put("confidenceScore", toDouble(evidence.getConfidenceScore()));
        map.put("credibilityScore", toDouble(evidence.getCredibilityScore()));
        map.put("evidenceStatus", evidence.getEvidenceStatus());
        map.put("sourceRef", buildSourceRef(evidence));
        return map;
    }

    private Double toDouble(java.math.BigDecimal value) {
        return value != null ? value.doubleValue() : null;
    }

    private String buildSourceRef(ContestEvidenceItem evidence) {
        String type = evidence.getSourceType() != null ? evidence.getSourceType() : "EVIDENCE";
        String refId = evidence.getId() != null ? String.valueOf(evidence.getId()) : "UNKNOWN";
        return "evidence:" + type + ":" + refId;
    }
}
