package com.example.matching.service.post.impl;

import com.example.matching.common.exception.BusinessException;
import com.example.matching.dto.post.JdAbilityItemDTO;
import com.example.matching.dto.post.PostAbilityModelConfigDTO;
import com.example.matching.dto.post.PostModelExcelRowDTO;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.post.PostPost;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.post.PostPostMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.service.post.PostAbilityModelService;
import com.example.matching.service.post.PostCapabilityGenerationService;
import com.example.matching.service.post.PostPostWriteService;
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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * {@link PostModelExcelImportServiceImpl} 单元测试。
 *
 * <p>覆盖模板B（直接导入）与模板A（AI 补齐）两条导入链路、权重归一化、
 * 岗位自动创建、标签缺失跳过、AI 异常降级，以及权重归一化与岗位模型复制。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PostModelExcelImportServiceImplTest {

    @Mock private PostPostMapper postPostMapper;
    @Mock private PostPostWriteService postPostWriteService;
    @Mock private AbilityTagMapper abilityTagMapper;
    @Mock private PostAbilityModelService postAbilityModelService;
    @Mock private PostCapabilityGenerationService capabilityGenerationService;

    @InjectMocks
    private PostModelExcelImportServiceImpl service;

    /**
     * MyBatis-Plus LambdaQueryWrapper 需要实体已登记表信息，
     * 否则会抛 "can not find lambda cache for this entity"（纯单测无 Spring 上下文）。
     */
    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        com.baomidou.mybatisplus.core.MybatisConfiguration cfg =
                new com.baomidou.mybatisplus.core.MybatisConfiguration();
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, ""), PostPost.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, ""), AbilityTag.class);
    }

    private PostModelExcelRowDTO templateBRow(String postCode, String postName, String tagCode,
                                              BigDecimal weight) {
        PostModelExcelRowDTO row = new PostModelExcelRowDTO();
        row.setPostCode(postCode);
        row.setPostName(postName);
        row.setTagCode(tagCode);
        row.setTagName(tagCode + "名称");
        row.setWeight(weight);
        return row;
    }

    private PostModelExcelRowDTO templateARow(String postCode, String postName, String description) {
        PostModelExcelRowDTO row = new PostModelExcelRowDTO();
        row.setPostCode(postCode);
        row.setPostName(postName);
        row.setPostDescription(description);
        return row;
    }

    private AbilityTag tag(Long id, String code, String name) {
        AbilityTag t = new AbilityTag();
        t.setId(id);
        t.setTagCode(code);
        t.setTagName(name);
        t.setStatus(1);
        return t;
    }

    private PostAbilityModel model(Long id, Long postId, Long tagId, BigDecimal weight) {
        PostAbilityModel m = new PostAbilityModel();
        m.setId(id);
        m.setPostId(postId);
        m.setTagId(tagId);
        m.setWeight(weight);
        m.setMinRequiredLevel(3);
        m.setIsRequired(1);
        m.setIsCore(1);
        return m;
    }

    // ==================== parseExcel ====================

    @Test
    @DisplayName("parseExcel：空输入流 → 解析异常被包装为 IMPORT_ERROR")
    void parseExcel_emptyStream() {
        InputStream in = new ByteArrayInputStream(new byte[0]);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.parseExcel(in));

        assertEquals("Excel解析失败", ex.getMessage());
        assertEquals("parseExcel", ex.getDetail().get("operation"));
        verify(abilityTagMapper, never()).selectOne(any());
    }

    @Test
    @DisplayName("parseExcel：合法但不存在的 Excel 文件 → 交由 EasyExcel 触发解析异常并降级")
    void parseExcel_brokenExcelFile() {
        // 合法 xlsx 文件头（ZIP magic），但内容被截断
        byte[] broken = new byte[]{(byte) 0x50, (byte) 0x4B, 0x03, 0x04, 0x00, 0x00, 0x00, 0x00};
        InputStream in = new ByteArrayInputStream(broken);

        assertThrows(Exception.class, () -> service.parseExcel(in));
    }

    @Test
    @DisplayName("parseExcel：非 Excel 内容 → 解析后无数据行，返回空列表")
    void parseExcel_invalidContent() {
        InputStream in = new ByteArrayInputStream("这不是一个 Excel 文件".getBytes());

        List<PostModelExcelRowDTO> rows = service.parseExcel(in);

        assertNotNull(rows);
        assertTrue(rows.isEmpty());
    }

    // ==================== batchImportFromTemplateB ====================

    @Test
    @DisplayName("batchImportFromTemplateB：空列表 → 返回空结果，不触碰数据库")
    void batchImportB_emptyRows() {
        Map<Long, Integer> result = service.batchImportFromTemplateB(Collections.emptyList());

        assertTrue(result.isEmpty());
        verify(postPostMapper, never()).selectOne(any());
    }

    @Test
    @DisplayName("batchImportFromTemplateB：postCode 为空的行被过滤，全部过滤后无任何写入")
    void batchImportB_blankPostCodeFiltered() {
        PostModelExcelRowDTO blank = templateBRow(null, "岗位A", "TAG_A", BigDecimal.TEN);
        PostModelExcelRowDTO blank2 = templateBRow("   ", "岗位B", "TAG_B", BigDecimal.TEN);

        Map<Long, Integer> result = service.batchImportFromTemplateB(List.of(blank, blank2));

        assertTrue(result.isEmpty());
        verify(postAbilityModelService, never()).batchConfig(anyList());
    }

    @Test
    @DisplayName("batchImportFromTemplateB：岗位已存在 + 标签命中 → 按岗位聚合，权重归一化到 100")
    void batchImportB_existingPostAndTag() {
        PostPost existing = new PostPost();
        existing.setId(100L);
        existing.setPostCode("POST_001");
        when(postPostMapper.selectOne(any())).thenReturn(existing);
        when(abilityTagMapper.selectOne(any())).thenReturn(tag(5L, "JAVA", "Java"));

        Map<Long, Integer> result = service.batchImportFromTemplateB(List.of(
                templateBRow("POST_001", "Java工程师", "JAVA", new BigDecimal("20")),
                templateBRow("POST_001", "Java工程师", "JAVA", new BigDecimal("30"))));

        assertEquals(1, result.size());
        assertEquals(2, result.get(100L));

        ArgumentCaptor<List<PostAbilityModelConfigDTO>> cap = ArgumentCaptor.forClass(List.class);
        verify(postAbilityModelService).batchConfig(cap.capture());
        List<PostAbilityModelConfigDTO> configs = cap.getValue();
        assertEquals(2, configs.size());
        assertEquals(40.00, configs.get(0).getWeight().doubleValue());
        assertEquals(60.00, configs.get(1).getWeight().doubleValue());
        assertEquals(100L, configs.get(0).getPostId());
        assertEquals(5L, configs.get(0).getTagId());
        assertEquals(2, configs.get(0).getMinRequiredLevel());
        assertEquals(0, configs.get(0).getIsCore());
        assertEquals(0, configs.get(0).getIsRequired());
        verify(postPostWriteService, never()).save(any(PostPost.class));
    }

    @Test
    @DisplayName("batchImportFromTemplateB：岗位不存在 → 自动创建岗位并使用回填主键")
    void batchImportB_createsPost() {
        when(postPostMapper.selectOne(any())).thenReturn(null);
        doAnswer(inv -> {
            PostPost p = inv.getArgument(0);
            p.setId(200L);
            return p;
        }).when(postPostWriteService).save(any(PostPost.class));
        when(abilityTagMapper.selectOne(any())).thenReturn(tag(6L, "MYSQL", "MySQL"));

        Map<Long, Integer> result = service.batchImportFromTemplateB(
                List.of(templateBRow("POST_002", "DBA", "MYSQL", new BigDecimal("100"))));

        assertEquals(1, result.size());
        assertEquals(1, result.get(200L));
        ArgumentCaptor<PostPost> cap = ArgumentCaptor.forClass(PostPost.class);
        verify(postPostWriteService).save(cap.capture());
        assertEquals("POST_002", cap.getValue().getPostCode());
        assertEquals("DBA", cap.getValue().getPostName());
        assertEquals(1, cap.getValue().getStatus());

        // 权重已是 100，不再归一化
        ArgumentCaptor<List<PostAbilityModelConfigDTO>> cfgCap = ArgumentCaptor.forClass(List.class);
        verify(postAbilityModelService).batchConfig(cfgCap.capture());
        assertEquals(100.00, cfgCap.getValue().get(0).getWeight().doubleValue());
    }

    @Test
    @DisplayName("batchImportFromTemplateB：岗位名为空 → 回退用岗位编码作为名称")
    void batchImportB_nullPostName() {
        when(postPostMapper.selectOne(any())).thenReturn(null);
        doAnswer(inv -> inv.getArgument(0)).when(postPostWriteService).save(any(PostPost.class));
        when(abilityTagMapper.selectOne(any())).thenReturn(tag(6L, "MYSQL", "MySQL"));

        service.batchImportFromTemplateB(
                List.of(templateBRow("POST_003", null, "MYSQL", new BigDecimal("50"))));

        ArgumentCaptor<PostPost> cap = ArgumentCaptor.forClass(PostPost.class);
        verify(postPostWriteService).save(cap.capture());
        assertEquals("POST_003", cap.getValue().getPostName());
    }

    @Test
    @DisplayName("batchImportFromTemplateB：tagCode 为空的行被跳过，仅保留有效行")
    void batchImportB_skipsBlankTagCode() {
        when(postPostMapper.selectOne(any())).thenReturn(null);
        doAnswer(inv -> {
            PostPost p = inv.getArgument(0);
            p.setId(300L);
            return p;
        }).when(postPostWriteService).save(any(PostPost.class));
        when(abilityTagMapper.selectOne(any())).thenReturn(tag(7L, "REDIS", "Redis"));

        Map<Long, Integer> result = service.batchImportFromTemplateB(List.of(
                templateBRow("POST_004", "岗位", null, BigDecimal.TEN),
                templateBRow("POST_004", "岗位", "  ", BigDecimal.TEN),
                templateBRow("POST_004", "岗位", "REDIS", BigDecimal.TEN)));

        assertEquals(1, result.get(300L));
        ArgumentCaptor<List<PostAbilityModelConfigDTO>> cap = ArgumentCaptor.forClass(List.class);
        verify(postAbilityModelService).batchConfig(cap.capture());
        assertEquals(1, cap.getValue().size());
    }

    @Test
    @DisplayName("batchImportFromTemplateB：标签查不到 → 该行跳过；全部跳过则不写能力模型")
    void batchImportB_tagNotFoundSkips() {
        when(postPostMapper.selectOne(any())).thenReturn(null);
        doAnswer(inv -> {
            PostPost p = inv.getArgument(0);
            p.setId(400L);
            return p;
        }).when(postPostWriteService).save(any(PostPost.class));
        when(abilityTagMapper.selectOne(any())).thenReturn(null);

        Map<Long, Integer> result = service.batchImportFromTemplateB(
                List.of(templateBRow("POST_005", "岗位", "UNKNOWN", BigDecimal.TEN)));

        assertTrue(result.isEmpty());
        verify(postAbilityModelService, never()).batchConfig(anyList());
    }

    @Test
    @DisplayName("batchImportFromTemplateB：tagCode 未命中但 tagName 命中 → 使用名称命中的标签")
    void batchImportB_fallsBackToTagName() {
        when(postPostMapper.selectOne(any())).thenReturn(null);
        doAnswer(inv -> {
            PostPost p = inv.getArgument(0);
            p.setId(500L);
            return p;
        }).when(postPostWriteService).save(any(PostPost.class));
        // 第一次按 tagCode 查（null），第二次按 tagName 查（命中）
        when(abilityTagMapper.selectOne(any())).thenReturn(null, tag(8L, "KAFKA", "Kafka"));

        Map<Long, Integer> result = service.batchImportFromTemplateB(
                List.of(templateBRow("POST_006", "岗位", "KAFKA_UNKNOWN", BigDecimal.TEN)));

        assertEquals(1, result.get(500L));
        ArgumentCaptor<List<PostAbilityModelConfigDTO>> cap = ArgumentCaptor.forClass(List.class);
        verify(postAbilityModelService).batchConfig(cap.capture());
        assertEquals(8L, cap.getValue().get(0).getTagId());
    }

    @Test
    @DisplayName("batchImportFromTemplateB：多岗位分组导入 → 每个岗位各自归一化并记录条数")
    void batchImportB_multiplePostsGrouped() {
        when(postPostMapper.selectOne(any())).thenReturn(null);
        doAnswer(inv -> {
            PostPost p = inv.getArgument(0);
            p.setId("POST_A".equals(p.getPostCode()) ? 600L : 700L);
            return p;
        }).when(postPostWriteService).save(any(PostPost.class));
        when(abilityTagMapper.selectOne(any())).thenReturn(tag(9L, "TAG", "标签"));

        Map<Long, Integer> result = service.batchImportFromTemplateB(List.of(
                templateBRow("POST_A", "岗位A", "TAG", new BigDecimal("10")),
                templateBRow("POST_B", "岗位B", "TAG", new BigDecimal("10")),
                templateBRow("POST_A", "岗位A", "TAG", new BigDecimal("10"))));

        assertEquals(2, result.size());
        assertEquals(2, result.get(600L));
        assertEquals(1, result.get(700L));
        verify(postAbilityModelService, times(2)).batchConfig(anyList());
    }

    @Test
    @DisplayName("batchImportFromTemplateB：权重全为 null → 使用默认权重 10，单个能力项归一化为 100")
    void batchImportB_nullWeights() {
        when(postPostMapper.selectOne(any())).thenReturn(null);
        doAnswer(inv -> {
            PostPost p = inv.getArgument(0);
            p.setId(800L);
            return p;
        }).when(postPostWriteService).save(any(PostPost.class));
        when(abilityTagMapper.selectOne(any())).thenReturn(tag(10L, "TAG", "标签"));

        Map<Long, Integer> result = service.batchImportFromTemplateB(
                List.of(templateBRow("POST_007", "岗位", "TAG", null)));

        assertEquals(1, result.get(800L));
        ArgumentCaptor<List<PostAbilityModelConfigDTO>> cap = ArgumentCaptor.forClass(List.class);
        verify(postAbilityModelService).batchConfig(cap.capture());
        // 未显式传权重时使用默认值 10，作为唯一能力项归一化到 100
        assertEquals(100.00, cap.getValue().get(0).getWeight().doubleValue());
    }

    @Test
    @DisplayName("batchImportFromTemplateB：显式传入等级/核心/必填/备注 → 原样透传")
    void batchImportB_explicitFields() {
        when(postPostMapper.selectOne(any())).thenReturn(null);
        doAnswer(inv -> {
            PostPost p = inv.getArgument(0);
            p.setId(900L);
            return p;
        }).when(postPostWriteService).save(any(PostPost.class));
        when(abilityTagMapper.selectOne(any())).thenReturn(tag(11L, "TAG", "标签"));

        PostModelExcelRowDTO row = templateBRow("POST_008", "岗位", "TAG", new BigDecimal("100"));
        row.setMinRequiredLevel(5);
        row.setIsCore(1);
        row.setIsRequired(1);
        row.setRemark("核心能力");

        service.batchImportFromTemplateB(List.of(row));

        ArgumentCaptor<List<PostAbilityModelConfigDTO>> cap = ArgumentCaptor.forClass(List.class);
        verify(postAbilityModelService).batchConfig(cap.capture());
        PostAbilityModelConfigDTO cfg = cap.getValue().get(0);
        assertEquals(5, cfg.getMinRequiredLevel());
        assertEquals(1, cfg.getIsCore());
        assertEquals(1, cfg.getIsRequired());
        assertEquals("核心能力", cfg.getRemark());
    }

    // ==================== batchImportFromTemplateA ====================

    @Test
    @DisplayName("batchImportFromTemplateA：岗位描述为空 → 跳过 AI 分析")
    void batchImportA_blankDescription() {
        when(postPostMapper.selectOne(any())).thenReturn(null);
        doAnswer(inv -> {
            PostPost p = inv.getArgument(0);
            p.setId(1000L);
            return p;
        }).when(postPostWriteService).save(any(PostPost.class));

        Map<Long, Integer> result = service.batchImportFromTemplateA(
                List.of(templateARow("POST_A1", "岗位", "   ")));

        assertTrue(result.isEmpty());
        verify(capabilityGenerationService, never()).analyzePostText(anyString(), anyString());
    }

    @Test
    @DisplayName("batchImportFromTemplateA：AI 返回匹配标签 → 归一化后批量配置")
    void batchImportA_aiSuccess() {
        when(postPostMapper.selectOne(any())).thenReturn(null);
        doAnswer(inv -> {
            PostPost p = inv.getArgument(0);
            p.setId(1100L);
            return p;
        }).when(postPostWriteService).save(any(PostPost.class));

        JdAbilityItemDTO item = new JdAbilityItemDTO();
        item.setMatchedTagId(21L);
        item.setMinRequiredLevel(4);
        item.setWeight(new BigDecimal("30"));
        item.setIsCore(1);
        when(capabilityGenerationService.analyzePostText(anyString(), anyString())).thenReturn(List.of(item));

        Map<Long, Integer> result = service.batchImportFromTemplateA(List.of(
                templateARow("POST_A2", "岗位", "负责Java后端开发")));

        assertEquals(1, result.get(1100L));
        ArgumentCaptor<List<PostAbilityModelConfigDTO>> cap = ArgumentCaptor.forClass(List.class);
        verify(postAbilityModelService).batchConfig(cap.capture());
        PostAbilityModelConfigDTO cfg = cap.getValue().get(0);
        assertEquals(1100L, cfg.getPostId());
        assertEquals(21L, cfg.getTagId());
        assertEquals(4, cfg.getMinRequiredLevel());
        assertEquals(100.00, cfg.getWeight().doubleValue());
    }

    @Test
    @DisplayName("batchImportFromTemplateA：多个岗位描述被合并后交给 AI 分析")
    void batchImportA_mergesDescriptions() {
        when(postPostMapper.selectOne(any())).thenReturn(null);
        doAnswer(inv -> {
            PostPost p = inv.getArgument(0);
            p.setId(1200L);
            return p;
        }).when(postPostWriteService).save(any(PostPost.class));
        when(capabilityGenerationService.analyzePostText(anyString(), anyString())).thenReturn(Collections.emptyList());

        service.batchImportFromTemplateA(List.of(
                templateARow("POST_A3", "岗位", "职责A"),
                templateARow("POST_A3", "岗位", "要求B")));

        ArgumentCaptor<String> textCap = ArgumentCaptor.forClass(String.class);
        verify(capabilityGenerationService).analyzePostText(anyString(), textCap.capture());
        assertEquals("职责A\n要求B\n", textCap.getValue());
    }

    @Test
    @DisplayName("batchImportFromTemplateA：AI 未匹配标签但建议名称可查到 → 用建议名称回填 tagId")
    void batchImportA_suggestedNameFallback() {
        when(postPostMapper.selectOne(any())).thenReturn(null);
        doAnswer(inv -> {
            PostPost p = inv.getArgument(0);
            p.setId(1300L);
            return p;
        }).when(postPostWriteService).save(any(PostPost.class));

        JdAbilityItemDTO item = new JdAbilityItemDTO();
        item.setSuggestedName("Redis");
        item.setWeight(new BigDecimal("100"));
        when(capabilityGenerationService.analyzePostText(anyString(), anyString())).thenReturn(List.of(item));
        when(abilityTagMapper.selectOne(any())).thenReturn(tag(31L, "REDIS", "Redis"));

        Map<Long, Integer> result = service.batchImportFromTemplateA(List.of(
                templateARow("POST_A4", "岗位", "熟悉 Redis")));

        assertEquals(1, result.get(1300L));
        ArgumentCaptor<List<PostAbilityModelConfigDTO>> cap = ArgumentCaptor.forClass(List.class);
        verify(postAbilityModelService).batchConfig(cap.capture());
        assertEquals(31L, cap.getValue().get(0).getTagId());
    }

    @Test
    @DisplayName("batchImportFromTemplateA：AI 未匹配且建议名称查不到 → 该能力项被过滤，无配置写入")
    void batchImportA_unresolvableItemFiltered() {
        when(postPostMapper.selectOne(any())).thenReturn(null);
        doAnswer(inv -> {
            PostPost p = inv.getArgument(0);
            p.setId(1400L);
            return p;
        }).when(postPostWriteService).save(any(PostPost.class));

        JdAbilityItemDTO item = new JdAbilityItemDTO();
        item.setSuggestedName("无人认识的技能");
        when(capabilityGenerationService.analyzePostText(anyString(), anyString())).thenReturn(List.of(item));
        when(abilityTagMapper.selectOne(any())).thenReturn(null);

        Map<Long, Integer> result = service.batchImportFromTemplateA(List.of(
                templateARow("POST_A5", "岗位", "描述")));

        assertTrue(result.isEmpty());
        verify(postAbilityModelService, never()).batchConfig(anyList());
    }

    @Test
    @DisplayName("batchImportFromTemplateA：AI 返回 null 或空列表 → 该岗位不写入能力模型")
    void batchImportA_emptyAiResult() {
        when(postPostMapper.selectOne(any())).thenReturn(null);
        doAnswer(inv -> {
            PostPost p = inv.getArgument(0);
            p.setId(1500L);
            return p;
        }).when(postPostWriteService).save(any(PostPost.class));
        when(capabilityGenerationService.analyzePostText(anyString(), anyString())).thenReturn(null);

        assertTrue(service.batchImportFromTemplateA(
                List.of(templateARow("POST_A6", "岗位", "描述"))).isEmpty());

        when(capabilityGenerationService.analyzePostText(anyString(), anyString())).thenReturn(Collections.emptyList());
        assertTrue(service.batchImportFromTemplateA(
                List.of(templateARow("POST_A6", "岗位", "描述"))).isEmpty());

        verify(postAbilityModelService, never()).batchConfig(anyList());
    }

    @Test
    @DisplayName("batchImportFromTemplateA：AI 调用抛异常 → 记录日志并继续，不中断整体导入")
    void batchImportA_aiThrows() {
        when(postPostMapper.selectOne(any())).thenReturn(null);
        doAnswer(inv -> {
            PostPost p = inv.getArgument(0);
            p.setId(1600L);
            return p;
        }).when(postPostWriteService).save(any(PostPost.class));
        when(capabilityGenerationService.analyzePostText(anyString(), anyString()))
                .thenThrow(new RuntimeException("LLM down"));

        Map<Long, Integer> result = service.batchImportFromTemplateA(List.of(
                templateARow("POST_A7", "岗位", "描述")));

        assertTrue(result.isEmpty());
        verify(postAbilityModelService, never()).batchConfig(anyList());
    }

    @Test
    @DisplayName("batchImportFromTemplateA：建议名称命中但 minRequiredLevel/weight 为空 → 使用默认值")
    void batchImportA_defaultsApplied() {
        when(postPostMapper.selectOne(any())).thenReturn(null);
        doAnswer(inv -> {
            PostPost p = inv.getArgument(0);
            p.setId(1700L);
            return p;
        }).when(postPostWriteService).save(any(PostPost.class));

        JdAbilityItemDTO item = new JdAbilityItemDTO();
        item.setMatchedTagId(41L);
        when(capabilityGenerationService.analyzePostText(anyString(), anyString())).thenReturn(List.of(item));

        service.batchImportFromTemplateA(List.of(templateARow("POST_A8", "岗位", "描述")));

        ArgumentCaptor<List<PostAbilityModelConfigDTO>> cap = ArgumentCaptor.forClass(List.class);
        verify(postAbilityModelService).batchConfig(cap.capture());
        PostAbilityModelConfigDTO cfg = cap.getValue().get(0);
        assertEquals(2, cfg.getMinRequiredLevel());
        // 默认权重 10，作为唯一能力项被归一化到 100
        assertEquals(100.00, cfg.getWeight().doubleValue());
        assertEquals(0, cfg.getIsCore());
        assertEquals(0, cfg.getIsRequired());
    }

    // ==================== normalizeWeights ====================

    @Test
    @DisplayName("normalizeWeights：岗位无能力模型 → 返回空列表，不写库")
    void normalizeWeights_emptyModel() {
        when(postAbilityModelService.listByPostId(100L)).thenReturn(Collections.emptyList());

        assertTrue(service.normalizeWeights(100L).isEmpty());
        verify(postAbilityModelService, never()).batchConfig(anyList());
    }

    @Test
    @DisplayName("normalizeWeights：权重总和为 0 → 返回空列表，避免除零")
    void normalizeWeights_zeroTotal() {
        when(postAbilityModelService.listByPostId(100L)).thenReturn(List.of(
                model(1L, 100L, 5L, BigDecimal.ZERO),
                model(2L, 100L, 6L, null)));

        assertTrue(service.normalizeWeights(100L).isEmpty());
        verify(postAbilityModelService, never()).batchConfig(anyList());
    }

    @Test
    @DisplayName("normalizeWeights：按比例归一化到 100 并批量更新")
    void normalizeWeights_scalesTo100() {
        when(postAbilityModelService.listByPostId(100L)).thenReturn(List.of(
                model(1L, 100L, 5L, new BigDecimal("30")),
                model(2L, 100L, 6L, new BigDecimal("10"))));

        List<PostAbilityModelConfigDTO> result = service.normalizeWeights(100L);

        assertEquals(2, result.size());
        assertEquals(75.00, result.get(0).getWeight().doubleValue());
        assertEquals(25.00, result.get(1).getWeight().doubleValue());
        assertEquals(1L, result.get(0).getId());
        assertEquals(5L, result.get(0).getTagId());
        assertEquals(3, result.get(0).getMinRequiredLevel());
        verify(postAbilityModelService).batchConfig(result);
    }

    // ==================== copyPostModel ====================

    @Test
    @DisplayName("copyPostModel：源岗位没有能力模型 → 抛 POST_NOT_FOUND")
    void copyPostModel_sourceEmpty() {
        when(postAbilityModelService.listByPostId(100L)).thenReturn(Collections.emptyList());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.copyPostModel(100L, 200L));

        assertEquals("源岗位没有能力模型配置", ex.getMessage());
        assertEquals("copyPostModel", ex.getDetail().get("operation"));
        assertEquals(100L, ex.getDetail().get("entityId"));
    }

    @Test
    @DisplayName("copyPostModel：目标岗位不存在 → 抛 POST_NOT_FOUND")
    void copyPostModel_targetMissing() {
        when(postAbilityModelService.listByPostId(100L)).thenReturn(List.of(model(1L, 100L, 5L, BigDecimal.TEN)));
        when(postPostMapper.selectById(200L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.copyPostModel(100L, 200L));

        assertTrue(ex.getMessage().contains("目标岗位不存在"));
        assertEquals(200L, ex.getDetail().get("entityId"));
    }

    @Test
    @DisplayName("copyPostModel：正常复制 → 配置指向目标岗位并标注来源，返回复制条数")
    void copyPostModel_success() {
        when(postAbilityModelService.listByPostId(100L)).thenReturn(List.of(
                model(1L, 100L, 5L, new BigDecimal("60")),
                model(2L, 100L, 6L, new BigDecimal("40"))));
        PostPost target = new PostPost();
        target.setId(200L);
        when(postPostMapper.selectById(200L)).thenReturn(target);

        int copied = service.copyPostModel(100L, 200L);

        assertEquals(2, copied);
        ArgumentCaptor<List<PostAbilityModelConfigDTO>> cap = ArgumentCaptor.forClass(List.class);
        verify(postAbilityModelService).batchConfig(cap.capture());
        List<PostAbilityModelConfigDTO> configs = cap.getValue();
        assertEquals(200L, configs.get(0).getPostId());
        assertEquals(5L, configs.get(0).getTagId());
        assertEquals("从岗位ID=100复制", configs.get(0).getRemark());
        assertNull(configs.get(0).getId());
    }
}
