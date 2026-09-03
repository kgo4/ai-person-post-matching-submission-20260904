package com.example.matching.service.employee.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.matching.agent.dto.person.PersonAbilityClaim;
import com.example.matching.agent.dto.person.PersonAbilityExtractRequest;
import com.example.matching.agent.dto.person.PersonAbilityExtractionResult;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.dto.system.AbilityImportResultDTO;
import com.example.matching.common.exception.PermanentResumeParseException;
import com.example.matching.common.exception.RetryableResumeParseException;
import com.example.matching.config.RabbitMQConfig;
import com.example.matching.dto.system.TagAdmissionContext;
import com.example.matching.dto.system.TagAdmissionResult;
import com.example.matching.entity.employee.EmpResumeParse;
import com.example.matching.event.AbilityChangeEvent;
import com.example.matching.event.ResumeParseQueuedEvent;
import com.example.matching.mapper.employee.EmpResumeParseMapper;
import com.example.matching.service.common.EventOutboxDispatcher;
import com.example.matching.service.common.DocumentUploadValidator;
import com.example.matching.service.employee.ResumeParseService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.usermodel.Range;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 简历解析服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeParseServiceImpl extends ServiceImpl<EmpResumeParseMapper, EmpResumeParse> implements ResumeParseService {

    /** ai_analysis_result 安全上限：1MB（LONGTEXT理论上限4GB，此处防止异常大响应） */
    
    /** AI 分析输入的文本长度上限（约 8000 tokens） */
    
    /** 最大重试次数 */
    private static final int MAX_RETRY_COUNT = 3;

    /** 僵尸任务超时阈值：处理中超过 10 分钟视为僵尸 */
    private static final int ZOMBIE_TIMEOUT_MINUTES = 10;

    /** 延迟重试队列路由键，按重试次数递增延迟 */
    private static final String[] RETRY_ROUTING_KEYS = {
            "resume.parse.retry.30s",   // 第 1 次重试：30 秒
            "resume.parse.retry.5m",    // 第 2 次重试：5 分钟
            "resume.parse.retry.30m"    // 第 3 次重试：30 分钟
    };

            private final ObjectMapper objectMapper;
        private final ApplicationEventPublisher eventPublisher;
    private final ResumeFileParser fileParser;
    private final ResumeAbilityImportService abilityImportService;
            private final EventOutboxDispatcher outboxDispatcher;
    private final com.example.matching.common.util.PersonAbilityClaimNormalizer claimNormalizer;
    private final com.example.matching.service.assessment.CapabilityAssessmentWorkflowService workflowService;
    private final com.example.matching.service.assessment.AbilityEvidenceCollectionService evidenceCollectionService;
    private final com.example.matching.port.assessment.CapabilityStageLifecycleEventPublisher lifecycleEventPublisher;

    @Override
    @Transactional
    public EmpResumeParse uploadAndParse(Long empId, String originalFilename, byte[] content, Long userId) {
        DocumentUploadValidator.validateResume(originalFilename, content);
        // 1. 验证文件类型
        String fileType = getFileType(originalFilename);
        if (!Arrays.asList("PDF", "DOC", "DOCX").contains(fileType.toUpperCase())) {
            throw new BusinessException(400, "仅支持PDF、DOC、DOCX格式的文件");
        }

        // 2. 计算文件哈希值
        String fileHash;
        fileHash = computeSha256(content);

        // 3. 检查是否有相同文件的已完成记录（去重）
        List<EmpResumeParse> existing = list(Wrappers.<EmpResumeParse>lambdaQuery()
                .eq(EmpResumeParse::getEmpId, empId)
                .eq(EmpResumeParse::getFileHash, fileHash)
                .eq(EmpResumeParse::getStatus, 2)
                .orderByDesc(EmpResumeParse::getCreatedTime));
        if (!existing.isEmpty()) {
            log.info("简历文件去重复用已有解析结果: empId={}, fileHash={}, existingId={}",
                    empId, fileHash, existing.get(0).getId());
            return existing.get(0);
        }

        // 4. 保存文件
        String filePath = saveFile(originalFilename, content, empId);

        // 5. 创建解析记录
        EmpResumeParse parseRecord = new EmpResumeParse();
        parseRecord.setEmpId(empId);
        parseRecord.setFileName(originalFilename);
        parseRecord.setFilePath(filePath);
        parseRecord.setFileType(fileType);
        parseRecord.setFileHash(fileHash);
        parseRecord.setStatus(0); // 待解析
        parseRecord.setRetryCount(0);
        parseRecord.setCreatedBy(userId);
        parseRecord.setCreatedTime(LocalDateTime.now());
        save(parseRecord);

        // 6. 事务提交后投递MQ
        eventPublisher.publishEvent(new ResumeParseQueuedEvent(parseRecord.getId()));

        return parseRecord;
    }

    @Override
    public void processQueuedParse(Long parseId) {
        EmpResumeParse record = getById(parseId);
        if (record == null) {
            log.warn("简历解析任务不存在: parseId={}", parseId);
            return;
        }

        int status = record.getStatus() != null ? record.getStatus() : 0;
        // 幂等控制：仅 待处理(0)/等待重试(4) 可转为处理中；其他状态直接跳过
        if (status != 0 && status != 4) {
            log.info("简历解析任务已处理，跳过: parseId={}, status={}", parseId, status);
            return;
        }

        // 条件更新抢占任务：CAS 确保并发安全
        boolean acquired = lambdaUpdate()
                .eq(EmpResumeParse::getId, parseId)
                .in(EmpResumeParse::getStatus, 0, 4)
                .set(EmpResumeParse::getStatus, 1)
                .set(EmpResumeParse::getProcessingStartedAt, LocalDateTime.now())
                .update();
        if (!acquired) {
            log.info("简历解析任务已被其他消费者抢占，跳过: parseId={}", parseId);
            return;
        }

        // 重新加载最新状态
        record = getById(parseId);
        markAssessmentWorkflowParsing(record);

        try {
            executeParse(record);
        } catch (RetryableResumeParseException retryableEx) {
            handleRetryableFailure(record, retryableEx);
        } catch (PermanentResumeParseException permanentEx) {
            handlePermanentFailure(record, permanentEx);
        } catch (Exception unexpectedEx) {
            // 未预期的异常默认视为可重试（AI 超时等场景）
            log.warn("简历解析遇到未预期异常，按可重试处理: parseId={}, error={}",
                    parseId, unexpectedEx.getMessage());
            handleRetryableFailure(record,
                    new RetryableResumeParseException("UNEXPECTED", unexpectedEx.getMessage(), unexpectedEx));
        }
    }

    /**
     * 解析消费者成功抢占任务后，发布生命周期事件同步评估工作流。
     * <p>
     * 只发布 TASK_CLAIMED，不直接改工作流状态；由协调器将工作流推进到
     * RESUME_PARSING 并将阶段运行置 RUNNING。
     */
    private void markAssessmentWorkflowParsing(EmpResumeParse record) {
        if (record == null || record.getEmpId() == null || workflowService == null) {
            return;
        }
        try {
            var workflow = workflowService.getActiveWorkflow(record.getEmpId());
            if (workflow == null) {
                return;
            }
            // 幂等创建简历解析阶段运行（解析完成后证据保存会复用同一哈希）
            String parseHash = com.example.matching.service.assessment.impl.CapabilityAssessmentWorkflowServiceImpl.hashInput(
                    workflow.getId().toString(), "RESUME_PARSE", String.valueOf(record.getId()));
            var parseRun = workflowService.createStageRun(workflow.getId(), "RESUME_PARSE", parseHash,
                    "{\"resumeParseId\":" + record.getId() + "}", "RESUME_PARSE", record.getId());
            lifecycleEventPublisher.publish(com.example.matching.event.CapabilityStageLifecycleEvent.of(
                    workflow.getId(), parseRun.getId(), "RESUME_PARSE",
                    "RESUME_PARSE", record.getId(),
                    com.example.matching.common.enums.StageLifecycleEventType.TASK_CLAIMED,
                    null, null));
        } catch (Exception e) {
            // 工作流状态是展示/治理元数据，更新失败不应阻断简历解析主链路。
            log.warn("发布评估工作流解析中事件失败: parseId={}, empId={}, error={}",
                    record.getId(), record.getEmpId(), e.getMessage());
        }
    }

    @Override
    @Transactional
    public void markTaskDispatchFailed(Long parseId, String errorMessage) {
        EmpResumeParse record = getById(parseId);
        if (record == null) {
            log.warn("简历解析任务投递失败，但记录不存在: parseId={}", parseId);
            return;
        }
        record.setStatus(3);
        record.setErrorMessage(errorMessage);
        record.setLastErrorType("PERMANENT");
        record.setLastErrorMessage(errorMessage);
        updateById(record);
    }

    /**
     * 处理可重试失败：增加重试计数，投递延迟队列或进入最终失败。
     */
    private void handleRetryableFailure(EmpResumeParse record, RetryableResumeParseException ex) {
        int currentRetry = record.getRetryCount() != null ? record.getRetryCount() : 0;
        int nextRetry = currentRetry + 1;

        record.setRetryCount(nextRetry);
        record.setLastErrorType(ex.getErrorType());
        record.setLastErrorMessage(truncateErrorMessage(ex.getMessage()));

        if (nextRetry >= MAX_RETRY_COUNT) {
            // 超过最大重试次数，进入最终失败
            log.warn("简历解析任务超过最大重试次数，进入最终失败: parseId={}, retryCount={}",
                    record.getId(), nextRetry);
            record.setStatus(3);
            record.setErrorMessage("重试 " + nextRetry + " 次后仍失败: " + ex.getMessage());
            updateById(record);
            publishParseFailure(record, "RESUME_PARSE_FINAL_FAILURE", "重试 " + nextRetry + " 次后仍失败: " + ex.getMessage());
        } else {
            // 投递到延迟重试队列
            String routingKey = RETRY_ROUTING_KEYS[Math.min(nextRetry - 1, RETRY_ROUTING_KEYS.length - 1)];
            record.setStatus(4); // 等待重试
            record.setNextRetryTime(calculateNextRetryTime(nextRetry));
            updateById(record);

            enqueueParseTask(routingKey, record.getId());
            log.info("简历解析任务已写入延迟重试 Outbox: parseId={}, retryCount={}, queue={}",
                    record.getId(), nextRetry, routingKey);
        }
    }

    /**
     * 处理不可重试失败：直接进入最终失败。
     */
    private void handlePermanentFailure(EmpResumeParse record, PermanentResumeParseException ex) {
        log.warn("简历解析任务遇到不可重试错误: parseId={}, error={}", record.getId(), ex.getMessage());
        record.setStatus(3);
        record.setErrorMessage(ex.getMessage());
        record.setLastErrorType(ex.getErrorType());
        record.setLastErrorMessage(truncateErrorMessage(ex.getMessage()));
        updateById(record);
        publishParseFailure(record, "RESUME_PARSE_PERMANENT_FAILURE", ex.getMessage());
    }

    /**
     * 简历解析最终失败：发布 TASK_FAILED_FINAL 生命周期事件（协调器将工作流置 FAILED）。
     */
    private void publishParseFailure(EmpResumeParse record, String errorCode, String errorMessage) {
        try {
            if (record == null || record.getEmpId() == null || workflowService == null) {
                return;
            }
            var workflow = workflowService.getActiveWorkflow(record.getEmpId());
            if (workflow == null) {
                return;
            }
            String parseHash = com.example.matching.service.assessment.impl.CapabilityAssessmentWorkflowServiceImpl.hashInput(
                    workflow.getId().toString(), "RESUME_PARSE", String.valueOf(record.getId()));
            var parseRun = workflowService.createStageRun(workflow.getId(), "RESUME_PARSE", parseHash,
                    "{\"resumeParseId\":" + record.getId() + "}", "RESUME_PARSE", record.getId());
            lifecycleEventPublisher.publish(com.example.matching.event.CapabilityStageLifecycleEvent.failedFinal(
                    workflow.getId(), parseRun.getId(), "RESUME_PARSE",
                    "RESUME_PARSE", record.getId(), errorCode, errorMessage));
        } catch (Exception e) {
            log.warn("发布简历解析失败事件异常: parseId={}, error={}", record.getId(), e.getMessage());
        }
    }

    /**
     * 人工重试：仅允许失败或死信状态的记录重试。
     */
    @Override
    @Transactional
    public EmpResumeParse retryFailedTask(Long parseId) {
        EmpResumeParse record = getById(parseId);
        if (record == null) {
            throw new BusinessException(404, "解析记录不存在");
        }
        int status = record.getStatus() != null ? record.getStatus() : -1;
        if (status != 3 && status != 4) {
            throw new BusinessException(400, "仅失败或等待重试状态的任务可重新投递，当前状态: " + status);
        }

        // 清空错误信息，重置为待处理，保留重试次数用于审计
        record.setStatus(0);
        record.setErrorMessage(null);
        record.setLastErrorMessage(null);
        record.setNextRetryTime(null);
        record.setProcessingStartedAt(null);
        updateById(record);

        enqueueParseTask("resume.parse.execute", record.getId());
        log.info("人工重试：简历解析任务已写入 Outbox: parseId={}", parseId);

        return record;
    }

    /**
     * 扫描僵尸任务：处理中超过阈值仍未完成的任务，重新进入待处理。
     * 由定时任务调用。
     */
    @Override
    @Transactional
    public int recoverZombieTasks() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(ZOMBIE_TIMEOUT_MINUTES);
        List<EmpResumeParse> zombies = list(Wrappers.<EmpResumeParse>lambdaQuery()
                .eq(EmpResumeParse::getStatus, 1)
                .lt(EmpResumeParse::getProcessingStartedAt, threshold));

        if (zombies.isEmpty()) {
            return 0;
        }

        int recovered = 0;
        for (EmpResumeParse zombie : zombies) {
            int currentRetry = zombie.getRetryCount() != null ? zombie.getRetryCount() : 0;
            if (currentRetry >= MAX_RETRY_COUNT) {
                // 已达最大重试：CAS 标记最终失败（防多实例重复）
                boolean claimed = lambdaUpdate()
                        .eq(EmpResumeParse::getId, zombie.getId())
                        .eq(EmpResumeParse::getStatus, 1)
                        .set(EmpResumeParse::getStatus, 3)
                        .set(EmpResumeParse::getErrorMessage, "僵尸任务：处理超时且已达最大重试次数")
                        .set(EmpResumeParse::getLastErrorType, "PERMANENT")
                        .update();
                if (claimed) {
                    publishParseFailure(zombie, "RESUME_PARSE_ZOMBIE_FINAL_FAILURE",
                            "僵尸任务处理超时且重试已耗尽");
                    log.warn("僵尸任务标记最终失败: parseId={}", zombie.getId());
                }
            } else {
                // 重新投递：CAS 抢占（status=1 -> 0），防止多实例重复投递
                boolean claimed = lambdaUpdate()
                        .eq(EmpResumeParse::getId, zombie.getId())
                        .eq(EmpResumeParse::getStatus, 1)
                        .set(EmpResumeParse::getStatus, 0)
                        .set(EmpResumeParse::getProcessingStartedAt, null)
                        .update();
                if (!claimed) {
                    log.debug("僵尸任务已被其他实例恢复，跳过: parseId={}", zombie.getId());
                    continue;
                }
                enqueueParseTask("resume.parse.execute", zombie.getId());
                log.info("僵尸任务已写入恢复 Outbox: parseId={}, retryCount={}", zombie.getId(), currentRetry);
                recovered++;
            }
        }
        log.info("僵尸任务扫描完成: 发现={}, 恢复={}, 总数={}", zombies.size(), recovered, zombies.size());
        return recovered;
    }

    /**
     * M27：扫描 status=4（等待重试）且 nextRetryTime 已过期但未投递的记录并补投。
     * <p>
     * 使用条件更新（CAS）防多实例重复；补投后 nextRetryTime 置空作为"已投递"标记。
     */
    @Override
    @Transactional
    public int recoverWaitingRetryTasks() {
        LocalDateTime now = LocalDateTime.now();
        List<EmpResumeParse> waiting = list(Wrappers.<EmpResumeParse>lambdaQuery()
                .eq(EmpResumeParse::getStatus, 4)
                .isNotNull(EmpResumeParse::getNextRetryTime)
                .lt(EmpResumeParse::getNextRetryTime, now)
                .last("LIMIT 100"));

        if (waiting.isEmpty()) {
            return 0;
        }

        int reEnqueued = 0;
        for (EmpResumeParse record : waiting) {
            boolean claimed = lambdaUpdate()
                    .eq(EmpResumeParse::getId, record.getId())
                    .eq(EmpResumeParse::getStatus, 4)
                    .isNotNull(EmpResumeParse::getNextRetryTime)
                    .lt(EmpResumeParse::getNextRetryTime, now)
                    .set(EmpResumeParse::getNextRetryTime, null)
                    .update();
            if (!claimed) {
                log.debug("等待重试任务已被其他实例补投，跳过: parseId={}", record.getId());
                continue;
            }
            enqueueParseTask("resume.parse.execute", record.getId());
            log.info("重新投递等待重试超时的简历解析任务: parseId={}, retryCount={}",
                    record.getId(), record.getRetryCount());
            reEnqueued++;
        }
        return reEnqueued;
    }

    /**
     * 扫描 status=0（待处理）但创建超过 10 分钟仍未投递的记录（Outbox 投递失败场景），
     * 通过 CAS 重新投递到主队列。
     */
    @Override
    @Transactional
    public int recoverUndispatchedTasks() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(10);
        List<EmpResumeParse> undispatched = list(Wrappers.<EmpResumeParse>lambdaQuery()
                .eq(EmpResumeParse::getStatus, 0)
                .lt(EmpResumeParse::getCreatedTime, threshold)
                .last("LIMIT 100"));

        if (undispatched.isEmpty()) {
            return 0;
        }

        int reEnqueued = 0;
        for (EmpResumeParse record : undispatched) {
            boolean claimed = lambdaUpdate()
                    .eq(EmpResumeParse::getId, record.getId())
                    .eq(EmpResumeParse::getStatus, 0)
                    .lt(EmpResumeParse::getCreatedTime, threshold)
                    .set(EmpResumeParse::getUpdatedTime, LocalDateTime.now())
                    .update();
            if (claimed) {
                enqueueParseTask("resume.parse.execute", record.getId());
                log.info("重新投递长时间未分发简历解析任务: parseId={}, createdTime={}", record.getId(), record.getCreatedTime());
                reEnqueued++;
            }
        }
        log.info("未分发简历补偿扫描完成: 发现={}, 重新投递={}", undispatched.size(), reEnqueued);
        return reEnqueued;
    }

    /**
     * 计算下次重试时间
     */
    private LocalDateTime calculateNextRetryTime(int retryCount) {
        return switch (retryCount) {
            case 1 -> LocalDateTime.now().plusSeconds(30);
            case 2 -> LocalDateTime.now().plusMinutes(5);
            default -> LocalDateTime.now().plusMinutes(30);
        };
    }

    private void enqueueParseTask(String routingKey, Long parseId) {
        outboxDispatcher.enqueue("RESUME_PARSE", RabbitMQConfig.MATCHING_EXCHANGE, routingKey, parseId);
    }

    /**
     * 截断错误信息，防止数据库溢出
     */
    private String truncateErrorMessage(String message) {
        if (message == null) return null;
        return message.length() > 500 ? message.substring(0, 500) + "..." : message;
    }

    @Override
    public List<EmpResumeParse> listByEmpId(Long empId) {
        return list(Wrappers.<EmpResumeParse>lambdaQuery()
                .eq(EmpResumeParse::getEmpId, empId)
                .orderByDesc(EmpResumeParse::getCreatedTime));
    }

    @Override
    @Transactional
    public EmpResumeParse reparse(Long parseId) {
        EmpResumeParse record = getById(parseId);
        if (record == null) {
            throw new BusinessException(404, "解析记录不存在");
        }
        if (record.getParsedContent() == null || record.getParsedContent().isBlank()) {
            throw new BusinessException(400, "该记录没有解析原文，无法重新分析");
        }

        record.setStatus(0);
        record.setErrorMessage(null);
        record.setRetryCount(0);
        record.setNextRetryTime(null);
        record.setProcessingStartedAt(null);
        record.setLastErrorType(null);
        record.setLastErrorMessage(null);
        updateById(record);
        eventPublisher.publishEvent(new ResumeParseQueuedEvent(record.getId()));
        return record;
    }

    /**
     * @deprecated 已废弃。简历能力正式入库统一走 saveResumeEvidenceForWorkflow（能力评估工作流证据路径），
     * 此「直接导入」旧链路保留仅供兼容，勿新增调用。
     */
    @Override
    @Deprecated
    @Transactional
    public AbilityImportResultDTO importToAbilityProfile(Long parseId) {
        return abilityImportService.importToAbilityProfile(parseId);
    }

    @Override
    @Transactional
    public int saveResumeEvidenceForWorkflow(Long parseId) {
        EmpResumeParse record = getById(parseId);
        if (record == null || record.getStatus() != 2) {
            return 0;
        }
        // 仅当存在活跃评估工作流时保存证据（旧流程员工不受影响）
        var workflow = workflowService.getActiveWorkflow(record.getEmpId());
        if (workflow == null) {
            return 0;
        }
        String aiResult = record.getAiAnalysisResult();
        if (aiResult == null || aiResult.isBlank()) {
            log.warn("简历解析结果为空，跳过证据保存: parseId={}", parseId);
            // 解析任务本身已成功（无证据可存）：发布 TASK_SUCCEEDED 推进工作流，
            // 否则阶段永远 RUNNING、工作流卡在 RESUME_PARSING（前端一直显示"解析中"）
            publishParseSucceeded(workflow.getId(), parseId);
            return 0;
        }
        // 复用 normalizer 提取能力主张（与旧导入链路同一解析入口）
        PersonAbilityExtractionResult extractionResult;
        try {
            extractionResult = claimNormalizer.normalize(aiResult);
        } catch (Exception e) {
            log.warn("解析简历能力主张失败，跳过证据保存: parseId={}, error={}", parseId, e.getMessage());
            publishParseSucceeded(workflow.getId(), parseId);
            return 0;
        }
        if (extractionResult == null || extractionResult.getClaims() == null || extractionResult.getClaims().isEmpty()) {
            log.info("简历无能力主张，跳过证据保存: parseId={}", parseId);
            publishParseSucceeded(workflow.getId(), parseId);
            return 0;
        }

        // 转换为证据 DTO（无原文证据的 Claim 由证据收集服务确定性校验拒绝）
        String expectedSourceRef = "source:RESUME_PARSE:" + parseId;
        List<com.example.matching.dto.assessment.ResumeAbilityClaimDTO> claimDtos = new ArrayList<>();
        for (PersonAbilityClaim claim : extractionResult.getClaims()) {
            String abilityName = claim.getNormalizedAbilityName() != null && !claim.getNormalizedAbilityName().isBlank()
                    ? claim.getNormalizedAbilityName() : claim.getAbilityName();
            if (abilityName == null || abilityName.isBlank() || claim.getMasteryLevel() == null) {
                continue;
            }
            com.example.matching.dto.assessment.ResumeAbilityClaimDTO dto =
                    new com.example.matching.dto.assessment.ResumeAbilityClaimDTO();
            dto.setAbilityName(abilityName);
            dto.setNormalizedAbilityName(abilityName);
            dto.setClaimedLevel(claim.getMasteryLevel());
            dto.setEvidenceText(claim.getEvidenceText());
            dto.setSourceRefId(parseId);
            dto.setConfidenceScore(claim.getConfidenceScore());
            // 证据定位：优先使用偏移，否则回退到解析记录定位
            dto.setEvidenceLocation(claim.getEvidenceStart() != null
                    ? "resume:" + parseId + ":" + claim.getEvidenceStart() + "-" + (claim.getEvidenceEnd() != null ? claim.getEvidenceEnd() : "")
                    : "resume:" + parseId + ":0");
            List<String> refs = claim.getSourceRefs() != null && !claim.getSourceRefs().isEmpty()
                    ? claim.getSourceRefs() : List.of(expectedSourceRef);
            dto.setSourceRefs(refs.stream()
                    .filter(r -> r != null && !r.contains(":null"))
                    .map(r -> r.startsWith("source:") ? r : expectedSourceRef)
                    .distinct()
                    .toList());
            claimDtos.add(dto);
        }
        if (claimDtos.isEmpty()) {
            publishParseSucceeded(workflow.getId(), parseId);
            return 0;
        }

        // 阶段运行（幂等）
        Long workflowId = workflow.getId();
        String parseHash = com.example.matching.service.assessment.impl.CapabilityAssessmentWorkflowServiceImpl.hashInput(
                workflowId.toString(), "RESUME_PARSE", String.valueOf(parseId));
        var parseRun = workflowService.createStageRun(workflowId, "RESUME_PARSE", parseHash,
                "{\"resumeParseId\":" + parseId + "}", "RESUME_PARSE", parseId);
        String extractHash = com.example.matching.service.assessment.impl.CapabilityAssessmentWorkflowServiceImpl.hashInput(
                workflowId.toString(), "RESUME_CLAIM_EXTRACTION", String.valueOf(parseId));
        var extractRun = workflowService.createStageRun(workflowId, "RESUME_CLAIM_EXTRACTION", extractHash,
                "{\"resumeParseId\":" + parseId + "}", "RESUME_PARSE", parseId);

        int saved = evidenceCollectionService.saveResumeClaims(
                workflowId, extractRun.getId(), record.getEmpId(), claimDtos, record.getCreatedBy());
        if (saved > 0) {
            evidenceCollectionService.groupClaimsByAbility(workflowId, record.getEmpId());
        }
        // 不再直接改工作流状态：发布生命周期事件，由协调器统一推进
        // （简历解析成功 -> RESUME_EVIDENCE_READY；证据提取成功同样推进）
        lifecycleEventPublisher.publish(com.example.matching.event.CapabilityStageLifecycleEvent.succeeded(
                workflowId, parseRun.getId(), "RESUME_PARSE", "RESUME_PARSE", parseId));
        if (saved > 0) {
            lifecycleEventPublisher.publish(com.example.matching.event.CapabilityStageLifecycleEvent.succeeded(
                    workflowId, extractRun.getId(), "RESUME_CLAIM_EXTRACTION", "RESUME_PARSE", parseId));
        } else {
            lifecycleEventPublisher.publish(com.example.matching.event.CapabilityStageLifecycleEvent.noEvidence(
                    workflowId, extractRun.getId(), "RESUME_CLAIM_EXTRACTION", "RESUME_PARSE", parseId));
            log.info("简历 claim 均被证据校验过滤，发布 NO_EVIDENCE 事件: workflowId={}, parseId={}",
                    workflowId, parseId);
        }
        return saved;
    }

    /**
     * 简历解析成功但无证据可存时，幂等发布 parseRun 和 extractRun 的 TASK_SUCCEEDED 生命周期事件，
     * 由协调器将阶段 RUNNING→SUCCEEDED、工作流 RESUME_PARSING→RESUME_EVIDENCE_READY。
     * 否则阶段永远 RUNNING，Reconciler 补偿规则全部失效（工作流卡在"解析中"）。
     * <p>
     * 必须同时创建 RESUME_CLAIM_EXTRACTION 的 stage run：协调器在 RESUME_PARSE SUCCEEDED 后
     * 会将 workflow.currentStage 设为 RESUME_CLAIM_EXTRACTION，该阶段必须有 SUCCEEDED 记录
     * 才能通过 generateTest → assertStagePrerequisite 的前置校验。
     */
    private void publishParseSucceeded(Long workflowId, Long parseId) {
        String parseHash = com.example.matching.service.assessment.impl.CapabilityAssessmentWorkflowServiceImpl.hashInput(
                workflowId.toString(), "RESUME_PARSE", String.valueOf(parseId));
        var parseRun = workflowService.createStageRun(workflowId, "RESUME_PARSE", parseHash,
                "{\"resumeParseId\":" + parseId + "}", "RESUME_PARSE", parseId);
        lifecycleEventPublisher.publish(com.example.matching.event.CapabilityStageLifecycleEvent.succeeded(
                workflowId, parseRun.getId(), "RESUME_PARSE", "RESUME_PARSE", parseId));
        // 同步创建 RESUME_CLAIM_EXTRACTION 阶段运行，发布 NO_EVIDENCE（无证据）而非 TASK_SUCCEEDED，
        // 使工作流进入 RESUME_PARSED_NO_EVIDENCE 状态。协调器将 RESUME_CLAIM_EXTRACTION 标记 SUCCEEDED，
        // 保证后续 generateTest → assertStagePrerequisite 的前置校验通过。
        String extractHash = com.example.matching.service.assessment.impl.CapabilityAssessmentWorkflowServiceImpl.hashInput(
                workflowId.toString(), "RESUME_CLAIM_EXTRACTION", String.valueOf(parseId));
        var extractRun = workflowService.createStageRun(workflowId, "RESUME_CLAIM_EXTRACTION", extractHash,
                "{\"resumeParseId\":" + parseId + "}", "RESUME_PARSE", parseId);
        lifecycleEventPublisher.publish(com.example.matching.event.CapabilityStageLifecycleEvent.noEvidence(
                workflowId, extractRun.getId(), "RESUME_CLAIM_EXTRACTION", "RESUME_PARSE", parseId));
        log.info("简历解析成功但无证据，发布 NO_EVIDENCE 事件: workflowId={}, parseId={}", workflowId, parseId);
    }

    private void executeParse(EmpResumeParse record) {
        fileParser.executeParse(record);
    }

    private String getFileType(String filename) {
        return fileParser.getFileType(filename);
    }

    private String saveFile(String originalFilename, byte[] content, Long empId) {
        return fileParser.saveFile(originalFilename, content, empId);
    }

    private String computeSha256(byte[] bytes) {
        return fileParser.computeSha256(bytes);
    }
}
