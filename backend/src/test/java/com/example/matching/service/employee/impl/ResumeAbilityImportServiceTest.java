package com.example.matching.service.employee.impl;

import com.example.matching.agent.dto.person.PersonAbilityClaim;
import com.example.matching.agent.dto.person.PersonAbilityExtractionResult;
import com.example.matching.common.util.PersonAbilityClaimNormalizer;
import com.example.matching.dto.system.AbilityImportResultDTO;
import com.example.matching.entity.employee.EmpResumeParse;
import com.example.matching.event.AbilityChangeEvent;
import com.example.matching.mapper.employee.EmpResumeParseMapper;
import com.example.matching.service.agent.AgentBusinessApplyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ResumeAbilityImportService 单元测试。
 * <p>
 * 验证活跃导入路径统一经 PersonAbilityClaimNormalizer 解析（旧格式兼容不再散落在服务内），
 * 且 NO_CLAIMS / 解析失败路径行为不变。
 */
@ExtendWith(MockitoExtension.class)
class ResumeAbilityImportServiceTest {

    @Mock private EmpResumeParseMapper empResumeParseMapper;
    @Mock private AgentBusinessApplyService agentBusinessApplyService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private PersonAbilityClaimNormalizer claimNormalizer;

    @InjectMocks private ResumeAbilityImportService service;

    private EmpResumeParse parseRecord;

    @BeforeEach
    void setUp() {
        parseRecord = new EmpResumeParse();
        parseRecord.setId(1L);
        parseRecord.setEmpId(100L);
        parseRecord.setStatus(2);
        parseRecord.setParsedContent("三年后端经验");
        parseRecord.setAiAnalysisResult("{\"claims\":[]}");
    }

    @Test
    @DisplayName("简历能力只进入人员画像，不进入标签准入")
    void routesThroughNormalizerAndAppliesWithoutTagAdmission() throws Exception {
        when(empResumeParseMapper.selectById(1L)).thenReturn(parseRecord);

        PersonAbilityClaim claim = new PersonAbilityClaim();
        claim.setAbilityName("Java");
        claim.setMasteryLevel(4);
        claim.setEvidenceText("三年后端经验");
        PersonAbilityExtractionResult extractionResult = new PersonAbilityExtractionResult();
        extractionResult.setClaims(List.of(claim));

        when(claimNormalizer.normalize("{\"claims\":[]}")).thenReturn(extractionResult);
        when(agentBusinessApplyService.applyPersonAbilities(any(), eq(true)))
                .thenReturn(new AgentBusinessApplyService.PersonAbilityApplyResult(1, 1, 0, 0, 0));

        AbilityImportResultDTO dto = service.importToAbilityProfile(1L);

        verify(claimNormalizer).normalize("{\"claims\":[]}");
        ArgumentCaptor<PersonAbilityExtractionResult> captor = ArgumentCaptor.forClass(PersonAbilityExtractionResult.class);
        verify(agentBusinessApplyService).applyPersonAbilities(captor.capture(), eq(true));
        assertThat(captor.getValue().getClaims()).hasSize(1);
        assertThat(captor.getValue().getClaims().get(0).getAbilityTagId()).isNull();
        verify(eventPublisher).publishEvent(any(AbilityChangeEvent.class));
        assertThat(dto.getImported()).isEqualTo(1);
        assertThat(parseRecord.getAbilityImportStatus()).isEqualTo("SUCCEEDED");
    }

    @Test
    @DisplayName("normalizer 返回空 claims 时按 NO_CLAIMS 处理，不进入应用链路")
    void emptyClaimsYieldsNoClaims() throws Exception {
        when(empResumeParseMapper.selectById(1L)).thenReturn(parseRecord);
        PersonAbilityExtractionResult empty = new PersonAbilityExtractionResult();
        empty.setClaims(List.of());
        when(claimNormalizer.normalize("{\"claims\":[]}")).thenReturn(empty);

        AbilityImportResultDTO dto = service.importToAbilityProfile(1L);

        assertThat(dto.getImported()).isZero();
        verify(agentBusinessApplyService, never()).applyPersonAbilities(any(), eq(true));
        assertThat(parseRecord.getAbilityImportStatus()).isEqualTo("NO_CLAIMS");
    }

    @Test
    @DisplayName("normalizer 抛异常时按解析失败处理，不进入应用链路")
    void normalizeFailureYieldsNoClaims() throws Exception {
        when(empResumeParseMapper.selectById(1L)).thenReturn(parseRecord);
        when(claimNormalizer.normalize(any())).thenThrow(new RuntimeException("bad json"));

        AbilityImportResultDTO dto = service.importToAbilityProfile(1L);

        assertThat(dto.getImported()).isZero();
        verify(agentBusinessApplyService, never()).applyPersonAbilities(any(), eq(true));
        assertThat(parseRecord.getAbilityImportStatus()).isEqualTo("NO_CLAIMS");
    }

    @Test
    @DisplayName("解析记录未完成时拒绝导入")
    void incompleteParseRejected() throws Exception {
        parseRecord.setStatus(1);
        when(empResumeParseMapper.selectById(1L)).thenReturn(parseRecord);

        boolean threw = false;
        try {
            service.importToAbilityProfile(1L);
        } catch (Exception e) {
            threw = true;
        }
        assertThat(threw).isTrue();
        verify(claimNormalizer, never()).normalize(any());
    }
}
