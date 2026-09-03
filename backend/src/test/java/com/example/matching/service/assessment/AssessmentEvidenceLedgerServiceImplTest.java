package com.example.matching.service.assessment;

import com.example.matching.entity.ability.PersonAbilityClaim;
import com.example.matching.entity.workflow.AssessmentEvidenceLedger;
import com.example.matching.mapper.workflow.AssessmentEvidenceLedgerMapper;
import com.example.matching.service.assessment.impl.AssessmentEvidenceLedgerServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssessmentEvidenceLedgerServiceImplTest {

    @Test
    void recordIsIdempotentForTheSameEvidenceRow() {
        AssessmentEvidenceLedgerMapper mapper = mock(AssessmentEvidenceLedgerMapper.class);
        PersonAbilityClaim claim = new PersonAbilityClaim();
        claim.setWorkflowId(10L);
        claim.setSourceType("AI_TEST");
        claim.setSourceRefId(20L);
        claim.setEvidenceText("test evidence");
        claim.setClaimedLevel(3);
        claim.setConfidenceScore(BigDecimal.valueOf(80));
        claim.setEvidenceStatus("COLLECTED");
        claim.setSourceRefsJson("[\"source:AI_TEST:20:Q1\"]");
        claim.setScopeHash("scope-1");

        when(mapper.selectCount(any())).thenReturn(1L);

        AssessmentEvidenceLedgerService service = new AssessmentEvidenceLedgerServiceImpl(mapper);
        service.record(claim, 100L, null, 1L);

        verify(mapper, never()).insert(any(AssessmentEvidenceLedger.class));
    }
}
