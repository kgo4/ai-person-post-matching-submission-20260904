package com.example.matching.service.system.impl;

import com.example.matching.entity.system.AbilityTag;
import com.example.matching.entity.system.AbilityTagCandidate;
import com.example.matching.entity.system.AbilityTagCandidatePlacementProposal;
import com.example.matching.mapper.system.AbilityTagCandidateMapper;
import com.example.matching.mapper.system.AbilityTagCandidatePlacementProposalMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.service.system.AbilityTagCandidateService;
import com.example.matching.service.system.PlacementApplyResult;
import com.example.matching.service.system.AbilityTagPlacementProposalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AbilityTagPlacementProposalServiceImplTest {

    private AbilityTagCandidatePlacementProposalMapper proposalMapper;
    private AbilityTagCandidateMapper candidateMapper;
    private AbilityTagMapper tagMapper;
    private AbilityTagCandidateService candidateService;
    private AbilityTagPlacementProposalService service;

    @BeforeEach
    void setUp() {
        proposalMapper = mock(AbilityTagCandidatePlacementProposalMapper.class);
        candidateMapper = mock(AbilityTagCandidateMapper.class);
        tagMapper = mock(AbilityTagMapper.class);
        candidateService = mock(AbilityTagCandidateService.class);
        service = new AbilityTagPlacementProposalServiceImpl(
                proposalMapper, candidateMapper, tagMapper, candidateService);
    }

    @Test
    void applyMergeProposalUsesExistingCandidateMergeAndMarksProposalApplied() {
        AbilityTagCandidatePlacementProposal proposal = pendingProposal("MERGE_EXISTING");
        proposal.setTargetParentDomainId(10L);
        proposal.setTargetTagId(20L);
        when(proposalMapper.selectById(2L)).thenReturn(proposal);
        when(candidateMapper.selectById(1L)).thenReturn(pendingCandidate());
        when(tagMapper.selectById(10L)).thenReturn(enabledTag(10L, 1, 0L));
        when(tagMapper.selectById(20L)).thenReturn(enabledTag(20L, 2, 10L));

        PlacementApplyResult result = service.apply(1L, 2L, 1, 7L);

        assertThat(result.status()).isEqualTo("APPLIED");
        assertThat(result.finalTagId()).isEqualTo(20L);
        verify(candidateService).merge(1L, 20L, 7L, "采纳标签挂载建议");
        verify(proposalMapper).updateById(argThat((AbilityTagCandidatePlacementProposal updated) ->
                "APPLIED".equals(updated.getStatus()) && Long.valueOf(20L).equals(updated.getFinalTagId())));
    }

    @Test
    void applyCreateProposalUsesExistingApprovalUnderEnabledL1() {
        AbilityTagCandidatePlacementProposal proposal = pendingProposal("CREATE_L2");
        proposal.setTargetParentDomainId(10L);
        when(proposalMapper.selectById(2L)).thenReturn(proposal);
        when(candidateMapper.selectById(1L)).thenReturn(pendingCandidate());
        when(tagMapper.selectById(10L)).thenReturn(enabledTag(10L, 1, 0L));
        when(candidateService.approve(1L, 10L, 7L, "采纳标签挂载建议")).thenReturn(31L);

        PlacementApplyResult result = service.apply(1L, 2L, 1, 7L);

        assertThat(result.status()).isEqualTo("APPLIED");
        assertThat(result.finalTagId()).isEqualTo(31L);
        verify(candidateService).approve(1L, 10L, 7L, "采纳标签挂载建议");
        verifyNoMoreInteractions(candidateService);
    }

    @Test
    void repeatedApplyReturnsPersistedResultWithoutWritingTaxonomyAgain() {
        AbilityTagCandidatePlacementProposal proposal = pendingProposal("MERGE_EXISTING");
        proposal.setStatus("APPLIED");
        proposal.setFinalTagId(20L);
        when(proposalMapper.selectById(2L)).thenReturn(proposal);

        PlacementApplyResult result = service.apply(1L, 2L, 1, 7L);

        assertThat(result.status()).isEqualTo("APPLIED");
        assertThat(result.finalTagId()).isEqualTo(20L);
        verifyNoInteractions(candidateMapper, tagMapper, candidateService);
    }

    private AbilityTagCandidatePlacementProposal pendingProposal(String action) {
        AbilityTagCandidatePlacementProposal proposal = new AbilityTagCandidatePlacementProposal();
        proposal.setId(2L);
        proposal.setCandidateId(1L);
        proposal.setAction(action);
        proposal.setStatus("PENDING");
        proposal.setProposalVersion(1);
        return proposal;
    }

    private AbilityTagCandidate pendingCandidate() {
        AbilityTagCandidate candidate = new AbilityTagCandidate();
        candidate.setId(1L);
        candidate.setStatus("PENDING");
        candidate.setTagCategory("TECHNICAL");
        return candidate;
    }

    private AbilityTag enabledTag(Long id, int level, Long parentId) {
        AbilityTag tag = new AbilityTag();
        tag.setId(id);
        tag.setStatus(1);
        tag.setTagLevel(level);
        tag.setParentId(parentId);
        tag.setTagCategory("TECHNICAL");
        return tag;
    }
}
