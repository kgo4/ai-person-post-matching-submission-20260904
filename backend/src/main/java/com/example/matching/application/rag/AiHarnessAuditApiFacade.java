package com.example.matching.application.rag;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.dto.harness.AiHarnessReviewUpdateDTO;
import com.example.matching.dto.rag.api.AiHarnessCheckLogResponse;
import com.example.matching.dto.rag.api.HarnessCheckRequest;
import com.example.matching.entity.harness.AiHarnessCheckLog;
import com.example.matching.service.system.AuditQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiHarnessAuditApiFacade {

    private final AuditQueryService auditService;

    public PageResponse<AiHarnessCheckLogResponse> pageChecks(long current, long size, String scenario, String decision) {
        IPage<AiHarnessCheckLog> page = auditService.pageHarness(new Page<>(current, size), decision, scenario,
                null, null, null, null, null);
        Map<Long, AuditQueryService.HarnessPerson> persons = auditService.resolveHarnessPersons(page.getRecords());
        return PageResponse.from(page, e -> toResponse(e, persons.get(e.getId())));
    }

    public Map<String, Object> summary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        long passCount = auditService.countHarnessByStatus("PASS", null);
        long reviewCount = auditService.countHarnessByStatus("REVIEW", null);
        long blockCount = auditService.countHarnessByStatus("BLOCK", null);
        long highRiskCount = auditService.countHarnessByRiskLevel("HIGH", null);
        long mediumRiskCount = auditService.countHarnessByRiskLevel("MEDIUM", null);
        long selfEvidenceCount = auditService.countHarnessSelfEvidence(null);

        summary.put("passCount", passCount);
        summary.put("reviewCount", reviewCount);
        summary.put("blockCount", blockCount);
        summary.put("totalCount", passCount + reviewCount + blockCount);
        summary.put("highRiskCount", highRiskCount);
        summary.put("mediumRiskCount", mediumRiskCount);
        summary.put("selfEvidenceCount", selfEvidenceCount);
        summary.put("pendingCount", auditService.countHarnessByReviewStatus("PENDING", null));
        return summary;
    }

    public boolean updateReviewStatus(Long id, HarnessCheckRequest req) {
        AiHarnessCheckLog log = auditService.getHarnessById(id);
        if (log == null) {
            return false;
        }
        log.setReviewStatus(req.reviewStatus());
        log.setReviewComment(req.reviewComment());
        log.setReviewedTime(LocalDateTime.now());
        auditService.updateHarnessLog(log);
        return true;
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
}
