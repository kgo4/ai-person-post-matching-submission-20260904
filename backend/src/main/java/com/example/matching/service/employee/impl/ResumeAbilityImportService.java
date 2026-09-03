package com.example.matching.service.employee.impl;

import com.example.matching.agent.dto.person.PersonAbilityClaim;
import com.example.matching.agent.dto.person.PersonAbilityExtractionResult;
import com.example.matching.service.agent.AgentBusinessApplyService;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.util.PersonAbilityClaimNormalizer;
import com.example.matching.dto.system.AbilityImportResultDTO;
import com.example.matching.entity.employee.EmpResumeParse;
import com.example.matching.event.AbilityChangeEvent;
import com.example.matching.mapper.employee.EmpResumeParseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 简历能力导入：把解析结果写入员工能力画像。
 * <p>
 * 从 ResumeParseServiceImpl（980+ 行）中拆分的能力导入组件。
 *
 * @deprecated 已废弃。简历能力正式入库统一走「能力评估工作流证据路径」
 * （ResumeParseServiceImpl.saveResumeEvidenceForWorkflow → AbilityEvidenceCollectionServiceImpl），
 * 或经 GovernedAdmissionServiceImpl 治理准入；此「直接导入」旧链路保留仅供兼容，勿新增调用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Deprecated
public class ResumeAbilityImportService {

    private final EmpResumeParseMapper empResumeParseMapper;
    private final AgentBusinessApplyService agentBusinessApplyService;
    private final ApplicationEventPublisher eventPublisher;
    private final PersonAbilityClaimNormalizer claimNormalizer;

    public AbilityImportResultDTO importToAbilityProfile(Long parseId) {
        EmpResumeParse parseRecord = empResumeParseMapper.selectById(parseId);
        if (parseRecord == null || parseRecord.getStatus() != 2) {
            throw new BusinessException(400, "解析记录不存在或未完成");
        }

        try {
            PersonAbilityExtractionResult extractionResult = parseExtractionResult(parseRecord);

            if (extractionResult == null || extractionResult.getClaims() == null || extractionResult.getClaims().isEmpty()) {
                log.warn("简历导入: 解析结果为空或无claims: parseId={}", parseId);
                markImportState(parseRecord, "NO_CLAIMS", "解析结果为空或无能力声明");
                return AbilityImportResultDTO.empty();
            }

            log.info("简历导入 parsed claims: parseId={}, count={}", parseId, extractionResult.getClaims().size());

            int totalValidClaims = countValidClaims(extractionResult);
            log.info("简历导入 valid claims (abilityName+masteryLevel都非空): parseId={}, count={}", parseId, totalValidClaims);

            AbilityImportResultDTO admissionResult = normalizeClaimsForImport(parseRecord, extractionResult);
            log.info("简历导入声明规范化完成: parseId={}, validClaims={}, rejected={}",
                    parseId, extractionResult.getClaims().size(),
                    admissionResult.getRejected());

            String expectedSourceRef = "source:RESUME_PARSE:" + parseId;
            for (PersonAbilityClaim claim : extractionResult.getClaims()) {
                claim.setEmpId(parseRecord.getEmpId());
                claim.setSourceType("RESUME_PARSE");
                claim.setSourceRefId(parseId);
                List<String> refs = claim.getSourceRefs();
                boolean needsRewrite = refs == null || refs.isEmpty()
                        || refs.stream().anyMatch(r -> r == null || r.contains(":null") || r.contains("RESUME_PARSE:") && !r.equals(expectedSourceRef));
                if (needsRewrite) {
                    claim.setSourceRefs(List.of(expectedSourceRef));
                }
            }

            AgentBusinessApplyService.PersonAbilityApplyResult applyResult =
                    agentBusinessApplyService.applyPersonAbilities(extractionResult, true);

            log.info("简历导入 apply result: parseId={}, total={}, pass={}, review={}, block={}, error={}",
                    parseId, applyResult.getTotalClaims(), applyResult.getPassCount(),
                    applyResult.getReviewCount(), applyResult.getBlockCount(), applyResult.getErrorCount());

            if (applyResult.getPassCount() > 0) {
                eventPublisher.publishEvent(new AbilityChangeEvent(this, "EMP_ABILITY", parseRecord.getEmpId()));
            }

            AbilityImportResultDTO finalResult = buildFinalImportResult(
                    totalValidClaims, applyResult, admissionResult);
            log.info("简历导入结果: parseId={}, {}", parseId, finalResult.getMessage());

            String importStatus = finalResult.getImported() > 0
                    ? "SUCCEEDED"
                    : (finalResult.getCandidate() > 0 ? "REVIEW_REQUIRED" : "BLOCKED");
            markImportState(parseRecord, importStatus, finalResult.getMessage());
            return finalResult;

        } catch (Exception e) {
            log.error("导入简历解析结果失败: {}", e.getMessage(), e);
            markImportState(parseRecord, "FAILED", "导入失败: " + truncate(e.getMessage(), 200));
            throw new BusinessException(500, "导入失败: " + e.getMessage());
        }
    }

