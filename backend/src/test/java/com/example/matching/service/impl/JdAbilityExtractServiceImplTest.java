package com.example.matching.service.impl;

import com.example.matching.service.post.impl.JdAbilityExtractServiceImpl;

import com.example.matching.dto.post.JdAbilityItemDTO;
import com.example.matching.dto.post.JdAnalyzeResponseDTO;
import com.example.matching.entity.post.JdImportTask;
import com.example.matching.entity.post.PostPost;
import com.example.matching.mapper.post.JdImportTaskMapper;
import com.example.matching.mapper.post.PostPostMapper;
import com.example.matching.service.post.PostCapabilityGenerationService;
import com.example.matching.service.system.AbilityTagService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdAbilityExtractServiceImplTest {

    @Mock
    private PostCapabilityGenerationService capabilityGenerationService;
    @Mock
    private PostPostMapper postPostMapper;
    @Mock
    private JdImportTaskMapper jdImportTaskMapper;
    @Mock
    private AbilityTagService abilityTagService;

    @Test
    void analyzeJd_delegatesExactlyOnceToAgentFacadeAndUsesAgentSummary() {
        JdAbilityExtractServiceImpl service = createService();

        PostPost post = new PostPost();
        post.setId(1L);
        post.setPostName("Java Developer");
        when(postPostMapper.selectById(1L)).thenReturn(post);
        JdAbilityItemDTO item = new JdAbilityItemDTO();
        item.setSuggestedName("Spring Boot");
        item.setMinRequiredLevel(3);
        item.setWeight(new BigDecimal("25"));
        item.setMatchStatus("MATCHED");
        item.setReasoning("evidence");
        PostCapabilityGenerationService.PostAbilityAnalysisResult analysis =
                new PostCapabilityGenerationService.PostAbilityAnalysisResult(
                        List.of(item), "负责Java模块设计开发的岗位摘要");
        when(capabilityGenerationService.analyzePostTextWithResult(
                eq("Java Developer"), any(), eq("JD_IMPORT"), any(), any()))
                .thenReturn(analysis);
        doReturn(1).when(jdImportTaskMapper).insert(any(JdImportTask.class));
        doReturn(1).when(jdImportTaskMapper).updateById(any(JdImportTask.class));
        String jdText = "Responsible for Java module design and delivery.";

        JdAnalyzeResponseDTO response = service.analyzeJd(1L, jdText);

        // 恰好一次委托，且不出现第二次 LLM 调用（旧 job-summary prompt 已移除）
        verify(capabilityGenerationService).analyzePostTextWithResult(
                eq("Java Developer"), any(), eq("JD_IMPORT"), any(), any());
        verify(capabilityGenerationService, never()).analyzePostText(any(), any());
        assertThat(response.getJobSummary()).isEqualTo("负责Java模块设计开发的岗位摘要");
        assertThat(response.getAbilities()).hasSize(1);
        assertThat(response.getAbilities().get(0).getSuggestedName()).isEqualTo("Spring Boot");
        assertThat(response.getAnalysisStatus()).isEqualTo(2);
    }

    @Test
    void analyzeJd_blankAgentSummaryFallsBackToTruncatedJd() {
        JdAbilityExtractServiceImpl service = createService();

        PostPost post = new PostPost();
        post.setId(1L);
        post.setPostName("Java Developer");
        when(postPostMapper.selectById(1L)).thenReturn(post);
        when(capabilityGenerationService.analyzePostTextWithResult(
                eq("Java Developer"), any(), eq("JD_IMPORT"), any(), any()))
                .thenReturn(new PostCapabilityGenerationService.PostAbilityAnalysisResult(List.of(), null));
        doReturn(1).when(jdImportTaskMapper).insert(any(JdImportTask.class));
        doReturn(1).when(jdImportTaskMapper).updateById(any(JdImportTask.class));

        JdAnalyzeResponseDTO response = service.analyzeJd(1L, "Responsible for Java module design and delivery.");

        assertThat(response.getJobSummary()).contains("Responsible for Java module design");
        verify(capabilityGenerationService, never()).analyzePostText(any(), any());
    }

    @Test
    void applyAnalysisResult_delegatesToCapabilityGenerationService() {
        JdAbilityExtractServiceImpl service = createService();

        JdAbilityItemDTO item = new JdAbilityItemDTO();
        item.setSuggestedName("Spring Boot development");
        item.setTagCategory("TECHNICAL");
        item.setMatchStatus("NEW");
        item.setMinRequiredLevel(3);
        item.setWeight(new BigDecimal("25"));
        item.setIsCore(1);
        item.setIsRequired(1);

        service.applyAnalysisResult(1L, List.of(item));

        verify(capabilityGenerationService).applyAbilityItemsToPost(1L, List.of(item));
    }

    private JdAbilityExtractServiceImpl createService() {
        return new JdAbilityExtractServiceImpl(
                capabilityGenerationService,
                postPostMapper,
                jdImportTaskMapper,
                abilityTagService
        );
    }
}
