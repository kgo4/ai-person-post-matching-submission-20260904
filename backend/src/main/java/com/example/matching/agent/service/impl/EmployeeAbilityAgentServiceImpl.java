package com.example.matching.agent.service.impl;

import com.example.matching.agent.config.LangChain4jAgentProperties;
import com.example.matching.agent.dto.AgentContextPackage;
import com.example.matching.agent.dto.EmployeeAbilityAgentRequest;
import com.example.matching.agent.dto.EmployeeAbilityAgentResult;
import com.example.matching.agent.dto.person.PersonAbilityClaim;
import com.example.matching.agent.dto.person.PersonAbilityExtractRequest;
import com.example.matching.agent.dto.person.PersonAbilityExtractionResult;
import com.example.matching.agent.dto.person.EvidenceValidationResult;
import com.example.matching.agent.lc4j.EmployeeAbilityAiService;
import com.example.matching.agent.service.AgentContextPackageService;
import com.example.matching.agent.service.EmployeeAbilityAgentService;
import com.example.matching.ai.validation.DeterministicAiFallbacks;
import com.example.matching.ai.validation.EmployeeAbilityExtractionValidator;
import com.example.matching.application.agent.EmployeeAbilitySnapshot;
import com.example.matching.common.exception.AiServiceException;
import com.example.matching.common.enums.AbilitySourceType;
import com.example.matching.utils.ScoreUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 员工能力Agent服务实现
 * <p>
 * 职责：
 * - 解释员工能力画像
 * - 说明能力来源
 * - 发现缺失证据
 * - 给出补充建议
 * <p>
 * 注意：只读不写，不直接修改EmpAbility
 *
 * @author system
 */
@Slf4j
@Service
public class EmployeeAbilityAgentServiceImpl extends AbstractAgentService implements EmployeeAbilityAgentService {

    /** EOF 型结构化响应的最小恢复分段，避免无限二分。 */
    private static final int MIN_RECOVERY_CHUNK_CHARS = 512;

    private final LangChain4jAgentProperties properties;
    private final AgentContextPackageService contextPackageService;
    private final ObjectMapper objectMapper;
    private final AgentMemoryContextService memoryContextService;
    private final AgentMemoryRuleEnforcer memoryRuleEnforcer;
    private final EmployeeAbilityExtractionValidator extractionValidator;

    private final EmployeeAbilityAiService employeeAbilityAiService;

    /** Task10：可选注入的结构化指标（测试环境无 MeterRegistry 时为空） */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.example.matching.agent.config.ExtractionMetrics extractionMetrics;

    /** 服务端标签确定性兜底匹配：AI 未给出正式标签时补全 matchedTagId，减少"无标签 → REVIEW" */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.beans.factory.ObjectProvider<com.example.matching.service.system.support.AbilityTagMatchService> abilityTagMatchProvider;

    public EmployeeAbilityAgentServiceImpl(
            LangChain4jAgentProperties properties,
            AgentContextPackageService contextPackageService,
            ObjectMapper objectMapper,
            AgentRunConfidencePolicy confidencePolicy,
            AgentMemoryContextService memoryContextService,
            AgentMemoryRuleEnforcer memoryRuleEnforcer,
            EmployeeAbilityExtractionValidator extractionValidator) {
        this(properties, contextPackageService, objectMapper, confidencePolicy, memoryContextService,
                memoryRuleEnforcer, extractionValidator,
                new org.springframework.beans.factory.support.StaticListableBeanFactory()
                        .getBeanProvider(EmployeeAbilityAiService.class));
    }

    @Autowired
    public EmployeeAbilityAgentServiceImpl(
            LangChain4jAgentProperties properties,
            AgentContextPackageService contextPackageService,
            ObjectMapper objectMapper,
            AgentRunConfidencePolicy confidencePolicy,
            AgentMemoryContextService memoryContextService,
            AgentMemoryRuleEnforcer memoryRuleEnforcer,
            EmployeeAbilityExtractionValidator extractionValidator,
            ObjectProvider<EmployeeAbilityAiService> aiServiceProvider) {
        super(confidencePolicy);
        this.properties = properties;
        this.contextPackageService = contextPackageService;
        this.objectMapper = objectMapper;
        this.memoryContextService = memoryContextService;
        this.memoryRuleEnforcer = memoryRuleEnforcer;
        this.extractionValidator = extractionValidator;
        this.employeeAbilityAiService = aiServiceProvider.getIfAvailable();
    }

