package com.example.matching.service.kg.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.matching.entity.kg.KgGraphChangeSet;
import com.example.matching.entity.kg.KgGraphNode;
import com.example.matching.mapper.kg.KgGraphEdgeMapper;
import com.example.matching.mapper.kg.KgGraphNodeMapper;
import com.example.matching.port.post.PostQueryPort;
import com.example.matching.port.tag.TagQueryPort;
import com.example.matching.port.talent.TalentQueryPort;
import com.example.matching.service.kg.KnowledgeGraphBuildService;
import com.example.matching.service.kg.Neo4jGraphStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeGraphIncrementalServiceDegradationTest {

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                KgGraphNode.class);
    }

    @Test
    void applyKeepsMysqlProjectionWhenNeo4jSyncFails() {
        KgGraphNodeMapper nodeMapper = mock(KgGraphNodeMapper.class);
        KgGraphEdgeMapper edgeMapper = mock(KgGraphEdgeMapper.class);
        TagQueryPort tagQueryPort = mock(TagQueryPort.class);
        Neo4jGraphStore graphStore = mock(Neo4jGraphStore.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<Neo4jGraphStore> graphStoreProvider = mock(ObjectProvider.class);

        when(tagQueryPort.getTagById(7L)).thenReturn(new TagQueryPort.TagDTO(
                7L, "Java", "JAVA", "TECH", "TECH", 1,
                null, null, "SYSTEM", null, null, null));
        when(nodeMapper.selectOne(any())).thenReturn(null);
        when(graphStoreProvider.getIfAvailable()).thenReturn(graphStore);
        when(graphStore.syncIncremental(any(), any(), any(), any()))
                .thenReturn(Map.of("status", "FAIL", "message", "Neo4j unavailable"));

        KnowledgeGraphIncrementalServiceImpl service = new KnowledgeGraphIncrementalServiceImpl(
                nodeMapper,
                edgeMapper,
                mock(PostQueryPort.class),
                mock(TalentQueryPort.class),
                tagQueryPort,
                mock(KnowledgeGraphBuildService.class),
                graphStoreProvider,
                new ObjectMapper());
        KgGraphChangeSet changeSet = new KgGraphChangeSet();
        changeSet.setChangeCode("KGC_DEGRADED");
        changeSet.setSourceType("ABILITY_TAG");
        changeSet.setEntityId(7L);
        changeSet.setOperationType("UPSERT");

        var result = service.apply(changeSet);

        assertThat(result.affectedNodeCount()).isEqualTo(1);
        verify(nodeMapper).insert(any(KgGraphNode.class));
        verify(graphStore).syncIncremental(any(), any(), any(), any());
    }
}
