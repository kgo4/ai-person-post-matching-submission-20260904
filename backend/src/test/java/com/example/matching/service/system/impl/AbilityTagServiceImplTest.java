package com.example.matching.service.system.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.dto.system.AbilityTagSaveDTO;
import com.example.matching.dto.system.TagAdmissionContext;
import com.example.matching.dto.system.TagAdmissionResult;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.entity.system.AbilityTagAlias;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.mapper.system.AbilityTagAliasMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.service.system.AbilityTagVectorOperations;
import com.example.matching.service.system.TaxonomyClassifyResult;
import com.example.matching.vo.system.AbilityTagTreeVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
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
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * {@link AbilityTagServiceImpl} 单元测试。
 *
 * <p>覆盖标签保存（新建/编辑/编码重复/层级校验）、树与分类查询、分页、
 * 状态更新、递归删除子树、归并（别名/岗位能力/员工能力合并）与准入委托等分支。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AbilityTagServiceImplTest {

    /**
     * MyBatis-Plus lambda 包装器需要实体已登记表信息，否则抛
     * "can not find lambda cache for this entity"。
     */
    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        TableInfoHelper.initTableInfo(assistant, AbilityTag.class);
        TableInfoHelper.initTableInfo(assistant, AbilityTagAlias.class);
        TableInfoHelper.initTableInfo(assistant, EmpAbility.class);
        TableInfoHelper.initTableInfo(assistant, PostAbilityModel.class);
    }

    @Mock private AbilityTagAliasMapper tagAliasMapper;
    @Mock private PostAbilityModelMapper postAbilityModelMapper;
    @Mock private EmpAbilityMapper empAbilityMapper;
    @Mock private AbilityTagVectorOperations vectorOperations;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private AbilityTagAdmissionEngine admissionEngine;
    @Mock private AbilityTagTaxonomyClassifier taxonomyClassifier;
    @Mock private AbilityTagMapper baseMapper;

    private AbilityTagServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AbilityTagServiceImpl(tagAliasMapper, postAbilityModelMapper, empAbilityMapper,
                vectorOperations, eventPublisher, admissionEngine, taxonomyClassifier);
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);
        // 继承 ServiceImpl 的 save 会在纯单测下走真实批量代理，这里显式桩掉以驱动主流程
        AbilityTagServiceImpl spy = spy(service);
        doReturn(true).when(spy).saveBatch(anyList());
        doReturn(true).when(spy).save(any(AbilityTag.class));
        doReturn(true).when(spy).updateById(any(AbilityTag.class));
        doReturn(true).when(spy).removeById(any(java.io.Serializable.class));
        service = spy;
    }

    private AbilityTag tag(Long id, String name, Long parentId, Integer level, Integer status, String category) {
        AbilityTag t = new AbilityTag();
        t.setId(id);
        t.setTagCode("TAG_" + id);
        t.setTagName(name);
        t.setParentId(parentId);
        t.setTagLevel(level);
        t.setStatus(status);
        t.setTagCategory(category);
        t.setDomain("GENERAL");
        t.setSortOrder(1);
        return t;
    }

    // ==================== saveTag ====================

    @Test
    @DisplayName("saveTag：新建标签 → 置 parentId=0/ROOT_LEVEL，回填 canonicalTagId 并发布事件")
    void saveTag_create() {
        AbilityTagSaveDTO dto = new AbilityTagSaveDTO();
        dto.setTagCode("JAVA_BASIC");
        dto.setTagName("Java基础");
        dto.setTagCategory("TECHNICAL");
        when(baseMapper.selectCount(any())).thenReturn(0L);
        doAnswer(inv -> {
            AbilityTag t = inv.getArgument(0);
            if (t.getId() == null) {
                t.setId(100L);
            }
            return true;
        }).when(service).save(any(AbilityTag.class));

        Long id = service.saveTag(dto);

        assertEquals(100L, id);
        verify(eventPublisher).publishEvent(any(com.example.matching.event.GraphChangeRequestedEvent.class));
        verify(service).updateById(any(AbilityTag.class));
    }

    @Test
    @DisplayName("saveTag：标签编码重复 → 抛 ABILITY_TAG_CODE_DUPLICATE")
    void saveTag_duplicateCode() {
        AbilityTagSaveDTO dto = new AbilityTagSaveDTO();
        dto.setTagCode("JAVA_BASIC");
        dto.setTagCategory("TECHNICAL");
        when(baseMapper.selectCount(any())).thenReturn(1L);

        assertThrows(BusinessException.class, () -> service.saveTag(dto));
        verify(service, never()).save(any(AbilityTag.class));
    }

    @Test
    @DisplayName("saveTag：编辑标签不存在 → 抛 ABILITY_TAG_NOT_FOUND")
    void saveTag_updateNotFound() {
        AbilityTagSaveDTO dto = new AbilityTagSaveDTO();
        dto.setId(5L);
        dto.setTagCategory("TECHNICAL");
        doReturn(null).when(service).getById(5L);

        assertThrows(BusinessException.class, () -> service.saveTag(dto));
    }

    @Test
    @DisplayName("saveTag：编辑已有标签 → 保留层级并更新")
    void saveTag_update() {
        AbilityTagSaveDTO dto = new AbilityTagSaveDTO();
        dto.setId(5L);
        dto.setTagName("新名称");
        dto.setTagCategory("TECHNICAL");
        AbilityTag existing = tag(5L, "旧名称", 0L, 0, 1, "TECHNICAL");
        doReturn(existing).when(service).getById(5L);

        Long id = service.saveTag(dto);

        assertEquals(5L, id);
        assertEquals("新名称", existing.getTagName());
        verify(service).updateById(existing);
    }

    @Test
    @DisplayName("saveTag：层级为空 → 抛 PARAM_ERROR")
    void saveTag_nullTagLevel() {
        AbilityTagSaveDTO dto = new AbilityTagSaveDTO();
        dto.setId(5L);
        dto.setTagCategory("TECHNICAL");
        AbilityTag existing = tag(5L, "名称", 0L, null, 1, "TECHNICAL");
        doReturn(existing).when(service).getById(5L);

        assertThrows(BusinessException.class, () -> service.saveTag(dto));
    }

    @Test
    @DisplayName("saveTag：非根节点却未挂载父节点 → 抛 PARAM_ERROR")
    void saveTag_nonRootWithoutParent() {
        AbilityTagSaveDTO dto = new AbilityTagSaveDTO();
        dto.setId(5L);
        dto.setTagCategory("TECHNICAL");
        AbilityTag existing = tag(5L, "名称", null, 1, 1, "TECHNICAL");
        doReturn(existing).when(service).getById(5L);

        assertThrows(BusinessException.class, () -> service.saveTag(dto));
    }

    @Test
    @DisplayName("saveTag：父标签不合法 → 抛 PARAM_ERROR")
    void saveTag_invalidParent() {
        AbilityTagSaveDTO dto = new AbilityTagSaveDTO();
        dto.setId(5L);
        dto.setTagCategory("TECHNICAL");
        AbilityTag existing = tag(5L, "名称", 9L, 2, 1, "TECHNICAL");
        doReturn(existing).when(service).getById(5L);
        AbilityTag parent = tag(9L, "父", 0L, 0, 0, "TECHNICAL");
        doReturn(parent).when(service).getById(9L);

        assertThrows(BusinessException.class, () -> service.saveTag(dto));
    }

    @Test
    @DisplayName("saveTag：父标签合法（同级分类、上一级、启用）→ 更新成功")
    void saveTag_validParent() {
        AbilityTagSaveDTO dto = new AbilityTagSaveDTO();
        dto.setId(5L);
        dto.setTagCategory("TECHNICAL");
        AbilityTag existing = tag(5L, "名称", 9L, 2, 1, "TECHNICAL");
        doReturn(existing).when(service).getById(5L);
        AbilityTag parent = tag(9L, "父", 0L, 1, 1, "TECHNICAL");
        doReturn(parent).when(service).getById(9L);

        assertEquals(5L, service.saveTag(dto));
        verify(service).updateById(existing);
    }

    // ==================== getTree / getByCategory ====================

    @Test
    @DisplayName("getTree：构建父子层级树")
    void getTree_buildsHierarchy() {
        when(baseMapper.selectList(any())).thenReturn(List.of(
                tag(1L, "根", 0L, 0, 1, "TECHNICAL"),
                tag(2L, "子", 1L, 1, 1, "TECHNICAL")));

        List<AbilityTagTreeVO> tree = service.getTree();

        assertEquals(1, tree.size());
        assertEquals("根", tree.get(0).getTagName());
        assertEquals(1, tree.get(0).getChildren().size());
        assertEquals("子", tree.get(0).getChildren().get(0).getTagName());
    }

    @Test
    @DisplayName("getTree：空集合 → 返回空树")
    void getTree_empty() {
        when(baseMapper.selectList(any())).thenReturn(Collections.emptyList());

        assertTrue(service.getTree().isEmpty());
    }

    @Test
    @DisplayName("getByCategory：按分类构建树，无子节点时 children 为空列表")
    void getByCategory_noChildren() {
        when(baseMapper.selectList(any())).thenReturn(List.of(
                tag(1L, "根", 0L, 0, 1, "TECHNICAL")));

        List<AbilityTagTreeVO> tree = service.getByCategory("TECHNICAL");

        assertEquals(1, tree.size());
        assertTrue(tree.get(0).getChildren().isEmpty());
    }

    // ==================== pageTags ====================

    @Test
    @DisplayName("pageTags：带关键词与分类 → 正常分页")
    @SuppressWarnings("unchecked")
    void pageTags_withFilters() {
        Page<AbilityTag> page = new Page<>(1, 10);
        Page<AbilityTag> result = new Page<>(1, 10);
        result.setRecords(List.of(tag(1L, "Java", 0L, 0, 1, "TECHNICAL")));
        when(baseMapper.selectPage(any(), any())).thenReturn(result);

        IPage<AbilityTag> out = service.pageTags(page, "Java", "TECHNICAL");

        assertEquals(1, out.getRecords().size());
    }

    @Test
    @DisplayName("pageTags：无过滤条件 → 也能分页")
    @SuppressWarnings("unchecked")
    void pageTags_noFilters() {
        Page<AbilityTag> page = new Page<>(1, 10);
        Page<AbilityTag> result = new Page<>(1, 10);
        result.setRecords(Collections.emptyList());
        when(baseMapper.selectPage(any(), any())).thenReturn(result);

        IPage<AbilityTag> out = service.pageTags(page, "  ", null);

        assertTrue(out.getRecords().isEmpty());
    }

    // ==================== updateStatus ====================

    @Test
    @DisplayName("updateStatus：标签不存在 → 抛 ABILITY_TAG_NOT_FOUND")
    void updateStatus_notFound() {
        doReturn(null).when(service).getById(1L);

        assertThrows(BusinessException.class, () -> service.updateStatus(1L, 0));
    }

    @Test
    @DisplayName("updateStatus：正常更新状态")
    void updateStatus_success() {
        AbilityTag t = tag(1L, "Java", 0L, 0, 1, "TECHNICAL");
        doReturn(t).when(service).getById(1L);

        service.updateStatus(1L, 0);

        assertEquals(0, t.getStatus());
        verify(service).updateById(t);
    }

    // ==================== deleteTag ====================

    @Test
    @DisplayName("deleteTag：标签不存在 → 抛 ABILITY_TAG_NOT_FOUND")
    void deleteTag_notFound() {
        doReturn(null).when(service).getById(1L);

        assertThrows(BusinessException.class, () -> service.deleteTag(1L));
    }

    @Test
    @DisplayName("deleteTag：递归软删除整棵子树并逐个发布禁用事件")
    void deleteTag_recursiveSubtree() {
        AbilityTag root = tag(1L, "根", 0L, 0, 1, "TECHNICAL");
        AbilityTag child = tag(2L, "子", 1L, 1, 1, "TECHNICAL");
        // 第一次 getById(1) 用于存在性检查；collectSubtreeIds 第 1 轮 selectList 返回 child；
        // 第 2 轮 selectList 返回空结束递归。
        doReturn(root).when(service).getById(1L);
        doReturn(child).when(service).getById(2L);
        when(baseMapper.selectList(any())).thenReturn(List.of(child), Collections.emptyList());

        service.deleteTag(1L);

        assertEquals(0, root.getStatus());
        assertEquals(0, child.getStatus());
        verify(service).removeById(1L);
        verify(service).removeById(2L);
        verify(eventPublisher, times(2))
                .publishEvent(any(com.example.matching.event.GraphChangeRequestedEvent.class));
    }

    @Test
    @DisplayName("deleteTag：子树节点在更新前已被删除 → 跳过该节点")
    void deleteTag_skipsVanishedNode() {
        AbilityTag root = tag(1L, "根", 0L, 0, 1, "TECHNICAL");
        // 存在性检查（第 1 次 getById）返回 root，递归循环内的 getById 返回 null（节点已消失）
        boolean[] firstCheck = {true};
        doAnswer(inv -> {
            if (firstCheck[0]) {
                firstCheck[0] = false;
                return root;
            }
            return null;
        }).when(service).getById(1L);
        when(baseMapper.selectList(any())).thenReturn(Collections.emptyList());

        service.deleteTag(1L);

        verify(service, never()).removeById(any(java.io.Serializable.class));
        verify(eventPublisher, never())
                .publishEvent(any(com.example.matching.event.GraphChangeRequestedEvent.class));
    }

    // ==================== mergeTags ====================

    @Test
    @DisplayName("mergeTags：源与目标相同 → 直接返回不处理")
    void mergeTags_sameId() {
        service.mergeTags(1L, 1L);

        verify(service, never()).getById(any());
    }

    @Test
    @DisplayName("mergeTags：源或目标标签不存在 → 抛 ABILITY_TAG_NOT_FOUND")
    void mergeTags_notFound() {
        doReturn(null).when(service).getById(1L);
        doReturn(tag(2L, "目标", 0L, 0, 1, "TECHNICAL")).when(service).getById(2L);

        assertThrows(BusinessException.class, () -> service.mergeTags(1L, 2L));
    }

    @Test
    @DisplayName("mergeTags：岗位与员工能力都存在重复 → 合并取最大并删除源记录")
    void mergeTags_mergesReferences() {
        AbilityTag source = tag(1L, "源", 0L, 0, 1, "TECHNICAL");
        AbilityTag target = tag(2L, "目标", 0L, 0, 1, "TECHNICAL");
        target.setCanonicalTagId(2L);
        doReturn(source).when(service).getById(1L);
        doReturn(target).when(service).getById(2L);

        PostAbilityModel srcModel = new PostAbilityModel();
        srcModel.setId(11L);
        srcModel.setPostId(100L);
        srcModel.setTagId(1L);
        srcModel.setMinRequiredLevel(2);
        srcModel.setWeight(new BigDecimal("0.30"));
        srcModel.setIsRequired(0);
        srcModel.setIsCore(0);
        srcModel.setRemark("r1");
        when(postAbilityModelMapper.selectList(any())).thenReturn(List.of(srcModel));
        PostAbilityModel existingModel = new PostAbilityModel();
        existingModel.setId(12L);
        existingModel.setPostId(100L);
        existingModel.setTagId(2L);
        existingModel.setMinRequiredLevel(4);
        existingModel.setWeight(new BigDecimal("0.50"));
        existingModel.setIsRequired(1);
        existingModel.setIsCore(1);
        when(postAbilityModelMapper.selectOne(any())).thenReturn(existingModel);

        EmpAbility srcAbility = new EmpAbility();
        srcAbility.setId(21L);
        srcAbility.setEmpId(7L);
        srcAbility.setTagId(1L);
        srcAbility.setMasteryLevel(2);
        srcAbility.setSourceWeight(new BigDecimal("0.40"));
        srcAbility.setEvaluationDate(LocalDate.of(2026, 1, 1));
        srcAbility.setEvaluationSource("RESUME_PARSE");
        when(empAbilityMapper.selectList(any())).thenReturn(List.of(srcAbility));
        EmpAbility existingAbility = new EmpAbility();
        existingAbility.setId(22L);
        existingAbility.setEmpId(7L);
        existingAbility.setTagId(2L);
        existingAbility.setMasteryLevel(3);
        existingAbility.setSourceWeight(new BigDecimal("0.20"));
        existingAbility.setEvaluationDate(LocalDate.of(2025, 1, 1));
        when(empAbilityMapper.selectOne(any())).thenReturn(existingAbility);

        service.mergeTags(1L, 2L);

        assertEquals(4, existingModel.getMinRequiredLevel());
        assertEquals(1, existingModel.getIsRequired());
        assertEquals(3, existingAbility.getMasteryLevel());
        assertEquals(0, new BigDecimal("0.40").compareTo(existingAbility.getSourceWeight()));
        assertEquals(LocalDate.of(2026, 1, 1), existingAbility.getEvaluationDate());
        assertEquals("RESUME_PARSE", existingAbility.getEvaluationSource());
        verify(postAbilityModelMapper).deleteById(11L);
        verify(empAbilityMapper).deleteById(21L);
        verify(tagAliasMapper).insert(any(AbilityTagAlias.class));
        verify(service).updateById(source);
        assertEquals(0, source.getStatus());
        assertEquals(2L, source.getCanonicalTagId());
    }

    @Test
    @DisplayName("mergeTags：无重复引用 → 仅改挂 tagId，不删除记录")
    void mergeTags_reassignsTagId() {
        AbilityTag source = tag(1L, "源", 0L, 0, 1, "TECHNICAL");
        AbilityTag target = tag(2L, "目标", 0L, 0, 1, "TECHNICAL");
        doReturn(source).when(service).getById(1L);
        doReturn(target).when(service).getById(2L);

        PostAbilityModel model = new PostAbilityModel();
        model.setId(11L);
        model.setPostId(100L);
        model.setTagId(1L);
        when(postAbilityModelMapper.selectList(any())).thenReturn(List.of(model));
        when(postAbilityModelMapper.selectOne(any())).thenReturn(null);

        EmpAbility ability = new EmpAbility();
        ability.setId(21L);
        ability.setEmpId(7L);
        ability.setTagId(1L);
        when(empAbilityMapper.selectList(any())).thenReturn(List.of(ability));
        when(empAbilityMapper.selectOne(any())).thenReturn(null);

        service.mergeTags(1L, 2L);

        assertEquals(2L, model.getTagId());
        assertEquals(2L, ability.getTagId());
        verify(postAbilityModelMapper, never()).deleteById(any());
        verify(empAbilityMapper, never()).deleteById(any());
    }

    @Test
    @DisplayName("mergeTags：源标签 canonicalTagId 为空 → 目标标准标签取自身 ID")
    void mergeTags_targetWithoutCanonical() {
        AbilityTag source = tag(1L, "源", 0L, 0, 1, "TECHNICAL");
        AbilityTag target = tag(2L, "目标", 0L, 0, 1, "TECHNICAL");
        target.setCanonicalTagId(null);
        doReturn(source).when(service).getById(1L);
        doReturn(target).when(service).getById(2L);
        when(postAbilityModelMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(empAbilityMapper.selectList(any())).thenReturn(Collections.emptyList());

        service.mergeTags(1L, 2L);

        assertEquals(2L, source.getCanonicalTagId());
    }

    // ==================== createAssessableCapability ====================

    @Test
    @DisplayName("createAssessableCapability：挂到合法 L1 域 → 继承分类与领域")
    void createAssessableCapability_withParent() {
        AbilityTag parent = tag(9L, "技术域", 0L, 1, 1, "TECHNICAL");
        parent.setDomain("CLOUD");
        doReturn(parent).when(service).getById(9L);
        doAnswer(inv -> {
            AbilityTag t = inv.getArgument(0);
            if (t.getId() == null) {
                t.setId(100L);
            }
            return true;
        }).when(service).save(any(AbilityTag.class));

        AbilityTag created = service.createAssessableCapability(
                "K8s", 9L, null, null, "容器编排", "MANUAL");

        assertEquals(9L, created.getParentId());
        assertEquals("TECHNICAL", created.getTagCategory());
        assertEquals("CLOUD", created.getDomain());
        assertEquals(100L, created.getCanonicalTagId());
    }

    @Test
    @DisplayName("createAssessableCapability：父节点非法（非 L1）→ 抛 PARAM_ERROR")
    void createAssessableCapability_invalidParent() {
        AbilityTag parent = tag(9L, "技术域", 0L, 0, 1, "TECHNICAL");
        doReturn(parent).when(service).getById(9L);

        assertThrows(BusinessException.class, () ->
                service.createAssessableCapability("K8s", 9L, null, null, null, "MANUAL"));
    }

    @Test
    @DisplayName("createAssessableCapability：分类与父域不一致 → 抛 PARAM_ERROR")
    void createAssessableCapability_categoryMismatch() {
        AbilityTag parent = tag(9L, "技术域", 0L, 1, 1, "TECHNICAL");
        doReturn(parent).when(service).getById(9L);

        assertThrows(BusinessException.class, () ->
                service.createAssessableCapability("K8s", 9L, "SOFT", null, null, "MANUAL"));
    }

    @Test
    @DisplayName("createAssessableCapability：无父域且分类器命中 → 自动归层到 L2")
    void createAssessableCapability_autoTaxonomyMatched() {
        AbilityTag matched = tag(20L, "后端能力", 30L, 2, 1, "TECHNICAL");
        matched.setDomain("CLOUD");
        when(taxonomyClassifier.classify(anyString()))
                .thenReturn(TaxonomyClassifyResult.of(matched, "VECTOR", new BigDecimal("0.9")));
        doAnswer(inv -> {
            AbilityTag t = inv.getArgument(0);
            t.setId(100L);
            return true;
        }).when(service).save(any(AbilityTag.class));

        AbilityTag created = service.createAssessableCapability(
                "K8s", null, null, null, null, null);

        assertEquals(30L, created.getParentId());
        assertEquals(2, created.getTagLevel());
        assertEquals("TECHNICAL", created.getTagCategory());
    }

    @Test
    @DisplayName("createAssessableCapability：分类器异常 → 降级为平铺标签仍可入库")
    void createAssessableCapability_autoTaxonomyFallback() {
        when(taxonomyClassifier.classify(anyString())).thenThrow(new RuntimeException("classifier down"));
        doAnswer(inv -> {
            AbilityTag t = inv.getArgument(0);
            t.setId(100L);
            return true;
        }).when(service).save(any(AbilityTag.class));

        AbilityTag created = service.createAssessableCapability(
                "未知能力", null, null, null, null, null);

        assertEquals(0L, created.getParentId());
        assertEquals(0, created.getTagLevel());
        assertEquals("TECHNICAL", created.getTagCategory());
    }

    @Test
    @DisplayName("createAssessableCapability：分类器返回不可用候选 → 降级为平铺标签")
    void createAssessableCapability_autoTaxonomyUnusable() {
        AbilityTag matched = tag(20L, "非评估层", 0L, 0, 1, "TECHNICAL");
        when(taxonomyClassifier.classify(anyString()))
                .thenReturn(TaxonomyClassifyResult.of(matched, "RULE", BigDecimal.ONE));
        doAnswer(inv -> {
            AbilityTag t = inv.getArgument(0);
            t.setId(100L);
            return true;
        }).when(service).save(any(AbilityTag.class));

        AbilityTag created = service.createAssessableCapability(
                "X", null, "SOFT", "GENERAL", null, "MANUAL");

        assertEquals(0L, created.getParentId());
        assertEquals("SOFT", created.getTagCategory());
        assertEquals("GENERAL", created.getDomain());
    }

    // ==================== 委托与简单转发 ====================

    @Test
    @DisplayName("委托方法：findByName/findOrCreateByName/findSimilarTags/别名 转发到准入引擎")
    void delegateMethods() {
        AbilityTag t = tag(1L, "Java", 0L, 0, 1, "TECHNICAL");
        when(admissionEngine.findByName("Java")).thenReturn(t);
        when(admissionEngine.findOrCreateByName(anyString(), any(), any())).thenReturn(t);
        when(admissionEngine.findSimilarTags(anyString(), anyDouble())).thenReturn(List.of(t));
        when(admissionEngine.findByAlias("别名")).thenReturn(t);
        when(admissionEngine.createFormalTag(any(), any(), any(), any(), any())).thenReturn(t);
        when(admissionEngine.findSimilarTagOrCreate(any(), any(), any())).thenReturn(t);

        assertSame(t, service.findByName("Java"));
        assertSame(t, service.findOrCreateByName("Java", "TECHNICAL", "MANUAL"));
        assertEquals(1, service.findSimilarTags("Java", 0.8).size());
        assertSame(t, service.findByAlias("别名"));
        assertSame(t, service.createFormalTag("Java", "TECHNICAL", "GENERAL", "desc", "MANUAL"));
        assertSame(t, service.findSimilarTagOrCreate("Java", "TECHNICAL", "MANUAL"));
    }

    @Test
    @DisplayName("batchGenerateVectors / batchInitCanonicalTagIds：转发到向量操作服务")
    void vectorDelegates() {
        when(vectorOperations.batchGenerateVectors()).thenReturn(3);
        when(vectorOperations.batchInitCanonicalTagIds()).thenReturn(5);

        assertEquals(3, service.batchGenerateVectors());
        assertEquals(5, service.batchInitCanonicalTagIds());
    }

    @Test
    @DisplayName("admitNewTag：转发到准入引擎")
    void admitNewTagDelegate() {
        TagAdmissionResult expected = TagAdmissionResult.rejected("no evidence");
        when(admissionEngine.admitNewTag(any(TagAdmissionContext.class))).thenReturn(expected);

        assertSame(expected, service.admitNewTag(TagAdmissionContext.builder().tagName("X").build()));
    }

    // ==================== addAlias ====================

    @Test
    @DisplayName("addAlias：tagId 为空或别名为空白 → 直接返回")
    void addAlias_invalidArgs() {
        service.addAlias(null, "别名", "MANUAL");
        service.addAlias(1L, "  ", "MANUAL");

        verify(admissionEngine, never()).saveAlias(any(), any());
    }

    @Test
    @DisplayName("addAlias：标签不存在 → 跳过保存")
    void addAlias_tagMissing() {
        doReturn(null).when(service).getById(1L);

        service.addAlias(1L, "别名", "MANUAL");

        verify(admissionEngine, never()).saveAlias(any(), any());
    }

    @Test
    @DisplayName("addAlias：正常保存别名")
    void addAlias_success() {
        doReturn(tag(1L, "Java", 0L, 0, 1, "TECHNICAL")).when(service).getById(1L);

        service.addAlias(1L, "别名", "MANUAL");

        verify(admissionEngine).saveAlias(1L, "别名");
    }

    // ==================== getById / updateById 代理 ====================

    @Test
    @DisplayName("getById / updateById：转发到 baseMapper")
    void baseMapperProxies() {
        AbilityTag t = tag(1L, "Java", 0L, 0, 1, "TECHNICAL");
        when(baseMapper.selectById(1L)).thenReturn(t);
        when(baseMapper.updateById(t)).thenReturn(1);

        assertSame(t, service.getById(1L));
        assertTrue(service.updateById(t));
    }
}
