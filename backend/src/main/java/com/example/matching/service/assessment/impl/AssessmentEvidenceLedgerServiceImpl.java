package com.example.matching.service.assessment.impl;

import com.example.matching.entity.ability.PersonAbilityClaim;
import com.example.matching.entity.workflow.AssessmentEvidenceLedger;
import com.example.matching.mapper.workflow.AssessmentEvidenceLedgerMapper;
import com.example.matching.service.assessment.AssessmentEvidenceLedgerService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AssessmentEvidenceLedgerServiceImpl implements AssessmentEvidenceLedgerService {
    private final AssessmentEvidenceLedgerMapper mapper;

    @Override
    public void record(PersonAbilityClaim claim, Long assessmentAbilityId, Long canonicalTagId, Long questionId) {
        if (claim.getWorkflowId() == null || assessmentAbilityId == null || claim.getScopeHash() == null) return;
        LambdaQueryWrapper<AssessmentEvidenceLedger> existingQuery = new LambdaQueryWrapper<AssessmentEvidenceLedger>()
                .eq(AssessmentEvidenceLedger::getWorkflowId, claim.getWorkflowId())
                .eq(AssessmentEvidenceLedger::getAssessmentAbilityId, assessmentAbilityId)
                .eq(AssessmentEvidenceLedger::getSourceType, claim.getSourceType())
                .eq(AssessmentEvidenceLedger::getSourceRefId, claim.getSourceRefId())
                .eq(AssessmentEvidenceLedger::getEvidenceText, claim.getEvidenceText())
                .eq(AssessmentEvidenceLedger::getScopeHash, claim.getScopeHash());
        if (questionId == null) {
            existingQuery.isNull(AssessmentEvidenceLedger::getQuestionId);
        } else {
            existingQuery.eq(AssessmentEvidenceLedger::getQuestionId, questionId);
        }
        if (mapper.selectCount(existingQuery) > 0) return;
        AssessmentEvidenceLedger row = new AssessmentEvidenceLedger();
        row.setWorkflowId(claim.getWorkflowId());
        row.setAssessmentAbilityId(assessmentAbilityId);
        row.setCanonicalTagId(canonicalTagId);
        row.setSourceType(claim.getSourceType());
        row.setSourceRefId(claim.getSourceRefId());
        row.setQuestionId(questionId);
        row.setEvidenceText(claim.getEvidenceText());
        row.setObservedLevel(claim.getClaimedLevel());
        row.setConfidenceScore(claim.getConfidenceScore());
        row.setEvidenceStatus(claim.getEvidenceStatus());
        row.setSourceRefsJson(claim.getSourceRefsJson());
        row.setScopeHash(claim.getScopeHash());
        row.setCreatedTime(LocalDateTime.now());
        mapper.insert(row);
    }
}