    @Override
    public EmployeeAbilityAgentResult analyze(EmployeeAbilityAgentRequest request) {
        AgentContextPackage context = contextPackageService.buildForEmployee(request.getEmpId());

        if (!properties.isEnabled() || employeeAbilityAiService == null) {
            log.info("LangChain4j未启用或EmployeeAbilityAiService未注册，使用降级方案");
            return fallbackEmployeeAnalysis(context);
        }

        return runWithFallback(
                () -> {
                    String contextJson = objectMapper.writeValueAsString(context);
                    log.info("员工能力Agent(LangChain4j)分析: empId={}", request.getEmpId());
                    EmployeeAbilityAgentResult result = com.example.matching.agent.config.AgentToolProvider
                            .withScope(() -> employeeAbilityAiService.analyze(contextJson));
                    if (result == null) {
                        throw new IllegalStateException("Employee ability analysis returned no structured result");
                    }
                    String serializedResult = objectMapper.writeValueAsString(result);
                    return finalizeRun(result, context.getSourceRefs(), false, serializedResult);
                },
                e -> {
                    log.error("员工能力Agent LangChain4j调用失败，使用降级方案", e);
                    return fallbackEmployeeAnalysis(context);
                });
    }

    /**
     * 降级方案：基于上下文数据生成基础分析
     */
    private EmployeeAbilityAgentResult fallbackEmployeeAnalysis(AgentContextPackage context) {
        EmployeeAbilityAgentResult result = new EmployeeAbilityAgentResult();
        result.setFallbackUsed(true);

        if (context == null) {
            result.setSummary("无法获取员工上下文数据");
            result.setStrongAbilities(Collections.emptyList());
            result.setWeakAbilities(Collections.emptyList());
            result.setMissingEvidence(Collections.emptyList());
            result.setRiskSignals(Collections.emptyList());
            result.setSuggestions(Collections.emptyList());
            return result;
        }

        StringBuilder summary = new StringBuilder();
        summary.append("员工能力画像分析");

        List<EmployeeAbilitySnapshot> abilities = context.getEmployeeAbilities();
        if (abilities != null && !abilities.isEmpty()) {
            summary.append("，共").append(abilities.size()).append("项能力");
        }

        result.setSummary(summary.toString());

        if (abilities != null) {
            result.setStrongAbilities(new ArrayList<>());
            result.setWeakAbilities(new ArrayList<>());

            for (EmployeeAbilitySnapshot ability : abilities) {
                EmployeeAbilityAgentResult.AbilityItem item = new EmployeeAbilityAgentResult.AbilityItem();
                item.setAbilityTagId(ability.abilityTagId());
                item.setAbilityName(ability.abilityName());
                item.setLevel(ability.currentLevel());
                item.setSource(ability.source());
                item.setCredibility(ability.credibility() != null ? ability.credibility().intValue() : null);

                Integer currentLevel = ability.currentLevel();
                if (currentLevel != null && currentLevel >= 4) {
                    result.getStrongAbilities().add(item);
                } else if (currentLevel != null && currentLevel <= 2) {
                    result.getWeakAbilities().add(item);
                }
            }
        }

        result.setMissingEvidence(new ArrayList<>());
        if (abilities != null) {
            for (EmployeeAbilitySnapshot ability : abilities) {
                if (ability.evidenceCount() == 0) {
                    EmployeeAbilityAgentResult.MissingEvidenceItem item = new EmployeeAbilityAgentResult.MissingEvidenceItem();
                    item.setAbilityTagId(ability.abilityTagId());
                    item.setAbilityName(ability.abilityName());
                    item.setReason("无支撑证据");
                    item.setSuggestion("建议补充相关项目经历或培训记录");
                    result.getMissingEvidence().add(item);
                }
            }
        }

        result.setRiskSignals(new ArrayList<>());
        result.setSuggestions(new ArrayList<>());
        if (!result.getMissingEvidence().isEmpty()) {
            result.getSuggestions().add("建议补充" + result.getMissingEvidence().size() + "项缺失证据");
        }
        if (!result.getWeakAbilities().isEmpty()) {
            result.getSuggestions().add("建议提升" + result.getWeakAbilities().size() + "项薄弱能力");
        }

        return finalizeRun(result, context.getSourceRefs(), true, null);
    }

