package com.example.matching.agent.tools;

import com.example.matching.dto.kg.context.GraphAbilityEvidenceContext;
import com.example.matching.dto.kg.context.GraphContextStatus;
import com.example.matching.dto.kg.context.GraphLearningPrerequisiteContext;
import com.example.matching.dto.kg.context.GraphMatchContext;
import com.example.matching.mapper.kg.KgGraphEdgeMapper;
import com.example.matching.mapper.kg.KgGraphNodeMapper;
import com.example.matching.service.kg.KnowledgeGraphQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 知识图谱工具单元测试
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeGraphToolTest {

    @Mock
    private KgGraphNodeMapper graphNodeMapper;
    @Mock
    private KgGraphEdgeMapper graphEdgeMapper;
    @Mock
    private KnowledgeGraphQueryService queryService;

    @InjectMocks
    private KnowledgeGraphTool tool;

    @Test
    @DisplayName("人岗匹配上下文：委托给受限查询服务")
    void getMatchGraphContext_delegatesToBoundedQueryService() {
        GraphMatchContext expected = GraphMatchContext.empty(GraphContextStatus.POST_NOT_FOUND, 7L, 9L);
        when(queryService.getMatchContext(7L, 9L)).thenReturn(expected);

        assertSame(expected, tool.getMatchGraphContext(7L, 9L));
        verify(queryService).getMatchContext(7L, 9L);
    }

    @Test
    @DisplayName("能力证据上下文：委托给受限查询服务")
    void getAbilityEvidenceContext_delegatesToBoundedQueryService() {
        GraphAbilityEvidenceContext expected = new GraphAbilityEvidenceContext(31L, "Spring Boot", List.of());
        when(queryService.getAbilityEvidenceContext(31L, 7L)).thenReturn(expected);

        assertSame(expected, tool.getAbilityEvidenceContext(31L, 7L));
        verify(queryService).getAbilityEvidenceContext(31L, 7L);
    }

    @Test
    @DisplayName("学习前置条件上下文：委托给受限查询服务")
    void getLearningPrerequisiteContext_delegatesToBoundedQueryService() {
        GraphLearningPrerequisiteContext expected = new GraphLearningPrerequisiteContext(List.of(1L, 2L), List.of());
        when(queryService.getLearningPrerequisiteContext(List.of(1L, 2L))).thenReturn(expected);

        assertThat(tool.getLearningPrerequisiteContext(List.of(1L, 2L)))
                .containsEntry("available", true)
                .containsEntry("item", expected);
        verify(queryService).getLearningPrerequisiteContext(List.of(1L, 2L));
    }

    @Test
    void getLearningPrerequisiteContextReturnsStructuredLimitResult() {
        List<Long> abilityIds = java.util.stream.LongStream.rangeClosed(1, 101).boxed().toList();

        Map<String, Object> result = tool.getLearningPrerequisiteContext(abilityIds);

        assertThat(result.get("available")).isEqualTo(false);
        assertThat(result.get("reason")).isNotNull();
        org.mockito.Mockito.verifyNoInteractions(queryService);
    }

    @Test
    @DisplayName("图查询异常 -> 结构化降级上下文，不向 LLM 抛原始堆栈")
    void getMatchGraphContext_degradesToUnavailableContextOnFailure() {
        when(queryService.getMatchContext(7L, 9L)).thenThrow(new RuntimeException("DB down"));

        GraphMatchContext result = tool.getMatchGraphContext(7L, 9L);

        assertThat(result.status()).isEqualTo(GraphContextStatus.GRAPH_DATA_UNAVAILABLE);
        assertThat(result.abilities()).isEmpty();
    }

    @Test
    @DisplayName("证据链查询异常 -> 空证据列表")
    void getAbilityEvidenceContext_degradesToEmptyEvidenceOnFailure() {
        when(queryService.getAbilityEvidenceContext(31L, 7L))
                .thenThrow(new RuntimeException("DB down"));

        GraphAbilityEvidenceContext result = tool.getAbilityEvidenceContext(31L, 7L);

        assertThat(result.abilityId()).isEqualTo(31L);
        assertThat(result.evidence()).isEmpty();
    }

    @Test
    @DisplayName("前置条件查询异常 -> 结构化错误信息")
    void getLearningPrerequisiteContext_degradesToErrorMapOnFailure() {
        when(queryService.getLearningPrerequisiteContext(List.of(1L)))
                .thenThrow(new RuntimeException("DB down"));

        Map<String, Object> result = tool.getLearningPrerequisiteContext(List.of(1L));

        assertThat(result.get("available")).isEqualTo(false);
        assertThat(result.get("reason")).isNotNull();
        assertThat(result.get("item")).isEqualTo(List.of());
    }

    @Test
    @DisplayName("节点连接查询异常 -> 结构化错误信息")
    void getNodeConnections_degradesToErrorMapOnFailure() {
        when(graphNodeMapper.selectOne(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("DB down"));

        Map<String, Object> result = tool.getNodeConnections("POST:1");

        assertThat(result.get("available")).isEqualTo(false);
        assertThat(result.get("reason")).isNotNull();
    }

    @Test
    @DisplayName("节点搜索异常 -> 结构化错误列表")
    void searchNodes_degradesToErrorListOnFailure() {
        when(graphNodeMapper.selectPage(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("DB down"));

        Map<String, Object> result = tool.searchNodes("POST", "Java");

        assertThat(result.get("available")).isEqualTo(false);
        assertThat(result.get("reason")).isNotNull();
        assertThat(result.get("items")).isEqualTo(List.of());
    }
}
