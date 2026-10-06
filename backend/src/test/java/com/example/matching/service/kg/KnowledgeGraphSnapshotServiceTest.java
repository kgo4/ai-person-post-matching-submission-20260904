package com.example.matching.service.kg;

import com.example.matching.entity.kg.KgGraphSnapshot;
import com.example.matching.mapper.kg.KgGraphSnapshotMapper;
import com.example.matching.service.kg.impl.KnowledgeGraphSnapshotServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KnowledgeGraphSnapshotServiceTest {

    @Mock
    private KgGraphSnapshotMapper graphSnapshotMapper;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private KnowledgeGraphSnapshotServiceImpl snapshotService;

    @Test
    @DisplayName("create snapshot counts nodes and edges from graph json")
    void createSnapshot_countsNodesAndEdges() {
        String graphJson = """
                {
                  "nodes": [{"id":"n1"}, {"id":"n2"}],
                  "edges": [{"id":"e1", "source":"n1", "target":"n2"}]
                }
                """;
        doReturn(1).when(graphSnapshotMapper).insert(any(KgGraphSnapshot.class));

        snapshotService.createSnapshot("FULL", "test", graphJson, 1L);

        ArgumentCaptor<KgGraphSnapshot> captor = ArgumentCaptor.forClass(KgGraphSnapshot.class);
        verify(graphSnapshotMapper).insert(captor.capture());
        assertEquals(2, captor.getValue().getNodeCount());
        assertEquals(1, captor.getValue().getEdgeCount());
    }
}