    @Override
    public PersonAbilityExtractionResult extractAbilities(PersonAbilityExtractRequest request) {
        request.setSourceType(AbilitySourceType.canonicalize(request.getSourceType()));
        log.info("提取员工能力: empId={}, sourceType={}, sourceRefId={}", request.getEmpId(), request.getSourceType(), request.getSourceRefId());

        PersonAbilityExtractionResult result = new PersonAbilityExtractionResult();
        result.setEmpId(request.getEmpId());
        result.setSourceType(request.getSourceType());
        result.setSourceRefId(request.getSourceRefId());

        // Task4：无来源引用（sourceRefId 或请求受控引用）时直接进入 REVIEW/RETRY，
        // 不得用模型自造 ID 补齐
        if (request.getSourceRefId() == null
                && (request.getSourceRefs() == null || request.getSourceRefs().isEmpty())) {
            log.warn("[AI_OUTPUT_INVALID] 员工能力提取缺少来源引用，进入 REVIEW/RETRY: empId={}",
                    request.getEmpId());
            result.setFallbackUsed(true);
            result.setClaims(new ArrayList<>());
            result.setSummary("提取失败：缺少来源引用（sourceRefId/sourceRefs），进入 REVIEW/RETRY");
            result.setDurationMs(0L);
            return result;
        }

        String rawContextText = buildRawContextText(request);
        AgentMemoryContextService.ContextRules contextRules = memoryContextService.resolveRules(
                rawContextText, AgentMemoryContextService.SCOPE_EMPLOYEE);

        long startTime = System.currentTimeMillis();

        try {
            if (!properties.isEnabled() || employeeAbilityAiService == null) {
                log.info("LangChain4j未启用或EmployeeAbilityAiService未注册，使用确定性降级方案");
                result.setFallbackUsed(true);
                result.setClaims(DeterministicAiFallbacks.employeeAbilityClaims(request).getClaims());
                result.setSummary("确定性降级方案：基于现有数据生成能力声明");
            } else {
                try {
                    List<com.example.matching.agent.service.ExtractionChunker.Chunk> chunks =
                            com.example.matching.agent.service.ExtractionChunker.chunk(
                                    request.getSourceText(), com.example.matching.agent.service.ExtractionChunker.DEFAULT_MAX_CHARS);
                    if (chunks.size() > 1) {
                        // Task8：长文本分块提取（逐块调用、合并、按规范化名称去重；失败块标记 RETRY/REVIEW）
                        result.setClaims(extractChunkedClaims(request, contextRules, chunks, result, startTime));
                        result.setFallbackUsed(false);
                        result.setSummary(result.getSummary() != null ? result.getSummary()
                                : buildExtractionSummary(result.getClaims()));
                        return result;
                    }
                    String contextJson = buildExtractContextJsonWithMemory(request, contextRules);
                    PersonAbilityExtractionResult aiResult = com.example.matching.agent.config.AgentToolProvider
                            .withScope(() -> employeeAbilityAiService.extractAbilities(contextJson));
                    if (aiResult == null) {
                        throw new IllegalStateException("Employee ability extraction returned no structured result");
                    }
                    // rawModelOutput: 序列化后的结构化提取结果（parsed structured result，非 raw LLM completion）
                    String serializedResult = objectMapper.writeValueAsString(aiResult);
                    result.setRawModelOutput(serializedResult);
                    memoryRuleEnforcer.auditGuidanceResponse(contextRules, serializedResult,
                            "EMPLOYEE_ABILITY_EXTRACTION", request.getSourceType(),
                            request.getSourceRefId(), rawContextText);

                    // 服务端先按偏移切片补全证据（切片边界再次核验），再进行原文校验；
                    // 校验失败进入明确 fallback，禁止送入治理写表
                    if (aiResult.getClaims() != null) {
                        for (PersonAbilityClaim claim : aiResult.getClaims()) {
                            sliceEvidenceFromSource(claim, request.getSourceText());
                        }
                    }
                    // 与岗位能力提取保持一致：先由服务端回填受控 sourceRefs 和证据切片，
                    // 再校验模型结果，避免模型返回占位或旧引用导致整批证据被拒绝。
                    normalizeExtractedClaims(aiResult.getClaims(), request);
                    if (request.isOcrDerived()) {
                        aiResult.setClaims(retainGroundedOcrClaims(aiResult.getClaims(), request));
                    } else {
                        try {
                            // 受控引用集合 = 服务端标准引用（source:{type}:{refId}）
                            extractionValidator.validate(aiResult, request.getSourceText(), buildServerRefs(request));
                        } catch (com.example.matching.ai.validation.AiOutputValidationException e) {
                            log.warn("[AI_OUTPUT_INVALID] 员工能力提取结果不合法，使用确定性降级: {}", e.getMessage());
                            recordValidationFailure(e.getMessage());
                            result.setFallbackUsed(true);
                            result.setClaims(DeterministicAiFallbacks.employeeAbilityClaims(request).getClaims());
                            result.setSummary("确定性降级方案：AI提取结果校验失败（" + e.getMessage() + "）");
                            result.setDurationMs(System.currentTimeMillis() - startTime);
                            return result;
                        }
                    }
                    List<PersonAbilityClaim> claims = aiResult.getClaims() != null
                            ? new ArrayList<>(aiResult.getClaims()) : new ArrayList<>();
                    result.setClaims(claims);
                    result.setBasicInfo(aiResult.getBasicInfo());
                    result.setFallbackUsed(false);
                    result.setSummary(aiResult.getSummary() != null ? aiResult.getSummary() : buildExtractionSummary(claims));
                } catch (Exception e) {
                    // 分块恢复仅针对整文提取的首次失败；递归拆分已到极限时上抛的
                    // AiServiceException（incompleteStructuredOutputRetryable）不得再次分块，
                    // 否则持续失败会形成「分块→失败→再分块」的无限循环，应直接走任务重试/降级。
                    if (!(e instanceof AiServiceException)
                            && isIncompleteStructuredOutput(e) && canSplitForRecovery(request.getSourceText())) {
                        List<com.example.matching.agent.service.ExtractionChunker.Chunk> recoveryChunks =
                                splitForRecovery(request.getSourceText());
                        log.warn("员工能力 Agent 返回未闭合结构化结果，拆分简历后重试提取: empId={}, parseId={}, chunks={}",
                                request.getEmpId(), request.getSourceRefId(), recoveryChunks.size());
                        result.setClaims(extractChunkedClaims(request, contextRules, recoveryChunks, result, startTime));
                        result.setFallbackUsed(false);
                        result.setSummary(result.getSummary() != null ? result.getSummary()
                                : buildExtractionSummary(result.getClaims()));
                        return result;
                    }
                    if (isResumeParseRequest(request)
                            && (isTransientAiTransportFailure(e) || isIncompleteStructuredOutput(e))) {
                        log.warn("员工能力 Agent 在简历解析时发生可恢复故障，将进入任务重试: empId={}, parseId={}, error={}",
                                request.getEmpId(), request.getSourceRefId(), e.getMessage());
                        throw AiServiceException.retryable("langchain4j", "employee-ability-extraction",
                                "AI 简历能力提取响应不完整或传输失败", e);
                    }
                    log.error("员工能力Agent LangChain4j调用失败，使用确定性降级方案: errorType={}, message={}",
                            e.getClass().getSimpleName(), e.getMessage(), e);
                    result.setFallbackUsed(true);
                    result.setClaims(DeterministicAiFallbacks.employeeAbilityClaims(request).getClaims());
                    result.setSummary("确定性降级方案：AI调用失败");
                }
            }

            if (contextRules.hardRules() != null && !contextRules.hardRules().isEmpty()) {
                AgentMemoryRuleEnforcer.EnforcementResult enforced = memoryRuleEnforcer.enforce(
                        result, contextRules, request.getSourceType(), request.getSourceRefId(), rawContextText);
                result.setClaims(enforced.claimsAs());
            }
        } catch (AiServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("提取员工能力失败", e);
            result.setFallbackUsed(true);
            result.setClaims(Collections.emptyList());
            result.setSummary("提取失败: " + e.getMessage());
        }

        result.setDurationMs(System.currentTimeMillis() - startTime);
        // Task10：提取日志只含统计与来源，不记录简历全文或敏感证据
        log.info("员工能力提取完成: empId={}, sourceType={}, sourceRefId={}, claimCount={}, fallbackUsed={}, failedChunks={}",
                request.getEmpId(), request.getSourceType(), request.getSourceRefId(),
                result.getClaims() != null ? result.getClaims().size() : 0,
                result.isFallbackUsed(), result.getFailedChunkCount());
        return result;
    }

