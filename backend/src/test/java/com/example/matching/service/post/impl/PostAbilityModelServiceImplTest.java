package com.example.matching.service.post.impl;

import com.example.matching.common.exception.BusinessException;
import com.example.matching.dto.post.PostAbilityModelConfigDTO;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.post.PostModelQuality;
import com.example.matching.entity.post.PostPost;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.mapper.post.PostModelQualityMapper;
import com.example.matching.mapper.post.PostPostMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.service.ability.AbilityEvidenceIngestionService;
import com.example.matching.service.common.VectorRecallCacheEpoch;
import com.example.matching.converter.post.PostPostConverterImpl;
import com.example.matching.vo.post.PostAbilityModelVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * {@link PostAbilityModelServiceImpl} 单元测试。
 *
 * <p>覆盖单条/批量能力配置、模型查询与权重规范化、质量评分、删除、配置校验的
 * 正常路径与失败分支。所有 Mapper 与外部服务 mock；{@code ServiceImpl.baseMapper}
 * 通过反射注入 mock Mapper 以驱动 {@code save/list/updateById} 等父类方法。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PostAbilityModelServiceImplTest {

    @Mock private PostPostMapper postPostMapper;
    @Mock private AbilityTagMapper abilityTagMapper;
    @Mock private PostModelQualityMapper postModelQualityMapper;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private AbilityEvidenceIngestionService abilityEvidenceIngestionService;
    @Mock private VectorRecallCacheEpoch vectorRecallCacheEpoch;
    @Mock private PostAbilityModelMapper baseMapper;

    private PostAbilityModelServiceImpl service;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        var cfg = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, PostAbilityModel.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, AbilityTag.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, PostPost.class);
    }

    @BeforeEach
    void setUp() {
        PostAbilityModelServiceImpl target = new PostAbilityModelServiceImpl(
                postPostMapper, abilityTagMapper, postModelQualityMapper, eventPublisher,
                new ObjectMapper(), abilityEvidenceIngestionService, vectorRecallCacheEpoch,
                new PostPostConverterImpl());
        ReflectionTestUtils.setField(target, "baseMapper", baseMapper);
        // ServiceImpl.saveBatch 需要真实 MyBatis mapper 代理，纯单测下改为 spy 打桩
        service = spy(target);
        doReturn(true).when(service).saveBatch(anyList());
    }

    private static AbilityTag assessableTag(Long id, String name, String category) {
        AbilityTag tag = new AbilityTag();
        tag.setId(id);
        tag.setTagName(name);
        tag.setTagCategory(category);
        tag.setTagLevel(2);
        tag.setStatus(1);
        return tag;
    }

    private static PostAbilityModelConfigDTO config(Long postId, Long tagId, String abilityName,
                                                    BigDecimal weight, Integer isCore, Integer isRequired) {
        PostAbilityModelConfigDTO dto = new PostAbilityModelConfigDTO();
        dto.setPostId(postId);
        dto.setTagId(tagId);
        dto.setAbilityName(abilityName);
        dto.setWeight(weight);
        dto.setIsCore(isCore);
        dto.setIsRequired(isRequired);
        return dto;
    }

    private static PostAbilityModel model(Long id, Long postId, Long tagId, String name,
                                          BigDecimal weight, Integer isCore) {
        PostAbilityModel m = new PostAbilityModel();
        m.setId(id);
        m.setPostId(postId);
        m.setTagId(tagId);
        m.setAbilityName(name);
        m.setWeight(weight);
        m.setIsCore(isCore);
        m.setMinRequiredLevel(3);
        return m;
    }

    // ==================== 保留的既有用例 ====================

    @Test
    @DisplayName("getPostAbilityModel：未挂标签的能力 → 返回能力名与 modelId 作为展示名")
    void getPostAbilityModel_returnsNameAndModelIdForUntaggedAbility() {
        PostPost post = new PostPost();
        post.setId(1L);
        when(postPostMapper.selectById(1L)).thenReturn(post);

        when(baseMapper.selectList(any())).thenReturn(List.of(
                model(101L, 1L, null, "接口自动化测试", new BigDecimal("20"), 0)));

        PostAbilityModelVO.AbilityRequirementDetail detail = service.getPostAbilityModel(1L)
                .getAbilityRequirements().get(0);

        assertEquals(101L, detail.getModelId());
        assertNull(detail.getTagId());
        assertEquals("接口自动化测试", detail.getAbilityName());
        assertEquals("接口自动化测试", detail.getTagName());
    }

    @Test
    @DisplayName("batchConfig：即使总和有效，单项负权重也应被拒绝")
    void batchConfig_rejectsNegativeWeightEvenWhenTotalIsValid() {
        assertThrows(BusinessException.class, () -> service.batchConfig(List.of(
                config(1L, 10L, "能力A", new BigDecimal("-50"), 0, 0),
                config(1L, 11L, "能力B", new BigDecimal("150"), 0, 0))));
    }

    // ==================== saveConfig ====================

    @Test
    @DisplayName("saveConfig：新增（无 id）→ 保存并回填 modelId，发事件")
    void saveConfig_insert() {
        PostAbilityModelConfigDTO dto = config(1L, null, "Java开发能力", new BigDecimal("100"), 0, 0);
        doAnswer(inv -> {
            PostAbilityModel m = inv.getArgument(0);
            m.setId(50L);
            return 1;
        }).when(baseMapper).insert(any(PostAbilityModel.class));

        service.saveConfig(dto);

        ArgumentCaptor<PostAbilityModel> cap = ArgumentCaptor.forClass(PostAbilityModel.class);
        verify(baseMapper).insert(cap.capture());
        assertEquals("Java开发能力", cap.getValue().getAbilityName());
        assertEquals("java开发能力", cap.getValue().getSkillPointKey());
        verify(abilityEvidenceIngestionService).ingestPostAbilityModel(50L, "POST_ABILITY_MODEL");
        verify(vectorRecallCacheEpoch).advance();
        verify(eventPublisher).publishEvent(any(com.example.matching.event.PostModelChangeEvent.class));
    }

    @Test
    @DisplayName("saveConfig：更新（带 id）→ 读取并 updateById")
    void saveConfig_update() {
        PostAbilityModel existing = model(5L, 1L, null, "旧能力", new BigDecimal("100"), 0);
        when(baseMapper.selectById(5L)).thenReturn(existing);

        PostAbilityModelConfigDTO dto = config(1L, null, "新能力", new BigDecimal("100"), 0, 0);
        dto.setId(5L);

        service.saveConfig(dto);

        verify(baseMapper).updateById(existing);
        assertEquals("新能力", existing.getAbilityName());
    }

    @Test
    @DisplayName("saveConfig：id 存在但记录已不存在 → 不更新，仍尝试摄入证据")
    void saveConfig_updateMissingModel() {
        when(baseMapper.selectById(5L)).thenReturn(null);
        PostAbilityModelConfigDTO dto = config(1L, null, "能力", new BigDecimal("100"), 0, 0);
        dto.setId(5L);

        service.saveConfig(dto);

        verify(baseMapper, never()).updateById(any(PostAbilityModel.class));
    }

    @Test
    @DisplayName("saveConfig：能力名为空 → 抛参数异常")
    void saveConfig_blankAbilityName() {
        PostAbilityModelConfigDTO dto = config(1L, null, "   ", new BigDecimal("100"), 0, 0);
        assertThrows(BusinessException.class, () -> service.saveConfig(dto));
    }

    @Test
    @DisplayName("saveConfig：tagId 指向非可评估标签 → 不阻断，静默丢弃标签关联")
    void saveConfig_invalidTag() {
        when(abilityTagMapper.selectById(99L)).thenReturn(null); // 不可评估
        doAnswer(inv -> {
            PostAbilityModel m = inv.getArgument(0);
            m.setId(1L);
            return 1;
        }).when(baseMapper).insert(any(PostAbilityModel.class));
        PostAbilityModelConfigDTO dto = config(1L, 99L, "能力", new BigDecimal("100"), 0, 0);

        service.saveConfig(dto);

        assertNull(dto.getTagId());
        verify(baseMapper).insert(any(PostAbilityModel.class));
    }

    @Test
    @DisplayName("saveConfig：tagId 有效 → 保留标签关联")
    void saveConfig_validTagKeepsTagId() {
        when(abilityTagMapper.selectById(7L)).thenReturn(assessableTag(7L, "Java", "TECHNICAL"));
        doAnswer(inv -> {
            PostAbilityModel m = inv.getArgument(0);
            m.setId(1L);
            return 1;
        }).when(baseMapper).insert(any(PostAbilityModel.class));
        PostAbilityModelConfigDTO dto = config(1L, 7L, "Java能力", new BigDecimal("100"), 0, 0);

        service.saveConfig(dto);

        assertEquals(7L, dto.getTagId());
    }

    // ==================== getPostAbilityModel 更多分支 ====================

    @Test
    @DisplayName("getPostAbilityModel：岗位不存在 → 返回 null")
    void getPostAbilityModel_postMissing() {
        when(postPostMapper.selectById(1L)).thenReturn(null);
        assertNull(service.getPostAbilityModel(1L));
    }

    @Test
    @DisplayName("getPostAbilityModel：有标签 → 用标签名；无能力名 → 用默认占位名")
    void getPostAbilityModel_usesTagNameAndFallback() {
        PostPost post = new PostPost();
        post.setId(1L);
        when(postPostMapper.selectById(1L)).thenReturn(post);
        when(abilityTagMapper.selectById(7L)).thenReturn(assessableTag(7L, "MySQL", "TECHNICAL"));
        when(baseMapper.selectList(any())).thenReturn(List.of(
                model(1L, 1L, 7L, null, new BigDecimal("60"), 1),
                model(2L, 1L, null, "   ", new BigDecimal("40"), 0)));

        var details = service.getPostAbilityModel(1L).getAbilityRequirements();

        assertEquals("MySQL", details.get(0).getTagName());
        assertNull(details.get(0).getAbilityName());
        assertTrue(details.get(1).getTagName().startsWith("未命名能力（模型#2）"));
    }

    // ==================== listByPostId / 权重规范化 ====================

    @Test
    @DisplayName("listByPostId：权重总和正常（≈100）→ 不做规范化")
    void listByPostId_weightsWithinRange() {
        when(baseMapper.selectList(any())).thenReturn(List.of(
                model(1L, 1L, null, "A", new BigDecimal("60"), 0),
                model(2L, 1L, null, "B", new BigDecimal("40"), 0)));

        List<PostAbilityModel> models = service.listByPostId(1L);

        assertEquals(2, models.size());
        verify(baseMapper, never()).updateById(any(PostAbilityModel.class));
    }

    @Test
    @DisplayName("listByPostId：权重总和偏离 → 归一化到 100 并逐条 updateById")
    void listByPostId_normalizesWeights() {
        when(baseMapper.selectList(any())).thenReturn(List.of(
                model(1L, 1L, null, "A", new BigDecimal("2"), 0),
                model(2L, 1L, null, "B", new BigDecimal("6"), 0)));

        List<PostAbilityModel> models = service.listByPostId(1L);

        BigDecimal total = models.stream().map(PostAbilityModel::getWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, total.compareTo(new BigDecimal("100")));
        verify(baseMapper, times(2)).updateById(any(PostAbilityModel.class));
    }

    @Test
    @DisplayName("listByPostId：空列表 → 直接返回，不规范化")
    void listByPostId_empty() {
        when(baseMapper.selectList(any())).thenReturn(new ArrayList<>());
        assertTrue(service.listByPostId(1L).isEmpty());
    }

    @Test
    @DisplayName("listByPostId：权重总和为 0 → 不规范化（signum<=0 分支）")
    void listByPostId_zeroTotal() {
        when(baseMapper.selectList(any())).thenReturn(List.of(
                model(1L, 1L, null, "A", BigDecimal.ZERO, 0)));

        service.listByPostId(1L);

        verify(baseMapper, never()).updateById(any(PostAbilityModel.class));
    }

    // ==================== listConfiguredPostIds ====================

    @Test
    @DisplayName("listConfiguredPostIds：null / 空 → 返回空集合")
    void listConfiguredPostIds_empty() {
        assertTrue(service.listConfiguredPostIds(null).isEmpty());
        assertTrue(service.listConfiguredPostIds(List.of()).isEmpty());
    }

    @Test
    @DisplayName("listConfiguredPostIds：返回去重后的岗位 id 集合，忽略 null")
    void listConfiguredPostIds_returnsDistinct() {
        PostAbilityModel nullPost = model(9L, null, null, "X", BigDecimal.ONE, 0);
        when(baseMapper.selectList(any())).thenReturn(List.of(
                model(1L, 10L, null, "A", BigDecimal.ONE, 0),
                model(2L, 10L, null, "B", BigDecimal.ONE, 0),
                nullPost));

        Set<Long> ids = service.listConfiguredPostIds(List.of(10L, 11L));

        assertEquals(Set.of(10L), ids);
    }

    // ==================== batchConfig ====================

    @Test
    @DisplayName("batchConfig：null / 空列表 → 抛参数异常")
    void batchConfig_emptyList() {
        assertThrows(BusinessException.class, () -> service.batchConfig(null));
        assertThrows(BusinessException.class, () -> service.batchConfig(List.of()));
    }

    @Test
    @DisplayName("batchConfig：存在 postId 为 null 的项 → 抛参数异常")
    void batchConfig_nullPostIdItem() {
        assertThrows(BusinessException.class, () -> service.batchConfig(List.of(
                config(null, null, "能力", new BigDecimal("100"), 0, 0))));
    }

    @Test
    @DisplayName("batchConfig：postId 不一致 → 抛参数异常")
    void batchConfig_inconsistentPostId() {
        assertThrows(BusinessException.class, () -> service.batchConfig(List.of(
                config(1L, null, "能力A", new BigDecimal("60"), 0, 0),
                config(2L, null, "能力B", new BigDecimal("40"), 0, 0))));
    }

    @Test
    @DisplayName("batchConfig：能力名缺失 → 抛参数异常")
    void batchConfig_missingAbilityName() {
        assertThrows(BusinessException.class, () -> service.batchConfig(List.of(
                config(1L, null, null, new BigDecimal("100"), 0, 0))));
    }

    @Test
    @DisplayName("batchConfig：同名能力重复配置 → 抛重复异常")
    void batchConfig_duplicateAbility() {
        assertThrows(BusinessException.class, () -> service.batchConfig(List.of(
                config(1L, null, "Java", new BigDecimal("50"), 0, 0),
                config(1L, null, "java", new BigDecimal("50"), 0, 0))));
    }

    @Test
    @DisplayName("batchConfig：权重总和超出 95-105 → 抛权重异常")
    void batchConfig_invalidTotalWeight() {
        assertThrows(BusinessException.class, () -> service.batchConfig(List.of(
                config(1L, null, "Java", BigDecimal.ZERO, 0, 0))));
    }

    @Test
    @DisplayName("batchConfig：必填能力权重为 0 → 抛必填权重异常")
    void batchConfig_requiredWeightZero() {
        assertThrows(BusinessException.class, () -> service.batchConfig(List.of(
                config(1L, null, "Java", new BigDecimal("0"), 0, 1),
                config(1L, null, "MySQL", new BigDecimal("100"), 0, 0))));
    }

    @Test
    @DisplayName("batchConfig：tagId 非可评估 → 丢弃关联后仍正常落库")
    void batchConfig_invalidTag() {
        when(abilityTagMapper.selectBatchIds(anyList())).thenReturn(new ArrayList<>());
        PostAbilityModelConfigDTO dto = config(1L, 77L, "Java", new BigDecimal("100"), 0, 0);

        service.batchConfig(List.of(dto));

        assertNull(dto.getTagId());
        verify(baseMapper).physicalDeleteByPostId(1L);
    }

    @Test
    @DisplayName("batchConfig：核心项权重偏低 → 仅告警不阻断，正常落库")
    void batchConfig_coreLowWeightWarnsButSucceeds() {
        when(abilityTagMapper.selectBatchIds(anyList())).thenReturn(new ArrayList<>());
        service.batchConfig(List.of(
                config(1L, null, "Java", new BigDecimal("50"), 1, 0),
                config(1L, null, "MySQL", new BigDecimal("50"), 0, 0)));

        verify(baseMapper).physicalDeleteByPostId(1L);
    }

    @Test
    @DisplayName("batchConfig：正常路径 → 物理删旧、批量存新、计质量分、发事件")
    void batchConfig_success() {
        service.batchConfig(List.of(
                config(1L, null, "Java开发", new BigDecimal("60"), 1, 1),
                config(1L, null, "MySQL", new BigDecimal("40"), 0, 0)));

        verify(baseMapper).physicalDeleteByPostId(1L);
        verify(service).saveBatch(anyList());
        verify(vectorRecallCacheEpoch).advance();
        verify(eventPublisher).publishEvent(any(com.example.matching.event.PostModelChangeEvent.class));
    }

    @Test
    @DisplayName("batchConfig：质量评分计算失败 → 吞异常，主流程仍成功")
    void batchConfig_qualityScoreFailureSwallowed() {
        when(postModelQualityMapper.insert(any(PostModelQuality.class)))
                .thenThrow(new RuntimeException("db down"));

        service.batchConfig(List.of(
                config(1L, null, "Java开发", new BigDecimal("100"), 0, 0)));

        verify(baseMapper).physicalDeleteByPostId(1L);
    }

    @Test
    @DisplayName("batchConfig：相对权重（0-1）历史值 → 归一化为百分比后再落库")
    void batchConfig_legacyRelativeWeights() {
        ArgumentCaptor<List<PostAbilityModel>> cap = ArgumentCaptor.forClass(List.class);

        service.batchConfig(List.of(
                config(1L, null, "能力A", new BigDecimal("0.6"), 0, 0),
                config(1L, null, "能力B", new BigDecimal("0.4"), 0, 0)));

        verify(service).saveBatch(cap.capture());
        assertTrue(cap.getValue().get(0).getWeight().compareTo(new BigDecimal("50")) > 0);
    }

    // ==================== deleteModel ====================

    @Test
    @DisplayName("deleteModel：模型不存在 → 直接返回")
    void deleteModel_missing() {
        when(baseMapper.selectById(1L)).thenReturn(null);
        service.deleteModel(1L);
        verify(baseMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("deleteModel：正常删除 → 发事件触发向量同步")
    void deleteModel_success() {
        when(baseMapper.selectById(1L)).thenReturn(model(1L, 10L, null, "A", BigDecimal.TEN, 0));

        service.deleteModel(1L);

        verify(baseMapper).deleteById(1L);
        verify(vectorRecallCacheEpoch).advance();
        verify(eventPublisher).publishEvent(any(com.example.matching.event.PostModelChangeEvent.class));
    }

    @Test
    @DisplayName("deleteModel：postId 为 null → 不发事件")
    void deleteModel_nullPostId() {
        when(baseMapper.selectById(1L)).thenReturn(model(1L, null, null, "A", BigDecimal.TEN, 0));

        service.deleteModel(1L);

        verify(eventPublisher, never()).publishEvent(any(com.example.matching.event.PostModelChangeEvent.class));
    }

    // ==================== calculateQualityScore ====================

    @Test
    @DisplayName("calculateQualityScore：无模型 → 返回 0")
    void calculateQualityScore_noModels() {
        when(baseMapper.selectList(any())).thenReturn(new ArrayList<>());
        assertEquals(0, service.calculateQualityScore(1L).compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("calculateQualityScore：JD 存在且有核心项 → 加权综合分（含 JD 满分）")
    void calculateQualityScore_withJdAndCore() {
        when(baseMapper.selectList(any())).thenReturn(List.of(
                model(1L, 1L, 7L, "Java", new BigDecimal("60"), 1),
                model(2L, 1L, 8L, "MySQL", new BigDecimal("40"), 0)));
        PostPost post = new PostPost();
        post.setId(1L);
        post.setJobDescription("完整 JD 描述");
        when(postPostMapper.selectById(1L)).thenReturn(post);
        when(abilityTagMapper.selectById(7L)).thenReturn(assessableTag(7L, "Java", "TECHNICAL"));
        when(abilityTagMapper.selectById(8L)).thenReturn(assessableTag(8L, "MySQL", "BUSINESS"));

        BigDecimal score = service.calculateQualityScore(1L);

        assertTrue(score.compareTo(BigDecimal.ZERO) > 0);
        assertTrue(score.compareTo(new BigDecimal("100")) <= 0);
    }

    @Test
    @DisplayName("calculateQualityScore：无 JD 且无核心项 → JD 分为 0，核心清晰度为 0")
    void calculateQualityScore_noJdNoCore() {
        when(baseMapper.selectList(any())).thenReturn(List.of(
                model(1L, 1L, null, "A", new BigDecimal("100"), 0)));
        when(postPostMapper.selectById(1L)).thenReturn(null);
        when(abilityTagMapper.selectById(anyLong())).thenReturn(null);

        BigDecimal score = service.calculateQualityScore(1L);

        // 权重完整度 100*0.35 + 核心清晰度 0 + 覆盖度 0 + JD 0 = 35.00
        assertEquals(new BigDecimal("35.00"), score);
    }

    // ==================== techStack 推断（通过 saveConfig 间接覆盖） ====================

    @Test
    @DisplayName("saveConfig：未提供 techStack → 按能力名推断技术栈")
    void saveConfig_inferTechStack() {
        ArgumentCaptor<PostAbilityModel> cap = ArgumentCaptor.forClass(PostAbilityModel.class);
        doAnswer(inv -> {
            PostAbilityModel m = inv.getArgument(0);
            m.setId(1L);
            return 1;
        }).when(baseMapper).insert(any(PostAbilityModel.class));

        service.saveConfig(config(1L, null, "Spring Boot 微服务", new BigDecimal("100"), 0, 0));
        verify(baseMapper).insert(cap.capture());
        assertEquals("Spring", cap.getValue().getTechStack());

        service.saveConfig(config(1L, null, "MySQL 索引优化", new BigDecimal("100"), 0, 0));
        verify(baseMapper, times(2)).insert(cap.capture());
        assertEquals("数据存储", cap.getValue().getTechStack());
    }

    @Test
    @DisplayName("saveConfig：显式提供 techStack → 直接使用并去空白")
    void saveConfig_explicitTechStack() {
        ArgumentCaptor<PostAbilityModel> cap = ArgumentCaptor.forClass(PostAbilityModel.class);
        doAnswer(inv -> {
            PostAbilityModel m = inv.getArgument(0);
            m.setId(1L);
            return 1;
        }).when(baseMapper).insert(any(PostAbilityModel.class));

        PostAbilityModelConfigDTO dto = config(1L, null, "能力", new BigDecimal("100"), 0, 0);
        dto.setTechStack("  自定义栈  ");
        service.saveConfig(dto);

        verify(baseMapper).insert(cap.capture());
        assertEquals("自定义栈", cap.getValue().getTechStack());
    }
}
