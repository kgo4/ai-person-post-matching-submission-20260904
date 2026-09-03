package com.example.matching.agent.service.impl;

import com.example.matching.entity.system.AbilityTag;
import com.example.matching.entity.system.AbilityTagCandidate;
import com.example.matching.entity.system.AbilityTagCandidatePlacementProposal;
import com.example.matching.mapper.system.AbilityTagCandidateMapper;
import com.example.matching.service.system.AbilityTagPlacementProposalService;
import com.example.matching.service.system.TaxonomyClassifyResult;
import com.example.matching.service.system.impl.AbilityTagTaxonomyClassifier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AbilityTagPlacementAgentServiceImplTest {

    @Test
    void classifierHitProducesMergeProposalWithoutAnyAiService() {
        AbilityTagCandidateMapper candidateMapper = mock(AbilityTagCandidateMapper.class);
        AbilityTagTaxonomyClassifier classifier = mock(AbilityTagTaxonomyClassifier.class);
        AbilityTagPlacementProposalService proposalService = mock(AbilityTagPlacementProposalService.class);

        when(candidateMapper.selectById(1L)).thenReturn(candidate());
        AbilityTag target = tag(20L, 2, 10L);
        when(classifier.classify("Kubernetes 运维")).thenReturn(TaxonomyClassifyResult.of(target, "VECTOR", new BigDecimal("0.80")));
        AbilityTagCandidatePlacementProposal stored = new AbilityTagCandidatePlacementProposal();
        stored.setId(99L);
        when(proposalService.createPending(any())).thenReturn(stored);

        AbilityTagPlacementAgentServiceImpl service =
                new AbilityTagPlacementAgentServiceImpl(candidateMapper, classifier, proposalService);

        Optional<AbilityTagCandidatePlacementProposal> result = service.generateProposal(1L);

        assertThat(result).isPresent();
        verify(proposalService).createPending(argThat(proposal ->
                "MERGE_EXISTING".equals(proposal.getAction())
                        && Long.valueOf(10L).equals(proposal.getTargetParentDomainId())
                        && Long.valueOf(20L).equals(proposal.getTargetTagId())));
    }

    @Test
    void classifierMissYieldsNoProposal() {
        AbilityTagCandidateMapper candidateMapper = mock(AbilityTagCandidateMapper.class);
        AbilityTagTaxonomyClassifier classifier = mock(AbilityTagTaxonomyClassifier.class);
        AbilityTagPlacementProposalService proposalService = mock(AbilityTagPlacementProposalService.class);

        when(candidateMapper.selectById(1L)).thenReturn(candidate());
        when(classifier.classify("Kubernetes 运维")).thenReturn(null);

        AbilityTagPlacementAgentServiceImpl service =
                new AbilityTagPlacementAgentServiceImpl(candidateMapper, classifier, proposalService);

        Optional<AbilityTagCandidatePlacementProposal> result = service.generateProposal(1L);

        assertThat(result).isEmpty();
        verify(proposalService, never()).createPending(any());
    }

    private AbilityTagCandidate candidate() {
        AbilityTagCandidate candidate = new AbilityTagCandidate();
        candidate.setId(1L);
        candidate.setCandidateName("Kubernetes 运维");
        candidate.setTagCategory("TECHNICAL");
        candidate.setStatus("PENDING");
        candidate.setEvidenceText("熟悉 Kubernetes 运维");
        return candidate;
    }

    private AbilityTag tag(Long id, int level, Long parentId) {
        AbilityTag tag = new AbilityTag();
        tag.setId(id);
        tag.setTagLevel(level);
        tag.setParentId(parentId);
        tag.setTagCategory("TECHNICAL");
        tag.setStatus(1);
        return tag;
    }
}
