package com.example.matching.service.kg.build;

import com.example.matching.entity.kg.KgGraphEdge;
import com.example.matching.entity.kg.KgGraphNode;
import com.example.matching.mapper.kg.KgGraphEdgeMapper;
import com.example.matching.mapper.kg.KgGraphNodeMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link GraphSnapshotWriter} 的单元测试。
 *
 * <p>这个类负责把投影出来的图节点/边**落库前的规范化与去重**，是图谱快照的写入口。
 * 里面几乎全是"写错也不会报错、只会让图数据悄悄变脏"的分支：置信度默认值按边类型分流、
 * 重复边要合并来源引用、元数据里已有的值不能被覆盖。这里逐条锁定。
 */
class GraphSnapshotWriterTest {

    private final KgGraphNodeMapper graphNodeMapper = mock(KgGraphNodeMapper.class);
    private final KgGraphEdgeMapper graphEdgeMapper = mock(KgGraphEdgeMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GraphSnapshotWriter writer = new GraphSnapshotWriter(graphNodeMapper, graphEdgeMapper, objectMapper);

    private static final GraphBuildContext CTX = new GraphBuildContext("KGV_TEST", "2026-09-04T00:00:00");

    private static KgGraphEdge edge(String edgeKey, String edgeType, BigDecimal weight, String metadataJson) {
        KgGraphEdge e = new KgGraphEdge();
        e.setEdgeKey(edgeKey);
        e.setEdgeType(edgeType);
        e.setSourceNodeKey("EMPLOYEE:1");
        e.setTargetNodeKey("ABILITY:1");
        e.setWeightValue(weight);
        e.setMetadataJson(metadataJson);
        return e;
    }

    private Map<String, Object> metadataOf(KgGraphEdge edge) throws Exception {
        return objectMapper.readValue(edge.getMetadataJson(), new TypeReference<Map<String, Object>>() {
        });
    }

    /** 只落了一条边时的取值（多条时应改用 ArgumentCaptor.getAllValues）。 */
    private KgGraphEdge singleWrittenEdge() {
        ArgumentCaptor<KgGraphEdge> captor = ArgumentCaptor.forClass(KgGraphEdge.class);
        verify(graphEdgeMapper, times(1)).insert(captor.capture());
        return captor.getValue();
    }

    /**
     * 清掉之前的调用记录后再插入一条边并返回落库的那条。
     *
     * <p>同一测试方法里要验多个分支时必须用它：mock 是共享的，
     * 直接连着调两次再 `verify(times(1))` 会抛 TooManyActualInvocations。
     */
    private KgGraphEdge insertAndCapture(KgGraphEdge e) {
        reset(graphEdgeMapper);
        writer.batchInsertEdges(List.of(e), CTX);
        return singleWrittenEdge();
    }

    // ------------------------------------------------------------------ 键生成

    @Test
    @DisplayName("节点/边键的格式必须稳定（下游靠它做幂等与去重）")
    void buildsStableKeys() {
        assertEquals("POST:12", writer.generateNodeKey("POST", 12L));
        assertEquals("ABILITY:-1", writer.generateNodeKey("ABILITY", -1L));
        assertEquals("RELATED_TO_POST:12_ABILITY:3",
                writer.generateEdgeKey("RELATED_TO", "POST:12", "ABILITY:3"));
    }

    // ------------------------------------------------------------------ 节点写入

    @Test
    @DisplayName("批量写节点：空集合直接返回，不做任何 DB 访问")
    void skipsEmptyNodeBatch() {
        writer.batchInsertNodes(List.of());
        verify(graphNodeMapper, never()).insert(any(KgGraphNode.class));
    }

    @Test
    @DisplayName("批量写节点：超过阈值会分批，但每一条都要落库（不能丢数据）")
    void insertsEveryNodeAcrossBatches() {
        List<KgGraphNode> nodes = new ArrayList<>();
        // 1200 = 500 + 500 + 200，正好跨 3 个批次
        for (int i = 0; i < 1200; i++) {
            KgGraphNode node = new KgGraphNode();
            node.setNodeKey("POST:" + i);
            nodes.add(node);
        }

        writer.batchInsertNodes(nodes);

        verify(graphNodeMapper, times(1200)).insert(any(KgGraphNode.class));
    }

    // ------------------------------------------------------------------ 边写入与规范化

    @Test
    @DisplayName("批量写边：空集合直接返回")
    void skipsEmptyEdgeBatch() {
        writer.batchInsertEdges(List.of(), CTX);
        verify(graphEdgeMapper, never()).insert(any(KgGraphEdge.class));
    }

    @Test
    @DisplayName("无来源引用时补默认 provenance 与 kg:GRAPH_RELATION 引用")
    void backfillsProvenanceWhenNoSourceRefs() throws Exception {
        writer.batchInsertEdges(List.of(edge("RELATED_TO_E1_A1", "RELATED_TO", new BigDecimal("0.80"), null)), CTX);

        Map<String, Object> metadata = metadataOf(singleWrittenEdge());
        assertEquals("SYSTEM_PROJECTION", metadata.get("provenance"));
        assertEquals(List.of("kg:GRAPH_RELATION:RELATED_TO_E1_A1"), metadata.get("sourceRefs"));
        assertEquals("RELATED_TO", metadata.get("relationType"));
        assertEquals("ACTIVE", metadata.get("relationStatus"));
        assertEquals("KGV_TEST", metadata.get("graphVersion"));
        assertEquals("2026-09-04T00:00:00", metadata.get("validFrom"));
    }

    @Test
    @DisplayName("已有来源引用时不覆盖，也不补 provenance（人工/业务来源必须保留）")
    void keepsExistingSourceRefs() throws Exception {
        writer.batchInsertEdges(List.of(edge("k1", "RELATED_TO", null,
                "{\"sourceRefs\":[\"source:JD_IMPORT:7\"]}")), CTX);

        Map<String, Object> metadata = metadataOf(singleWrittenEdge());
        assertEquals(List.of("source:JD_IMPORT:7"), metadata.get("sourceRefs"));
        assertFalse(metadata.containsKey("provenance"), "已有 sourceRefs 时不应标成系统投影");
    }

    @Test
    @DisplayName("reviewStatus 按边类型分流：RELATED_TO→APPROVED，其余→SYSTEM_VERIFIED，SUPPORTED_BY→不写")
    void assignsReviewStatusByEdgeType() throws Exception {
        assertEquals("APPROVED",
                metadataOf(insertAndCapture(edge("k1", "RELATED_TO", null, null))).get("reviewStatus"));
        assertEquals("SYSTEM_VERIFIED",
                metadataOf(insertAndCapture(edge("k2", "SIMILAR_TO", null, null))).get("reviewStatus"));
        assertFalse(metadataOf(insertAndCapture(edge("k3", "SUPPORTED_BY", null, null))).containsKey("reviewStatus"),
                "SUPPORTED_BY 是证据边，不应带人工审核状态");
    }

    @Test
    @DisplayName("元数据里已有的 reviewStatus / relationStatus / graphVersion 不被覆盖")
    void doesNotOverwriteExistingMetadataKeys() throws Exception {
        writer.batchInsertEdges(List.of(edge("k1", "RELATED_TO", null,
                "{\"reviewStatus\":\"REJECTED\",\"relationStatus\":\"DEPRECATED\",\"graphVersion\":\"KGV_OLD\"}")), CTX);

        Map<String, Object> metadata = metadataOf(singleWrittenEdge());
        assertEquals("REJECTED", metadata.get("reviewStatus"));
        assertEquals("DEPRECATED", metadata.get("relationStatus"));
        assertEquals("KGV_OLD", metadata.get("graphVersion"));
    }

    @Test
    @DisplayName("上下文缺字段时兜底：validFrom 取当前时间、graphVersion 取 KGV_UNVERSIONED")
    void fallsBackWhenContextIsIncomplete() throws Exception {
        writer.batchInsertEdges(List.of(edge("k1", "RELATED_TO", null, null)), new GraphBuildContext(null, null));

        Map<String, Object> metadata = metadataOf(singleWrittenEdge());
        assertEquals("KGV_UNVERSIONED", metadata.get("graphVersion"));
        Object validFrom = metadata.get("validFrom");
        assertNotNull(validFrom);
        assertFalse(String.valueOf(validFrom).isBlank());
    }

    @Test
    @DisplayName("置信度默认值：RELATED_TO 权重×100、SUPPORTED_BY 归一化到百分制、其他类型固定 100")
    void derivesConfidenceScoreByEdgeType() {
        assertEquals(0, insertAndCapture(edge("k1", "RELATED_TO", new BigDecimal("0.85"), null))
                .getConfidenceScore().compareTo(new BigDecimal("85.00")));

        // SUPPORTED_BY：权重 <= 1 视为比例 → ×100
        assertEquals(0, insertAndCapture(edge("k2", "SUPPORTED_BY", new BigDecimal("0.7"), null))
                .getConfidenceScore().compareTo(new BigDecimal("70.0")));

        // SUPPORTED_BY：权重 > 1 已是百分制 → 原样
        assertEquals(0, insertAndCapture(edge("k3", "SUPPORTED_BY", new BigDecimal("88"), null))
                .getConfidenceScore().compareTo(new BigDecimal("88")));

        // 其他类型且无权重 → 100
        assertEquals(0, insertAndCapture(edge("k4", "SIMILAR_TO", null, null))
                .getConfidenceScore().compareTo(new BigDecimal("100")));
    }

    @Test
    @DisplayName("已有置信度时保持原值（不被默认值覆盖）")
    void keepsExplicitConfidenceScore() {
        KgGraphEdge e = edge("k1", "RELATED_TO", new BigDecimal("0.5"), null);
        e.setConfidenceScore(new BigDecimal("42"));

        writer.batchInsertEdges(List.of(e), CTX);

        assertEquals(0, singleWrittenEdge().getConfidenceScore().compareTo(new BigDecimal("42")));
    }

    // ------------------------------------------------------------------ 重复边合并

    @Test
    @DisplayName("同键重复边合并：只落一条，权重取大者，来源引用取并集（兼容旧的单值 sourceRef）")
    void mergesDuplicateEdgeKeysAndPreservesSourceReferences() throws Exception {
        writer.batchInsertEdges(List.of(
                edge("HAS_ABILITY_EMPLOYEE:1_ABILITY:1", "HAS_ABILITY", new BigDecimal("2.00"),
                        "{\"sourceRef\":\"fact:EMP_ABILITY:1\"}"),
                edge("HAS_ABILITY_EMPLOYEE:1_ABILITY:1", "HAS_ABILITY", new BigDecimal("3.00"),
                        "{\"sourceRef\":\"fact:EMP_ABILITY:2\"}")
        ), new GraphBuildContext("KGV_TEST", "2026-09-04T00:00:00"));

        KgGraphEdge merged = singleWrittenEdge();
        Map<String, Object> metadata = metadataOf(merged);
        assertEquals(List.of("fact:EMP_ABILITY:1", "fact:EMP_ABILITY:2"), metadata.get("sourceRefs"));
        assertEquals(0, merged.getWeightValue().compareTo(new BigDecimal("3.00")));
    }

    @Test
    @DisplayName("重复边合并：后一条权重更小时不回退权重，但仍要合并来源引用")
    void mergeKeepsLargerWeightWhenDuplicateIsSmaller() throws Exception {
        writer.batchInsertEdges(List.of(
                edge("DUP", "RELATED_TO", new BigDecimal("0.90"), "{\"sourceRefs\":[\"source:JD_IMPORT:1\"]}"),
                edge("DUP", "RELATED_TO", new BigDecimal("0.10"), "{\"sourceRefs\":[\"source:JD_IMPORT:2\"]}")
        ), CTX);

        KgGraphEdge merged = singleWrittenEdge();
        assertEquals(0, merged.getWeightValue().compareTo(new BigDecimal("0.90")));
        assertEquals(0, merged.getConfidenceScore().compareTo(new BigDecimal("90.00")),
                "置信度应随较大权重一起更新");
        @SuppressWarnings("unchecked")
        List<String> refs = (List<String>) metadataOf(merged).get("sourceRefs");
        assertEquals(2, refs.size(), "来源引用要合并去重，不能只剩一条");
    }

    @Test
    @DisplayName("元数据是非法 JSON 时丢弃重建，不让脏数据把建图打断")
    void discardsInvalidMetadataJson() throws Exception {
        writer.batchInsertEdges(List.of(edge("k1", "RELATED_TO", null, "{ this is not json ")), CTX);

        Map<String, Object> metadata = metadataOf(singleWrittenEdge());
        assertEquals("SYSTEM_PROJECTION", metadata.get("provenance"));
        assertEquals("RELATED_TO", metadata.get("relationType"));
    }

    @Test
    @DisplayName("兼容旧的单值 sourceRef 字段")
    void readsLegacySingleSourceRef() throws Exception {
        writer.batchInsertEdges(List.of(edge("k1", "RELATED_TO", null, "{\"sourceRef\":\"source:JD_IMPORT:9\"}")), CTX);

        assertEquals(List.of("source:JD_IMPORT:9"), metadataOf(singleWrittenEdge()).get("sourceRefs"));
    }

    // ------------------------------------------------------------------ 其他

    @Test
    @DisplayName("graphNodeExists：空键直接 false，且不访问数据库")
    void graphNodeExistsShortCircuitsOnBlankKey() {
        assertFalse(writer.graphNodeExists(null));
        assertFalse(writer.graphNodeExists(""));
        verify(graphNodeMapper, never()).selectCount(any());
    }

    @Test
    @DisplayName("graphNodeExists：按节点键计数判断")
    void graphNodeExistsUsesCount() {
        when(graphNodeMapper.selectCount(any())).thenReturn(1L);
        assertTrue(writer.graphNodeExists("POST:1"));

        when(graphNodeMapper.selectCount(any())).thenReturn(0L);
        assertFalse(writer.graphNodeExists("POST:2"));
    }

    @Test
    @DisplayName("readSourceRefs：空/非法 JSON 都返回空列表而不是抛异常")
    void readSourceRefsIsFaultTolerant() {
        assertTrue(writer.readSourceRefs(null).isEmpty());
        assertTrue(writer.readSourceRefs("   ").isEmpty());
        assertTrue(writer.readSourceRefs("{not json}").isEmpty());
        assertEquals(List.of("a", "b"), writer.readSourceRefs("[\"a\",\"b\"]"));
    }

    @Test
    @DisplayName("GraphBuildContext 的取值访问器")
    void contextExposesBothFields() {
        GraphBuildContext ctx = new GraphBuildContext("KGV_1", "2026-09-04");
        assertEquals("KGV_1", ctx.graphVersion());
        assertEquals("2026-09-04", ctx.validFrom());
    }
}
