package com.example.matching.service.kg.build;

import com.example.matching.entity.kg.KgGraphEdge;
import com.example.matching.mapper.kg.KgGraphEdgeMapper;
import com.example.matching.mapper.kg.KgGraphNodeMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GraphSnapshotWriterTest {

    @Mock
    private KgGraphNodeMapper graphNodeMapper;
    @Mock
    private KgGraphEdgeMapper graphEdgeMapper;

    @Test
    void batchInsertEdges_mergesDuplicateEdgeKeysAndPreservesSourceReferences() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        GraphSnapshotWriter writer = new GraphSnapshotWriter(graphNodeMapper, graphEdgeMapper, objectMapper);

        writer.batchInsertEdges(List.of(
                edge("HAS_ABILITY_EMPLOYEE:1_ABILITY:1", "fact:EMP_ABILITY:1", "2.00"),
                edge("HAS_ABILITY_EMPLOYEE:1_ABILITY:1", "fact:EMP_ABILITY:2", "3.00")
        ), new GraphBuildContext("KGV_TEST", "2026-07-24T00:00:00"));

        ArgumentCaptor<KgGraphEdge> edgeCaptor = ArgumentCaptor.forClass(KgGraphEdge.class);
        verify(graphEdgeMapper, times(1)).insert(edgeCaptor.capture());
        KgGraphEdge inserted = edgeCaptor.getValue();
        Map<String, Object> metadata = objectMapper.readValue(inserted.getMetadataJson(), new TypeReference<>() {});
        assertThat(metadata.get("sourceRefs"))
                .asList()
                .containsExactly("fact:EMP_ABILITY:1", "fact:EMP_ABILITY:2");
        assertThat(inserted.getWeightValue()).isEqualByComparingTo("3.00");
    }

    private KgGraphEdge edge(String edgeKey, String sourceRef, String weight) {
        KgGraphEdge edge = new KgGraphEdge();
        edge.setEdgeKey(edgeKey);
        edge.setEdgeType("HAS_ABILITY");
        edge.setSourceNodeKey("EMPLOYEE:1");
        edge.setTargetNodeKey("ABILITY:1");
        edge.setWeightValue(new BigDecimal(weight));
        edge.setMetadataJson("{\"sourceRef\":\"" + sourceRef + "\"}");
        return edge;
    }
}
