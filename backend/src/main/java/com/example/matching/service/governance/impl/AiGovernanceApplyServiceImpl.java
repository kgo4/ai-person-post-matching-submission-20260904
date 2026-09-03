package com.example.matching.service.governance.impl;

import com.example.matching.entity.harness.AiHarnessCheckLog;
import com.example.matching.mapper.harness.AiHarnessCheckLogMapper;
import com.example.matching.service.governance.AiGovernanceApplyService;
import com.example.matching.service.governance.enums.AiGovernanceClaimType;
import com.example.matching.service.governance.enums.AiGovernanceReviewStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * AI 治理应用服务 - 仅更新治理记录。
 * <p>
 * 能力声明的采纳/驳回编排已上移到应用层 {@code GovernanceReviewWorkflow}，
 * 本类不再依赖 service.ability，消除 governance→ability 反向服务依赖（架构循环）。
 */
@Slf4j
@Service
public class AiGovernanceApplyServiceImpl implements AiGovernanceApplyService {

    private final AiHarnessCheckLogMapper harnessCheckLogMapper;

    public AiGovernanceApplyServiceImpl(AiHarnessCheckLogMapper harnessCheckLogMapper) {
        this.harnessCheckLogMapper = harnessCheckLogMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean applyToBusiness(Long harnessLogId, String reviewComment) {
        log.warn("applyToBusiness 已废弃，请使用 acceptReview");
        return acceptReview(harnessLogId, reviewComment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean acceptReview(Long harnessLogId, String reviewComment) {
        AiHarnessCheckLog checkLog = harnessCheckLogMapper.selectById(harnessLogId);
        if (checkLog == null) {
            log.warn("治理记录不存在: id={}", harnessLogId);
            return false;
        }

        String currentStatus = checkLog.getReviewStatus();
        if (!isReviewable(currentStatus, harnessLogId)) {
            log.warn("治理记录状态不允许采纳: id={}, status={}", harnessLogId, currentStatus);
            return false;
        }

        boolean personnelClaim = "EMP_ABILITY".equals(checkLog.getClaimType());
        checkLog.setReviewStatus(AiGovernanceReviewStatus.ACCEPTED.name());
        checkLog.setReviewComment(reviewComment);
        checkLog.setReviewedTime(LocalDateTime.now());
        checkLog.setBusinessApplyStatus(personnelClaim ? "APPLIED" : "MANUAL_ACCEPTED");

        String claimType = checkLog.getClaimType();
        // Harness persists the actual business target before review. Do not
        // replace it with the claim type: for personnel claims the two codes
        // can differ, and the target is required to resolve the employee.
        if ((checkLog.getBusinessTargetType() == null || checkLog.getBusinessTargetType().isBlank())
                && claimType != null) {
            checkLog.setBusinessTargetType(claimType);
        }

        harnessCheckLogMapper.updateById(checkLog);
        log.info("治理记录已采纳: id={}, claimType={}, scenario={}",
                harnessLogId, claimType, checkLog.getScenario());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean rejectReview(Long harnessLogId, String reviewComment) {
        AiHarnessCheckLog checkLog = harnessCheckLogMapper.selectById(harnessLogId);
        if (checkLog == null) {
            return false;
        }
        String currentStatus = checkLog.getReviewStatus();
        if (!isReviewable(currentStatus, harnessLogId)) {
            return false;
        }
        checkLog.setReviewStatus(AiGovernanceReviewStatus.REJECTED.name());
        checkLog.setReviewComment(reviewComment);
        checkLog.setReviewedTime(LocalDateTime.now());
        checkLog.setBusinessApplyStatus("SKIPPED");
        harnessCheckLogMapper.updateById(checkLog);
        return true;
    }

    @Override
    public AiHarnessCheckLog getCheckLog(Long harnessLogId) {
        return harnessCheckLogMapper.selectById(harnessLogId);
    }

    private boolean isReviewable(String status, Long harnessLogId) {
        return AiGovernanceReviewStatus.PENDING.name().equals(status)
                || "REVIEW".equals(status)
                || ("AUTO_PASSED".equals(status)
                && harnessCheckLogMapper.countPendingLevelDecision(harnessLogId) > 0);
    }
}