    /**
     * 回写 V90 简历自动导入状态字段（此前为纯 DB 死状态机，无人读写）。
     */
    private void markImportState(EmpResumeParse parseRecord, String status, String summary) {
        try {
            parseRecord.setAbilityImportStatus(status);
            parseRecord.setAbilityImportSummary(truncate(summary, 480));
            parseRecord.setAbilityImportedAt(java.time.LocalDateTime.now());
            empResumeParseMapper.updateById(parseRecord);
        } catch (Exception e) {
            log.warn("回写简历导入状态失败: parseId={}, status={}, error={}",
                    parseRecord.getId(), status, e.getMessage());
        }
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) return null;
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private int countValidClaims(PersonAbilityExtractionResult extractionResult) {
        if (extractionResult == null || extractionResult.getClaims() == null) {
            return 0;
        }
        int count = 0;
        for (PersonAbilityClaim claim : extractionResult.getClaims()) {
            String name = firstNonBlank(claim.getNormalizedAbilityName(), claim.getAbilityName());
            if (name != null && claim.getMasteryLevel() != null) {
                count++;
            }
        }
        return count;
    }

    private AbilityImportResultDTO buildFinalImportResult(
            int totalValidClaims,
            AgentBusinessApplyService.PersonAbilityApplyResult applyResult,
            AbilityImportResultDTO admissionResult) {

        int candidate = admissionResult != null ? admissionResult.getCandidate() : 0;
        int rejected = (admissionResult != null ? admissionResult.getRejected() : 0)
                + applyResult.getBlockCount() + applyResult.getErrorCount();

        AbilityImportResultDTO result = AbilityImportResultDTO.builder()
                .total(totalValidClaims)
                .imported(applyResult.getPassCount())
                .reused(applyResult.getPassCount())
                .created(0)
                .candidate(candidate + applyResult.getReviewCount())
                .rejected(rejected)
                .importedAbilityIds(new ArrayList<>())
                .candidateIds(admissionResult != null ? admissionResult.getCandidateIds() : new ArrayList<>())
                .rejections(admissionResult != null ? admissionResult.getRejections() : new ArrayList<>())
                .build();
        result.setMessage(result.buildMessage());
        return result;
    }

    /**
     * 解析简历 AI 结果：统一经 {@link PersonAbilityClaimNormalizer} 规范化为 claims[]。
     * 旧格式兼容（abilities/abilityClaims/skills、tagName/level 等）已收敛到 normalizer，
     * 本服务不再探测旧字段。
     */
    private PersonAbilityExtractionResult parseExtractionResult(EmpResumeParse parseRecord) {
        String aiResult = parseRecord.getAiAnalysisResult();
        if (aiResult == null || aiResult.isBlank()) {
            log.warn("简历解析结果为空: parseId={}", parseRecord.getId());
            return null;
        }

        log.info("简历导入 raw result: parseId={}, aiResultLength={}, aiResultPreview={}",
                parseRecord.getId(), aiResult.length(),
                aiResult.length() > 500 ? aiResult.substring(0, 500) + "..." : aiResult);

        try {
            PersonAbilityExtractionResult result = claimNormalizer.normalize(aiResult);
            log.info("简历导入 parsed claims: parseId={}, count={}",
                    parseRecord.getId(), result.getClaims() != null ? result.getClaims().size() : 0);
            return result;
        } catch (Exception e) {
            log.warn("解析简历分析结果失败: parseId={}, error={}", parseRecord.getId(), e.getMessage());
            return null;
        }
    }

    private AbilityImportResultDTO normalizeClaimsForImport(EmpResumeParse parseRecord, PersonAbilityExtractionResult extractionResult) {
        if (extractionResult == null || extractionResult.getClaims() == null) {
            return AbilityImportResultDTO.empty();
        }
        extractionResult.setEmpId(parseRecord.getEmpId());
        extractionResult.setSourceType("RESUME_PARSE");
        extractionResult.setSourceRefId(parseRecord.getId());

        List<PersonAbilityClaim> validClaims = new ArrayList<>();
        List<AbilityImportResultDTO.RejectionDetail> rejections = new ArrayList<>();

        for (PersonAbilityClaim claim : extractionResult.getClaims()) {
            if (claim == null) {
                continue;
            }
            String abilityName = firstNonBlank(claim.getNormalizedAbilityName(), claim.getAbilityName());
            if (abilityName == null || claim.getMasteryLevel() == null) {
                log.warn("skip invalid resume claim: abilityName={}, level={}", abilityName, claim.getMasteryLevel());
                rejections.add(AbilityImportResultDTO.RejectionDetail.builder()
                        .tagName(abilityName)
                        .reason("能力名称或等级缺失")
                        .build());
                continue;
            }

            claim.setEmpId(parseRecord.getEmpId());
            claim.setSourceType("RESUME_PARSE");
            claim.setSourceRefId(parseRecord.getId());
            claim.setAbilityName(abilityName);
            claim.setNormalizedAbilityName(abilityName);
            if (claim.getEvidenceText() == null || claim.getEvidenceText().isBlank()) {
                claim.setEvidenceText("从简历解析导入");
            }
            if (claim.getSourceRefs() == null || claim.getSourceRefs().isEmpty()) {
                claim.setSourceRefs(List.of("source:RESUME_PARSE:" + parseRecord.getId()));
            }

            validClaims.add(claim);
        }

        // 人员能力与系统标签库独立。这里保留能力名称和原文证据，
        // 由人员能力应用链路处理，绝不创建/候选系统标签。
        extractionResult.setClaims(validClaims);
        return AbilityImportResultDTO.builder()
                .total(validClaims.size() + rejections.size())
                .imported(0)
                .reused(0)
                .created(0)
                .candidate(0)
                .rejected(rejections.size())
                .importedAbilityIds(new ArrayList<>())
                .candidateIds(new ArrayList<>())
                .rejections(rejections)
                .build();
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }
}
