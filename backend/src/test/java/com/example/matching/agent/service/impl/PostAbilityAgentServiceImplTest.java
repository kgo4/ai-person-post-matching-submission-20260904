package com.example.matching.agent.service.impl;

import com.example.matching.agent.config.LangChain4jAgentProperties;
import com.example.matching.agent.dto.AgentContextPackage;
import com.example.matching.agent.dto.post.PostAbilityClaim;
import com.example.matching.agent.dto.post.PostAbilityExtractRequest;
import com.example.matching.agent.dto.post.PostAbilityExtractionResult;
import com.example.matching.agent.lc4j.PostAbilityAiService;
import com.example.matching.agent.service.AgentContextPackageService;
import com.example.matching.agent.service.AgentFallbackService;
import com.example.matching.agent.service.PostAbilityAgentService;
import com.example.matching.application.agent.PostRequirementSnapshot;
import com.example.matching.infrastructure.llm.LlmResponseParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PostAbilityAgentServiceImpl 单元测试。
 * <p>
 * 验证岗位能力提取的唯一实现路径：
 * - Agent 未启用/未注册时走确定性降级（基于现有岗位能力数据）
 * - Agent 返回结构化结果时 claims 被规范化（postId/sourceType/sourceRefId 回填）
 * - Agent 返回空结果时走降级，不产生第二套 schema 解析
 */
class PostAbilityAgentServiceImplTest {

    private LangChain4jAgentProperties properties;
    @Mock private AgentContextPackageService contextPackageService;
    @Mock private AgentFallbackService fallbackService;
    @Mock private LlmResponseParser llmResponseParser;
    @Mock private AgentMemoryContextService memoryContextService;
    @Mock private AgentMemoryRuleEnforcer memoryRuleEnforcer;
    @Mock private PostAbilityAiService aiService;

    private PostAbilityAgentService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        properties = new LangChain4jAgentProperties();
        AgentRunConfidencePolicy confidencePolicy = new AgentRunConfidencePolicy();
        when(memoryContextService.resolveRules(anyString(), anyString()))
                .thenReturn(new AgentMemoryContextService.ContextRules(List.of(), List.of(), null, null));

        ObjectProvider<PostAbilityAiService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(aiService);