    /** Task10：按失败原因分类记录结构化指标。 */
    private void recordValidationFailure(String message) {
        if (extractionMetrics == null || message == null) {
            return;
        }
        if (message.contains("无法在 sourceText 中定位")) {
            extractionMetrics.evidenceNotLocatable(com.example.matching.agent.config.ExtractionMetrics.SCENARIO_EMPLOYEE);
        } else if (message.contains("sourceRefs")) {
            extractionMetrics.sourceRefInvalid(com.example.matching.agent.config.ExtractionMetrics.SCENARIO_EMPLOYEE);
        } else {
            extractionMetrics.validationFailed(com.example.matching.agent.config.ExtractionMetrics.SCENARIO_EMPLOYEE);
        }
    }

    /**
     * OCR 文本可能存在标点、全半角和断行差异。该路径仍逐条验证证据，只是不让一条坏声明清空整份扫描简历。
     */
    private List<PersonAbilityClaim> retainGroundedOcrClaims(List<PersonAbilityClaim> claims,
                                                             PersonAbilityExtractRequest request) {
        if (claims == null || claims.isEmpty()) {
            return new ArrayList<>();
        }

        List<PersonAbilityClaim> grounded = new ArrayList<>();
        for (int index = 0; index < claims.size(); index++) {
            PersonAbilityClaim claim = claims.get(index);
            PersonAbilityExtractionResult singleClaim = new PersonAbilityExtractionResult();
            singleClaim.setClaims(List.of(claim));
            try {
                extractionValidator.validate(singleClaim, request.getSourceText(), buildServerRefs(request), true);
                grounded.add(claim);
            } catch (com.example.matching.ai.validation.AiOutputValidationException e) {
                recordValidationFailure(e.getMessage());
                log.warn("[OCR_EVIDENCE_REJECTED] 忽略无法定位的 OCR 能力声明: empId={}, claimIndex={}, error={}",
                        request.getEmpId(), index, e.getMessage());
            }
        }
        return grounded;
    }

    private boolean isResumeParseRequest(PersonAbilityExtractRequest request) {
        return "RESUME_PARSE".equals(request.getSourceType());
    }

