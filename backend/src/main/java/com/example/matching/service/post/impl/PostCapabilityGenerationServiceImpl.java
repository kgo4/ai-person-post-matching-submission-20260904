package com.example.matching.service.post.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.ai.service.VectorEmbeddingService;
import com.example.matching.dto.post.AbilityExtractResultDTO;
import com.example.matching.dto.post.JdAbilityItemDTO;
import com.example.matching.dto.post.PostAnalysisResultDTO;
import com.example.matching.dto.post.PostCleaningResult;
import com.example.matching.dto.post.PostRawInput;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.agent.dto.post.PostAbilityClaim;
import com.example.matching.agent.dto.post.PostAbilityExtractRequest;
import com.example.matching.agent.dto.post.PostAbilityExtractionResult;
import com.example.matching.agent.service.PostAbilityAgentService;
import com.example.matching.event.PostAbilityTagGovernanceRequestedEvent;
import com.example.matching.service.agent.AgentBusinessApplyService;
import com.example.matching.service.post.PostCapabilityGenerationService;
import com.example.matching.service.post.PostDataCleaningService;
import com.example.matching.service.rag.RagQueryLogService;
import com.example.matching.service.system.AbilityTagService;
import com.example.matching.service.system.AbilityTagHierarchy;
import com.example.matching.entity.rag.RagQueryLog;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 统一岗位能力生成服务实现
 * <p>
 * 从JdAbilityExtractServiceImpl中提取的核心逻辑，供JD分析、Excel导入、新兴岗位定义共用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostCapabilityGenerationServiceImpl implements PostCapabilityGenerationService {

    private final AgentBusinessApplyService agentBusinessApplyService;
    private final ObjectMapper objectMapper;
    private final AbilityTagService abilityTagService;
    private final PostDataCleaningService postDataCleaningService;
    private final PostCapabilityExtractionSupport extractionSupport;
    private final ApplicationEventPublisher eventPublisher;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.example.matching.service.post.PostAbilityGroundingRecordService groundingRecordService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private RagQueryLogService ragQueryLogService;

    /** 向量相似度阈值：高于此值认为精确匹配 */
    private static final double SIMILARITY_THRESHOLD_HIGH = 0.85;
    /** 向量相似度阈值：高于此值认为疑似相似 */
    private static final double SIMILARITY_THRESHOLD_LOW = 0.6;

    @Override
    public List<JdAbilityItemDTO> analyzePostText(String postName, String postText) {
        // ========== 0. 系统内部自动清洗去噪（用户无感知） ==========
        PostRawInput rawInput = PostRawInput.builder()
                .postName(postName)
                .rawText(postText)
                .sourceType("JD_TEXT")
                .build();
        PostCleaningResult cleaningResult = postDataCleaningService.cleanAndDetect(rawInput);

        // 如果被阻断（强重复/质量过低），抛出异常
        if (cleaningResult.isBlocked()) {
            log.warn("岗位数据被清洗服务阻断: postName={}, reason={}", postName, cleaningResult.getBlockReason());
            throw new com.example.matching.common.exception.BusinessException(400, cleaningResult.getBlockReason());
        }

        // 使用清洗后的文本继续后续流程
        String effectivePostName = cleaningResult.getCleanedPostName() != null ? cleaningResult.getCleanedPostName() : postName;
        String effectiveText = cleaningResult.getCleanedText() != null ? cleaningResult.getCleanedText() : postText;

        // 1. 查询系统已有能力标签
        List<AbilityTag> existingTags = assessableTags();

        // 2. RAG检索上下文（已废弃：直接使用 MyBatis-Plus Mapper 查询结构化表）
        String ragContext = "";

        // 3. 构建富化sourceText（含RAG上下文和已有标签参考）
        String enrichedSourceText = extractionSupport.buildEnrichedSourceText(effectivePostName, effectiveText, existingTags, ragContext);

        // 4. 通过Agent提取岗位能力（替代直接ChatClient调用）
        long startTime = System.currentTimeMillis();
        PostAbilityExtractionResult extractionResult = extractionSupport.extractAbilitiesViaAgent(
                effectivePostName, enrichedSourceText, effectiveText, "JD_IMPORT", null, List.of());
        long latencyMs = System.currentTimeMillis() - startTime;

        // 4.1 标记清洗记录已进入Agent
        try {
            String agentInputSnapshot = objectMapper.writeValueAsString(Map.of(
                    "postName", effectivePostName,
                    "cleanedText", effectiveText,
                    "responsibilities", cleaningResult.getResponsibilities() != null ? cleaningResult.getResponsibilities() : List.of(),
                    "requirements", cleaningResult.getRequirements() != null ? cleaningResult.getRequirements() : List.of(),
                    "qualityScore", cleaningResult.getQualityScore() != null ? cleaningResult.getQualityScore() : BigDecimal.ZERO,
                    "sourceRefs", List.of("post_cleaning_record:" + cleaningResult.getCleaningRecordId())
            ));
            postDataCleaningService.markEnteredAgent(cleaningResult.getCleaningRecordId(), agentInputSnapshot);
        } catch (Exception e) {
            log.warn("标记清洗记录进入Agent失败: {}", e.getMessage());
        }

        // 5. 将Agent返回的claims转换为JdAbilityItemDTO（含标签匹配）
        List<JdAbilityItemDTO> items = extractionSupport.convertClaimsToItems(extractionResult, existingTags);

        return items;
    }

    @Override
    public List<JdAbilityItemDTO> analyzePostText(String postName, String postText,
                                                   String harnessSourceType, Long sourceRefId,
                                                   List<String> harnessSourceRefs) {
        return analyzePostTextWithResult(postName, postText, harnessSourceType, sourceRefId,
                harnessSourceRefs).items();
    }

    @Override
    public PostAbilityAnalysisResult analyzePostTextWithResult(String postName, String postText,
                                                               String harnessSourceType, Long sourceRefId,
                                                               List<String> harnessSourceRefs) {
        // 1. 查询系统已有能力标签
        List<AbilityTag> existingTags = assessableTags();

        // 2. RAG检索上下文（已废弃：直接使用 MyBatis-Plus Mapper 查询结构化表）
        String ragContext = "";

        // 3. 构建富化sourceText
        String enrichedSourceText = extractionSupport.buildEnrichedSourceText(postName, postText, existingTags, ragContext);

        // 4. 通过Agent提取岗位能力
        long startTime = System.currentTimeMillis();
        PostAbilityExtractionResult extractionResult = extractionSupport.extractAbilitiesViaAgent(
                postName, enrichedSourceText, postText, harnessSourceType, sourceRefId, harnessSourceRefs);
        long latencyMs = System.currentTimeMillis() - startTime;

        // 5. 将Agent返回的claims转换为JdAbilityItemDTO
        List<JdAbilityItemDTO> items = extractionSupport.convertClaimsToItems(extractionResult, existingTags);

        String summary = extractionResult != null ? extractionResult.getSummary() : null;
        return new PostAbilityAnalysisResult(items, summary);
    }

    @Override
    public List<JdAbilityItemDTO> analyzeMarketJdText(String postName, String cleanedPostText,
                                                       Long sourceRefId, List<String> sourceRefs) {
        // 1. 查询系统已有能力标签
        List<AbilityTag> existingTags = assessableTags();

        // 2. 构建富化sourceText（本方法接收的是已清洗文本，不再调用 cleanAndDetect）
        String enrichedSourceText = extractionSupport.buildEnrichedSourceText(postName, cleanedPostText, existingTags, "");

        // 3. 通过Agent提取岗位能力（sourceType 固定 MARKET_JD）
        PostAbilityExtractionResult extractionResult = extractionSupport.extractAbilitiesViaAgent(
                postName, enrichedSourceText, cleanedPostText, "MARKET_JD", sourceRefId, sourceRefs);

        // 4. 转换为JdAbilityItemDTO（含证据端到端保留与正式标签匹配）
        return extractionSupport.convertClaimsToItems(extractionResult, existingTags);
    }

    @Override
    public AbilityExtractResultDTO analyzePostTextDualTrack(String postName, String postText, String sourceType, Long sourceRefId) {
        // 1. 调用 analyzePostText 获取原始结果
        List<JdAbilityItemDTO> items = analyzePostText(postName, postText);

        // 2. 分轨：匹配到正式标签 vs 未匹配的新能力
        AbilityExtractResultDTO result = new AbilityExtractResultDTO();
        List<AbilityExtractResultDTO.MappedAbility> mapped = new ArrayList<>();
        List<AbilityExtractResultDTO.CandidateAbility> candidates = new ArrayList<>();

        for (JdAbilityItemDTO item : items) {
            if ("MATCHED".equals(item.getMatchStatus()) || "SIMILAR".equals(item.getMatchStatus())) {
                AbilityExtractResultDTO.MappedAbility m = new AbilityExtractResultDTO.MappedAbility();
                m.setTagId(item.getMatchedTagId());
                m.setTagName(item.getMatchedTagName());
                m.setMinRequiredLevel(item.getMinRequiredLevel());
                m.setWeight(item.getWeight());
                m.setIsCore(item.getIsCore());
                m.setIsRequired(item.getIsRequired());
                m.setMatchSource("MATCHED".equals(item.getMatchStatus()) ? "exact" : "similar");
                m.setReasoning(item.getReasoning());
                mapped.add(m);
            } else {
                AbilityExtractResultDTO.CandidateAbility c = new AbilityExtractResultDTO.CandidateAbility();
                c.setCandidateName(item.getSuggestedName());
                c.setReason(item.getReasoning());
                c.setSuggestedCategory(item.getTagCategory());
                candidates.add(c);
            }
        }

        result.setMappedAbilities(mapped);
        result.setCandidateAbilities(candidates);
        return result;
    }

    @Override
    public PostAnalysisResultDTO analyzePostTextFull(String postName, String postText) {
        // 1. 查询系统已有能力标签
        List<AbilityTag> existingTags = assessableTags();

        // 2. RAG检索上下文（已废弃：直接使用 MyBatis-Plus Mapper 查询结构化表）
        String ragContext = "";

        // 3. 构建富化sourceText
        String enrichedSourceText = extractionSupport.buildEnrichedSourceText(postName, postText, existingTags, ragContext);

        // 4. 通过Agent提取岗位能力（替代直接ChatClient调用）
        PostAbilityExtractionResult extractionResult = extractionSupport.extractAbilitiesViaAgent(
                postName, enrichedSourceText, postText, "JD_IMPORT", null, List.of());

        // 5. 从Agent原始输出解析结构化字段（jobSummary等）
        PostAnalysisResultDTO result = new PostAnalysisResultDTO();
        if (extractionResult.getRawModelOutput() != null) {
            try {
                Map<String, Object> parsed = extractionSupport.parseAiResponse(extractionResult.getRawModelOutput());
                result.setJobSummary((String) parsed.get("jobSummary"));
                result.setCoreResponsibilities(extractionSupport.toStringList(parsed.get("coreResponsibilities")));
                result.setRequiredSkills(extractionSupport.toStringList(parsed.get("requiredSkills")));
                result.setBonusSkills(extractionSupport.toStringList(parsed.get("bonusSkills")));
                result.setIndustryScenarios(extractionSupport.toStringList(parsed.get("industryScenarios")));
            } catch (Exception e) {
                log.warn("从Agent原始输出解析结构化字段失败: {}", e.getMessage());
            }
        }

        // 6. 将Agent返回的claims转换为JdAbilityItemDTO
        List<JdAbilityItemDTO> items = extractionSupport.convertClaimsToItems(extractionResult, existingTags);

        result.setAbilities(items);
        return result;
    }


    @Override
    public void matchWithExistingTags(JdAbilityItemDTO item, List<AbilityTag> existingTags) {
        extractionSupport.matchWithExistingTags(item, existingTags);
    }

    private List<AbilityTag> assessableTags() {
        return abilityTagService.list(Wrappers.<AbilityTag>lambdaQuery()
                        .eq(AbilityTag::getStatus, 1)
                        .eq(AbilityTag::getTagLevel, AbilityTagHierarchy.ASSESSABLE_LEVEL));
    }

    @Override
    @Transactional
    public void applyAbilityItemsToPost(Long postId, List<JdAbilityItemDTO> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        // 将有原文证据的能力转换为岗位画像声明，标签候选与画像写入彼此独立。
        PostAbilityExtractionResult extractionResult = extractionSupport.convertItemsToExtractionResult(postId, items, null, null);
        log.info("岗位能力转换统计: postId={}, formal={}, pending={}, rejected={}",
                postId, extractionResult.getFormalCount(), extractionResult.getPendingCount(),
                extractionResult.getRejectedCount());
        appendGroundingAudit(extractionResult);
        if (extractionResult.getPendingCount() > 0) {
            log.info("未匹配能力将于岗位画像提交后独立进入标签候选池: postId={}, pending={}",
                    postId, extractionResult.getPendingCount());
        }
        if (extractionResult.getClaims().isEmpty()) {
            log.info("没有通过可信门禁的岗位能力项，跳过正式岗位模型写入: postId={}, pending={}, rejected={}",
                    postId, extractionResult.getPendingCount(), extractionResult.getRejectedCount());
            return;
        }

        AgentBusinessApplyService.PostAbilityApplyResult applyResult =
                agentBusinessApplyService.applyPostAbilities(extractionResult);
        if (applyResult == null) {
            log.warn("岗位能力应用服务未返回统计结果: postId={}", postId);
            return;
        }
        log.info("能力项已应用到岗位能力模型: postId={}, total={}, pass={}, review={}, block={}, error={}",
                postId, applyResult.getTotalClaims(), applyResult.getPassCount(),
                applyResult.getReviewCount(), applyResult.getBlockCount(), applyResult.getErrorCount());
        publishTagCandidateRequests(extractionResult);
    }

    @Override
    @Transactional
    public void applyAbilityItemsToPost(Long postId, List<JdAbilityItemDTO> items, Map<String, AbilityTag> tagNameMap) {
        if (items == null || items.isEmpty()) {
            return;
        }

        // 将有原文证据的能力转换为岗位画像声明；不会在导入时创建正式标签。
        PostAbilityExtractionResult extractionResult = extractionSupport.convertItemsToExtractionResult(postId, items, tagNameMap, null);
        log.info("岗位能力转换统计（预加载模式）: postId={}, formal={}, pending={}, rejected={}",
                postId, extractionResult.getFormalCount(), extractionResult.getPendingCount(),
                extractionResult.getRejectedCount());
        appendGroundingAudit(extractionResult);
        if (extractionResult.getPendingCount() > 0) {
            log.info("未匹配能力将于岗位画像提交后独立进入标签候选池: postId={}, pending={}",
                    postId, extractionResult.getPendingCount());
        }
        if (extractionResult.getClaims().isEmpty()) {
            log.info("没有通过可信门禁的岗位能力项，跳过正式岗位模型写入: postId={}, pending={}, rejected={}",
                    postId, extractionResult.getPendingCount(), extractionResult.getRejectedCount());
            return;
        }

        AgentBusinessApplyService.PostAbilityApplyResult applyResult =
                agentBusinessApplyService.applyPostAbilities(extractionResult);
        if (applyResult == null) {
            log.warn("岗位能力应用服务未返回统计结果（预加载模式）: postId={}", postId);
            return;
        }
        log.info("能力项已应用到岗位能力模型（预加载模式）: postId={}, total={}, pass={}, review={}, block={}, error={}",
                postId, applyResult.getTotalClaims(), applyResult.getPassCount(),
                applyResult.getReviewCount(), applyResult.getBlockCount(), applyResult.getErrorCount());
        publishTagCandidateRequests(extractionResult);
    }

    private void publishTagCandidateRequests(PostAbilityExtractionResult extractionResult) {
        java.util.LinkedHashSet<PostAbilityClaim> claims = new java.util.LinkedHashSet<>();
        if (extractionResult.getClaims() != null) claims.addAll(extractionResult.getClaims());
        if (extractionResult.getDeferredClaims() != null) claims.addAll(extractionResult.getDeferredClaims());
        if (claims.isEmpty()) return;
        for (PostAbilityClaim claim : claims) {
            if (!claim.isValid()) {
                continue;
            }
            eventPublisher.publishEvent(new PostAbilityTagGovernanceRequestedEvent(
                    claim.getPostId(), claim.getAbilityName(), "TECHNICAL", claim.getSourceType(),
                    claim.getSourceRefId(), claim.getEvidenceText(), claim.getExtractReason()));
        }
    }

    private void appendGroundingAudit(PostAbilityExtractionResult extractionResult) {
        if (groundingRecordService == null) return;
        java.util.Set<PostAbilityClaim> deferred = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        java.util.Set<PostAbilityClaim> rejected = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        if (extractionResult.getDeferredClaims() != null) deferred.addAll(extractionResult.getDeferredClaims());
        if (extractionResult.getRejectedClaims() != null) rejected.addAll(extractionResult.getRejectedClaims());
        groundingRecordService.append(extractionResult.getClaims() == null ? List.of() : extractionResult.getClaims().stream()
                .filter(claim -> !deferred.contains(claim) && !rejected.contains(claim)).toList(), "SUBMITTED");
        groundingRecordService.append(extractionResult.getDeferredClaims(), "DEFERRED");
        groundingRecordService.append(extractionResult.getRejectedClaims(), "REJECTED");
    }

    /**
     * 将JdAbilityItemDTO列表转换为PostAbilityExtractionResult（供AgentBusinessApplyService使用）
     *
     * @param postId      岗位ID
     * @param items       能力项列表
     * @param tagNameMap  预加载的标签Map（可选，用于避免重复创建标签）
     * @param postText    岗位文本（用于标签准入上下文）
     * @return 提取结果
     */

}
