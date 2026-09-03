package com.example.matching.agent.tools;

import com.example.matching.agent.dto.EvidenceContextResult;
import com.example.matching.entity.contest.ContestEvidenceItem;
import com.example.matching.mapper.contest.ContestEvidenceItemMapper;
import com.example.matching.service.rag.RagRetrievalService;
import com.example.matching.service.rag.RagScenarioEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("EvidenceContextTool")
class EvidenceContextToolTest {

    private ContestEvidenceItemMapper evidenceMapper;
    private RagRetrievalService ragRetrievalService;
    private EvidenceContextTool tool;

    @BeforeEach
    void setUp() {
        evidenceMapper = mock(ContestEvidenceItemMapper.class);
        ragRetrievalService = mock(RagRetrievalService.class);
        tool = new EvidenceContextTool(evidenceMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(tool, "ragRetrievalService", ragRetrievalService);
    }

    @Test
    @DisplayName("MySQL 证据充足时不触发 RAG 补充")
    void returnsMysqlEvidenceWithoutRagFallback() {
        when(evidenceMapper.selectList(any())).thenReturn(List.of(evidence(1L), evidence(2L), evidence(3L)));

        Map<String, Object> result = tool.getPublicEvidenceByTagId(10L);

        @SuppressWarnings("unchecked")
        List<?> verifiedEvidence = (List<?>) result.get("verifiedEvidence");
        assertThat(verifiedEvidence).hasSize(3);
        @SuppressWarnings("unchecked")
        List<?> ragReferences = (List<?>) result.get("ragReferences");
        assertThat(ragReferences).isEmpty();
        assertThat(result.get("degraded")).isEqualTo(false);
        verify(ragRetrievalService, never()).retrieveContext(anyString(), any(RagScenarioEnum.class), anyInt());
    }

    @Test
    @DisplayName("MySQL 证据不足时 RAG 引用进入独立数组且无可信度")
    void fallsBackToRagWhenMysqlEvidenceIsThin() {
        when(evidenceMapper.selectList(any())).thenReturn(List.of(evidence(1L)));
        when(ragRetrievalService.retrieveContext(anyString(), any(RagScenarioEnum.class), anyInt()))
                .thenReturn("RAG 补充知识片段");

        Map<String, Object> result = tool.getPublicEvidenceByTagId(10L);

        @SuppressWarnings("unchecked")
        List<?> verifiedEvidence = (List<?>) result.get("verifiedEvidence");
        assertThat(verifiedEvidence).hasSize(1);
        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> ragReferences = (List<java.util.Map<String, Object>>) result.get("ragReferences");
        assertThat(ragReferences).hasSize(1);
        assertThat(ragReferences.get(0).get("sourceType")).isEqualTo("RAG_KNOWLEDGE");
        assertThat(ragReferences.get(0).get("score")).isNull();
    }

    @Test
    @DisplayName("MySQL 查询异常 -> 结构化降级返回，不抛原始堆栈")
    void degradesGracefullyWhenMysqlFails() {
        when(evidenceMapper.selectList(any())).thenThrow(new RuntimeException("connection refused"));

        Map<String, Object> result = tool.getPublicEvidenceByTagId(10L);

        assertThat(result.get("found")).isEqualTo(false);
        assertThat(result.get("reason")).isNotNull();
        @SuppressWarnings("unchecked")
        List<?> verifiedEvidence = (List<?>) result.get("verifiedEvidence");
        assertThat(verifiedEvidence).isEmpty();
    }

    @Test
    @DisplayName("MySQL 与 RAG 都失败 -> 空结果不崩溃")
    void returnsEmptyWhenEverythingFails() {
        when(evidenceMapper.selectList(any())).thenThrow(new RuntimeException("connection refused"));
        when(ragRetrievalService.retrieveContext(anyString(), any(RagScenarioEnum.class), anyInt()))
                .thenThrow(new RuntimeException("rag down"));

        Map<String, Object> result = tool.getPublicEvidenceByTagId(10L);

        @SuppressWarnings("unchecked")
        List<?> verifiedEvidence = (List<?>) result.get("verifiedEvidence");
        assertThat(verifiedEvidence).isEmpty();
        @SuppressWarnings("unchecked")
        List<?> ragReferences = (List<?>) result.get("ragReferences");
        assertThat(ragReferences).isEmpty();
    }

    @Test
    @DisplayName("员工专属证据查询异常 -> 结构化降级（空列表 + reason）")
    void empAbilityEvidenceDegradesGracefully() {
        when(evidenceMapper.selectList(any())).thenThrow(new RuntimeException("connection refused"));

        Map<String, Object> result = tool.getEvidenceByEmpAbilityId(5L);

        @SuppressWarnings("unchecked")
        List<?> evidence = (List<?>) result.get("items");
        assertThat(evidence).isEmpty();
        assertThat(result.get("reason")).isNotNull();
    }

    @Test
    @DisplayName("证据详情查询异常 -> 结构化降级不崩溃")
    void evidenceDetailDegradesGracefully() {
        when(evidenceMapper.selectById(5L)).thenThrow(new RuntimeException("connection refused"));

        Map<String, Object> result = tool.getEvidenceDetail(5L);

        assertThat(result.get("found")).isEqualTo(false);
        assertThat(result.get("reason")).isNotNull();
    }

    @Test
    @DisplayName("契约: RAG 引用不得携带任何 credibilityScore 字段")
    void contract_ragReferenceHasNoCredibilityField() {
        assertThat(java.util.Arrays.stream(EvidenceContextResult.RagReference.class.getDeclaredFields())
                .noneMatch(field -> field.getName().toLowerCase().contains("credibility")))
                .isTrue();
    }

    @Test
    @DisplayName("契约: MySQL 证据不足时返回分离的 verifiedEvidence 与 ragReferences 数组")
    void contract_ragSupplementIsSeparatedFromVerifiedEvidence() {
        when(evidenceMapper.selectList(any())).thenReturn(List.of(evidence(1L)));
        when(ragRetrievalService.retrieveContext(anyString(), any(RagScenarioEnum.class), anyInt()))
                .thenReturn("RAG 补充知识片段");

        Map<String, Object> result = tool.getPublicEvidenceByTagId(10L);

        @SuppressWarnings("unchecked")
        List<?> verifiedEvidence = (List<?>) result.get("verifiedEvidence");
        assertThat(verifiedEvidence)
                .as("verifiedEvidence 只能包含数据库已验证证据")
                .hasSize(1);
        @SuppressWarnings("unchecked")
        List<?> ragReferences = (List<?>) result.get("ragReferences");
        assertThat(ragReferences)
                .as("RAG 引用必须独立成数组")
                .isNotEmpty();
        assertThat(hasCredibilityField())
                .as("EvidenceContextResult.RagReference 不得声明 credibilityScore 字段")
                .isFalse();
    }

    private boolean hasCredibilityField() {
        return java.util.Arrays.stream(EvidenceContextResult.RagReference.class.getDeclaredFields())
                .anyMatch(field -> field.getName().toLowerCase().contains("credibility"));
    }

    private ContestEvidenceItem evidence(Long id) {
        ContestEvidenceItem item = new ContestEvidenceItem();
        item.setId(id);
        item.setTagId(10L);
        item.setSourceTitle("证据" + id);
        item.setSourceText("内容" + id);
        item.setSourceType("CONTEST");
        item.setIsDeleted(0);
        return item;
    }
}