    private boolean isTransientAiTransportFailure(Throwable error) {
        for (Throwable current = error; current != null && current.getCause() != current; current = current.getCause()) {
            if (current instanceof InterruptedIOException
                    || current instanceof ConnectException
                    || current instanceof SocketException
                    || current instanceof UnknownHostException
                    || current instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否为不完整或格式错误的结构化输出异常。
     * <p>
     * 覆盖以下场景：
     * <ul>
     *   <li>EOFException：LLM 输出被截断，JSON 未闭合</li>
     *   <li>JsonSyntaxException / JsonParseException：LLM 返回非 JSON 格式文本
     *       （如纯文本、Markdown 包裹的 JSON、异常 token 序列等），Gson/Jackson 无法解析为对象</li>
     *   <li>JsonMappingException：JSON 结构合法但与目标类型不匹配</li>
     * </ul>
     * 这些异常都意味着 LLM 输出无法被正确反序列化，应触发恢复机制（分块重试 / 任务重试）。
     */
    private boolean isIncompleteStructuredOutput(Throwable error) {
        for (Throwable current = error; current != null && current.getCause() != current; current = current.getCause()) {
            if (current instanceof java.io.EOFException) {
                return true;
            }
            if (current instanceof com.example.matching.infrastructure.llm.ModelResponseParseException
                    && current.getMessage() != null
                    && current.getMessage().toLowerCase(java.util.Locale.ROOT).contains("empty response")) {
                return true;
            }
            // Gson JSON 解析异常：格式错误、非 JSON 文本、类型不匹配
            if (current instanceof com.google.gson.JsonSyntaxException
                    || current instanceof com.google.gson.JsonParseException) {
                return true;
            }
            // Jackson JSON 解析异常（LangChain4j 在某些版本可能切换 JSON 编解码器）
            if (current instanceof com.fasterxml.jackson.core.JsonParseException
                    || current instanceof com.fasterxml.jackson.databind.JsonMappingException) {
                return true;
            }
        }
        return false;
    }

    private boolean canSplitForRecovery(String sourceText) {
        return sourceText != null && sourceText.length() > MIN_RECOVERY_CHUNK_CHARS;
    }

    private List<com.example.matching.agent.service.ExtractionChunker.Chunk> splitForRecovery(String sourceText) {
        int maxChars = Math.max(MIN_RECOVERY_CHUNK_CHARS, (sourceText.length() + 1) / 2);
        return com.example.matching.agent.service.ExtractionChunker.chunk(sourceText, maxChars);
    }

    private List<com.example.matching.agent.service.ExtractionChunker.Chunk> splitChunkForRecovery(
            com.example.matching.agent.service.ExtractionChunker.Chunk parent) {
        List<com.example.matching.agent.service.ExtractionChunker.Chunk> relativeChunks =
                splitForRecovery(parent.text());
        List<com.example.matching.agent.service.ExtractionChunker.Chunk> absoluteChunks = new ArrayList<>();
        for (com.example.matching.agent.service.ExtractionChunker.Chunk child : relativeChunks) {
            absoluteChunks.add(new com.example.matching.agent.service.ExtractionChunker.Chunk(
                    child.chunkIndex(), parent.start() + child.start(), parent.start() + child.end(), child.text()));
        }
        return absoluteChunks;
    }

    private AiServiceException incompleteStructuredOutputRetryable(Throwable cause) {
        return AiServiceException.retryable("langchain4j", "employee-ability-extraction",
                "AI 简历能力提取返回未闭合的结构化结果", cause);
    }

    private String buildExtractContextJsonWithMemory(PersonAbilityExtractRequest request,
                                                      AgentMemoryContextService.ContextRules contextRules) {
        try {
            Map<String, Object> context = new java.util.LinkedHashMap<>();
            context.put("empId", request.getEmpId());
            context.put("sourceType", request.getSourceType());
            context.put("sourceRefId", request.getSourceRefId());
            context.put("sourceText", request.getSourceText());
            if (request.getEvidenceText() != null && !request.getEvidenceText().isBlank()) {
                context.put("evidenceText", request.getEvidenceText());
            }
            if (request.getSourceRefs() != null && !request.getSourceRefs().isEmpty()) {
                context.put("sourceRefs", request.getSourceRefs());
            }
            if (request.getExistingAbilities() != null && !request.getExistingAbilities().isEmpty()) {
                context.put("existingAbilities", request.getExistingAbilities());
            }

            if (contextRules.guidancePrompt() != null && !contextRules.guidancePrompt().isBlank()) {
                context.put("governanceGuidance", contextRules.guidancePrompt());
            }
            if (contextRules.hardRuleSummary() != null && !contextRules.hardRuleSummary().isBlank()) {
                context.put("hardRuleSummary", contextRules.hardRuleSummary());
            }

            return objectMapper.writeValueAsString(context);
        } catch (Exception e) {
            log.error("构建提取上下文失败", e);
            return "{}";
        }
    }

    private String buildRawContextText(PersonAbilityExtractRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            return request.getEmpId() + " " + request.getSourceType();
        }
    }

    private List<PersonAbilityClaim> normalizeExtractedClaims(List<PersonAbilityClaim> claims,
                                                                PersonAbilityExtractRequest request) {
        if (claims == null) return new ArrayList<>();
        // 服务端标准引用：source:{canonicalSourceType}:{sourceRefId}；模型自报引用一律忽略
        List<String> serverRefs = buildServerRefs(request);
        for (PersonAbilityClaim claim : claims) {
            claim.setEmpId(request.getEmpId());
            claim.setSourceType(request.getSourceType());
            claim.setSourceRefId(request.getSourceRefId());
            if (claim.getNormalizedAbilityName() == null) claim.setNormalizedAbilityName(claim.getAbilityName());
            claim.setConfidenceScore(ScoreUtils.clampScore(claim.getConfidenceScore()));
            claim.setSourceRefs(serverRefs);
            // 服务端标签兜底匹配：AI 未匹配到正式标签时，用能力名确定性匹配标签库
            // （精确 → 别名 → 归一化 → 包含），命中即补全 abilityTagId，减少无标签 REVIEW。
            applyServerTagFallback(claim);
            // 服务端优先从原文切片生成证据文本并核验边界
            sliceEvidenceFromSource(claim, request.getSourceText());
            try {
                claim.setRawModelOutput(objectMapper.writeValueAsString(claim));
            } catch (Exception e) {
                claim.setRawModelOutput(null);
            }
        }
        return new ArrayList<>(claims);
    }

    /**
     * 服务端标签兜底匹配：AI 未给出正式标签时，用能力名在标签库做确定性匹配。
     */
    private void applyServerTagFallback(PersonAbilityClaim claim) {
        if (claim == null || claim.getAbilityTagId() != null || claim.getAbilityName() == null) {
            return;
        }
        if (abilityTagMatchProvider == null) {
            return;
        }
        try {
            com.example.matching.service.system.support.AbilityTagMatchService matcher =
                    abilityTagMatchProvider.getIfAvailable();
            if (matcher == null) {
                return;
            }
            com.example.matching.entity.system.AbilityTag matched =
                    matcher.matchByName(claim.getAbilityName());
            if (matched != null && matched.getId() != null) {
                claim.setAbilityTagId(matched.getId());
                if (claim.getSimilarTagId() == null) {
                    claim.setSimilarTagId(matched.getId());
                }
            }
        } catch (Exception e) {
            log.debug("人员能力服务端标签兜底匹配失败，忽略: ability={}, error={}",
                    claim.getAbilityName(), e.getMessage());
        }
    }

    /**
     * Task4：服务端标准引用（忽略模型自报的外部引用）。
     */
    private List<String> buildServerRefs(PersonAbilityExtractRequest request) {
        if (request.getSourceRefId() != null) {
            return List.of(com.example.matching.common.constant.SourceRefConstants.sourceRef(
                    request.getSourceType(), request.getSourceRefId()));
        }
        return request.getSourceRefs() != null ? request.getSourceRefs() : List.of();
    }

    /**
     * Task4：模型给出 evidenceStart/evidenceEnd 偏移时，服务端从原文切片生成
     * evidenceText 并核验边界；无偏移或越界时保持模型证据文本（由校验器把关）。
     */
    /**
     * Task8：长文本分块提取。逐块调用 AI 并校验；失败块仅标记 RETRY/REVIEW，
     * 不把旧事实复制成新提取结果；合并后按规范化名称去重并保留证据偏移（已修正为全量偏移）。
     */
    private List<PersonAbilityClaim> extractChunkedClaims(PersonAbilityExtractRequest request,
                                                          AgentMemoryContextService.ContextRules contextRules,
                                                          List<com.example.matching.agent.service.ExtractionChunker.Chunk> chunks,
                                                          PersonAbilityExtractionResult result,
                                                          long startTime) {
        List<PersonAbilityClaim> merged = new ArrayList<>();
        int failed = 0;
        for (com.example.matching.agent.service.ExtractionChunker.Chunk chunk : chunks) {
            try {
                PersonAbilityExtractRequest chunkRequest = new PersonAbilityExtractRequest();
                chunkRequest.setEmpId(request.getEmpId());
                chunkRequest.setSourceType(request.getSourceType());
                chunkRequest.setSourceRefId(request.getSourceRefId());
                chunkRequest.setSourceText(chunk.text());
                chunkRequest.setSourceRefs(request.getSourceRefs());
                chunkRequest.setExistingAbilities(request.getExistingAbilities());
                chunkRequest.setChunkIndex(chunk.chunkIndex());
                chunkRequest.setChunkStartOffset(chunk.start());

                AgentMemoryContextService.ContextRules chunkRules = memoryContextService.resolveRules(
                        buildRawContextText(chunkRequest), AgentMemoryContextService.SCOPE_EMPLOYEE);
                String contextJson = buildExtractContextJsonWithMemory(chunkRequest, chunkRules);
                PersonAbilityExtractionResult aiResult = com.example.matching.agent.config.AgentToolProvider
                        .withScope(() -> employeeAbilityAiService.extractAbilities(contextJson));
                if (aiResult == null || aiResult.getClaims() == null || aiResult.getClaims().isEmpty()) {
                    failed++;
                    log.warn("[EXTRACTION_CHUNK_FAILED] 分块无结果，标记 RETRY/REVIEW: chunkIndex={}", chunk.chunkIndex());
                    continue;
                }
                // 块内按偏移切片补全证据（偏移相对块文本）
                for (PersonAbilityClaim claim : aiResult.getClaims()) {
                    sliceEvidenceFromSource(claim, chunk.text());
                }
                List<PersonAbilityClaim> groundedClaims = new ArrayList<>();
                boolean chunkHadFailure = false;
                for (int claimIndex = 0; claimIndex < aiResult.getClaims().size(); claimIndex++) {
                    PersonAbilityClaim claim = aiResult.getClaims().get(claimIndex);
                    PersonAbilityExtractionResult singleClaimResult = new PersonAbilityExtractionResult();
                    singleClaimResult.setClaims(List.of(claim));
                    try {
                        extractionValidator.validate(singleClaimResult, chunk.text(), buildServerRefs(request));
                        groundedClaims.add(claim);
                    } catch (com.example.matching.ai.validation.AiOutputValidationException e) {
                        chunkHadFailure = true;
                        log.warn("[AI_OUTPUT_INVALID] 忽略分块中的无依据能力声明: chunkIndex={}, claimIndex={}, error={}",
                                chunk.chunkIndex(), claimIndex, e.getMessage());
                    }
                }
                if (chunkHadFailure) failed++;
                if (groundedClaims.isEmpty()) continue;
                // 证据偏移修正为全量原文偏移
                int base = chunk.start();
                for (PersonAbilityClaim claim : groundedClaims) {
                    if (claim.getEvidenceStart() != null) {
                        claim.setEvidenceStart(claim.getEvidenceStart() + base);
                    }
                    if (claim.getEvidenceEnd() != null) {
                        claim.setEvidenceEnd(claim.getEvidenceEnd() + base);
                    }
                }
                merged.addAll(groundedClaims);
            } catch (AiServiceException e) {
                throw e;
            } catch (Exception e) {
                if (isIncompleteStructuredOutput(e)) {
                    if (!canSplitForRecovery(chunk.text())) {
                        throw incompleteStructuredOutputRetryable(e);
                    }
                    List<com.example.matching.agent.service.ExtractionChunker.Chunk> recoveryChunks =
                            splitChunkForRecovery(chunk);
                    log.warn("[EXTRACTION_CHUNK_RECOVERY] 分块响应未闭合，递归拆分后重试: chunkIndex={}, recoveryChunks={}",
                            chunk.chunkIndex(), recoveryChunks.size());
                    merged.addAll(extractChunkedClaims(request, contextRules, recoveryChunks, result, startTime));
                    continue;
                }
                failed++;
                log.warn("[EXTRACTION_CHUNK_FAILED] 分块提取异常，标记 RETRY/REVIEW: chunkIndex={}, error={}",
                        chunk.chunkIndex(), e.getMessage());
            }
        }
        if (failed > 0) {
            result.setFailedChunkCount(failed);
            result.setSummary("分块提取完成，但 " + failed + " 个分块失败（RETRY/REVIEW），未复制旧事实");
            log.warn("员工能力分块提取: empId={}, chunks={}, failed={}",
                    request.getEmpId(), chunks.size(), failed);
        }
        List<PersonAbilityClaim> normalized = normalizeExtractedClaims(merged, request);
        return deduplicateClaims(normalized);
    }

    /**
     * Task8：合并去重——按规范化名称（去空白）保留证据更长且置信度更高的项。
     */
    private List<PersonAbilityClaim> deduplicateClaims(List<PersonAbilityClaim> claims) {
        Map<String, PersonAbilityClaim> byKey = new java.util.LinkedHashMap<>();
        for (PersonAbilityClaim claim : claims) {
            String name = claim.getNormalizedAbilityName() != null ? claim.getNormalizedAbilityName() : claim.getAbilityName();
            String key = name == null ? "" : name.replaceAll("\\s+", "");
            PersonAbilityClaim existing = byKey.get(key);
            if (existing == null) {
                byKey.put(key, claim);
            } else {
                int existingLen = existing.getEvidenceText() != null ? existing.getEvidenceText().length() : 0;
                int claimLen = claim.getEvidenceText() != null ? claim.getEvidenceText().length() : 0;
                java.math.BigDecimal existingConf = existing.getConfidenceScore() != null
                        ? existing.getConfidenceScore() : java.math.BigDecimal.ZERO;
                java.math.BigDecimal claimConf = claim.getConfidenceScore() != null
                        ? claim.getConfidenceScore() : java.math.BigDecimal.ZERO;
                boolean claimBetter = claimLen > existingLen
                        || (claimLen == existingLen && claimConf.compareTo(existingConf) > 0);
                if (claimBetter) {
                    byKey.put(key, claim);
                }
            }
        }
        return new ArrayList<>(byKey.values());
    }

    private void sliceEvidenceFromSource(PersonAbilityClaim claim, String sourceText) {
        // 尝试偏移切片
        if (claim.getEvidenceStart() != null && claim.getEvidenceEnd() != null
                && sourceText != null) {
            int start = claim.getEvidenceStart();
            int end = claim.getEvidenceEnd();
            if (start == 0 && end == 0) {
                // 模型未提供偏移
                claim.setEvidenceStart(null);
                claim.setEvidenceEnd(null);
            } else if (start < 0 || end > sourceText.length() || start >= end) {
                log.warn("[AI_OUTPUT_INVALID] 证据切片边界越界，尝试标准化查找: start={}, end={}, len={}",
                        start, end, sourceText.length());
                claim.setEvidenceStart(null);
                claim.setEvidenceEnd(null);
                claim.setValidationResult(EvidenceValidationResult.OFFSET_INVALID);
            } else {
                String sliced = sourceText.substring(start, end);
                if (sliced != null && !sliced.isBlank()) {
                    claim.setEvidenceText(sliced.trim());
                }
            }
        }
        // 没有有效偏移或 offset 切片失败 → 尝试 normalized lookup（仅适用有 evidenceText 的 claim）
        if ((claim.getEvidenceStart() == null || claim.getEvidenceEnd() == null)
                && claim.getEvidenceText() != null && !claim.getEvidenceText().isBlank()
                && sourceText != null && !sourceText.isBlank()) {
            locateEvidenceByNormalizedText(claim, sourceText);
        }
    }

    /**
     * 通过标准化文本查找在 sourceText 中定位 evidenceText。
     * 成功则设置 evidenceStart/evidenceEnd 和 validationResult=GROUNDED；
     * 找不到（模型虚构或改写证据）不修改 offsets，设置 TEXT_NOT_FOUND。
     */
    private void locateEvidenceByNormalizedText(PersonAbilityClaim claim, String sourceText) {
        String rawEvidence = claim.getEvidenceText();
        String normalizedEvidence = rawEvidence.replaceAll("\\s+", "");
        String normalizedSource = sourceText.replaceAll("\\s+", "");
        int idx = normalizedSource.indexOf(normalizedEvidence);
        if (idx < 0) {
            // 二级查找：尝试将 evidenceText 按句子拆分在 sourceText 中查找
            // 支持模型输出了证据但格式略有修改（如多一个空格）
            idx = fuzzyLocateEvidence(rawEvidence, normalizedSource);
        }
        if (idx >= 0) {
            // 标准化后索引映射回原始文本偏移
            claim.setValidationResult(EvidenceValidationResult.GROUNDED);
        } else {
            // 证据无法定位：模型提供的 evidenceText 不是原文的连续文本
            log.warn("[EVIDENCE_NOT_LOCATED] 证据文本无法在原文中定位，不接受改写证据: '{}'",
                    rawEvidence.length() > 80 ? rawEvidence.substring(0, 80) + "..." : rawEvidence);
            claim.setValidationResult(EvidenceValidationResult.TEXT_NOT_FOUND);
        }
    }

    /**
     * 模糊定位：将 evidenceText 拆为句子，查找最长可定位片段。
     */
    private int fuzzyLocateEvidence(String evidence, String normalizedSource) {
        // 尝试去除首尾空白后查找
        String trimmed = evidence.trim().replaceAll("\\s+", "");
        int idx = normalizedSource.indexOf(trimmed);
        if (idx >= 0) return idx;
        // 按句号/换行拆分为子串，查找最长可定位片段
        String[] parts = evidence.split("[。\\n]");
        int bestIdx = -1;
        int bestLen = 0;
        for (String part : parts) {
            String p = part.trim().replaceAll("\\s+", "");
            if (p.length() < 3) continue; // 太短的子串无意义
            int pi = normalizedSource.indexOf(p);
            if (pi >= 0 && p.length() > bestLen) {
                bestIdx = pi;
                bestLen = p.length();
            }
        }
        return bestIdx;
    }

    private String buildExtractionSummary(List<PersonAbilityClaim> claims) {
        if (claims == null || claims.isEmpty()) {
            return "未提取到能力声明";
        }

        int total = claims.size();
        long highConfidence = claims.stream()
                .filter(c -> c.getConfidenceScore() != null && c.getConfidenceScore().compareTo(BigDecimal.valueOf(75)) >= 0)
                .count();

        List<String> topAbilities = claims.stream()
                .limit(5)
                .map(PersonAbilityClaim::getAbilityName)
                .filter(name -> name != null && !name.isBlank())
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("AI提取完成，共提取").append(total).append("项能力");
        if (highConfidence > 0) {
            sb.append("，其中").append(highConfidence).append("项高置信度");
        }
        if (!topAbilities.isEmpty()) {
            sb.append("。主要包括：").append(String.join("、", topAbilities));
            if (total > 5) {
                sb.append("等");
            }
        }
        return sb.toString();
    }
}
