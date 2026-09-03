package com.example.matching.service.learning.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.ai.service.LangChain4jChatService;
import com.example.matching.ai.service.PromptTemplateService;
import com.example.matching.dto.closure.MatchDiagnosisResult;
import com.example.matching.dto.learning.AiLearningSuggestionDTO;
import com.example.matching.dto.learning.LearningPathGenerateRequest;
import com.example.matching.dto.learning.LearningPathRequestDTO;
import com.example.matching.entity.learning.AiLearningSuggestionLog;
import com.example.matching.entity.learning.LearningResource;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.mapper.learning.AiLearningSuggestionLogMapper;
import com.example.matching.mapper.learning.LearningResourceMapper;
import com.example.matching.mapper.matching.MatchingRecordMapper;
import com.example.matching.port.closure.MatchDiagnosisQueryPort;
import com.example.matching.resilience.AiServiceResilience;
import com.example.matching.common.util.AbilityNameNormalizer;
import com.example.matching.service.learning.AiLearningSuggestionService;
import com.example.matching.service.learning.AiLearningSuggestionValidator;
import com.example.matching.service.learning.LearningPathPlanService;
import com.example.matching.service.rag.RagRetrievalRequest;
import com.example.matching.service.rag.RagRetrievalResult;
import com.example.matching.service.rag.RagRetrievalService;
import com.example.matching.service.rag.RagScenarioEnum;
import com.example.matching.service.kg.KnowledgeGraphQueryService;
import com.example.matching.dto.kg.context.GraphLearningPrerequisiteContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI 学习建议服务实现
 * <p>
 * 核心原则：AI 负责"组织学习建议和解释路径"，系统负责"提供事实、资源和闭环回写"。
 * <p>
 * 防幻觉控制：
 * 1. 检索约束：只允许从 LEARNING_RESOURCE、能力标签、匹配诊断、能力证据中取上下文
 * 2. Prompt 约束：每条建议必须引用 resourceId，不能生成未提供的 URL
 * 3. 结构化输出：固定 JSON 结构
 * 4. 服务端校验：逐条校验 resourceId、abilityName、title/url
 * 5. 展示端标识：区分系统事实和 AI 建议
 * <p>
 * TODO-WF3: Replace Spring AI ChatClient path with LearningPathAgentService.preview() once output mapping is defined.
 * Agent returns {summary, steps: [{abilityTagId, abilityName, currentLevel, targetLevel, priority, title, description,
 * estimatedHours}], projectTasks, assessments} but consumer expects AiLearningSuggestionDTO.AbilitySuggestion with
 * resource-anchored LearningStep (resourceId, url, validated, suggestedBy). Agent output lacks resource references.
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiLearningSuggestionServiceImpl implements AiLearningSuggestionService {

    private final LangChain4jChatService langChain4jChatService;
    private final PromptTemplateService promptTemplateService;
    private final AiServiceResilience aiServiceResilience;
    private final ObjectMapper objectMapper;
    private final RagRetrievalService ragRetrievalService;
    private final MatchDiagnosisQueryPort matchDiagnosisQueryPort;
    private final LearningResourceMapper resourceMapper;
    private final MatchingRecordMapper matchingRecordMapper;
    private final AiLearningSuggestionValidator validator;
    private final AiLearningSuggestionLogMapper suggestionLogMapper;
    private final LearningPathPlanService learningPathPlanService;
    private final KnowledgeGraphQueryService knowledgeGraphQueryService;
    private final com.example.matching.infrastructure.llm.LlmResponseParser llmResponseParser;

    @Override
    public AiLearningSuggestionDTO.Response generateSuggestions(AiLearningSuggestionDTO.Request request) {
        log.info("开始生成AI学习建议: matchingRecordId={}, empId={}, postId={}",
                request.getMatchingRecordId(), request.getEmpId(), request.getPostId());

        // 1. 获取能力差距诊断
        List<AiLearningSuggestionDTO.Request.GapInput> gaps = resolveGaps(request);
        if (gaps.isEmpty()) {
            log.info("无能力差距，跳过AI建议生成: matchingRecordId={}", request.getMatchingRecordId());
            return buildEmptyResponse(request);
        }
        ensureLearningPathPlan(request);

        // 2. 从资源库检索匹配资源
        Map<String, List<LearningResource>> resourcesByAbility = loadResourcesByAbility(gaps);
        Map<Long, LearningResource> resourceMap = buildResourceMap(resourcesByAbility);

        // 3. 检查是否有可用资源
        boolean hasAnyResource = resourcesByAbility.values().stream()
                .anyMatch(list -> !list.isEmpty());
        if (!hasAnyResource) {
            log.info("系统资源库中无匹配资源，返回证据不足结果: matchingRecordId={}",
                    request.getMatchingRecordId());
            return buildInsufficientEvidenceResponse(request, gaps);
        }

        // 4. RAG 检索上下文
        String ragContext = retrieveRagContext(gaps);
        List<Long> ragChunkIds = retrieveRagChunkIds(gaps);

        // 4.5 Graph prerequisite context
        GraphLearningPrerequisiteContext graphPrerequisites = retrieveGraphPrerequisites(gaps);

        // 5. 构建数据模型并渲染 Prompt
        String prompt = renderPrompt(request, gaps, resourcesByAbility, ragContext, graphPrerequisites);

        // 6. 调用 AI（带熔断和重试）
        String aiResponse = callAi(prompt);

        // 7. 解析 AI 响应
        List<AiLearningSuggestionDTO.AbilitySuggestion> suggestions = parseAiResponse(aiResponse, gaps);

        // 8. 服务端校验
        Set<String> allowedAbilityNames = gaps.stream()
                .map(AiLearningSuggestionDTO.Request.GapInput::getAbilityName)
                .collect(Collectors.toSet());
        Set<Long> allowedTagIds = gaps.stream()
                .map(AiLearningSuggestionDTO.Request.GapInput::getTagId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        AiLearningSuggestionDTO.ValidationSummary validation = validator.validate(
                suggestions, allowedAbilityNames, resourceMap, allowedTagIds, graphPrerequisites);

        // 9. 构建响应
        AiLearningSuggestionDTO.Response response = new AiLearningSuggestionDTO.Response();
        response.setMatchingRecordId(request.getMatchingRecordId());
        response.setEmpId(request.getEmpId());
        response.setPostId(request.getPostId());
        response.setSuggestions(suggestions);
        response.setValidation(validation);
        response.setHasInsufficientEvidence(validation.isHasInsufficientEvidence());
        response.setRagChunkIds(ragChunkIds);

        // 10. 保存审计日志
        saveAuditLogs(response, aiResponse, ragChunkIds);

        log.info("AI学习建议生成完成: matchingRecordId={}, validatedSteps={}, filteredSteps={}",
                request.getMatchingRecordId(), validation.getValidatedSteps(), validation.getFilteredSteps());

        return response;
    }

    private void ensureLearningPathPlan(AiLearningSuggestionDTO.Request request) {
        if (request.getMatchingRecordId() == null) {
            return;
        }
        try {
            LearningPathGenerateRequest planRequest = new LearningPathGenerateRequest();
            planRequest.setMatchingRecordId(request.getMatchingRecordId());
            planRequest.setIncludeProjectTasks(true);
            planRequest.setForceRegenerate(false);
            learningPathPlanService.generateFromMatchingRecord(planRequest);
        } catch (Exception e) {
            log.warn("Failed to create learning path for matching record {}: {}",
                    request.getMatchingRecordId(), e.getMessage());
        }
    }

    @Override
    public List<AiLearningSuggestionDTO.Response> getCachedSuggestions(Long matchingRecordId) {
        List<AiLearningSuggestionLog> logs = suggestionLogMapper.selectList(
                Wrappers.<AiLearningSuggestionLog>lambdaQuery()
                        .eq(AiLearningSuggestionLog::getMatchingRecordId, matchingRecordId)
                        .eq(AiLearningSuggestionLog::getStatus, "ACTIVE")
                        .orderByDesc(AiLearningSuggestionLog::getCreatedAt));

        if (logs.isEmpty()) {
            return List.of();
        }

        // 按能力分组返回
        Map<Long, List<AiLearningSuggestionLog>> grouped = logs.stream()
                .collect(Collectors.groupingBy(AiLearningSuggestionLog::getMatchingRecordId));

        List<AiLearningSuggestionDTO.Response> results = new ArrayList<>();
        for (Map.Entry<Long, List<AiLearningSuggestionLog>> entry : grouped.entrySet()) {
            AiLearningSuggestionDTO.Response response = new AiLearningSuggestionDTO.Response();
            response.setMatchingRecordId(entry.getKey());
            List<AiLearningSuggestionDTO.AbilitySuggestion> suggestions = new ArrayList<>();
            for (AiLearningSuggestionLog logEntry : entry.getValue()) {
                if (logEntry.getValidatedJson() != null) {
                    try {
                        AiLearningSuggestionDTO.AbilitySuggestion suggestion =
                                objectMapper.readValue(logEntry.getValidatedJson(),
                                        AiLearningSuggestionDTO.AbilitySuggestion.class);
                        suggestions.add(suggestion);
                    } catch (Exception e) {
                        log.warn("解析缓存建议失败: logId={}", logEntry.getId());
                    }
                }
            }
            response.setSuggestions(suggestions);
            results.add(response);
        }

        return results;
    }

    /**
     * 解析能力差距（从请求或匹配记录诊断）
     */
    private List<AiLearningSuggestionDTO.Request.GapInput> resolveGaps(
            AiLearningSuggestionDTO.Request request) {
        if (request.getGaps() != null && !request.getGaps().isEmpty()) {
            return request.getGaps();
        }

        // 从匹配记录诊断
        if (request.getMatchingRecordId() == null) {
            return List.of();
        }

        MatchDiagnosisResult diagnosis = matchDiagnosisQueryPort.diagnoseMatchingRecord(
                request.getMatchingRecordId());
        if (diagnosis.getGaps() == null || diagnosis.getGaps().isEmpty()) {
            return List.of();
        }

        return diagnosis.getGaps().stream().map(gap -> {
            AiLearningSuggestionDTO.Request.GapInput input = new AiLearningSuggestionDTO.Request.GapInput();
            input.setTagId(gap.getTagId());
            input.setAbilityName(gap.getAbilityName());
            input.setCurrentLevel(gap.getCurrentLevel());
            input.setRequiredLevel(gap.getRequiredLevel());
            input.setWeakEvidence(gap.isWeakEvidence());
            input.setReason(gap.getReason());
            return input;
        }).collect(Collectors.toList());
    }

    /**
     * 从资源库检索匹配资源
     */
    private Map<String, List<LearningResource>> loadResourcesByAbility(
            List<AiLearningSuggestionDTO.Request.GapInput> gaps) {
        Map<String, List<LearningResource>> result = new LinkedHashMap<>();

        // 加载所有启用的资源
        LambdaQueryWrapper<LearningResource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningResource::getStatus, 1);
        List<LearningResource> allResources = resourceMapper.selectList(wrapper);

        for (AiLearningSuggestionDTO.Request.GapInput gap : gaps) {
            String abilityName = gap.getAbilityName();
            String normalized = AbilityNameNormalizer.normalize(abilityName);

            List<LearningResource> matched = allResources.stream()
                    .filter(r -> {
                        String rNorm = AbilityNameNormalizer.normalize(r.getAbilityName());
                        return rNorm.equals(normalized)
                                || rNorm.contains(normalized)
                                || normalized.contains(rNorm);
                    })
                    .sorted(Comparator.comparingInt((LearningResource r) ->
                            AbilityNameNormalizer.normalize(r.getAbilityName()).equals(normalized) ? 0 : 1)
                            .thenComparingInt(r -> calculateDifficultyFit(r, gap)))
                    .limit(5)
                    .collect(Collectors.toList());

            result.put(abilityName, matched);
        }

        return result;
    }

    /**
     * 构建资源Map
     */
    private Map<Long, LearningResource> buildResourceMap(
            Map<String, List<LearningResource>> resourcesByAbility) {
        Map<Long, LearningResource> map = new HashMap<>();
        for (List<LearningResource> resources : resourcesByAbility.values()) {
            for (LearningResource r : resources) {
                map.put(r.getId(), r);
            }
        }
        return map;
    }

    /**
     * RAG 检索上下文
     */
    private String retrieveRagContext(List<AiLearningSuggestionDTO.Request.GapInput> gaps) {
        String query = gaps.stream()
                .map(AiLearningSuggestionDTO.Request.GapInput::getAbilityName)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
        return query.isBlank() ? "" : ragRetrievalService.retrieveContext(query,
                RagScenarioEnum.LEARNING_RECOMMENDATION, 5);
    }

    /**
     * RAG 检索 chunkIds
     */
    private List<Long> retrieveRagChunkIds(List<AiLearningSuggestionDTO.Request.GapInput> gaps) {
        String query = gaps.stream()
                .map(AiLearningSuggestionDTO.Request.GapInput::getAbilityName)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
        if (query.isBlank()) {
            return List.of();
        }
        RagRetrievalResult result = ragRetrievalService.retrieve(RagRetrievalRequest.builder()
                .queryText(query)
                .scenario(RagScenarioEnum.LEARNING_RECOMMENDATION)
                .topK(5)
                .build());
        if (result == null || !result.hasHits()) {
            return List.of();
        }
        return result.getHits().stream()
                .map(RagRetrievalResult.RagHit::getChunkId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private GraphLearningPrerequisiteContext retrieveGraphPrerequisites(
            List<AiLearningSuggestionDTO.Request.GapInput> gaps) {
        List<Long> tagIds = gaps.stream()
                .map(AiLearningSuggestionDTO.Request.GapInput::getTagId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (tagIds.isEmpty()) {
            return null;
        }
        try {
            return knowledgeGraphQueryService.getLearningPrerequisiteContext(tagIds);
        } catch (Exception e) {
            log.debug("Graph prerequisite context retrieval failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 渲染 Prompt
     */
    private String renderPrompt(AiLearningSuggestionDTO.Request request,
                                List<AiLearningSuggestionDTO.Request.GapInput> gaps,
                                Map<String, List<LearningResource>> resourcesByAbility,
                                String ragContext,
                                GraphLearningPrerequisiteContext graphPrerequisites) {
        Map<String, Object> dataModel = new HashMap<>();
        dataModel.put("empId", request.getEmpId());
        dataModel.put("postId", request.getPostId());

        // 差距信息（含差距等级）
        List<Map<String, Object>> gapList = gaps.stream().map(g -> {
            Map<String, Object> m = new HashMap<>();
            m.put("abilityName", g.getAbilityName());
            m.put("currentLevel", g.getCurrentLevel());
            m.put("requiredLevel", g.getRequiredLevel());
            m.put("gapLevel", g.getRequiredLevel() != null && g.getCurrentLevel() != null
                    ? g.getRequiredLevel() - g.getCurrentLevel().intValue() : 0);
            m.put("weakEvidence", g.isWeakEvidence());
            m.put("reason", g.getReason());
            return m;
        }).collect(Collectors.toList());
        dataModel.put("gaps", gapList);

        // 资源信息
        dataModel.put("resourcesByAbility", resourcesByAbility);
        dataModel.put("resources", resourcesByAbility.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toMap(LearningResource::getId, resource -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", resource.getId());
                    item.put("title", resource.getTitle());
                    item.put("resourceType", resource.getResourceType());
                    item.put("abilityName", resource.getAbilityName());
                    return item;
                }, (first, ignored) -> first, LinkedHashMap::new))
                .values().stream().toList());

        // RAG 上下文
        if (ragContext != null && !ragContext.isBlank()) {
            dataModel.put("ragContext", ragContext);
        }

        // Graph prerequisite context
        if (graphPrerequisites != null && graphPrerequisites.prerequisites() != null
                && !graphPrerequisites.prerequisites().isEmpty()) {
            dataModel.put("graphPrerequisites", graphPrerequisites);
        }

        return promptTemplateService.render("learning-suggestion-prompt", dataModel);
    }

    /**
     * 调用 AI（带熔断和重试）
     */
    private String callAi(String prompt) {
        return langChain4jChatService.chat("learning-suggestion", prompt,
                () -> "{\"suggestions\":[]}");
    }

    /**
     * 解析 AI 响应
     */
    private List<AiLearningSuggestionDTO.AbilitySuggestion> parseAiResponse(
            String aiResponse, List<AiLearningSuggestionDTO.Request.GapInput> gaps) {
        if (aiResponse == null || aiResponse.isBlank()) {
            return buildDefaultSuggestions(gaps);
        }

        try {
            // 提取 JSON
            String json = llmResponseParser.extractJson(aiResponse);
            Map<String, Object> root = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {});

            Object suggestionsObj = root.get("suggestions");
            if (suggestionsObj instanceof List<?> list) {
                return objectMapper.convertValue(list,
                        new TypeReference<List<AiLearningSuggestionDTO.AbilitySuggestion>>() {});
            }
        } catch (Exception e) {
            log.warn("解析AI学习建议响应失败，使用默认建议。error={}", e.getMessage());
        }

        return buildDefaultSuggestions(gaps);
    }

    /**
     * 构建默认建议（AI 不可用时的降级）
     */
    private List<AiLearningSuggestionDTO.AbilitySuggestion> buildDefaultSuggestions(
            List<AiLearningSuggestionDTO.Request.GapInput> gaps) {
        return gaps.stream().map(gap -> {
            AiLearningSuggestionDTO.AbilitySuggestion suggestion =
                    new AiLearningSuggestionDTO.AbilitySuggestion();
            suggestion.setAbilityName(gap.getAbilityName());
            suggestion.setTagId(gap.getTagId());
            suggestion.setCurrentLevel(gap.getCurrentLevel());
            suggestion.setRequiredLevel(gap.getRequiredLevel());
            suggestion.setRiskLevel(calculateRiskLevel(gap));
            suggestion.setReason(gap.getReason());
            suggestion.setInsufficientEvidence(true);
            suggestion.setSuggestionSource("FALLBACK");
            suggestion.setSteps(List.of());
            return suggestion;
        }).collect(Collectors.toList());
    }

    /**
     * 构建空响应
     */
    private AiLearningSuggestionDTO.Response buildEmptyResponse(
            AiLearningSuggestionDTO.Request request) {
        AiLearningSuggestionDTO.Response response = new AiLearningSuggestionDTO.Response();
        response.setMatchingRecordId(request.getMatchingRecordId());
        response.setEmpId(request.getEmpId());
        response.setPostId(request.getPostId());
        response.setSuggestions(List.of());
        AiLearningSuggestionDTO.ValidationSummary validation =
                new AiLearningSuggestionDTO.ValidationSummary();
        validation.setTotalSteps(0);
        validation.setValidatedSteps(0);
        validation.setFilteredSteps(0);
        validation.setHasInsufficientEvidence(false);
        response.setValidation(validation);
        response.setHasInsufficientEvidence(false);
        return response;
    }

    /**
     * 构建证据不足响应
     */
    private AiLearningSuggestionDTO.Response buildInsufficientEvidenceResponse(
            AiLearningSuggestionDTO.Request request,
            List<AiLearningSuggestionDTO.Request.GapInput> gaps) {
        AiLearningSuggestionDTO.Response response = new AiLearningSuggestionDTO.Response();
        response.setMatchingRecordId(request.getMatchingRecordId());
        response.setEmpId(request.getEmpId());
        response.setPostId(request.getPostId());
        response.setSuggestions(gaps.stream().map(gap -> {
            AiLearningSuggestionDTO.AbilitySuggestion s =
                    new AiLearningSuggestionDTO.AbilitySuggestion();
            s.setAbilityName(gap.getAbilityName());
            s.setTagId(gap.getTagId());
            s.setCurrentLevel(gap.getCurrentLevel());
            s.setRequiredLevel(gap.getRequiredLevel());
            s.setRiskLevel(calculateRiskLevel(gap));
            s.setReason(gap.getReason());
            s.setInsufficientEvidence(true);
            s.setSuggestionSource("NO_RESOURCE");
            s.setSteps(List.of());
            return s;
        }).collect(Collectors.toList()));
        AiLearningSuggestionDTO.ValidationSummary validation =
                new AiLearningSuggestionDTO.ValidationSummary();
        validation.setTotalSteps(0);
        validation.setValidatedSteps(0);
        validation.setFilteredSteps(0);
        validation.setHasInsufficientEvidence(true);
        validation.setDetails(List.of("系统资源库中无匹配资源"));
        response.setValidation(validation);
        response.setHasInsufficientEvidence(true);
        return response;
    }

    /**
     * 保存审计日志
     */
    private void saveAuditLogs(AiLearningSuggestionDTO.Response response,
                               String aiResponseJson,
                               List<Long> ragChunkIds) {
        try {
            String ragChunkIdsJson = objectMapper.writeValueAsString(ragChunkIds);
            for (AiLearningSuggestionDTO.AbilitySuggestion suggestion : response.getSuggestions()) {
                AiLearningSuggestionLog logEntry = new AiLearningSuggestionLog();
                logEntry.setMatchingRecordId(response.getMatchingRecordId());
                logEntry.setEmpId(response.getEmpId());
                logEntry.setPostId(response.getPostId());
                logEntry.setAbilityName(suggestion.getAbilityName());
                logEntry.setTagId(suggestion.getTagId());
                logEntry.setAiResponseJson(aiResponseJson);
                logEntry.setValidatedJson(objectMapper.writeValueAsString(suggestion));
                logEntry.setFilteredCount((int) suggestion.getSteps().stream()
                        .filter(s -> !s.isValidated()).count());
                logEntry.setRagChunkIds(ragChunkIdsJson);
                logEntry.setSuggestionSource(suggestion.getSuggestionSource());
                logEntry.setStatus("ACTIVE");
                suggestionLogMapper.insert(logEntry);
            }
        } catch (Exception e) {
            log.warn("保存AI学习建议审计日志失败: {}", e.getMessage());
        }
    }

    /**
     * 计算难度适配度
     */
    private int calculateDifficultyFit(LearningResource r,
                                       AiLearningSuggestionDTO.Request.GapInput gap) {
        int resourceLevel = r.getDifficultyLevel() != null ? r.getDifficultyLevel() : 3;
        int currentLevel = gap.getCurrentLevel() != null ? gap.getCurrentLevel().intValue() : 1;
        int targetLevel = gap.getRequiredLevel() != null ? gap.getRequiredLevel() : 3;

        if (resourceLevel >= currentLevel && resourceLevel <= targetLevel) {
            return 0;
        }
        if (resourceLevel == currentLevel - 1) {
            return 1;
        }
        if (resourceLevel == targetLevel + 1) {
            return 2;
        }
        return Math.abs(resourceLevel - (currentLevel + targetLevel) / 2) + 3;
    }

    /**
     * 计算风险等级
     */
    private String calculateRiskLevel(AiLearningSuggestionDTO.Request.GapInput gap) {
        int gapLevel = gap.getRequiredLevel() != null && gap.getCurrentLevel() != null
                ? gap.getRequiredLevel() - gap.getCurrentLevel().intValue() : 0;
        if (gapLevel >= 2 || gap.isWeakEvidence()) {
            return "HIGH";
        }
        if (gapLevel >= 1) {
            return "MEDIUM";
        }
        return "LOW";
    }
}
