package com.example.matching.application.governance;

import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.service.ability.PersonAbilityClaimAdmissionService;
import com.example.matching.service.assessment.AggregateAbilityHarnessReviewService;
import com.example.matching.service.governance.AiGovernanceApplyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 治理评审工作流（应用层编排）：
 * <p>
 * 先应用治理记录更新（AiGovernanceApplyService），再采纳/驳回能力声明
 * （PersonAbilityClaimAdmissionService）。两个写操作在同一事务内，保持 all-or-nothing：
 * 任一步失败都抛出异常触发整体回滚，禁止"治理记录已更新、声明仍 PENDING"的半提交。
 * <p>
 * 该编排从服务层上移到应用层，消除 governance→ability 的反向服务依赖（架构循环）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GovernanceReviewWorkflow {

    private final AiGovernanceApplyService governanceApplyService;
    private final PersonAbilityClaimAdmissionService personClaimAdmissionService;
    private final AggregateAbilityHarnessReviewService aggregateHarnessReviewService;

    /**
     * 采纳评审：先更新治理记录，成功后采纳能力声明。
     * <p>
     * 任何一步失败都抛出 {@link BusinessException}，由本方法上的事务整体回滚，
     * 保证人工采纳是 all-or-nothing。
     * <p>
     * BLOCK 决策的治理记录（含聚合 PERSON_ABILITY_AGGREGATE 生成的记录）：
     * 人工采纳 = 确认 Harness 的否决结论，能力不成立、不写入正式事实表，
     * 因此无需也不应执行 claim 融合（聚合记录本身不关联单条 claim，
     * findByHarnessLogId 会落空导致"claim admission failed"）。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean acceptReview(Long harnessLogId, String reviewComment) {
        return acceptReview(harnessLogId, reviewComment, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean acceptReview(Long harnessLogId, String reviewComment, boolean forceOverride) {
        boolean applied = governanceApplyService.acceptReview(harnessLogId, reviewComment);
        if (!applied) {
            log.warn("Governance accept did not apply, skipping claim admission: harnessLogId={}", harnessLogId);
            throw new BusinessException(ErrorCodeEnum.GOVERNANCE_REVIEW_FAILED, "治理复审未通过，状态未变更");
        }
        com.example.matching.entity.harness.AiHarnessCheckLog checkLog = governanceApplyService.getCheckLog(harnessLogId);
        // BLOCK 决策：人工采纳 = 确认 Harness 否决，能力不成立，无需 claim 融合。
        // 聚合记录（AGGREGATE_HARNESS / PERSON_ABILITY_AGGREGATE）：审核对象是能力组，
        // 组内 claim 无 harness_log_id 关联（check_log 由聚合层生成、不下发到 claim），
        // 单条 claim 融合链路不适用——人工采纳确认 Harness 复核结论，正式化由
        // claim_group 状态流转（tag 解析/确认）驱动，此处不执行 claim 融合。
        boolean aggregateRecord = aggregateHarnessReviewService.isAggregateHarnessReview(harnessLogId)
                || (checkLog != null && (
                "AGGREGATE_HARNESS".equals(checkLog.getSourceType())
                        || "PERSON_ABILITY_AGGREGATE".equals(checkLog.getScenario())));
        if (checkLog != null && "BLOCK".equals(checkLog.getDecision()) && !forceOverride) {
            log.info("治理记录采纳完成（决策={}, 聚合={}，不执行单条 claim 融合）: harnessLogId={}",
                    checkLog.getDecision(), aggregateRecord, harnessLogId);
            return true;
        }
        if (aggregateRecord) {
            if ("BLOCK".equals(checkLog.getDecision())) {
                requireOverrideComment(reviewComment);
            }
            aggregateHarnessReviewService.acceptAndProject(harnessLogId, reviewComment);
            return true;
        }
        if (isEmployeeAbilityEvidenceReview(checkLog)) {
            log.info("人员能力事实证据审核已采纳，无需 claim 融合: harnessLogId={}", harnessLogId);
            return true;
        }
        if (isResumeCapabilityExtractionReview(checkLog)) {
            log.info("简历提取能力审核已采纳，仅保留范围/标签证据，不执行 claim 融合: harnessLogId={}", harnessLogId);
            return true;
        }
        if (isNonPersonClaimReview(checkLog)) {
            log.info("非人员能力治理记录已采纳，无需人员 claim 准入: harnessLogId={}, claimType={}",
                    harnessLogId, checkLog != null ? checkLog.getClaimType() : null);
            return true;
        }
        boolean admitted = personClaimAdmissionService.acceptReview(harnessLogId);
        if (!admitted) {
            log.warn("Ability claim admission failed after governance accept: harnessLogId={}", harnessLogId);
            throw new BusinessException(ErrorCodeEnum.INTERNAL_ERROR, "claim admission failed: harnessLogId=" + harnessLogId);
        }
        return true;
    }

    /**
     * 驳回评审：先更新治理记录，成功后驳回能力声明。
     * <p>
     * 任何一步失败都抛出 {@link BusinessException}，由本方法上的事务整体回滚，
     * 避免治理记录 REJECTED、声明仍 PENDING 的半提交。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean rejectReview(Long harnessLogId, String reviewComment) {
        boolean applied = governanceApplyService.rejectReview(harnessLogId, reviewComment);
        if (!applied) {
            log.warn("Governance reject did not apply, skipping claim rejection: harnessLogId={}", harnessLogId);
            throw new BusinessException(ErrorCodeEnum.GOVERNANCE_REVIEW_FAILED, "治理复审未通过，状态未变更");
        }
        com.example.matching.entity.harness.AiHarnessCheckLog checkLog = governanceApplyService.getCheckLog(harnessLogId);
        boolean aggregateRecord = aggregateHarnessReviewService.isAggregateHarnessReview(harnessLogId)
                || (checkLog != null && (
                "AGGREGATE_HARNESS".equals(checkLog.getSourceType())
                        || "PERSON_ABILITY_AGGREGATE".equals(checkLog.getScenario())));
        if (aggregateRecord) {
            aggregateHarnessReviewService.rejectAndFinalize(harnessLogId, reviewComment);
            return true;
        }
        if (isEmployeeAbilityEvidenceReview(checkLog)) {
            log.info("人员能力事实证据审核已驳回，无需 claim 融合: harnessLogId={}", harnessLogId);
            return true;
        }
        if (isResumeCapabilityExtractionReview(checkLog)) {
            log.info("简历提取能力审核已驳回，仅保留审核记录: harnessLogId={}", harnessLogId);
            return true;
        }
        if (isNonPersonClaimReview(checkLog)) {
            log.info("非人员能力治理记录已驳回，无需人员 claim 驳回: harnessLogId={}, claimType={}",
                    harnessLogId, checkLog != null ? checkLog.getClaimType() : null);
            return true;
        }
        boolean rejected = personClaimAdmissionService.rejectReview(harnessLogId);
        if (!rejected) {
            log.warn("Ability claim rejection failed after governance reject: harnessLogId={}", harnessLogId);
            throw new BusinessException(ErrorCodeEnum.INTERNAL_ERROR, "claim rejection failed: harnessLogId=" + harnessLogId);
        }
        return true;
    }

    private boolean isEmployeeAbilityEvidenceReview(
            com.example.matching.entity.harness.AiHarnessCheckLog checkLog) {
        return checkLog != null
                && "EMP_ABILITY".equals(checkLog.getClaimType())
                && "EMP_ABILITY".equals(checkLog.getBusinessTargetType())
                && checkLog.getBusinessTargetId() != null
                && checkLog.getSourceRefs() != null
                && checkLog.getSourceRefs().contains("fact:EMP_ABILITY:" + checkLog.getBusinessTargetId());
    }

    private void requireOverrideComment(String reviewComment) {
        if (reviewComment == null || reviewComment.isBlank()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "强制覆盖自动拦截时必须填写原因");
        }
    }

    private boolean isResumeCapabilityExtractionReview(
            com.example.matching.entity.harness.AiHarnessCheckLog checkLog) {
        return checkLog != null
                && "RESUME_PARSE".equals(checkLog.getScenario())
                && "ABILITY_TAG".equals(checkLog.getClaimType());
    }

    /** 岗位模型与匹配反馈不是人员能力 claim，不能路由到人员准入服务。 */
    private boolean isNonPersonClaimReview(
            com.example.matching.entity.harness.AiHarnessCheckLog checkLog) {
        if (checkLog == null) {
            return false;
        }
        return "POST_ABILITY_MODEL".equals(checkLog.getClaimType())
                || "MATCHING_RECORD".equals(checkLog.getClaimType())
                || "POST_ABILITY_MODEL".equals(checkLog.getBusinessTargetType())
                || "MATCHING_RECORD".equals(checkLog.getBusinessTargetType());
    }
}
