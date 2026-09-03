package com.example.matching.service.system.impl;

import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.entity.system.AbilityTagCandidate;
import com.example.matching.mapper.system.AbilityTagCandidateMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.port.post.PostQueryPort;
import com.example.matching.port.talent.TalentQueryPort;
import com.example.matching.service.system.AbilityTagService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 工作包4：候选标签治理 RuntimeException 规范化验证
 */
class AbilityTagCandidateGovernanceExceptionTest {

    private AbilityTagGovernanceServiceImpl governanceService() {
        return new AbilityTagGovernanceServiceImpl(
                mock(AbilityTagCandidateMapper.class),
                mock(com.example.matching.mapper.system.AbilityTagUsageStatMapper.class),
                mock(AbilityTagMapper.class),
                mock(AbilityTagService.class),
                mock(PostQueryPort.class),
                mock(TalentQueryPort.class));
    }

    @Test
    void governanceApproveMissingCandidateThrowsNotFoundBusinessException() {
        AbilityTagGovernanceServiceImpl service = governanceService();
        AbilityTagCandidateMapper mapper = mock(AbilityTagCandidateMapper.class);
        when(mapper.selectById(99L)).thenReturn(null);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);

        assertThatThrownBy(() -> service.approveCandidate(99L, "TECH", 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode())
                        .isEqualTo(ErrorCodeEnum.NOT_FOUND.getCode()));
    }

    @Test
    void governanceApproveNonPendingCandidateThrowsStateConflictBusinessException() {
        AbilityTagGovernanceServiceImpl service = governanceService();
        AbilityTagCandidateMapper mapper = mock(AbilityTagCandidateMapper.class);
        AbilityTagCandidate approved = new AbilityTagCandidate();
        approved.setId(1L);
        approved.setStatus("APPROVED");
        when(mapper.selectById(1L)).thenReturn(approved);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);

        assertThatThrownBy(() -> service.approveCandidate(1L, "TECHNICAL", 9L, 7L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode())
                        .isEqualTo(ErrorCodeEnum.STATE_CONFLICT.getCode()));
    }

    @Test
    void governanceMergeMissingTargetThrowsNotFoundBusinessException() {
        AbilityTagGovernanceServiceImpl service = governanceService();
        AbilityTagCandidateMapper mapper = mock(AbilityTagCandidateMapper.class);
        AbilityTagCandidate candidate = new AbilityTagCandidate();
        candidate.setId(1L);
        candidate.setCandidateName("Java");
        candidate.setSourceType("TEST");
        candidate.setStatus("PENDING");
        when(mapper.selectById(1L)).thenReturn(candidate);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);

        assertThatThrownBy(() -> service.mergeCandidateToExisting(1L, 99L, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode())
                        .isEqualTo(ErrorCodeEnum.NOT_FOUND.getCode()));
    }

    @Test
    void candidateApproveNonPendingThrowsStateConflictBusinessException() {
        AbilityTagCandidateMapper mapper = mock(AbilityTagCandidateMapper.class);
        AbilityTagCandidateServiceImpl service = new AbilityTagCandidateServiceImpl(
                mock(AbilityTagMapper.class), mock(ObjectProvider.class));
        ReflectionTestUtils.setField(service, "baseMapper", mapper);

        AbilityTagCandidate approved = new AbilityTagCandidate();
        approved.setId(1L);
        approved.setStatus("APPROVED");
        when(mapper.selectById(1L)).thenReturn(approved);

        assertThatThrownBy(() -> service.approve(1L, 9L, 1L, "ok"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode())
                        .isEqualTo(ErrorCodeEnum.STATE_CONFLICT.getCode()));
    }

    @Test
    void candidateMergeMissingTargetThrowsNotFoundBusinessException() {
        AbilityTagCandidateMapper mapper = mock(AbilityTagCandidateMapper.class);
        AbilityTagMapper tagMapper = mock(AbilityTagMapper.class);
        AbilityTagCandidateServiceImpl service = new AbilityTagCandidateServiceImpl(
                tagMapper, mock(ObjectProvider.class));
        ReflectionTestUtils.setField(service, "baseMapper", mapper);

        AbilityTagCandidate pending = new AbilityTagCandidate();
        pending.setId(1L);
        pending.setStatus("PENDING");
        pending.setCandidateName("Java");
        when(mapper.selectById(1L)).thenReturn(pending);
        when(tagMapper.selectById(anyLong())).thenReturn(null);
        when(service.getById(any())).thenReturn(pending);
        when(service.getById(99L)).thenReturn(pending);
        when(mapper.selectById(99L)).thenReturn(pending);

        assertThatThrownBy(() -> service.merge(1L, 99L, 1L, "merge"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode())
                        .isEqualTo(ErrorCodeEnum.NOT_FOUND.getCode()));
    }
}
