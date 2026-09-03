package com.example.matching.application.governance;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.dto.governance.api.AiHarnessCheckLogResponse;
import com.example.matching.dto.governance.api.AssessmentHarnessPersonGroupResponse;
import com.example.matching.dto.governance.api.AssessmentHarnessReviewView;
import com.example.matching.dto.governance.api.BatchHarnessReviewRequest;
import com.example.matching.dto.governance.api.BatchHarnessReviewResult;
import com.example.matching.dto.governance.api.HarnessCheckRequest;
import com.example.matching.entity.harness.AiHarnessCheckLog;
import com.example.matching.service.governance.AiGovernanceApplyService;
import com.example.matching.service.system.AuditQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class AiGovernanceHarnessApiFacade {

    private static final Set<String> VALID_REVIEW_STATUS = Set.of("ACCEPTED", "REJECTED", "RESOLVED");

    private final AuditQueryService auditService;
    private final AiGovernanceApplyService applyService;
    private final GovernanceReviewWorkflow governanceReviewWorkflow;

    public PageResponse<AiHarnessCheckLogResponse> pageChecks(long current, long size, String scenario, String decision,
                                                              String reviewStatus, String riskLevel, String claimType,
                                                              Integer isSelfEvidence, Boolean assessmentOnly) {
        IPage<AiHarnessCheckLog> page = auditService.pageHarness(new Page<>(current, size), decision, scenario,
                reviewStatus, riskLevel, claimType, isSelfEvidence, assessmentOnly);
        Map<Long, AuditQueryService.HarnessPerson> persons = auditService.resolveHarnessPersons(page.getRecords());
        return PageResponse.from(page, e -> toResponse(e, persons.get(e.getId())));
    }

    public Map<String, Object> summary(Boolean assessmentOnly) {
        Map<String, Object> summary = new LinkedHashMap<>();
        long passCount = auditService.countHarnessByStatus("PASS", assessmentOnly);
        long reviewCount = auditService.countHarnessByStatus("REVIEW", assessmentOnly);
        long blockCount = auditService.countHarnessByStatus("BLOCK", assessmentOnly);

        summary.put("passCount", passCount);
        summary.put("reviewCount", reviewCount);
        summary.put("blockCount", blockCount);
        summary.put("totalCount", passCount + reviewCount + blockCount);
        summary.put("highRiskCount", auditService.countHarnessByRiskLevel("HIGH", assessmentOnly));
        summary.put("mediumRiskCount", auditService.countHarnessByRiskLevel("MEDIUM", assessmentOnly));
        summary.put("selfEvidenceCount", auditService.countHarnessSelfEvidence(assessmentOnly));
        summary.put("pendingCount", auditService.countHarnessByReviewStatus("PENDING", assessmentOnly));
        return summary;
    }

    /**
     * Personnel final-review work is organized by employee, not by individual
     * Harness record. Processed records are returned only from HISTORY.
     */
    public List<AssessmentHarnessPersonGroupResponse> listAssessmentPersonGroups(
            AssessmentHarnessReviewView view) {
        AssessmentHarnessReviewView effectiveView = view == null
                ? AssessmentHarnessReviewView.PENDING : view;
        List<AiHarnessCheckLog> logs = auditService
                .listAssessmentHarnessByReviewStatuses(effectiveView.reviewStatuses());
        Map<Long, AuditQueryService.HarnessPerson> persons = auditService.resolveHarnessPersons(logs);
        Map<String, AssessmentGroupAccumulator> groups = new LinkedHashMap<>();

        for (AiHarnessCheckLog log : logs) {
            AuditQueryService.HarnessPerson person = persons.get(log.getId());
            // 人员评估审核必须一条记录归属一个真实员工；历史孤儿记录不再
            // 伪装成“待关联人员”展示，员工删除时由级联清理移除。
            if (person == null) {
                continue;
            }
            String key = person == null ? "unassigned" : "emp:" + person.empId();
            AssessmentGroupAccumulator group = groups.computeIfAbsent(key,
                    ignored -> AssessmentGroupAccumulator.forPerson(person));
            group.add(log, toResponse(log, person), isSafeForBatchAccept(log));
        }

        return groups.values().stream()
                .sorted(Comparator.comparing(AssessmentGroupAccumulator::latestTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(AssessmentGroupAccumulator::toResponse)
                .toList();
    }

    public void updateReviewStatus(Long id, HarnessCheckRequest req) {
        if (!hasText(req.reviewStatus())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "reviewStatus不能为空");
        }
        if (!VALID_REVIEW_STATUS.contains(req.reviewStatus())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "reviewStatus必须为 ACCEPTED / REJECTED / RESOLVED 之一");
        }

        AiHarnessCheckLog log = auditService.getHarnessById(id);
        if (log == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "治理记录不存在");
        }

        String currentStatus = log.getReviewStatus();
        boolean manualDecision = "ACCEPTED".equals(req.reviewStatus())
                || "REJECTED".equals(req.reviewStatus());
        boolean pendingAssessmentReview = "PENDING".equals(currentStatus)
                || "REVIEW".equals(currentStatus)
                || ("AUTO_PASSED".equals(currentStatus) && auditService.hasPendingLevelDecision(id));
        if (manualDecision && !pendingAssessmentReview) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "Harness record is already processed");
        }
        if (!manualDecision && currentStatus != null
                && !currentStatus.equals("PENDING") && !currentStatus.equals("AUTO_PASSED")) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "该记录已审核（" + currentStatus + "），不能重复操作");
        }

        if ("REJECTED".equals(req.reviewStatus()) && !hasText(req.reviewComment())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "驳回时必须填写拒绝原因");
        }

        if ("RESOLVED".equals(req.reviewStatus()) && !hasText(req.reviewComment())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "标记已处理时必须填写处理说明");
        }

        if ("ACCEPTED".equals(req.reviewStatus())) {
            boolean forceOverride = Boolean.TRUE.equals(req.forceOverride());
            if ("BLOCK".equals(log.getDecision()) && !forceOverride) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "自动 BLOCK 记录不可采纳；如确属误判，请使用人工修改");
            }
            if (forceOverride && !"BLOCK".equals(log.getDecision())) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "仅自动 BLOCK 的能力可强制覆盖");
            }
            if (forceOverride && !hasText(req.reviewComment())) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "强制覆盖时必须填写原因");
            }
            if (!governanceReviewWorkflow.acceptReview(id, req.reviewComment(), forceOverride)) {
                throw new BusinessException(ErrorCodeEnum.INTERNAL_ERROR, "Harness claim acceptance failed");
            }
            return;
        }
        if ("REJECTED".equals(req.reviewStatus())) {
            if (!governanceReviewWorkflow.rejectReview(id, req.reviewComment())) {
                throw new BusinessException(ErrorCodeEnum.INTERNAL_ERROR, "Harness claim rejection failed");
            }
            return;
        }

        log.setReviewStatus(req.reviewStatus());
        log.setReviewComment(req.reviewComment());
        log.setReviewedTime(LocalDateTime.now());
        auditService.updateHarnessLog(log);
    }

    /**
     * 批量审核始终复用单条审核编排，以保留正式能力投影、审计和并发状态校验。
     * 批量通过仅允许非高风险、非自证据且 Harness 判定为 PASS 的待审核记录。
     */
    public BatchHarnessReviewResult batchReview(BatchHarnessReviewRequest request) {
        if (request == null || request.ids() == null || request.ids().isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "至少选择一条待审核记录");
        }
        if (request.ids().size() > 100) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "一次最多审核100条记录");
        }
        if (!"ACCEPTED".equals(request.reviewStatus()) && !"REJECTED".equals(request.reviewStatus())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "批量审核仅支持 ACCEPTED 或 REJECTED");
        }
        if ("REJECTED".equals(request.reviewStatus())
                && (!hasText(request.reviewComment()) || !hasText(request.rejectReasonCategory()))) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "批量驳回时必须填写原因分类和说明");
        }

        List<BatchHarnessReviewResult.ItemResult> results = new ArrayList<>();
        for (Long id : request.ids().stream().filter(java.util.Objects::nonNull).distinct().toList()) {
            try {
                AiHarnessCheckLog log = auditService.getHarnessById(id);
                if (log == null) {
                    results.add(new BatchHarnessReviewResult.ItemResult(id, false, "治理记录不存在"));
                    continue;
                }
                if (!"PENDING".equals(log.getReviewStatus())) {
                    results.add(new BatchHarnessReviewResult.ItemResult(id, false, "记录已处理"));
                    continue;
                }
                if ("ACCEPTED".equals(request.reviewStatus()) && !isSafeForBatchAccept(log)) {
                    results.add(new BatchHarnessReviewResult.ItemResult(id, false, "不满足批量通过条件，请逐条审核"));
                    continue;
                }
                updateReviewStatus(id, new HarnessCheckRequest(request.reviewStatus(), request.reviewComment(),
                        request.rejectReasonCategory(), false, false));
                results.add(new BatchHarnessReviewResult.ItemResult(id, true, null));
            } catch (BusinessException e) {
                results.add(new BatchHarnessReviewResult.ItemResult(id, false, e.getMessage()));
            } catch (Exception e) {
                results.add(new BatchHarnessReviewResult.ItemResult(id, false, "审核执行失败，请稍后重试"));
            }
        }
        int successCount = (int) results.stream().filter(BatchHarnessReviewResult.ItemResult::success).count();
        return new BatchHarnessReviewResult(successCount, results.size() - successCount, results);
    }

    private boolean isSafeForBatchAccept(AiHarnessCheckLog log) {
        return "PASS".equals(log.getDecision())
                && !"HIGH".equals(log.getRiskLevel())
                && !Integer.valueOf(1).equals(log.getIsSelfEvidence());
    }

    private static final class AssessmentGroupAccumulator {
        private final Long empId;
        private final String empName;
        private final String empCode;
        private final List<AiHarnessCheckLogResponse> items = new ArrayList<>();
        private int pendingCount;
        private int safeAiAcceptCount;
        private LocalDateTime latestTime;

        private AssessmentGroupAccumulator(Long empId, String empName, String empCode) {
            this.empId = empId;
            this.empName = empName;
            this.empCode = empCode;
        }

        static AssessmentGroupAccumulator forPerson(AuditQueryService.HarnessPerson person) {
            return person == null
                    ? new AssessmentGroupAccumulator(null, "待关联人员", null)
                    : new AssessmentGroupAccumulator(person.empId(), person.empName(), person.empCode());
        }

        void add(AiHarnessCheckLog log, AiHarnessCheckLogResponse response, boolean safeAiAccept) {
            items.add(response);
            if ("PENDING".equals(log.getReviewStatus())) {
                pendingCount++;
            }
            if (safeAiAccept) {
                safeAiAcceptCount++;
            }
            LocalDateTime activityTime = log.getReviewedTime() != null
                    ? log.getReviewedTime() : log.getCreatedTime();
            if (latestTime == null || (activityTime != null && activityTime.isAfter(latestTime))) {
                latestTime = activityTime;
            }
        }

        LocalDateTime latestTime() {
            return latestTime;
        }

        AssessmentHarnessPersonGroupResponse toResponse() {
            return new AssessmentHarnessPersonGroupResponse(empId, empName, empCode, List.copyOf(items),
                    items.size(), pendingCount, safeAiAcceptCount);
        }
    }

    /**
     * @deprecated 使用 {@link #acceptReview(Long, String)} 代替：/checks/{id}/accept
     */
    @Deprecated
    public boolean applyToBusiness(Long id, String reviewComment) {
        return acceptReview(id, reviewComment);
    }

    public boolean acceptReview(Long id, String reviewComment) {
        return governanceReviewWorkflow.acceptReview(id, reviewComment);
    }

    static AiHarnessCheckLogResponse toResponse(AiHarnessCheckLog e, AuditQueryService.HarnessPerson person) {
        if (e == null) return null;
        return new AiHarnessCheckLogResponse(
                e.getId(), e.getCheckCode(), e.getScenario(), e.getClaimType(),
                e.getClaimText(), e.getSourceType(), e.getSourceRefId(),
                e.getEvidenceText(), e.getRagChunkIds(), e.getSourceRefs(),
                e.getMatchedTagId(), e.getSimilarTagId(), e.getSupportScore(),
                e.getRiskLevel(), e.getDecision(), e.getIsSelfEvidence(),
                e.getReasonJson(), e.getReviewStatus(), e.getReviewComment(),
                e.getReviewedTime(), e.getBusinessApplyStatus(), e.getBusinessTargetType(),
                e.getBusinessTargetId(), e.getContextHash(), e.getContextSnapshotId(),
                e.getClaimPayloadJson(), e.getAcceptedSourceRefs(), e.getInvalidSourceRefs(),
                e.getMissingEvidenceJson(),
                person != null ? person.empId() : null,
                person != null ? person.empName() : null,
                person != null ? person.empCode() : null,
                e.getCreatedTime()
        );
    }

    private static boolean hasText(String v) {
        return v != null && !v.isBlank();
    }
}
