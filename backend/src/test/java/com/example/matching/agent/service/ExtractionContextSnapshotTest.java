package com.example.matching.agent.service;

import com.example.matching.agent.config.LangChain4jAgentProperties;
import com.example.matching.agent.dto.person.PersonAbilityClaim;
import com.example.matching.agent.dto.person.PersonAbilityExtractRequest;
import com.example.matching.agent.dto.person.PersonAbilityExtractionResult;
import com.example.matching.agent.dto.post.PostAbilityExtractRequest;
import com.example.matching.agent.service.impl.AgentMemoryContextService;
import com.example.matching.agent.service.impl.EmployeeAbilityAgentServiceImpl;
import com.example.matching.agent.service.impl.PostAbilityAgentServiceImpl;
import com.example.matching.ai.validation.EmployeeAbilityExtractionValidator;
import com.example.matching.ai.validation.PostAbilityExtractionValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Task8：提取上下文快照测试。
 * <p>
 * 断言员工/岗位提取上下文不含完整图谱 JSON、不含其他员工数据，
 * 且 sourceRefId/sourceText 均存在；长文本分块偏移正确、失败块标记 RETRY/REVIEW。
 */
class ExtractionContextSnapshotTest {

    private ObjectMapper mapper = new ObjectMapper();

    private AgentMemoryContextService.ContextRules emptyRules() {
        return new AgentMemoryContextService.ContextRules(List.of(), List.of(), null, null);
    }

    private EmployeeAbilityAgentServiceImpl employeeService(
            com.example.matching.agent.lc4j.EmployeeAbilityAiService aiService,
            AgentMemoryContextService memoryContextService) {
        LangChain4jAgentProperties properties = new LangChain4jAgentProperties();
        properties.setEnabled(true);
        EmployeeAbilityAgentServiceImpl service = new EmployeeAbilityAgentServiceImpl(
                properties, mock(com.example.matching.agent.service.AgentContextPackageService.class),
                mapper, mock(com.example.matching.agent.service.impl.AgentRunConfidencePolicy.class),
                memoryContextService, mock(com.example.matching.agent.service.impl.AgentMemoryRuleEnforcer.class),
                new EmployeeAbilityExtractionValidator());
        org.springframework.test.util.ReflectionTestUtils.setField(service, "employeeAbilityAiService", aiService);
        return service;
    }

    private PostAbilityAgentServiceImpl postService(
            com.example.matching.agent.lc4j.PostAbilityAiService aiService,
            AgentMemoryContextService memoryContextService) {
        LangChain4jAgentProperties properties = new LangChain4jAgentProperties();
        properties.setEnabled(true);
        PostAbilityAgentServiceImpl service = new PostAbilityAgentServiceImpl(
                properties, mock(com.example.matching.agent.service.AgentContextPackageService.class),
                mock(com.example.matching.agent.service.AgentFallbackService.class),
                mapper, mock(com.example.matching.infrastructure.llm.LlmResponseParser.class),
                mock(com.example.matching.agent.service.impl.AgentRunConfidencePolicy.class),
                memoryContextService, mock(com.example.matching.agent.service.impl.AgentMemoryRuleEnforcer.class),
                new PostAbilityExtractionValidator(),
                new org.springframework.beans.factory.ObjectProvider<com.example.matching.agent.lc4j.PostAbilityAiService>() {
                    @Override
                    public com.example.matching.agent.lc4j.PostAbilityAiService getObject() { return aiService; }
                    @Override
                    public com.example.matching.agent.lc4j.PostAbilityAiService getObject(Object... args) { return aiService; }
                    @Override
                    public com.example.matching.agent.lc4j.PostAbilityAiService getIfAvailable() { return aiService; }
                    @Override
                    public com.example.matching.agent.lc4j.PostAbilityAiService getIfUnique() { return aiService; }
                    @Override
                    public java.util.stream.Stream<com.example.matching.agent.lc4j.PostAbilityAiService> stream() { return java.util.stream.Stream.of(aiService); }
                });
        org.springframework.test.util.ReflectionTestUtils.setField(service, "postAbilityAiService", aiService);
        return service;
    }

