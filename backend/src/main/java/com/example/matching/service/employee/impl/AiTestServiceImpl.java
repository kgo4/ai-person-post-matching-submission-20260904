package com.example.matching.service.employee.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.matching.ai.validation.AiOutputValidationException;
import com.example.matching.common.exception.AiServiceException;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.config.RabbitMQConfig;
import com.example.matching.entity.employee.EmpAiTest;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.post.PostPost;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.entity.system.SysOperationLog;
import com.example.matching.listener.AiTestTaskPayload;
import com.example.matching.mapper.employee.EmpAiTestMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.mapper.post.PostPostMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.service.ability.AbilityEvidenceIngestionService;
import com.example.matching.service.agent.AgentBusinessApplyService;
import com.example.matching.service.common.EventOutboxDispatcher;
import com.example.matching.agent.dto.person.PersonAbilityClaim;
import com.example.matching.mapper.workflow.PersonAbilityClaimGroupMapper;
import com.example.matching.mapper.ability.PersonAbilityClaimMapper;
import com.example.matching.agent.dto.person.PersonAbilityExtractionResult;
import com.example.matching.service.employee.AiTestAgent;
import com.example.matching.service.employee.AiTestService;
import com.example.matching.service.assessment.CapabilityAssessmentOrchestrator;
import com.example.matching.ai.validation.AssessmentQuestionBindingValidator;
import com.example.matching.dto.assessment.AssessmentScopeDTO;
import com.example.matching.service.assessment.AssessmentAgentArtifactService;
import com.example.matching.service.system.SysOperationLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AiTestServiceImpl extends ServiceImpl<EmpAiTestMapper, EmpAiTest> implements AiTestService {

    private static final String SOURCE_TYPE_AI_TEST = "AI_TEST";

    private static final String STATE_PENDING = "PENDING";
    private static final String STATE_PROCESSING = "PROCESSING";
    private static final String STATE_SUCCEEDED = "SUCCEEDED";
    private static final String STATE_FAILED = "FAILED";

    private static final String ERROR_TYPE_AI_OUTPUT_INVALID = "AI_OUTPUT_INVALID";
    private static final String ERROR_TYPE_AI_SERVICE = "AI_SERVICE_ERROR";
    private static final String ERROR_TYPE_BUSINESS = "BUSINESS_ERROR";
    private static final String ERROR_TYPE_SYSTEM = "SYSTEM_ERROR";

    /** 最大自动重试次数（超过后进入 FAILED，不再自动投递） */
    private static final int MAX_RETRY_COUNT = 3;

    /** 注入题目生成上下文的简历能力声明上限（避免全量塞入） */
    private static final int MAX_RESUME_CLAIMS_IN_CONTEXT = 8;

    @Value("${ai.test.question-count:5}")
    private int questionCount;

    private final AiTestAgent aiTestAgent;
    private final ObjectMapper objectMapper;
    private final AbilityTagMapper abilityTagMapper;
    private final AbilityEvidenceIngestionService abilityEvidenceIngestionService;
    private final PostPostMapper postPostMapper;
    private final PostAbilityModelMapper postAbilityModelMapper;
    private final AgentBusinessApplyService agentBusinessApplyService;
    private final EventOutboxDispatcher outboxDispatcher;
    private final SysOperationLogService sysOperationLogService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final com.example.matching.port.assessment.CapabilityStageLifecycleEventPublisher lifecycleEventPublisher;
    private final com.example.matching.mapper.workflow.PersonAbilityClaimGroupMapper claimGroupMapper;
    private final com.example.matching.mapper.ability.PersonAbilityClaimMapper claimMapper;
    private CapabilityAssessmentOrchestrator assessmentOrchestrator;
    private AssessmentQuestionBindingValidator questionBindingValidator;
    private AssessmentAgentArtifactService artifactService;

    /** Single Spring injection constructor. Compatibility constructors below are test-only. */
    @org.springframework.beans.factory.annotation.Autowired
    public AiTestServiceImpl(AiTestAgent aiTestAgent, ObjectMapper objectMapper,
                             AbilityTagMapper abilityTagMapper,
                             AbilityEvidenceIngestionService abilityEvidenceIngestionService,
                             PostPostMapper postPostMapper, PostAbilityModelMapper postAbilityModelMapper,
                             AgentBusinessApplyService agentBusinessApplyService,
                             EventOutboxDispatcher outboxDispatcher,
                             SysOperationLogService sysOperationLogService,
                             org.springframework.context.ApplicationEventPublisher eventPublisher,
                             com.example.matching.port.assessment.CapabilityStageLifecycleEventPublisher lifecycleEventPublisher,
                             PersonAbilityClaimGroupMapper claimGroupMapper,
                             PersonAbilityClaimMapper claimMapper) {
        this.aiTestAgent = aiTestAgent;
        this.objectMapper = objectMapper;
        this.abilityTagMapper = abilityTagMapper;
        this.abilityEvidenceIngestionService = abilityEvidenceIngestionService;
        this.postPostMapper = postPostMapper;
        this.postAbilityModelMapper = postAbilityModelMapper;
        this.agentBusinessApplyService = agentBusinessApplyService;
        this.outboxDispatcher = outboxDispatcher;
        this.sysOperationLogService = sysOperationLogService;
        this.eventPublisher = eventPublisher;
        this.lifecycleEventPublisher = lifecycleEventPublisher;
        this.claimGroupMapper = claimGroupMapper;
        this.claimMapper = claimMapper;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setAssessmentOrchestrator(CapabilityAssessmentOrchestrator value) {
        this.assessmentOrchestrator = value;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setQuestionBindingValidator(AssessmentQuestionBindingValidator value) {
        this.questionBindingValidator = value;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setArtifactService(AssessmentAgentArtifactService value) {
        this.artifactService = value;
    }

    /** Compatibility constructor for existing unit fixtures. */
    public AiTestServiceImpl(AiTestAgent aiTestAgent, ObjectMapper objectMapper,
                             AbilityTagMapper abilityTagMapper,
                             AbilityEvidenceIngestionService abilityEvidenceIngestionService,
                             PostPostMapper postPostMapper, PostAbilityModelMapper postAbilityModelMapper,
                             AgentBusinessApplyService agentBusinessApplyService,
                             EventOutboxDispatcher outboxDispatcher,
                             SysOperationLogService sysOperationLogService,
                             org.springframework.context.ApplicationEventPublisher eventPublisher,
                             com.example.matching.port.assessment.CapabilityStageLifecycleEventPublisher lifecycleEventPublisher,
                             PersonAbilityClaimGroupMapper claimGroupMapper,
                             PersonAbilityClaimMapper claimMapper,
                             AssessmentQuestionBindingValidator bindingValidator,
                             com.example.matching.service.assessment.AssessmentScopeService ignoredScopeService,
                             com.example.matching.mapper.workflow.AiTestCoverageMapper ignoredCoverageMapper) {
        this(aiTestAgent, objectMapper, abilityTagMapper, abilityEvidenceIngestionService,
                postPostMapper, postAbilityModelMapper,
                agentBusinessApplyService, outboxDispatcher, sysOperationLogService,
                eventPublisher, lifecycleEventPublisher, claimGroupMapper, claimMapper);
        this.questionBindingValidator = bindingValidator;
    }

    @Override
    @Transactional
    public EmpAiTest generatePostTest(Long empId, Long postId, Long userId) {
        throw new BusinessException(400, "能力评估必须通过统一工作流启动");
        /*
        PostPost post = postPostMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException(404, "岗位不存在");
        }

        List<PostAbilityModel> models = postAbilityModelMapper.selectList(
                Wrappers.<PostAbilityModel>lambdaQuery().eq(PostAbilityModel::getPostId, postId));
        if (models.isEmpty()) {
            throw new BusinessException(400, "该岗位未配置能力模型");
        }

        Long firstTagId = models.get(0).getTagId();
        EmpAiTest test = new EmpAiTest();
        test.setEmpId(empId);
        test.setPostId(postId);
        test.setAbilityTagId(firstTagId);
        test.setAbilityTagName(post.getPostName() + " 综合能力测试");
        test.setTestTitle(post.getPostName() + " 岗位综合能力测试");
        test.setStatus(-1);
        test.setCreatedBy(userId);
        test.setCreatedTime(LocalDateTime.now());
        save(test);

        enqueueAiTestTask("ai.test.generate", new AiTestTaskPayload("GENERATE", test.getId()));
        log.info("AI测试题目生成任务已投递: testId={}", test.getId());
        return test;
        */
    }

    @Override
    @Transactional
    public EmpAiTest generateWorkflowTest(Long empId, Long workflowId, Long postId, Long userId) {
        assertWorkflowTestPostConfigured(postId);
        PostPost post = postPostMapper.selectById(postId);
        List<PostAbilityModel> models = postAbilityModelMapper.selectList(
                Wrappers.<PostAbilityModel>lambdaQuery().eq(PostAbilityModel::getPostId, postId));
        Long firstTagId = models.get(0).getTagId();
        EmpAiTest test = new EmpAiTest();
        test.setEmpId(empId);
        test.setWorkflowId(workflowId);
        test.setPostId(postId);
        test.setAbilityTagId(firstTagId);
        test.setAbilityTagName(post.getPostName() + " 综合能力测试");
        test.setTestTitle(post.getPostName() + " 岗位综合能力测试");
        test.setStatus(-1);
        test.setCreatedBy(userId);
        test.setCreatedTime(LocalDateTime.now());
        save(test);
        assessmentOrchestrator.freezeScope(workflowId, empId, postId);
        enqueueAiTestTask("ai.test.generate", new AiTestTaskPayload("GENERATE", test.getId()));
        log.info("工作流验证测试题目生成任务已投递: testId={}, workflowId={}", test.getId(), workflowId);
        return test;
    }

    @Override
    public void assertWorkflowTestPostConfigured(Long postId) {
        if (postId == null) {
            throw new BusinessException(400, "请先选择已配置能力模型的目标岗位");
        }
        PostPost post = postPostMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException(404, "目标岗位不存在: postId=" + postId);
        }
        boolean configured = postAbilityModelMapper.exists(
                Wrappers.<PostAbilityModel>lambdaQuery().eq(PostAbilityModel::getPostId, postId));
        if (!configured) {
            throw new BusinessException(400, "所选岗位未配置可用能力模型: "
                    + post.getPostName() + " (postId=" + postId + ")");
        }
    }

    @Override
    @Transactional
    public EmpAiTest generateTest(Long empId, Long abilityTagId, Long userId) {
        throw new BusinessException(400, "能力评估必须通过统一工作流启动");
        /*
        AbilityTag tag = abilityTagMapper.selectById(abilityTagId);
        if (tag == null) {
            throw new BusinessException(404, "能力标签不存在");
        }

        EmpAiTest test = new EmpAiTest();
        test.setEmpId(empId);
        test.setAbilityTagId(abilityTagId);
        test.setAbilityTagName(tag.getTagName());
        test.setTestTitle(tag.getTagName() + " 能力测试");
        test.setStatus(-1);
        test.setCreatedBy(userId);
        test.setCreatedTime(LocalDateTime.now());
        save(test);

        enqueueAiTestTask("ai.test.generate", new AiTestTaskPayload("GENERATE", test.getId()));
        log.info("AI测试题目生成任务已投递: testId={}", test.getId());
        return test;
        */
    }

    @Override
    public void processGenerateQuestions(Long testId) {
        // 幂等抢占：同一消息并发投递只允许一次成功
        int claimed = baseMapper.claimGeneration(testId);
        if (claimed != 1) {
            log.debug("AI测试题目生成抢占失败或已处理，直接返回: testId={}", testId);
            return;
        }

        EmpAiTest test = getById(testId);
        if (test == null) {
            log.error("AI测试记录不存在: testId={}", testId);
            baseMapper.failGeneration(testId, ERROR_TYPE_BUSINESS, "测试记录不存在");
            return;
        }

        // 抢占成功后发布生命周期事件：协调器将阶段运行置 RUNNING（不直接改工作流状态）
        publishLifecycleClaimed(test, "AI_TEST_GENERATION");

        try {
            String questionsJson;
            if (test.getTestTitle() != null && test.getTestTitle().contains("岗位综合能力测试")) {
                if (test.getPostId() == null) {
                    failGenerationPermanently(test, ERROR_TYPE_BUSINESS, "岗位测试缺少岗位ID，无法生成题目");
                    return;
                }
                PostPost post = postPostMapper.selectById(test.getPostId());
                if (post == null) {
                    failGenerationPermanently(test, ERROR_TYPE_BUSINESS, "岗位已不存在");
                    return;
                }
                List<PostAbilityModel> models = postAbilityModelMapper.selectList(
                        Wrappers.<PostAbilityModel>lambdaQuery().eq(PostAbilityModel::getPostId, post.getId()));
                String abilitiesDesc = buildPostAbilitiesDescription(models);
                // 工作流测试：加载简历能力声明，注入问题生成以覆盖声明验证
                String resumeClaims = buildResumeClaimsForWorkflow(test);
                String scopeJson = null;
                String blueprintJson = null;
                if (test.getWorkflowId() != null) {
                    var scope = assessmentOrchestrator.loadScope(test.getWorkflowId());
                    var blueprint = assessmentOrchestrator.loadOrCreateBlueprint(
                            test.getWorkflowId(), test.getEmpId(), test.getPostId());
                    scopeJson = objectMapper.writeValueAsString(scope);
                    blueprintJson = objectMapper.writeValueAsString(blueprint);
                }
                questionsJson = aiTestAgent.generateQuestions(new AiTestAgent.AiTestQuestionRequest(
                        null, null, null,
                        post.getPostName(),
                        post.getJobDescription() != null ? post.getJobDescription() : "暂无描述",
                        abilitiesDesc,
                        resumeClaims, scopeJson, blueprintJson
                ));
                if (test.getWorkflowId() != null) {
                    AssessmentScopeDTO scope = assessmentOrchestrator.loadScope(test.getWorkflowId());
                    List<Map<String, Object>> generated = objectMapper.readValue(questionsJson,
                            new com.fasterxml.jackson.core.type.TypeReference<>() {});
                    var binding = questionBindingValidator.validate(scope, generated);
                    if (!binding.outputValid()) {
                        throw new AiOutputValidationException("AI_TEST_QUESTION_SET", "questions", "OUT_OF_SCOPE_BINDING");
                    }
                    questionsJson = objectMapper.writeValueAsString(binding.validQuestions());
                }
            } else {
                AbilityTag tag = abilityTagMapper.selectById(test.getAbilityTagId());
                if (tag == null) {
                    failGenerationPermanently(test, ERROR_TYPE_BUSINESS, "能力标签已不存在");
                    return;
                }
                questionsJson = aiTestAgent.generateQuestions(new AiTestAgent.AiTestQuestionRequest(
                        tag.getTagName(), tag.getTagCategory(),
                        tag.getDescription() != null ? tag.getDescription() : "暂无描述",
                        null, null, null, null
                ));
            }

            test.setQuestions(questionsJson);
            test.setStatus(0);
            test.setErrorMessage(null);
            updateById(test);

            // 仅允许 PROCESSING -> SUCCEEDED
            baseMapper.markGenerationSucceeded(testId);
            if (test.getWorkflowId() != null) {
                eventPublisher.publishEvent(new com.example.matching.event.AiTestQuestionsGeneratedEvent(
                        test.getId(), test.getWorkflowId()));
            }
            log.info("AI测试题目生成完成: testId={}", testId);
        } catch (Exception e) {
            log.error("AI测试题目生成失败: testId={}, error={}", testId, e.getMessage(), e);
            handleGenerationFailure(test, e);
        }
    }

    /**
     * 题目生成失败处理：
     * 可重试异常 -> 回到 PENDING 并递增次数后重新投递；超过三次、校验失败、不可重试 -> FAILED
     */
    private void handleGenerationFailure(EmpAiTest test, Exception e) {
        boolean retryable = isRetryable(e);
        String errorType = resolveErrorType(e);
        String errorMessage = safeTruncate(e.getMessage(), 500);
        test.setErrorMessage(errorMessage);
        test.setLastErrorType(errorType);
        test.setLastErrorMessage(errorMessage);
        updateById(test);

        if (retryable && (test.getRetryCount() == null || test.getRetryCount() < MAX_RETRY_COUNT)) {
            baseMapper.retryGeneration(test.getId(), errorType, errorMessage);
            log.warn("AI测试题目生成可重试失败，重新投递: testId={}, retryCount={}, type={}",
                    test.getId(), test.getRetryCount() == null ? 0 : test.getRetryCount() + 1, errorType);
            enqueueAiTestTask("ai.test.generate", new AiTestTaskPayload("GENERATE", test.getId()));
        } else {
            baseMapper.failGeneration(test.getId(), errorType, errorMessage);
            publishWorkflowGenerationFailure(test, errorMessage);
            publishLifecycleFailedFinal(test, "AI_TEST_GENERATION", "AI_TEST_GENERATION_FAILED", errorMessage);
            log.error("AI测试题目生成不可重试或重试耗尽，标记 FAILED: testId={}, type={}", test.getId(), errorType);
        }
    }

    /**
     * 题目生成永久失败（业务前置条件不满足），标记 FAILED 并同步前端状态
     */
    private void failGenerationPermanently(EmpAiTest test, String errorType, String message) {
        test.setStatus(-1);
        test.setErrorMessage(message);
        updateById(test);
        baseMapper.failGeneration(test.getId(), errorType, safeTruncate(message, 500));
        publishWorkflowGenerationFailure(test, message);
        publishLifecycleFailedFinal(test, "AI_TEST_GENERATION", "AI_TEST_GENERATION_FAILED", message);
        log.warn("AI测试题目生成永久失败: testId={}, type={}, message={}", test.getId(), errorType, message);
    }

    private void publishWorkflowGenerationFailure(EmpAiTest test, String errorMessage) {
        if (test.getWorkflowId() != null) {
            eventPublisher.publishEvent(new com.example.matching.event.AiTestQuestionsGenerationFailedEvent(
                    test.getId(), test.getWorkflowId(), safeTruncate(errorMessage, 500)));
        }
    }

    /**
     * 判断异常是否可重试：AI 输出校验失败与业务异常不可重试，AI 服务异常按 retryable 标记，其余视为瞬时可重试
     */
    private boolean isRetryable(Throwable e) {
        if (e instanceof AiOutputValidationException) {
            return false;
        }
        if (e instanceof AiServiceException ai) {
            return ai.isRetryable();
        }
        if (e instanceof BusinessException) {
            return false;
        }
        return true;
    }

    private String resolveErrorType(Throwable e) {
        if (e instanceof AiOutputValidationException) {
            return ERROR_TYPE_AI_OUTPUT_INVALID;
        }
        if (e instanceof AiServiceException) {
            return ERROR_TYPE_AI_SERVICE;
        }
        if (e instanceof BusinessException) {
            return ERROR_TYPE_BUSINESS;
        }
        return ERROR_TYPE_SYSTEM;
    }

    private void markGenerateFailed(EmpAiTest test, String message) {
        test.setStatus(-1);
        test.setErrorMessage(safeTruncate(message, 500));
        updateById(test);
    }

    private void enqueueAiTestTask(String routingKey, AiTestTaskPayload payload) {
        EmpAiTest test = payload != null && payload.getTestId() != null ? getById(payload.getTestId()) : null;
        if (test != null && test.getWorkflowId() != null && artifactService != null) {
            String scopeHash = null;
            try {
                var scope = assessmentOrchestrator.loadScope(test.getWorkflowId());
                scopeHash = scope != null ? scope.scopeHash() : null;
            } catch (Exception e) {
                log.warn("读取评估范围哈希失败，将由消费者进行兼容校验: workflowId={}, error={}",
                        test.getWorkflowId(), e.getMessage());
            }
            var envelope = artifactService.storePayload(test.getWorkflowId(), null,
                    "AI_TEST_TASK", payload, scopeHash, null);
            outboxDispatcher.enqueue("AI_TEST", RabbitMQConfig.MATCHING_EXCHANGE, routingKey, envelope);
            return;
        }
        outboxDispatcher.enqueue("AI_TEST", RabbitMQConfig.MATCHING_EXCHANGE, routingKey, payload);
    }

    /**
     * 工作流测试任务被抢占后发布 TASK_CLAIMED 生命周期事件（协调器将阶段运行置 RUNNING）。
     */
    private void publishLifecycleClaimed(EmpAiTest test, String stageType) {
        if (test == null || test.getWorkflowId() == null) {
            return;
        }
        try {
            lifecycleEventPublisher.publish(com.example.matching.event.CapabilityStageLifecycleEvent.of(
                    test.getWorkflowId(), null, stageType,
                    "AI_TEST", test.getId(),
                    com.example.matching.common.enums.StageLifecycleEventType.TASK_CLAIMED,
                    null, null));
        } catch (Exception e) {
            log.warn("发布AI测试任务抢占事件失败: testId={}, stage={}, error={}",
                    test.getId(), stageType, e.getMessage());
        }
    }

    @Override
    @Transactional
    public EmpAiTest submitAnswers(Long testId, Map<String, Object> answers) {
        EmpAiTest test = getById(testId);
        if (test == null) {
            throw new BusinessException(404, "测试记录不存在");
        }
        if (test.getStatus() != 0) {
            throw new BusinessException(400, "当前状态不允许提交答案");
        }

        try {
            test.setAnswers(objectMapper.writeValueAsString(answers));
            test.setStatus(1);
            updateById(test);

            enqueueAiTestTask("ai.test.evaluate", new AiTestTaskPayload("EVALUATE", test.getId()));
            log.info("AI测试评估任务已投递: testId={}", testId);
            return test;
        } catch (Exception e) {
            log.error("提交答案失败: {}", e.getMessage(), e);
            throw new BusinessException(500, "提交失败: " + e.getMessage());
        }
    }

    @Override
    public void processEvaluateAnswers(Long testId) {
        // 幂等抢占：同一消息并发投递只允许一次成功
        int claimed = baseMapper.claimEvaluation(testId);
        if (claimed != 1) {
            log.debug("AI测试评分抢占失败或已处理，直接返回: testId={}", testId);
            return;
        }

        EmpAiTest test = getById(testId);
        if (test == null || test.getStatus() != 1) {
            log.warn("AI测试评估跳过: testId={}, status={}", testId, test != null ? test.getStatus() : null);
            baseMapper.failEvaluation(testId, ERROR_TYPE_BUSINESS,
                    test == null ? "测试记录不存在" : "当前状态不允许评估");
            return;
        }

        // 抢占成功后发布生命周期事件：协调器将阶段运行置 RUNNING
        publishLifecycleClaimed(test, "AI_TEST_EVALUATION");

        try {
            evaluateTest(test);
            // 仅允许 PROCESSING -> SUCCEEDED
            baseMapper.markEvaluationSucceeded(testId);
            log.info("AI测试评分完成: testId={}", testId);
        } catch (Exception e) {
            log.error("AI测试评估失败: testId={}, error={}", testId, e.getMessage(), e);
            handleEvaluationFailure(test, e);
        }
    }

    /**
     * 评分失败处理：
     * 可重试异常 -> 回到 PENDING 并递增次数后重新投递；超过三次、校验失败、不可重试 -> FAILED
     */
    private void handleEvaluationFailure(EmpAiTest test, Exception e) {
        boolean retryable = isRetryable(e);
        String errorType = resolveErrorType(e);
        String errorMessage = safeTruncate(e.getMessage(), 500);
        test.setErrorMessage(errorMessage);
        test.setLastErrorType(errorType);
        test.setLastErrorMessage(errorMessage);
        updateById(test);

        if (retryable && (test.getRetryCount() == null || test.getRetryCount() < MAX_RETRY_COUNT)) {
            baseMapper.retryEvaluation(test.getId(), errorType, errorMessage);
            log.warn("AI测试评分可重试失败，重新投递: testId={}, retryCount={}, type={}",
                    test.getId(), test.getRetryCount() == null ? 0 : test.getRetryCount() + 1, errorType);
            enqueueAiTestTask("ai.test.evaluate", new AiTestTaskPayload("EVALUATE", test.getId()));
        } else {
            baseMapper.failEvaluation(test.getId(), errorType, errorMessage);
            log.error("AI测试评分不可重试或重试耗尽，标记 FAILED: testId={}, type={}", test.getId(), errorType);
            publishLifecycleFailedFinal(test, "AI_TEST_EVALUATION", "AI_TEST_EVALUATION_FAILED", errorMessage);
        }
    }

    /**
     * 工作流测试任务最终失败：发布 TASK_FAILED_FINAL 生命周期事件（协调器置工作流 FAILED）。
     */
    private void publishLifecycleFailedFinal(EmpAiTest test, String stageType, String errorCode, String errorMessage) {
        if (test == null || test.getWorkflowId() == null) {
            return;
        }
        try {
            lifecycleEventPublisher.publish(com.example.matching.event.CapabilityStageLifecycleEvent.failedFinal(
                    test.getWorkflowId(), null, stageType,
                    "AI_TEST", test.getId(), errorCode, errorMessage));
        } catch (Exception e) {
            log.warn("发布AI测试最终失败事件失败: testId={}, stage={}, error={}",
                    test.getId(), stageType, e.getMessage());
        }
    }

    /**
     * 管理员重放：仅允许 FAILED -> PENDING，并写系统操作日志
     */
    @Override
    @Transactional
    public boolean redeliverTask(Long testId) {
        EmpAiTest test = getById(testId);
        if (test == null) {
            throw new BusinessException(404, "测试记录不存在");
        }

        boolean generationRedelivered = STATE_FAILED.equals(test.getGenerationState())
                && baseMapper.resetGenerationToPending(testId) == 1;
        boolean evaluationRedelivered = STATE_FAILED.equals(test.getEvaluationState())
                && baseMapper.resetEvaluationToPending(testId) == 1;

        if (!generationRedelivered && !evaluationRedelivered) {
            throw new BusinessException(ErrorCodeEnum.STATE_CONFLICT,
                    "当前状态不允许重放，仅 FAILED 状态可重放: generationState=" + test.getGenerationState()
                            + ", evaluationState=" + test.getEvaluationState());
        }

        if (generationRedelivered) {
            enqueueAiTestTask("ai.test.generate", new AiTestTaskPayload("GENERATE", testId));
        }
        if (evaluationRedelivered) {
            enqueueAiTestTask("ai.test.evaluate", new AiTestTaskPayload("EVALUATE", testId));
        }

        writeRedeliverAuditLog(testId, generationRedelivered, evaluationRedelivered);
        log.info("AI测试任务人工重放: testId={}, generation={}, evaluation={}",
                testId, generationRedelivered, evaluationRedelivered);
        return true;
    }

    private void writeRedeliverAuditLog(Long testId, boolean generation, boolean evaluation) {
        try {
            SysOperationLog logRecord = new SysOperationLog();
            logRecord.setUserId(com.example.matching.utils.SecurityUtils.getCurrentUserId());
            logRecord.setRealName(com.example.matching.utils.SecurityUtils.getCurrentUsername());
            logRecord.setOperationModule("AI_TEST");
            logRecord.setOperationType("UPDATE");
            logRecord.setOperationDesc("AI测试任务人工重放: testId=" + testId
                    + ", generation=" + generation + ", evaluation=" + evaluation);
            logRecord.setRequestUrl("/api/employee/ability/ai-test/" + testId + "/redeliver");
            logRecord.setOperationTime(LocalDateTime.now());
            sysOperationLogService.save(logRecord);
        } catch (Exception e) {
            log.warn("AI测试重放审计日志写入失败: testId={}", testId, e);
        }
    }

    @Override
    public EmpAiTest getTestResult(Long testId) {
        EmpAiTest test = getById(testId);
        if (test == null) {
            throw new BusinessException(404, "测试记录不存在");
        }
        return test;
    }

    @Override
    public List<EmpAiTest> listByEmpId(Long empId) {
        return list(Wrappers.<EmpAiTest>lambdaQuery()
                .eq(EmpAiTest::getEmpId, empId)
                .orderByDesc(EmpAiTest::getCreatedTime));
    }

    @Override
    public EmpAiTest getLatestByWorkflowId(Long workflowId) {
        if (workflowId == null) {
            return null;
        }
        return baseMapper.selectOne(Wrappers.<EmpAiTest>lambdaQuery()
                .eq(EmpAiTest::getWorkflowId, workflowId)
                .orderByDesc(EmpAiTest::getId)
                .last("LIMIT 1"));
    }

    @Override
    @Transactional
    public boolean importToAbilityProfile(Long testId) {
        EmpAiTest test = getById(testId);
        if (test == null || test.getStatus() != 2) {
            throw new BusinessException(400, "测试不存在或未完成");
        }
        if (test.getWorkflowId() != null) {
            throw new BusinessException(400,
                    "该测试属于能力评估工作流，测试证据已由工作流保存，禁止直接导入正式能力");
        }
        if (test.getMasteryLevel() == null) {
            throw new BusinessException(400, "测试结果未包含掌握等级评估");
        }

        if (test.getAbilityTagId() == null && test.getTestTitle() != null && test.getTestTitle().contains("岗位")) {
            return importPostComprehensiveTest(test);
        }

        if (test.getAbilityTagId() == null) {
            throw new BusinessException(400, "无法确定目标能力标签");
        }

        PersonAbilityExtractionResult extractionResult = buildExtractionResultFromTest(test);
        if (extractionResult == null || extractionResult.getClaims().isEmpty()) {
            log.warn("AI测试结果无法构建能力声明: testId={}", testId);
            return false;
        }

        AgentBusinessApplyService.PersonAbilityApplyResult applyResult =
                agentBusinessApplyService.applyPersonAbilities(extractionResult);

        log.info("AI测试结果导入完成: testId={}, total={}, pass={}, review={}, block={}, error={}",
                testId, applyResult.getTotalClaims(), applyResult.getPassCount(),
                applyResult.getReviewCount(), applyResult.getBlockCount(), applyResult.getErrorCount());

        if (applyResult.getPassCount() > 0) {
            test.setStatus(3);
            test.setImportedTime(LocalDateTime.now());
            updateById(test);
            return true;
        }

        return false;
    }

    private PersonAbilityExtractionResult buildExtractionResultFromTest(EmpAiTest test) {
        PersonAbilityExtractionResult result = new PersonAbilityExtractionResult();
        result.setEmpId(test.getEmpId());
        result.setSourceType(SOURCE_TYPE_AI_TEST);
        result.setSourceRefId(test.getId());

        List<PersonAbilityClaim> claims = new ArrayList<>();
        BigDecimal confidenceFromScore = calculateConfidenceFromScore(test.getScore());
        PersonAbilityClaim mainClaim = new PersonAbilityClaim();
        mainClaim.setEmpId(test.getEmpId());
        mainClaim.setAbilityTagId(test.getAbilityTagId());
        mainClaim.setAbilityName(test.getAbilityTagName());
        mainClaim.setMasteryLevel(test.getMasteryLevel());
        mainClaim.setConfidenceScore(confidenceFromScore);
        mainClaim.setSourceType(SOURCE_TYPE_AI_TEST);
        mainClaim.setSourceRefId(test.getId());
        mainClaim.setEvidenceText(buildAiTestEvidenceText(test, "AI测试结果导入"));
        mainClaim.setSourceRefs(List.of("source:AI_TEST:" + test.getId()));
        claims.add(mainClaim);

        result.setClaims(claims);
        return result;
    }

    private String buildPostAbilitiesDescription(List<PostAbilityModel> models) {
        StringBuilder abilitiesDesc = new StringBuilder();
        for (PostAbilityModel model : models) {
            AbilityTag tag = abilityTagMapper.selectById(model.getTagId());
            String name = tag != null ? tag.getTagName() : "能力#" + model.getTagId();
            abilitiesDesc.append(name).append("（要求").append(model.getMinRequiredLevel()).append("级）");
            if (Integer.valueOf(1).equals(model.getIsCore())) {
                abilitiesDesc.append("，核心");
            }
            if (Integer.valueOf(1).equals(model.getIsRequired())) {
                abilitiesDesc.append("，必填");
            }
            abilitiesDesc.append("\n");
        }
        return abilitiesDesc.toString();
    }

    /**
     * 构建工作流关联的简历能力声明摘要，注入问题生成提示上下文。
     * 格式：Java(L4, 置信度85): "5 years Java backend development"
     * 用于告知 AI 模型需要验证的具体声明，使生成的问题更具针对性。
     */
    private String buildResumeClaimsForWorkflow(EmpAiTest test) {
        if (test.getWorkflowId() == null) return null;
        try {
            List<com.example.matching.entity.workflow.PersonAbilityClaimGroup> groups =
                    claimGroupMapper.selectList(Wrappers.<com.example.matching.entity.workflow.PersonAbilityClaimGroup>
                            lambdaQuery().eq(com.example.matching.entity.workflow.PersonAbilityClaimGroup::getWorkflowId,
                                    test.getWorkflowId()));
            if (groups == null || groups.isEmpty()) return null;
            // 裁剪：每条能力取有证据的最佳声明，按置信度降序，注入前 K 条，避免全量塞入
            record ClaimSummary(String name, int level, BigDecimal confidence, String evidence) {}
            List<ClaimSummary> summaries = new ArrayList<>();
            for (var group : groups) {
                List<com.example.matching.entity.ability.PersonAbilityClaim> claims =
                        claimMapper.selectList(Wrappers.<com.example.matching.entity.ability.PersonAbilityClaim>
                                lambdaQuery().eq(com.example.matching.entity.ability.PersonAbilityClaim::getClaimGroupId,
                                        group.getId()));
                if (claims == null || claims.isEmpty()) continue;
                var bestClaim = claims.stream()
                        .filter(c -> c.getEvidenceText() != null && !c.getEvidenceText().isBlank())
                        .max(Comparator.comparing(c -> c.getConfidenceScore() != null ? c.getConfidenceScore() : BigDecimal.ZERO))
                        .orElse(claims.get(0));
                summaries.add(new ClaimSummary(
                        group.getNormalizedAbilityName(),
                        bestClaim.getClaimedLevel() != null ? bestClaim.getClaimedLevel() : 2,
                        bestClaim.getConfidenceScore(),
                        bestClaim.getEvidenceText()));
            }
            summaries.sort(Comparator.comparing(
                    (ClaimSummary s) -> s.confidence() != null ? s.confidence() : BigDecimal.ZERO).reversed());
            StringBuilder sb = new StringBuilder();
            int limit = Math.min(summaries.size(), MAX_RESUME_CLAIMS_IN_CONTEXT);
            for (int i = 0; i < limit; i++) {
                ClaimSummary s = summaries.get(i);
                sb.append(s.name()).append("(L").append(s.level()).append(")");
                if (s.evidence() != null) {
                    String evidence = s.evidence();
                    sb.append(": \"").append(evidence.length() > 120 ? evidence.substring(0, 120) + "..." : evidence).append("\"");
                }
                sb.append("\n");
            }
            return sb.length() > 0 ? sb.toString() : null;
        } catch (Exception e) {
            log.warn("构建简历声明摘要失败，继续不注入声明: testId={}", test.getId(), e);
            return null;
        }
    }

    private void evaluateTest(EmpAiTest test) {
        AiTestAgent.AiTestEvaluationResult evaluation = aiTestAgent.evaluateAnswers(
                new AiTestAgent.AiTestEvaluationRequest(
                        test.getAbilityTagName() != null ? test.getAbilityTagName() : "综合能力",
                        test.getQuestions(),
                        test.getAnswers()
                )
        );

        test.setAiEvaluation(evaluation.evaluationJson());
        test.setScore(evaluation.score());
        test.setMasteryLevel(evaluation.masteryLevel());
        test.setAnalysisReport(evaluation.analysisReport());
        test.setStatus(2);
        test.setCompletedTime(LocalDateTime.now());
        test.setErrorMessage(null);
        updateById(test);

        if (test.getWorkflowId() != null) {
            // 工作流测试：不直接正式入库，发布事件由能力评估流程保存测试证据
            eventPublisher.publishEvent(new com.example.matching.event.AiTestEvaluatedEvent(
                    test.getId(), test.getEmpId(), test.getWorkflowId()));
            log.info("工作流测试评分完成，发布评估事件: testId={}, workflowId={}", test.getId(), test.getWorkflowId());
            return;
        }
        // Tests only verify the frozen assessment scope; they never discover or persist abilities.
    }

    private boolean importPostComprehensiveTest(EmpAiTest test) {
        String postName = test.getTestTitle().replace(" 岗位综合能力测试", "");
        List<PostPost> posts = postPostMapper.selectList(Wrappers.<PostPost>lambdaQuery().eq(PostPost::getPostName, postName));
        if (posts.isEmpty()) {
            return false;
        }

        List<PostAbilityModel> models = postAbilityModelMapper.selectList(
                Wrappers.<PostAbilityModel>lambdaQuery().eq(PostAbilityModel::getPostId, posts.get(0).getId()));

        List<PersonAbilityClaim> claims = new ArrayList<>();
        for (PostAbilityModel model : models) {
            AbilityTag tag = abilityTagMapper.selectById(model.getTagId());
            String tagName = tag != null ? tag.getTagName() : "能力#" + model.getTagId();

            PersonAbilityClaim claim = new PersonAbilityClaim();
            claim.setEmpId(test.getEmpId());
            claim.setAbilityTagId(model.getTagId());
            claim.setAbilityName(tagName);
            claim.setMasteryLevel(test.getMasteryLevel());
            claim.setConfidenceScore(new BigDecimal("85"));
            claim.setSourceType(SOURCE_TYPE_AI_TEST);
            claim.setSourceRefId(test.getId());
            claim.setEvidenceText(buildAiTestEvidenceText(test, "岗位综合测试能力导入"));
            claim.setSourceRefs(List.of("source:AI_TEST:" + test.getId()));
            claims.add(claim);
        }

        if (claims.isEmpty()) {
            return false;
        }

        PersonAbilityExtractionResult extractionResult = new PersonAbilityExtractionResult();
        extractionResult.setEmpId(test.getEmpId());
        extractionResult.setSourceType(SOURCE_TYPE_AI_TEST);
        extractionResult.setSourceRefId(test.getId());
        extractionResult.setClaims(claims);

        AgentBusinessApplyService.PersonAbilityApplyResult applyResult =
                agentBusinessApplyService.applyPersonAbilities(extractionResult);

        log.info("岗位综合测试导入完成: testId={}, total={}, pass={}, review={}, block={}",
                test.getId(), applyResult.getTotalClaims(), applyResult.getPassCount(),
                applyResult.getReviewCount(), applyResult.getBlockCount());

        if (applyResult.getPassCount() > 0) {
            test.setStatus(3);
            test.setImportedTime(LocalDateTime.now());
            updateById(test);
            return true;
        }

        return false;
    }

    private String buildAiTestEvidenceText(EmpAiTest test, String prefix) {
        return prefix + "\nanalysisReport=" + (test.getAnalysisReport() != null ? test.getAnalysisReport() : "")
                + "\naiEvaluation=" + abbreviate(test.getAiEvaluation(), 800)
                + "\nanswers=" + abbreviate(test.getAnswers(), 400);
    }

    private BigDecimal calculateConfidenceFromScore(BigDecimal score) {
        if (score == null) {
            return new BigDecimal("50");
        }
        double confidence = 40 + score.doubleValue() * 0.5;
        confidence = Math.max(40, Math.min(90, confidence));
        return BigDecimal.valueOf(Math.round(confidence));
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private String safeTruncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