        service = new PostAbilityAgentServiceImpl(
                properties, contextPackageService, fallbackService, new ObjectMapper(),
                llmResponseParser, confidencePolicy, memoryContextService,
                memoryRuleEnforcer,
                new com.example.matching.ai.validation.PostAbilityExtractionValidator(),
                provider);
    }

    private PostAbilityExtractRequest request() {
        PostAbilityExtractRequest request = new PostAbilityExtractRequest();
        request.setPostId(7L);
        request.setSourceType("JD_IMPORT");
        request.setSourceRefId(42L);
        request.setSourceText("岗位名称：Java Developer\n\n负责Java模块设计");
        request.setSourceRefs(List.of("source:JD_IMPORT:42"));
        return request;
    }

    @Test
    @DisplayName("Agent 未启用时使用确定性降级声明")
    void disabledAgentUsesFallbackClaims() {
        properties.setEnabled(false);
        AgentContextPackage context = new AgentContextPackage();
        context.setPostRequirements(List.of());
        when(contextPackageService.buildForPost(7L)).thenReturn(context);

        PostAbilityExtractionResult result = service.extractAbilities(request());

        assertThat(result.isFallbackUsed()).isTrue();
        assertThat(result.getClaims()).isNotNull();
        assertThat(result.getPostId()).isEqualTo(7L);
        assertThat(result.getSourceType()).isEqualTo("JD_IMPORT");
        assertThat(result.getSourceRefId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("岗位提取不可用时不得将旧岗位模型伪装成本次 JD 提取结果")
    void disabledAgentDoesNotReuseExistingPostModelAsExtractionFallback() {
        properties.setEnabled(false);
        AgentContextPackage context = new AgentContextPackage();
        context.setPostRequirements(List.of(new PostRequirementSnapshot(
                99L, "Legacy Java", 4, new java.math.BigDecimal("0.8"), true, true)));
        when(contextPackageService.buildForPost(7L)).thenReturn(context);

        PostAbilityExtractionResult result = service.extractAbilities(request());

        assertThat(result.isFallbackUsed()).isTrue();
        assertThat(result.getClaims()).isEmpty();
        assertThat(result.getSummary()).contains("待重试");
    }

    @Test
    @DisplayName("Agent 返回结构化结果时 claims 被规范化回填")
    void structuredResultClaimsNormalized() {
        properties.setEnabled(true);
        PostAbilityExtractionResult aiResult = new PostAbilityExtractionResult();
        PostAbilityClaim claim = new PostAbilityClaim();
        claim.setAbilityName("Java");
        claim.setRequiredLevel(4);
        claim.setWeight(new java.math.BigDecimal("0.8"));
        claim.setEvidenceText("负责Java模块设计");
        claim.setConfidenceScore(new java.math.BigDecimal("85"));
        aiResult.setClaims(List.of(claim));
        aiResult.setSummary("Java岗位摘要");
        when(aiService.extractAbilities(anyString())).thenReturn(aiResult);

        PostAbilityExtractionResult result = service.extractAbilities(request());

        assertThat(result.isFallbackUsed()).isFalse();
        assertThat(result.getSummary()).isEqualTo("Java岗位摘要");
        assertThat(result.getClaims()).hasSize(1);
        PostAbilityClaim normalized = result.getClaims().get(0);
        assertThat(normalized.getPostId()).isEqualTo(7L);
        assertThat(normalized.getSourceType()).isEqualTo("JD_IMPORT");
        assertThat(normalized.getSourceRefId()).isEqualTo(42L);
        assertThat(normalized.getNormalizedAbilityName()).isEqualTo("Java");
        assertThat(normalized.getSourceRefs()).containsExactly("source:JD_IMPORT:42");
    }

    @Test
    @DisplayName("单段 JD 提取应先按证据偏移回填原文，再执行证据校验")
    void materializesOffsetEvidenceBeforeValidation() {
        properties.setEnabled(true);
        String evidence = "负责Java模块设计";
        PostAbilityExtractRequest request = request();
        int start = request.getSourceText().indexOf(evidence);

        PostAbilityClaim claim = new PostAbilityClaim();
        claim.setAbilityName("Java");
        claim.setRequiredLevel(4);
        claim.setWeight(new java.math.BigDecimal("0.8"));
        claim.setEvidenceStart(start);
        claim.setEvidenceEnd(start + evidence.length());
        claim.setConfidenceScore(new java.math.BigDecimal("85"));
        PostAbilityExtractionResult aiResult = new PostAbilityExtractionResult();
        aiResult.setClaims(List.of(claim));
        when(aiService.extractAbilities(anyString())).thenReturn(aiResult);

        PostAbilityExtractionResult result = service.extractAbilities(request);

        assertThat(result.isFallbackUsed()).isFalse();
        assertThat(result.getClaims()).hasSize(1);
        assertThat(result.getClaims().get(0).getEvidenceText()).isEqualTo(evidence);
    }

    @Test
    @DisplayName("证据无法定位时要求 Agent 修复一次，并且只接受原文证据")
    void retriesOnceWithValidationFeedbackWhenEvidenceIsNotLocatable() {
        properties.setEnabled(true);

        PostAbilityClaim invalid = new PostAbilityClaim();
        invalid.setAbilityName("Java");
        invalid.setRequiredLevel(4);
        invalid.setWeight(new java.math.BigDecimal("0.8"));
        invalid.setEvidenceText("具备扎实 Java 能力");

        PostAbilityClaim repaired = new PostAbilityClaim();
        repaired.setAbilityName("Java");
        repaired.setRequiredLevel(4);
        repaired.setWeight(new java.math.BigDecimal("0.8"));
        repaired.setEvidenceText("负责Java模块设计");

        PostAbilityExtractionResult invalidResult = new PostAbilityExtractionResult();
        invalidResult.setClaims(List.of(invalid));
        PostAbilityExtractionResult repairedResult = new PostAbilityExtractionResult();
        repairedResult.setClaims(List.of(repaired));
        when(aiService.extractAbilities(anyString())).thenReturn(invalidResult, repairedResult);

        PostAbilityExtractionResult result = service.extractAbilities(request());

        assertThat(result.isFallbackUsed()).isFalse();
        assertThat(result.getClaims()).singleElement()
                .extracting(PostAbilityClaim::getEvidenceText)
                .isEqualTo("负责Java模块设计");
        verify(aiService, times(2)).extractAbilities(anyString());
    }

    @Test
    @DisplayName("同一 JD 的无效能力不得清空已被原文证实的能力")
    void preservesGroundedClaimsWhenAnotherClaimIsInvalid() {
        properties.setEnabled(true);

        PostAbilityClaim valid = new PostAbilityClaim();
        valid.setAbilityName("Java");
        valid.setRequiredLevel(4);
        valid.setWeight(new java.math.BigDecimal("0.8"));
        valid.setEvidenceText("负责Java模块设计");
        valid.setEvidenceAnchor("Java");

        PostAbilityClaim invalid = new PostAbilityClaim();
        invalid.setAbilityName("Kubernetes");
        invalid.setRequiredLevel(3);
        invalid.setWeight(new java.math.BigDecimal("0.2"));
        invalid.setEvidenceText("掌握 Kubernetes 容器编排");
        invalid.setEvidenceAnchor("Kubernetes");

        PostAbilityExtractionResult aiResult = new PostAbilityExtractionResult();
        aiResult.setClaims(List.of(valid, invalid));
        when(aiService.extractAbilities(anyString())).thenReturn(aiResult);

        PostAbilityExtractionResult result = service.extractAbilities(request());

        assertThat(result.isFallbackUsed()).isFalse();
        assertThat(result.getClaims()).singleElement()
                .extracting(PostAbilityClaim::getAbilityName)
                .isEqualTo("Java");
        verify(aiService, times(1)).extractAbilities(anyString());
    }

    @Test
    @DisplayName("长 JD 分片时按分片原文逐项校验并保留全量原文偏移")
    void longJdValidatesEachChunkBeforeConvertingOffsets() {
        properties.setEnabled(true);
        String prefix = "岗位职责：" + "a".repeat(9000);
        String evidence = "负责 Kubernetes 平台运维";
        PostAbilityExtractRequest request = request();
        request.setSourceText(prefix + "\n" + evidence);

        PostAbilityExtractionResult first = new PostAbilityExtractionResult();
        first.setClaims(List.of());
        PostAbilityClaim claim = new PostAbilityClaim();
        claim.setAbilityName("Kubernetes平台运维");
        claim.setRequiredLevel(4);
        claim.setWeight(new java.math.BigDecimal("0.8"));
        claim.setEvidenceText(evidence);
        claim.setEvidenceAnchor("Kubernetes");
        PostAbilityExtractionResult second = new PostAbilityExtractionResult();
        second.setClaims(List.of(claim));
        when(aiService.extractAbilities(anyString())).thenReturn(first, second);

        PostAbilityExtractionResult result = service.extractAbilities(request);

        assertThat(result.getClaims()).singleElement().satisfies(extracted -> {
            assertThat(extracted.getEvidenceText()).isEqualTo(evidence);
            assertThat(extracted.getEvidenceStart()).isEqualTo(request.getSourceText().indexOf(evidence));
            assertThat(extracted.getEvidenceEnd()).isEqualTo(request.getSourceText().indexOf(evidence) + evidence.length());
        });
    }

    @Test
    @DisplayName("Agent 返回 null 时走降级方案，不产生第二套 schema")
    void nullAgentResultFallsBack() {
        properties.setEnabled(true);
        AgentContextPackage context = new AgentContextPackage();
        context.setPostRequirements(List.of());
        when(contextPackageService.buildForPost(7L)).thenReturn(context);
        when(aiService.extractAbilities(anyString())).thenReturn(null);

        PostAbilityExtractionResult result = service.extractAbilities(request());

        assertThat(result.isFallbackUsed()).isTrue();
        assertThat(result.getClaims()).isNotNull();
    }
}
