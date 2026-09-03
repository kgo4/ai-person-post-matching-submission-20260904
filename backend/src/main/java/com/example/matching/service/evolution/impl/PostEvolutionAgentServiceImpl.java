package com.example.matching.service.evolution.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.common.enums.TaskStatusEnum;
import com.example.matching.dto.evolution.AgentProgressVO;
import com.example.matching.dto.evolution.PostEvolutionAgentRequest;
import com.example.matching.event.PostEvolutionAgentQueuedEvent;
import com.example.matching.dto.evolution.PostEvolutionAgentResult;
import com.example.matching.dto.evolution.PostEvolutionAgentResult.HarnessSummary;
import com.example.matching.dto.evolution.PostEvolutionAgentResult.PostEvolutionChangeProposal;
import com.example.matching.dto.harness.AiHarnessDecisionDTO;
import com.example.matching.entity.evolution.PostEvolutionEvidence;
import com.example.matching.entity.evolution.PostEvolutionTask;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.mapper.evolution.PostEvolutionChangeItemMapper;
import com.example.matching.mapper.evolution.PostEvolutionEvidenceMapper;
import com.example.matching.mapper.evolution.PostEvolutionTaskMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.port.post.PostQueryPort;
import com.example.matching.service.evolution.EvolutionHarnessOrchestrator;
import com.example.matching.service.evolution.PostEvolutionAgentService;
import com.example.matching.service.evolution.PostEvolutionKnowledgeRetrievalService;
import com.example.matching.service.evolution.PostEvolutionKnowledgeRetrievalService.RetrievalResult;
import com.example.matching.service.evolution.PostEvolutionSignalService;
import com.example.matching.service.evolution.PostEvolutionSignalService.EvolutionSignal;
import com.example.matching.service.evolution.support.EvolutionAbilityTagResolver;
import com.example.matching.service.evolution.support.ResolvedEvolutionAbility;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 岗位演化 Agent 服务实现
 * <p>
 * 编排整个岗位演化流程：
 * Step 1: 读取岗位当前能力模型
 * Step 2: 检索行业白皮书相关证据
 * Step 3: 检索内部知识库/云知识库相关证据
 * Step 4: 提取演化信号
 * Step 5: 和当前能力模型对比
 * Step 6: 生成结构化变更建议
 * Step 7: 送 Harness 校验
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostEvolutionAgentServiceImpl implements PostEvolutionAgentService {

    private final PostEvolutionTaskMapper taskMapper;
    private final PostEvolutionChangeItemMapper changeItemMapper;
    private final PostEvolutionEvidenceMapper evidenceMapper;
    private final PostAbilityModelMapper postAbilityModelMapper;
    private final PostQueryPort postQueryPort;
    private final PostEvolutionKnowledgeRetrievalService knowledgeRetrievalService;
    private final PostEvolutionSignalService signalService;
    private final EvolutionHarnessOrchestrator harnessOrchestrator;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final PostEvolutionAgentPipeline pipeline;
    private final EvolutionAbilityTagResolver abilityTagResolver;

    @Override
    @Transactional
    public PostEvolutionAgentResult runEvolution(PostEvolutionAgentRequest request) {
        Long postId = request.getPostId();
        log.info("开始运行岗位演化 Agent: postId={}, triggerType={}", postId, request.getTriggerType());

        PostEvolutionAgentResult result = new PostEvolutionAgentResult();
        result.setPostId(postId);

        // Step 1: 读取岗位当前能力模型
        updateProgress(null, "READING_MODEL", 10);
        List<PostAbilityModel> currentAbilities = loadCurrentAbilityModel(postId);
        String postName = resolvePostName(postId);
        if (request.getPostName() == null || request.getPostName().isBlank()) {
            request.setPostName(postName);
        }
        result.setPostName(postName);
        log.info("Step 1 完成: 读取岗位能力模型, abilities={}", currentAbilities.size());

        // Step 2: 检索行业白皮书相关证据
        updateProgress(null, "RETRIEVING_INDUSTRY", 25);
        List<RetrievalResult> industryEvidence = new ArrayList<>();
        if (Boolean.TRUE.equals(request.getIncludeWhitepaper())) {
            industryEvidence = pipeline.retrieveIndustryEvidence(request, currentAbilities);
        }
        log.info("Step 2 完成: 检索行业证据, results={}", industryEvidence.size());

        // Step 3: 检索内部知识库/云知识库相关证据
        updateProgress(null, "RETRIEVING_INTERNAL", 40);
        List<RetrievalResult> internalEvidence = new ArrayList<>();
        if (Boolean.TRUE.equals(request.getIncludeCloudKnowledge())) {
            internalEvidence = pipeline.retrieveInternalEvidence(request, currentAbilities);
        }
        log.info("Step 3 完成: 检索内部证据, results={}", internalEvidence.size());

        updateProgress(null, "RETRIEVING_MARKET", 48);
        List<RetrievalResult> marketEvidence = new ArrayList<>();
        if (Boolean.TRUE.equals(request.getIncludeMarketJd())) {
            marketEvidence = pipeline.retrieveMarketEvidence(request, currentAbilities);
        }
        log.info("市场演化线索检索完成: results={}", marketEvidence.size());

        updateProgress(null, "RETRIEVING_ZHIHU", 52);
        List<RetrievalResult> zhihuEvidence = pipeline.retrieveZhihuEvidence(request, currentAbilities);
        log.info("知乎趋势线索检索完成: results={}", zhihuEvidence.size());

        // Step 4-5: AI Agent 生成变更建议（LLM 优先，规则兜底；能力来源=岗位能力表+证据，防幻觉由校验层保证）
        updateProgress(null, "GENERATING_SIGNALS", 55);
        PostEvolutionAgentPipeline.ProposalGenerationOutcome generation = pipeline.generateAiProposals(request, currentAbilities,
                industryEvidence, internalEvidence, marketEvidence, zhihuEvidence);
        List<PostEvolutionChangeProposal> proposals = generation.proposals();
        // 规则信号仅用于统计留痕
        List<EvolutionSignal> signals = pipeline.generateSignals(request, industryEvidence, internalEvidence, marketEvidence, zhihuEvidence, currentAbilities);
        result.setSignals(signals);
        result.setProposals(proposals);
        result.setProposalGenerationSummary(toProposalGenerationSummary(generation, proposals));
        log.info("Step 4-5 完成: 生成变更建议, proposals={}", proposals.size());

        // Step 6: 生成结构化证据
        updateProgress(null, "GENERATING_ITEMS", 75);
        List<PostEvolutionEvidence> evidences = pipeline.createEvidencesWithZhihu(industryEvidence, internalEvidence, marketEvidence, zhihuEvidence);
        result.setEvidences(evidences);
        log.info("Step 6 完成: 生成证据");

        // Step 7: 送 Harness 校验
        updateProgress(null, "HARNESS_CHECK", 85);
        HarnessSummary harnessSummary = harnessOrchestrator.verifyAllProposals(result, postId, null);
        result.setHarnessSummary(harnessSummary);
        log.info("Step 7 完成: Harness 校验, pass={}, review={}, block={}",
                harnessSummary.getPass(), harnessSummary.getReview(), harnessSummary.getBlock());

        // 生成摘要
        result.setSummary(pipeline.generateSummary(result));

        updateProgress(null, "COMPLETED", 100);
        log.info("岗位演化 Agent 运行完成: postId={}", postId);

        return result;
    }

    @Override
    @Transactional
    public PostEvolutionTask runEvolutionAndCreateTask(PostEvolutionAgentRequest request) {
        PostEvolutionTask task = new PostEvolutionTask();
        task.setTaskCode("EVO_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        task.setPostId(request.getPostId());
        task.setTaskName(resolvePostName(request.getPostId()) + " 演化任务");
        // Agent-triggered evolution retrieves its source material asynchronously and has no direct JD payload.
        task.setNewJdText("");
        task.setTaskStatus(TaskStatusEnum.PENDING.getCode());
        task.setTriggerType(request.getTriggerType());
        task.setIndustry(request.getIndustry());
        task.setBusinessDomain(request.getBusinessDomain());
        task.setSourceType("AGENT");
        task.setProgressStatus("QUEUED");
        task.setProgressPercent(0);
        task.setCreatedBy(request.getOperatorId());
        taskMapper.insert(task);
        eventPublisher.publishEvent(new PostEvolutionAgentQueuedEvent(task.getId(), request));
        return task;
    }

    @Override
    public void executeQueuedEvolution(Long taskId, PostEvolutionAgentRequest request) {
        int claimed = taskMapper.claimPendingTask(taskId,
                TaskStatusEnum.PENDING.getCode(),
                TaskStatusEnum.RUNNING.getCode());
        if (claimed != 1) {
            log.info("岗位演化任务已被其他消费者抢占或已处理，跳过: taskId={}", taskId);
            return;
        }
        PostEvolutionTask task = taskMapper.selectById(taskId);
        if (task == null) {
            log.warn("岗位演化任务不存在，跳过: taskId={}", taskId);
            return;
        }
        try {
            PostEvolutionAgentResult result = runEvolutionWithTask(task, request);
            persistAgentResult(task, result, request);
            log.info("岗位演化任务完成: taskId={}", taskId);
        } catch (Exception exception) {
            log.error("岗位演化任务失败: taskId={}, error={}", taskId, exception.getMessage(), exception);
            task.setTaskStatus(TaskStatusEnum.FAILED.getCode());
            task.setProgressStatus("FAILED");
            task.setErrorMessage(exception.getMessage());
            taskMapper.updateById(task);
        }
    }

    private void persistAgentResult(PostEvolutionTask task, PostEvolutionAgentResult result,
                                    PostEvolutionAgentRequest request) throws Exception {
        List<PostEvolutionEvidence> persistedEvidences = new ArrayList<>();
        if (result.getEvidences() != null) {
            for (PostEvolutionEvidence evidence : result.getEvidences()) {
                evidence.setTaskId(task.getId());
                evidenceMapper.insert(evidence);
                persistedEvidences.add(evidence);
            }
        }
        int savedCount = pipeline.saveChangeItems(task.getId(), result.getProposals(), persistedEvidences);
        // An empty result is completed analysis with no effective change, not an applied task.
        task.setTaskStatus(savedCount == 0
                ? TaskStatusEnum.COMPLETED.getCode()
                : TaskStatusEnum.WAIT_CONFIRM.getCode());
        task.setProgressStatus("COMPLETED");
        task.setProgressPercent(100);
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalProposals", result.getProposals() != null ? result.getProposals().size() : 0);
        summary.put("savedChangeItems", savedCount);
        summary.put("result", savedCount == 0 ? "NO_EFFECTIVE_CHANGE" : "REVIEW_REQUIRED");
        summary.put("evidenceCount", result.getEvidences() != null ? result.getEvidences().size() : 0);
        summary.put("signalCount", result.getSignals() != null ? result.getSignals().size() : 0);
        if (result.getProposalGenerationSummary() != null) {
            summary.putAll(result.getProposalGenerationSummary());
        }
        if (result.getHarnessSummary() != null) {
            summary.put("harnessPass", result.getHarnessSummary().getPass());
            summary.put("harnessReview", result.getHarnessSummary().getReview());
            summary.put("harnessBlock", result.getHarnessSummary().getBlock());
            task.setHarnessSummary(objectMapper.writeValueAsString(result.getHarnessSummary()));
        }
        task.setSummaryJson(objectMapper.writeValueAsString(summary));
        Map<String, Object> agentTrace = new HashMap<>();
        agentTrace.put("triggerType", request.getTriggerType());
        agentTrace.put("completedTime", LocalDateTime.now().toString());
        task.setAgentTrace(objectMapper.writeValueAsString(agentTrace));
        taskMapper.updateById(task);
    }

    private Map<String, Object> toProposalGenerationSummary(
            PostEvolutionAgentPipeline.ProposalGenerationOutcome generation,
            List<PostEvolutionChangeProposal> proposals) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("aiRawSuggestions", generation.aiRawSuggestionCount());
        summary.put("aiAcceptedSuggestions", generation.aiAcceptedSuggestionCount());
        summary.put("ruleProposalCount", generation.ruleProposalCount());
        summary.put("ruleFallback", generation.ruleFallback());
        summary.put("fallbackReason", generation.fallbackReason());
        Map<String, Long> actionCounts = (proposals == null ? List.<PostEvolutionChangeProposal>of() : proposals).stream()
                .collect(Collectors.groupingBy(proposal -> proposal.getChangeType() == null ? "UNKNOWN" : proposal.getChangeType(),
                        LinkedHashMap::new, Collectors.counting()));
        summary.put("addCount", actionCounts.getOrDefault("ADD", 0L));
        summary.put("updateCount", actionCounts.getOrDefault("UPDATE", 0L));
        summary.put("removeCount", actionCounts.getOrDefault("REMOVE", 0L));
        return summary;
    }

    @Override
    public AgentProgressVO getAgentProgress(Long taskId) {
        PostEvolutionTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "演化任务不存在: " + taskId);
        }

        AgentProgressVO progress = new AgentProgressVO();
        progress.setTaskId(taskId);
        progress.setCurrentStep(task.getProgressStatus());
        progress.setPercent(task.getProgressPercent() != null ? task.getProgressPercent() : 0);
        progress.setErrorMessage(task.getErrorMessage());

        // 构建步骤列表
        List<AgentProgressVO.StepProgress> steps = new ArrayList<>();
        steps.add(new AgentProgressVO.StepProgress("读取岗位模型", getStepStatus(task.getProgressStatus(), "READING_MODEL")));
        steps.add(new AgentProgressVO.StepProgress("检索行业资料", getStepStatus(task.getProgressStatus(), "RETRIEVING_INDUSTRY")));
        steps.add(new AgentProgressVO.StepProgress("检索内部资料", getStepStatus(task.getProgressStatus(), "RETRIEVING_INTERNAL")));
        steps.add(new AgentProgressVO.StepProgress("检索市场演化线索", getStepStatus(task.getProgressStatus(), "RETRIEVING_MARKET")));
        steps.add(new AgentProgressVO.StepProgress("检索知乎趋势", getStepStatus(task.getProgressStatus(), "RETRIEVING_ZHIHU")));
        steps.add(new AgentProgressVO.StepProgress("生成演化信号", getStepStatus(task.getProgressStatus(), "GENERATING_SIGNALS")));
        steps.add(new AgentProgressVO.StepProgress("Harness校验", getStepStatus(task.getProgressStatus(), "HARNESS_CHECK")));
        steps.add(new AgentProgressVO.StepProgress("生成变更项", getStepStatus(task.getProgressStatus(), "GENERATING_ITEMS")));
        progress.setSteps(steps);

        return progress;
    }

    // ===== 内部方法 =====

    /**
     * 带任务ID的 Agent 运行（用于更新进度）
     */
    private PostEvolutionAgentResult runEvolutionWithTask(PostEvolutionTask task, PostEvolutionAgentRequest request) {
        Long postId = request.getPostId();
        log.info("开始运行岗位演化 Agent (带任务): postId={}, taskId={}", postId, task.getId());

        PostEvolutionAgentResult result = new PostEvolutionAgentResult();
        result.setPostId(postId);

        // Step 1
        updateProgress(task, "READING_MODEL", 10);
        List<PostAbilityModel> currentAbilities = loadCurrentAbilityModel(postId);
        String postName = resolvePostName(postId);
        if (request.getPostName() == null || request.getPostName().isBlank()) {
            request.setPostName(postName);
        }
        result.setPostName(postName);

        // Step 2
        updateProgress(task, "RETRIEVING_INDUSTRY", 25);
        List<RetrievalResult> industryEvidence = new ArrayList<>();
        if (Boolean.TRUE.equals(request.getIncludeWhitepaper())) {
            industryEvidence = pipeline.retrieveIndustryEvidence(request, currentAbilities);
        }

        // Step 3
        updateProgress(task, "RETRIEVING_INTERNAL", 40);
        List<RetrievalResult> internalEvidence = new ArrayList<>();
        if (Boolean.TRUE.equals(request.getIncludeCloudKnowledge())) {
            internalEvidence = pipeline.retrieveInternalEvidence(request, currentAbilities);
        }

        updateProgress(task, "RETRIEVING_MARKET", 48);
        List<RetrievalResult> marketEvidence = new ArrayList<>();
        if (Boolean.TRUE.equals(request.getIncludeMarketJd())) {
            marketEvidence = pipeline.retrieveMarketEvidence(request, currentAbilities);
        }

        updateProgress(task, "RETRIEVING_ZHIHU", 52);
        List<RetrievalResult> zhihuEvidence = pipeline.retrieveZhihuEvidence(request, currentAbilities);

        // Step 4-5: AI Agent 生成变更建议（LLM 优先，规则兜底）
        updateProgress(task, "GENERATING_SIGNALS", 55);
        PostEvolutionAgentPipeline.ProposalGenerationOutcome generation = pipeline.generateAiProposals(request, currentAbilities,
                industryEvidence, internalEvidence, marketEvidence, zhihuEvidence);
        List<PostEvolutionChangeProposal> proposals = generation.proposals();
        List<EvolutionSignal> signals = pipeline.generateSignals(request, industryEvidence, internalEvidence, marketEvidence, zhihuEvidence, currentAbilities);
        result.setSignals(signals);
        result.setProposals(proposals);
        result.setProposalGenerationSummary(toProposalGenerationSummary(generation, proposals));

        // Step 6
        updateProgress(task, "GENERATING_ITEMS", 75);
        List<PostEvolutionEvidence> evidences = pipeline.createEvidencesWithZhihu(industryEvidence, internalEvidence, marketEvidence, zhihuEvidence);
        result.setEvidences(evidences);

        // Step 7
        updateProgress(task, "HARNESS_CHECK", 85);
        HarnessSummary harnessSummary = harnessOrchestrator.verifyAllProposals(result, postId, task.getId());
        result.setHarnessSummary(harnessSummary);

        result.setSummary(pipeline.generateSummary(result));

        updateProgress(task, "COMPLETED", 100);
        return result;
    }

    /**
     * 更新任务进度
     */
    private void updateProgress(PostEvolutionTask task, String status, int percent) {
        if (task != null) {
            task.setProgressStatus(status);
            task.setProgressPercent(percent);
            taskMapper.updateById(task);
        }
    }

    /**
     * 获取步骤状态
     */
    private String getStepStatus(String currentStep, String stepName) {
        if ("FAILED".equals(currentStep)) {
            return "ERROR";
        }
        if (currentStep == null) {
            return "PENDING";
        }

        List<String> stepOrder = Arrays.asList(
                "READING_MODEL", "RETRIEVING_INDUSTRY", "RETRIEVING_INTERNAL", "RETRIEVING_MARKET", "RETRIEVING_ZHIHU",
                "GENERATING_SIGNALS", "COMPARING_MODEL", "GENERATING_ITEMS",
                "HARNESS_CHECK", "COMPLETED"
        );

        int currentIndex = stepOrder.indexOf(currentStep);
        int stepIndex = stepOrder.indexOf(stepName);

        if (currentIndex < 0 || stepIndex < 0) {
            return "PENDING";
        }

        if (stepIndex < currentIndex) {
            return "DONE";
        } else if (stepIndex == currentIndex) {
            return "COMPLETED".equals(currentStep) ? "DONE" : "RUNNING";
        } else {
            return "PENDING";
        }
    }

    /**
     * 加载当前岗位能力模型
     */
    private List<PostAbilityModel> loadCurrentAbilityModel(Long postId) {
        LambdaQueryWrapper<PostAbilityModel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PostAbilityModel::getPostId, postId);
        wrapper.eq(PostAbilityModel::getIsDeleted, 0);
        return postAbilityModelMapper.selectList(wrapper);
    }

    /**
     * 解析岗位名称
     */
    private String resolvePostName(Long postId) {
        PostQueryPort.PostDTO post = postQueryPort.getPostById(postId);
        if (post != null && post.postName() != null) {
            return post.postName();
        }
        return "岗位 #" + postId;
    }

    /**
     * 检索行业证据
     */
}