    @Test
    @DisplayName("员工提取上下文：无图谱、无他人数据，含 sourceRefId/sourceText")
    void employeeContextHasNoGraphNoOtherEmployeesAndKeepsSourceRefs() throws Exception {
        com.example.matching.agent.lc4j.EmployeeAbilityAiService aiService =
                mock(com.example.matching.agent.lc4j.EmployeeAbilityAiService.class);
        AgentMemoryContextService memoryContextService = mock(AgentMemoryContextService.class);
        when(memoryContextService.resolveRules(anyString(), anyString())).thenReturn(emptyRules());
        when(aiService.extractAbilities(anyString())).thenAnswer(invocation -> {
            String contextJson = invocation.getArgument(0);
            Map<String, Object> context = mapper.readValue(contextJson,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            assertThat(context).containsKeys("empId", "sourceType", "sourceRefId", "sourceText");
            assertThat(contextJson.toLowerCase()).doesNotContain("graph");
            assertThat(contextJson).doesNotContain("KnowledgeGraph");
            assertThat(context).doesNotContainKey("otherEmployees");
            assertThat(context).doesNotContainKey("fullProfile");
            return new PersonAbilityExtractionResult();
        });

        PersonAbilityExtractRequest request = new PersonAbilityExtractRequest();
        request.setEmpId(1L);
        request.setSourceType("RESUME_PARSE");
        request.setSourceRefId(8L);
        request.setSourceText("Java backend resume text");
        request.setSourceRefs(List.of("source:RESUME_PARSE:8"));

        employeeService(aiService, memoryContextService).extractAbilities(request);
    }

    @Test
    @DisplayName("岗位提取上下文：无图谱，含 sourceRefId/sourceText")
    void postContextHasNoGraphAndKeepsSourceRefs() throws Exception {
        com.example.matching.agent.lc4j.PostAbilityAiService aiService =
                mock(com.example.matching.agent.lc4j.PostAbilityAiService.class);
        AgentMemoryContextService memoryContextService = mock(AgentMemoryContextService.class);
        when(memoryContextService.resolveRules(anyString(), anyString())).thenReturn(emptyRules());
        when(aiService.extractAbilities(anyString())).thenAnswer(invocation -> {
            String contextJson = invocation.getArgument(0);
            assertThat(contextJson).contains("sourceRefId");
            assertThat(contextJson).contains("sourceText");
            assertThat(contextJson.toLowerCase()).doesNotContain("graph");
            assertThat(contextJson).doesNotContain("KnowledgeGraph");
            return new com.example.matching.agent.dto.post.PostAbilityExtractionResult();
        });

        PostAbilityExtractRequest request = new PostAbilityExtractRequest();
        request.setPostId(7L);
        request.setSourceType("JD_IMPORT");
        request.setSourceRefId(42L);
        request.setSourceText("岗位名称：Java Developer\n\n负责Java模块设计");
        request.setSourceRefs(List.of("source:JD_IMPORT:42"));

        postService(aiService, memoryContextService).extractAbilities(request);
    }

    @Test
    @DisplayName("分块：偏移连续覆盖原文，段落边界优先")
    void chunkerKeepsContinuousOffsetsAndPrefersParagraphBoundary() {
        String longText = ("第一段描述：Java 后端开发与微服务架构设计。\n\n")
                .repeat(300) + "末尾段落：负责性能优化。";
        List<ExtractionChunker.Chunk> chunks = ExtractionChunker.chunk(longText, 12000);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0).start()).isZero();
        assertThat(chunks.get(chunks.size() - 1).end()).isEqualTo(longText.length());
        // 偏移连续
        for (int i = 1; i < chunks.size(); i++) {
            assertThat(chunks.get(i).start()).isEqualTo(chunks.get(i - 1).end());
        }
        // 每块不超过上限
        for (ExtractionChunker.Chunk chunk : chunks) {
            assertThat(chunk.text().length()).isLessThanOrEqualTo(12000);
        }
        // 分块内容拼接等于原文
        StringBuilder rebuilt = new StringBuilder();
        for (ExtractionChunker.Chunk chunk : chunks) {
            rebuilt.append(chunk.text());
        }
        assertThat(rebuilt.toString()).isEqualTo(longText);
    }

    @Test
    @DisplayName("默认 JD 分块为 Agent 上下文预留包装空间且完整覆盖原文")
    void defaultChunkSizePreservesLongJdWithoutTailLoss() {
        String longJd = ("岗位职责：负责分布式服务设计、消息处理与缓存优化。\n\n").repeat(500)
                + "岗位要求：熟悉 Java、RabbitMQ、Redis，并具备性能调优经验。";

        List<ExtractionChunker.Chunk> chunks = ExtractionChunker.chunk(longJd,
                ExtractionChunker.DEFAULT_MAX_CHARS);

        assertThat(chunks).allSatisfy(chunk ->
                assertThat(chunk.text().length()).isLessThanOrEqualTo(ExtractionChunker.DEFAULT_MAX_CHARS));
        assertThat(chunks.stream().map(ExtractionChunker.Chunk::text)
                .collect(java.util.stream.Collectors.joining())).isEqualTo(longJd);
    }

    @Test
    @DisplayName("分块失败仅标记 RETRY/REVIEW，不复制旧事实")
    void chunkedExtractionFailureMarksRetryWithoutCopyingOldFacts() throws Exception {
        com.example.matching.agent.lc4j.EmployeeAbilityAiService aiService =
                mock(com.example.matching.agent.lc4j.EmployeeAbilityAiService.class);
        AgentMemoryContextService memoryContextService = mock(AgentMemoryContextService.class);
        when(memoryContextService.resolveRules(anyString(), anyString())).thenReturn(emptyRules());

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        when(aiService.extractAbilities(captor.capture())).thenAnswer(invocation -> {
            // 第 1 块成功，第 2 块抛异常（模拟 AI 超时）
            if (captor.getAllValues().size() == 1) {
                PersonAbilityExtractionResult first = new PersonAbilityExtractionResult();
                PersonAbilityClaim claim = new PersonAbilityClaim();
                claim.setAbilityName("Java");
                claim.setMasteryLevel(4);
                claim.setEvidenceText("负责Java后端开发");
                first.setClaims(List.of(claim));
                return first;
            }
            throw new RuntimeException("chunk timeout");
        });

        // 构造超过 12000 字符的两段文本
        String text = "Java 后端开发经验丰富，负责Java后端开发与性能优化。\n\n" + "A".repeat(12000) + "。";

        PersonAbilityExtractRequest request = new PersonAbilityExtractRequest();
        request.setEmpId(1L);
        request.setSourceType("RESUME_PARSE");
        request.setSourceRefId(8L);
        request.setSourceText(text);
        request.setSourceRefs(List.of("source:RESUME_PARSE:8"));

        PersonAbilityExtractionResult result =
                employeeService(aiService, memoryContextService).extractAbilities(request);

        assertThat(captor.getAllValues()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(result.getFailedChunkCount()).isGreaterThan(0);
        assertThat(result.getSummary()).contains("RETRY/REVIEW");
        assertThat(result.getClaims()).isNotEmpty();
        assertThat(result.getClaims().get(0).getAbilityName()).isEqualTo("Java");
    }

    @Test
    @DisplayName("校验失败时记录 evidence.not_locatable 结构化指标")
    void validationFailureRecordsEvidenceNotLocatableMetric() throws Exception {
        com.example.matching.agent.lc4j.EmployeeAbilityAiService aiService =
                mock(com.example.matching.agent.lc4j.EmployeeAbilityAiService.class);
        AgentMemoryContextService memoryContextService = mock(AgentMemoryContextService.class);
        when(memoryContextService.resolveRules(anyString(), anyString())).thenReturn(emptyRules());
        com.example.matching.agent.config.ExtractionMetrics metrics =
                mock(com.example.matching.agent.config.ExtractionMetrics.class);

        PersonAbilityExtractionResult aiResult = new PersonAbilityExtractionResult();
        PersonAbilityClaim claim = new PersonAbilityClaim();
        claim.setAbilityName("Java");
        claim.setMasteryLevel(4);
        claim.setEvidenceText("原文中不存在的编造证据");
        aiResult.setClaims(List.of(claim));
        when(aiService.extractAbilities(anyString())).thenReturn(aiResult);

        EmployeeAbilityAgentServiceImpl service = employeeService(aiService, memoryContextService);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "extractionMetrics", metrics);

        PersonAbilityExtractRequest request = new PersonAbilityExtractRequest();
        request.setEmpId(1L);
        request.setSourceType("RESUME_PARSE");
        request.setSourceRefId(8L);
        request.setSourceText("负责Java后端开发");
        request.setSourceRefs(List.of("source:RESUME_PARSE:8"));

        PersonAbilityExtractionResult result = service.extractAbilities(request);

        assertThat(result.isFallbackUsed()).isTrue();
        verify(metrics).evidenceNotLocatable(com.example.matching.agent.config.ExtractionMetrics.SCENARIO_EMPLOYEE);
    }

    @Test
    @DisplayName("岗位分块合并：同名能力跨块去重，保留证据更长/置信度更高项")
    void postChunkedMergeDeduplicatesKeepingBetterClaim() throws Exception {
        com.example.matching.agent.lc4j.PostAbilityAiService aiService =
                mock(com.example.matching.agent.lc4j.PostAbilityAiService.class);
        AgentMemoryContextService memoryContextService = mock(AgentMemoryContextService.class);
        when(memoryContextService.resolveRules(anyString(), anyString())).thenReturn(emptyRules());

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        when(aiService.extractAbilities(captor.capture())).thenAnswer(invocation -> {
            if (captor.getAllValues().size() == 1) {
                // 块 0：证据短、置信度低
                com.example.matching.agent.dto.post.PostAbilityExtractionResult first =
                        new com.example.matching.agent.dto.post.PostAbilityExtractionResult();
                com.example.matching.agent.dto.post.PostAbilityClaim c1 =
                        new com.example.matching.agent.dto.post.PostAbilityClaim();
                c1.setAbilityName("Java");
                c1.setRequiredLevel(3);
                c1.setWeight(new java.math.BigDecimal("0.5"));
                c1.setEvidenceText("负责Java模块设计");
                c1.setConfidenceScore(new java.math.BigDecimal("70"));
                first.setClaims(List.of(c1));
                return first;
            }
            // 块 1：证据更长、置信度更高
            com.example.matching.agent.dto.post.PostAbilityExtractionResult second =
                    new com.example.matching.agent.dto.post.PostAbilityExtractionResult();
            com.example.matching.agent.dto.post.PostAbilityClaim c2 =
                    new com.example.matching.agent.dto.post.PostAbilityClaim();
            c2.setAbilityName("Java");
            c2.setRequiredLevel(3);
            c2.setWeight(new java.math.BigDecimal("0.6"));
            c2.setEvidenceText("负责Java后端开发与性能优化");
            c2.setConfidenceScore(new java.math.BigDecimal("90"));
            second.setClaims(List.of(c2));
            return second;
        });

        // 超过 12000 字符：块0 含证据1；证据2 完全位于 12000 之后（独立块）
        String text = "负责Java模块设计。\n\n" + "A".repeat(12000) + "\n\n负责Java后端开发与性能优化。";

        PostAbilityExtractRequest request = new PostAbilityExtractRequest();
        request.setPostId(7L);
        request.setSourceType("JD_IMPORT");
        request.setSourceRefId(42L);
        request.setSourceText(text);
        request.setSourceRefs(List.of("source:JD_IMPORT:42"));

        com.example.matching.agent.dto.post.PostAbilityExtractionResult result =
                postService(aiService, memoryContextService).extractAbilities(request);

        assertThat(captor.getAllValues()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(result.getClaims()).hasSize(1);
        assertThat(result.getClaims().get(0).getAbilityName()).isEqualTo("Java");
        assertThat(result.getClaims().get(0).getConfidenceScore()).isEqualByComparingTo("90");
        assertThat(result.getClaims().get(0).getEvidenceText()).isEqualTo("负责Java后端开发与性能优化");
        assertThat(result.getClaims().get(0).getWeight()).isEqualByComparingTo("0.6");
    }
}
