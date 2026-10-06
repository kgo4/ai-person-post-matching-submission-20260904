package com.example.matching.service.kg.impl;

import com.example.matching.entity.kg.KnowledgeDomain;
import com.example.matching.entity.kg.KnowledgeNode;
import com.example.matching.mapper.kg.AbilityTagDomainRelMapper;
import com.example.matching.mapper.kg.KnowledgeDomainMapper;
import com.example.matching.mapper.kg.KnowledgeNodeMapper;
import com.example.matching.service.kg.GraphChangeSetService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * {@link KnowledgeDomainServiceImpl} 单元测试。
 *
 * <p>覆盖知识领域 / 知识点的 CRUD、图变更集联动、按父节点查询，
 * 以及默认领域与默认知识点的幂等初始化（含 count 判断与领域缺失分支）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KnowledgeDomainServiceImplTest {

    @Mock private KnowledgeDomainMapper domainMapper;
    @Mock private KnowledgeNodeMapper nodeMapper;
    @Mock private AbilityTagDomainRelMapper relMapper;
    @Mock private GraphChangeSetService graphChangeSetService;

    @InjectMocks
    private KnowledgeDomainServiceImpl service;

    /**
     * MyBatis-Plus LambdaQueryWrapper 需要实体已登记表信息，
     * 否则会抛 "can not find lambda cache for this entity"（纯单测无 Spring 上下文）。
     */
    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        com.baomidou.mybatisplus.core.MybatisConfiguration cfg =
                new com.baomidou.mybatisplus.core.MybatisConfiguration();
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, ""), KnowledgeDomain.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, ""), KnowledgeNode.class);
    }

    private KnowledgeDomain domain(Long id, String code) {
        KnowledgeDomain d = new KnowledgeDomain();
        d.setId(id);
        d.setDomainCode(code);
        d.setDomainName("领域-" + code);
        d.setIsDeleted(0);
        return d;
    }

    private KnowledgeNode node(Long id, Long domainId, Long parentId) {
        KnowledgeNode n = new KnowledgeNode();
        n.setId(id);
        n.setDomainId(domainId);
        n.setParentId(parentId);
        n.setIsDeleted(0);
        return n;
    }

    // ==================== 领域查询 ====================

    @Test
    @DisplayName("getAllDomains：返回未删除领域（按排序序号升序）")
    void getAllDomains_ok() {
        when(domainMapper.selectList(any())).thenReturn(List.of(domain(1L, "PROGRAMMING"), domain(2L, "BACKEND")));

        List<KnowledgeDomain> result = service.getAllDomains();

        assertEquals(2, result.size());
        verify(domainMapper).selectList(any());
    }

    @Test
    @DisplayName("getDomainById：透传 mapper 结果，不存在返回 null")
    void getDomainById_bothPaths() {
        when(domainMapper.selectById(1L)).thenReturn(domain(1L, "PROGRAMMING"));
        assertNotNull(service.getDomainById(1L));

        when(domainMapper.selectById(99L)).thenReturn(null);
        assertNull(service.getDomainById(99L));
    }

    @Test
    @DisplayName("getDomainByCode：按编码查询")
    void getDomainByCode_ok() {
        when(domainMapper.selectOne(any())).thenReturn(domain(1L, "PROGRAMMING"));

        assertEquals("PROGRAMMING", service.getDomainByCode("PROGRAMMING").getDomainCode());
    }

    // ==================== 领域写操作（含图变更集） ====================

    @Test
    @DisplayName("createDomain：初始化 isDeleted/version，插入后登记图变更集")
    void createDomain_ok() {
        doAnswer(inv -> {
            KnowledgeDomain d = inv.getArgument(0);
            d.setId(11L);
            return 1;
        }).when(domainMapper).insert(any(KnowledgeDomain.class));

        KnowledgeDomain input = new KnowledgeDomain();
        input.setDomainCode("PROGRAMMING");
        input.setCreatedBy(7L);

        KnowledgeDomain saved = service.createDomain(input);

        assertEquals(11L, saved.getId());
        assertEquals(0, saved.getIsDeleted());
        assertEquals(1, saved.getVersion());
        verify(domainMapper).insert(input);
        verify(graphChangeSetService).requestChange(
                eq("KNOWLEDGE_DOMAIN"), eq("KNOWLEDGE_DOMAIN"), eq(11L), eq("UPSERT"), any(), eq(7L));
    }

    @Test
    @DisplayName("updateDomain：updateById 后登记 UPSERT 变更集")
    void updateDomain_ok() {
        KnowledgeDomain input = domain(11L, "PROGRAMMING");
        input.setUpdatedBy(8L);

        KnowledgeDomain result = service.updateDomain(input);

        assertSame(input, result);
        verify(domainMapper).updateById(input);
        verify(graphChangeSetService).requestChange(
                eq("KNOWLEDGE_DOMAIN"), eq("KNOWLEDGE_DOMAIN"), eq(11L), eq("UPSERT"), any(), eq(8L));
    }

    @Test
    @DisplayName("deleteDomain：逻辑删除并登记 DELETE 变更集（createdBy 为 null）")
    void deleteDomain_ok() {
        service.deleteDomain(11L);

        ArgumentCaptor<KnowledgeDomain> cap = ArgumentCaptor.forClass(KnowledgeDomain.class);
        verify(domainMapper).updateById(cap.capture());
        assertEquals(11L, cap.getValue().getId());
        assertEquals(1, cap.getValue().getIsDeleted());
        verify(graphChangeSetService).requestChange(
                eq("KNOWLEDGE_DOMAIN"), eq("KNOWLEDGE_DOMAIN"), eq(11L), eq("DELETE"), any(), isNull());
    }

    // ==================== 知识点查询 ====================

    @Test
    @DisplayName("getNodesByDomainId：返回该领域下未删除知识点")
    void getNodesByDomainId_ok() {
        when(nodeMapper.selectList(any())).thenReturn(List.of(node(1L, 5L, null), node(2L, 5L, 1L)));

        assertEquals(2, service.getNodesByDomainId(5L).size());
    }

    @Test
    @DisplayName("getChildNodes：返回指定父节点下的子知识点")
    void getChildNodes_ok() {
        when(nodeMapper.selectList(any())).thenReturn(List.of(node(2L, 5L, 1L)));

        List<KnowledgeNode> children = service.getChildNodes(1L);

        assertEquals(1, children.size());
        assertEquals(1L, children.get(0).getParentId());
    }

    @Test
    @DisplayName("getNodeById：透传 mapper 结果，不存在返回 null")
    void getNodeById_bothPaths() {
        when(nodeMapper.selectById(1L)).thenReturn(node(1L, 5L, null));
        assertNotNull(service.getNodeById(1L));

        when(nodeMapper.selectById(99L)).thenReturn(null);
        assertNull(service.getNodeById(99L));
    }

    @Test
    @DisplayName("getNodeByCode：按编码查询知识点")
    void getNodeByCode_ok() {
        when(nodeMapper.selectOne(any())).thenReturn(node(1L, 5L, null));

        assertNotNull(service.getNodeByCode("DATA_TYPE"));
    }

    // ==================== 知识点写操作（含图变更集） ====================

    @Test
    @DisplayName("createNode：初始化 isDeleted/version，插入后登记 KNOWLEDGE_NODE 变更集")
    void createNode_ok() {
        doAnswer(inv -> {
            KnowledgeNode n = inv.getArgument(0);
            n.setId(21L);
            return 1;
        }).when(nodeMapper).insert(any(KnowledgeNode.class));

        KnowledgeNode input = new KnowledgeNode();
        input.setNodeCode("DATA_TYPE");
        input.setCreatedBy(3L);

        KnowledgeNode saved = service.createNode(input);

        assertEquals(21L, saved.getId());
        assertEquals(0, saved.getIsDeleted());
        assertEquals(1, saved.getVersion());
        verify(graphChangeSetService).requestChange(
                eq("KNOWLEDGE_NODE"), eq("KNOWLEDGE_NODE"), eq(21L), eq("UPSERT"), any(), eq(3L));
    }

    @Test
    @DisplayName("updateNode：updateById 后登记 UPSERT 变更集")
    void updateNode_ok() {
        KnowledgeNode input = node(21L, 5L, null);
        input.setUpdatedBy(4L);

        KnowledgeNode result = service.updateNode(input);

        assertSame(input, result);
        verify(nodeMapper).updateById(input);
        verify(graphChangeSetService).requestChange(
                eq("KNOWLEDGE_NODE"), eq("KNOWLEDGE_NODE"), eq(21L), eq("UPSERT"), any(), eq(4L));
    }

    @Test
    @DisplayName("deleteNode：逻辑删除并登记 DELETE 变更集")
    void deleteNode_ok() {
        service.deleteNode(21L);

        ArgumentCaptor<KnowledgeNode> cap = ArgumentCaptor.forClass(KnowledgeNode.class);
        verify(nodeMapper).updateById(cap.capture());
        assertEquals(21L, cap.getValue().getId());
        assertEquals(1, cap.getValue().getIsDeleted());
        verify(graphChangeSetService).requestChange(
                eq("KNOWLEDGE_NODE"), eq("KNOWLEDGE_NODE"), eq(21L), eq("DELETE"), any(), isNull());
    }

    // ==================== 默认领域初始化 ====================

    @Test
    @DisplayName("initDefaultDomains：已有数据 → 跳过，不插入领域")
    void initDefaultDomains_skipsWhenExists() {
        when(domainMapper.selectCount(any())).thenReturn(3L);

        service.initDefaultDomains();

        verify(domainMapper, never()).insert(any(KnowledgeDomain.class));
        verify(graphChangeSetService, never()).requestChange(any(), any(), anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("initDefaultDomains：无数据 → 创建 8 个默认领域并逐个登记图变更集")
    void initDefaultDomains_createsDefaults() {
        when(domainMapper.selectCount(any())).thenReturn(0L);
        when(domainMapper.insert(any(KnowledgeDomain.class))).thenReturn(1);

        service.initDefaultDomains();

        ArgumentCaptor<KnowledgeDomain> cap = ArgumentCaptor.forClass(KnowledgeDomain.class);
        verify(domainMapper, times(8)).insert(cap.capture());
        List<KnowledgeDomain> all = cap.getAllValues();
        assertEquals("PROGRAMMING", all.get(0).getDomainCode());
        assertEquals("编程基础", all.get(0).getDomainName());
        assertEquals("ACTIVE", all.get(0).getStatus());
        assertEquals(18, all.get(0).getSortOrder());
        assertEquals(18, all.get(0).getDomainWeight());
        assertEquals(0, all.get(0).getIsDeleted());
        assertEquals(1, all.get(0).getVersion());
        assertEquals("DATA", all.get(7).getDomainCode());
        // 默认领域的创建走私有 createDomain，不登记图变更集
        verify(graphChangeSetService, never()).requestChange(any(), any(), any(), any(), any(), any());
    }

    // ==================== 默认知识点初始化 ====================

    @Test
    @DisplayName("initDefaultNodes：已有数据 → 跳过，不插入知识点")
    void initDefaultNodes_skipsWhenExists() {
        when(nodeMapper.selectCount(any())).thenReturn(10L);

        service.initDefaultNodes();

        verify(nodeMapper, never()).insert(any(KnowledgeNode.class));
    }

    @Test
    @DisplayName("initDefaultNodes：四个领域均存在 → 递归创建各级知识点（42 个）")
    void initDefaultNodes_allDomainsPresent() {
        when(nodeMapper.selectCount(any())).thenReturn(0L);
        when(domainMapper.selectOne(any())).thenReturn(
                domain(1L, "PROGRAMMING"), domain(2L, "BACKEND"),
                domain(3L, "AI_BASIC"), domain(4L, "LLM"));
        // 模拟自增主键回填：每次 insert 分配一个全新且唯一的 id，避免同 id 节点被重复归并
        java.util.concurrent.atomic.AtomicLong seq = new java.util.concurrent.atomic.AtomicLong(100);
        doAnswer(inv -> {
            KnowledgeNode n = inv.getArgument(0);
            n.setId(seq.getAndIncrement());
            return 1;
        }).when(nodeMapper).insert(any(KnowledgeNode.class));

        service.initDefaultNodes();

        // 编程12 + 后端10 + AI基础9 + LLM11 = 42 个知识点
        verify(nodeMapper, times(42)).insert(any(KnowledgeNode.class));
        // 默认初始化走私有 createNode，不登记图变更集
        verify(graphChangeSetService, never()).requestChange(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("initDefaultNodes：领域均缺失 → 不创建任何知识点")
    void initDefaultNodes_noDomains() {
        when(nodeMapper.selectCount(any())).thenReturn(0L);
        when(domainMapper.selectOne(any())).thenReturn(null);

        service.initDefaultNodes();

        verify(nodeMapper, never()).insert(any(KnowledgeNode.class));
    }

    @Test
    @DisplayName("initDefaultNodes：仅 PROGRAMMING 存在 → 只创建编程领域知识点")
    void initDefaultNodes_partialDomains() {
        when(nodeMapper.selectCount(any())).thenReturn(0L);
        when(domainMapper.selectOne(any())).thenReturn(domain(1L, "PROGRAMMING"), null, null, null);
        java.util.concurrent.atomic.AtomicLong seq = new java.util.concurrent.atomic.AtomicLong(200);
        doAnswer(inv -> {
            KnowledgeNode n = inv.getArgument(0);
            n.setId(seq.getAndIncrement());
            return 1;
        }).when(nodeMapper).insert(any(KnowledgeNode.class));

        service.initDefaultNodes();

        verify(nodeMapper, times(12)).insert(any(KnowledgeNode.class));
    }

    @Test
    @DisplayName("createNode（内部）：一级知识点 parentId 为 null，二级回填一级节点 id")
    void initDefaultNodes_nodeHierarchy() {
        when(nodeMapper.selectCount(any())).thenReturn(0L);
        when(domainMapper.selectOne(any())).thenReturn(domain(9L, "PROGRAMMING"), null, null, null);
        java.util.concurrent.atomic.AtomicLong seq = new java.util.concurrent.atomic.AtomicLong(900);
        doAnswer(inv -> {
            KnowledgeNode n = inv.getArgument(0);
            n.setId(seq.getAndIncrement());
            return 1;
        }).when(nodeMapper).insert(any(KnowledgeNode.class));

        service.initDefaultNodes();

        ArgumentCaptor<KnowledgeNode> cap = ArgumentCaptor.forClass(KnowledgeNode.class);
        verify(nodeMapper, times(12)).insert(cap.capture());
        KnowledgeNode first = cap.getAllValues().get(0);
        assertEquals("DATA_TYPE", first.getNodeCode());
        assertEquals(9L, first.getDomainId());
        assertNull(first.getParentId());
        assertEquals(1, first.getNodeLevel());
        assertEquals("ACTIVE", first.getStatus());
        assertEquals(0, first.getIsDeleted());
        assertEquals(1, first.getVersion());

        KnowledgeNode child = cap.getAllValues().get(6);
        assertEquals("MUTABLE", child.getNodeCode());
        assertEquals(900L, child.getParentId());
        assertEquals(2, child.getNodeLevel());
    }

    @Test
    @DisplayName("initDefaultNodes：count 为 0（表为空）→ 继续初始化流程，查询四个默认领域")
    void initDefaultNodes_zeroCountContinues() {
        when(nodeMapper.selectCount(any())).thenReturn(0L);
        when(domainMapper.selectOne(any())).thenReturn(null);

        service.initDefaultNodes();

        // 未提前 return（走到领域查询），但无领域可初始化
        verify(domainMapper, times(4)).selectOne(any());
        verify(nodeMapper, never()).insert(any(KnowledgeNode.class));
    }

    @Test
    @DisplayName("initDefaultDomains：每条默认领域都带完整展示字段")
    void initDefaultDomains_fieldMapping() {
        when(domainMapper.selectCount(any())).thenReturn(0L);
        when(domainMapper.insert(any(KnowledgeDomain.class))).thenReturn(1);

        service.initDefaultDomains();

        ArgumentCaptor<KnowledgeDomain> cap = ArgumentCaptor.forClass(KnowledgeDomain.class);
        verify(domainMapper, times(8)).insert(cap.capture());
        KnowledgeDomain llm = cap.getAllValues().stream()
                .filter(d -> "LLM".equals(d.getDomainCode())).findFirst().orElseThrow();
        assertEquals("LLM技术栈", llm.getDomainName());
        assertEquals("Sparkles", llm.getDomainIcon());
        assertEquals("#f59e0b", llm.getDomainColor());
        assertEquals(12, llm.getDomainWeight());
        assertEquals("大模型训练、推理、Prompt工程", llm.getDomainDescription());
    }

    @Test
    @DisplayName("getNodesByDomainId：无知识点时返回空列表")
    void getNodesByDomainId_empty() {
        when(nodeMapper.selectList(any())).thenReturn(Collections.emptyList());

        assertTrue(service.getNodesByDomainId(404L).isEmpty());
    }
}
