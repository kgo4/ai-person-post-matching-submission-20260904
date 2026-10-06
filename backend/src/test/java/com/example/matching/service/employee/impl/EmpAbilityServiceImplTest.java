package com.example.matching.service.employee.impl;

import com.example.matching.converter.employee.EmpEmployeeConverter;
import com.example.matching.dto.ability.GovernanceTemplateDTO;
import com.example.matching.dto.employee.EmpAbilitySaveDTO;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.entity.ability.PersonAbilityClaim;
import com.example.matching.entity.ability.PersonAbilityGovernanceEvent;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.entity.workflow.PersonAbilityClaimGroup;
import com.example.matching.mapper.ability.PersonAbilityClaimMapper;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.mapper.workflow.PersonAbilityClaimGroupMapper;
import com.example.matching.service.ability.AbilityEvidenceIngestionService;
import com.example.matching.service.ability.PersonAbilityGovernanceService;
import com.example.matching.utils.SecurityUtils;
import com.example.matching.vo.employee.EmpAbilityProfileVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
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
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * {@link EmpAbilityServiceImpl} 单元测试。
 *
 * <p>覆盖 saveAbility 的治理模板校验与四条治理分支、getProfile 的标签解析与评分、
 * listByEmpId、listPendingClaims 的旧链路 + 评估工作流合并去重，以及 batchSave 空/非空分支。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmpAbilityServiceImplTest {

    /**
     * 单测没有 Spring 容器，MyBatis-Plus 的 lambda 列名缓存不会自动建立，
     * 一旦代码里出现 lambda 查询就会抛 "can not find lambda cache for this entity"。
     */
    @BeforeAll
    static void initMybatisPlusTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AbilityTag.class);
        TableInfoHelper.initTableInfo(assistant, EmpAbility.class);
        TableInfoHelper.initTableInfo(assistant, PersonAbilityClaim.class);
        TableInfoHelper.initTableInfo(assistant, PersonAbilityClaimGroup.class);
        TableInfoHelper.initTableInfo(assistant, PersonAbilityGovernanceEvent.class);
    }

    @Mock private EmpEmployeeMapper empEmployeeMapper;
    @Mock private PersonAbilityClaimMapper personAbilityClaimMapper;
    @Mock private AbilityTagMapper abilityTagMapper;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private AbilityEvidenceIngestionService abilityEvidenceIngestionService;
    @Mock private PersonAbilityGovernanceService governanceService;
    @Mock private EmpEmployeeConverter empEmployeeConverter;
    @Mock private PersonAbilityClaimGroupMapper claimGroupMapper;
    @Mock private EmpAbilityMapper empAbilityMapper;

    private EmpAbilityServiceImpl service;

    private static final Long EMP_ID = 7L;

    @BeforeEach
    void setUp() {
        service = new EmpAbilityServiceImpl(empEmployeeMapper, personAbilityClaimMapper, abilityTagMapper,
                eventPublisher, abilityEvidenceIngestionService, governanceService,
                empEmployeeConverter, claimGroupMapper);
        ReflectionTestUtils.setField(service, "baseMapper", empAbilityMapper);
        lenient().when(empEmployeeConverter.toAbilityProfileVO(any()))
                .thenAnswer(invocation -> new EmpAbilityProfileVO());
        SecurityUtils.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityUtils.clear();
    }

    // ==================== saveAbility ====================

    @Test
    @DisplayName("saveAbility：能力名称为空 → 抛 PARAM_ERROR")
    void saveAbility_blankName() {
        EmpAbilitySaveDTO dto = new EmpAbilitySaveDTO();
        dto.setEmpId(EMP_ID);
        dto.setAbilityName("  ");
        dto.setEvaluationSource("MANUAL");

        assertThrows(BusinessException.class, () -> service.saveAbility(dto));
        verify(empAbilityMapper, never()).insert(any(EmpAbility.class));
    }

    @Test
    @DisplayName("saveAbility：更新但记录不存在 → 抛 NOT_FOUND")
    void saveAbility_updateNotFound() {
        EmpAbilitySaveDTO dto = new EmpAbilitySaveDTO();
        dto.setId(1L);
        dto.setEmpId(EMP_ID);
        dto.setAbilityName("Java");
        dto.setEvaluationSource("MANUAL");
        when(empAbilityMapper.selectById(1L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.saveAbility(dto));
    }

    @Test
    @DisplayName("saveAbility：新增 → 落库并回填 ID，随后摄取证据、发布变更事件")
    void saveAbility_insert() {
        EmpAbilitySaveDTO dto = new EmpAbilitySaveDTO();
        dto.setEmpId(EMP_ID);
        dto.setAbilityName("  Java  ");
        dto.setEvaluationSource("MANUAL");
        dto.setMasteryLevel(3);
        doAnswer(inv -> {
            EmpAbility e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(100L);
            }
            return 1;
        }).when(empAbilityMapper).insert(any(EmpAbility.class));

        service.saveAbility(dto);

        assertEquals("Java", dto.getAbilityName());
        verify(empAbilityMapper).insert(any(EmpAbility.class));
        verify(abilityEvidenceIngestionService).ingestEmployeeAbility(100L, "EMP_ABILITY");
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    @DisplayName("saveAbility：更新且无治理模板 → 走更新分支并记录日志")
    void saveAbility_updateWithoutTemplate() {
        EmpAbilitySaveDTO dto = new EmpAbilitySaveDTO();
        dto.setId(1L);
        dto.setEmpId(EMP_ID);
        dto.setAbilityName("Java");
        dto.setEvaluationSource("MANUAL");
        EmpAbility old = new EmpAbility();
        old.setId(1L);
        old.setTagId(5L);
        when(empAbilityMapper.selectById(1L)).thenReturn(old);

        service.saveAbility(dto);

        verify(empAbilityMapper).updateById(any(EmpAbility.class));
        verify(governanceService, never()).createEvent(any(PersonAbilityGovernanceEvent.class));
    }

    @Test
    @DisplayName("saveAbility：治理模板修改类型为空 → 抛 PARAM_ERROR")
    void saveAbility_governanceBlankModifyType() {
        EmpAbilitySaveDTO dto = newDtoWithTemplate();
        dto.getGovernanceTemplate().setModifyType(" ");
        dto.getGovernanceTemplate().setReason("原因");

        assertThrows(BusinessException.class, () -> service.saveAbility(dto));
    }

    @Test
    @DisplayName("saveAbility：治理模板缺修改原因 → 抛 PARAM_ERROR")
    void saveAbility_governanceBlankReason() {
        EmpAbilitySaveDTO dto = newDtoWithTemplate();
        dto.getGovernanceTemplate().setModifyType("MANUAL_ADD");
        dto.getGovernanceTemplate().setReason("");

        assertThrows(BusinessException.class, () -> service.saveAbility(dto));
    }

    @Test
    @DisplayName("saveAbility：MANUAL_ADD 缺支持证据 → 抛 PARAM_ERROR")
    void saveAbility_manualAddWithoutEvidence() {
        EmpAbilitySaveDTO dto = newDtoWithTemplate();
        dto.getGovernanceTemplate().setModifyType("MANUAL_ADD");
        dto.getGovernanceTemplate().setReason("新增");
        dto.getGovernanceTemplate().setSupportEvidence(null);

        assertThrows(BusinessException.class, () -> service.saveAbility(dto));
    }

    @Test
    @DisplayName("saveAbility：LEVEL_UP 缺新等级 → 抛 PARAM_ERROR")
    void saveAbility_levelUpWithoutLevel() {
        EmpAbilitySaveDTO dto = newDtoWithTemplate();
        dto.getGovernanceTemplate().setModifyType("LEVEL_UP");
        dto.getGovernanceTemplate().setReason("升级");
        dto.getGovernanceTemplate().setSupportEvidence("证据");
        dto.getGovernanceTemplate().setNewLevel(null);

        assertThrows(BusinessException.class, () -> service.saveAbility(dto));
    }

    @Test
    @DisplayName("saveAbility：DELETE_ABILITY 缺删除原因 → 抛 PARAM_ERROR")
    void saveAbility_deleteWithoutReason() {
        EmpAbilitySaveDTO dto = newDtoWithTemplate();
        dto.getGovernanceTemplate().setModifyType("DELETE_ABILITY");
        dto.getGovernanceTemplate().setReason("删除");
        dto.getGovernanceTemplate().setDeleteReason("  ");

        assertThrows(BusinessException.class, () -> service.saveAbility(dto));
    }

    @Test
    @DisplayName("saveAbility：MANUAL_ADD 合法 → 创建治理事件并生成记忆")
    void saveAbility_manualAddSuccess() {
        EmpAbilitySaveDTO dto = newDtoWithTemplate();
        GovernanceTemplateDTO t = dto.getGovernanceTemplate();
        t.setModifyType("MANUAL_ADD");
        t.setReason("新增能力");
        t.setSupportEvidence("项目证据");
        AbilityTag tag = new AbilityTag();
        tag.setId(5L);
        tag.setTagName("Java");
        when(abilityTagMapper.selectById(5L)).thenReturn(tag);
        doAnswer(inv -> {
            EmpAbility e = inv.getArgument(0);
            e.setId(100L);
            return 1;
        }).when(empAbilityMapper).insert(any(EmpAbility.class));

        service.saveAbility(dto);

        ArgumentCaptor<PersonAbilityGovernanceEvent> cap = ArgumentCaptor.forClass(PersonAbilityGovernanceEvent.class);
        verify(governanceService).createEvent(cap.capture());
        assertEquals("MANUAL_ADD", cap.getValue().getModifyType());
        assertEquals("Java", cap.getValue().getNewTagName());
        verify(governanceService).generateAgentMemory(any(PersonAbilityGovernanceEvent.class));
    }

    @Test
    @DisplayName("saveAbility：EVIDENCE_UPDATE → 创建证据更新事件")
    void saveAbility_evidenceUpdate() {
        EmpAbilitySaveDTO dto = newDtoWithTemplate();
        GovernanceTemplateDTO t = dto.getGovernanceTemplate();
        t.setModifyType("EVIDENCE_UPDATE");
        t.setReason("证据修正");
        when(abilityTagMapper.selectById(5L)).thenReturn(null);
        doAnswer(inv -> {
            EmpAbility e = inv.getArgument(0);
            e.setId(100L);
            return 1;
        }).when(empAbilityMapper).insert(any(EmpAbility.class));

        service.saveAbility(dto);

        ArgumentCaptor<PersonAbilityGovernanceEvent> cap = ArgumentCaptor.forClass(PersonAbilityGovernanceEvent.class);
        verify(governanceService).createEvent(cap.capture());
        assertEquals("EVIDENCE_UPDATE", cap.getValue().getModifyType());
    }

    @Test
    @DisplayName("saveAbility：LEVEL_UP → 调用治理服务 changeLevel")
    void saveAbility_levelUpDelegates() {
        EmpAbilitySaveDTO dto = newDtoWithTemplate();
        GovernanceTemplateDTO t = dto.getGovernanceTemplate();
        t.setModifyType("LEVEL_UP");
        t.setReason("升级");
        t.setSupportEvidence("证据");
        t.setNewLevel(4);
        doAnswer(inv -> {
            EmpAbility e = inv.getArgument(0);
            e.setId(100L);
            return 1;
        }).when(empAbilityMapper).insert(any(EmpAbility.class));

        service.saveAbility(dto);

        verify(governanceService).changeLevel(EMP_ID, 5L, 4, "升级", null);
    }

    @Test
    @DisplayName("saveAbility：DELETE_ABILITY → 调用治理服务 removeTag")
    void saveAbility_deleteDelegates() {
        EmpAbilitySaveDTO dto = newDtoWithTemplate();
        GovernanceTemplateDTO t = dto.getGovernanceTemplate();
        t.setModifyType("DELETE_ABILITY");
        t.setReason("删除能力");
        t.setDeleteReason("标签重复");
        doAnswer(inv -> {
            EmpAbility e = inv.getArgument(0);
            e.setId(100L);
            return 1;
        }).when(empAbilityMapper).insert(any(EmpAbility.class));

        service.saveAbility(dto);

        verify(governanceService).removeTag(EMP_ID, 5L, "删除能力", null);
    }

    @Test
    @DisplayName("saveAbility：TAG_REPLACE（更新）→ 调用治理服务 replaceTag，简历来源且勾选纠偏时开启泛化")
    void saveAbility_tagReplaceDelegates() {
        EmpAbilitySaveDTO dto = newDtoWithTemplate();
        dto.setId(1L);
        GovernanceTemplateDTO t = dto.getGovernanceTemplate();
        t.setModifyType("TAG_REPLACE");
        t.setReason("标签替换");
        t.setOldTagId(5L);
        t.setNewTagId(6L);
        t.setRememberResumeNameCorrection(true);
        EmpAbility old = new EmpAbility();
        old.setId(1L);
        old.setTagId(5L);
        old.setEvaluationSource("RESUME_PARSE");
        when(empAbilityMapper.selectById(1L)).thenReturn(old);

        service.saveAbility(dto);

        verify(governanceService).replaceTag(EMP_ID, 5L, 6L, "标签替换", null, true);
    }

    @Test
    @DisplayName("saveAbility：ABILITY_RENAME → 创建能力重命名事件")
    void saveAbility_abilityRename() {
        EmpAbilitySaveDTO dto = newDtoWithTemplate();
        dto.setId(1L);
        GovernanceTemplateDTO t = dto.getGovernanceTemplate();
        t.setModifyType("ABILITY_RENAME");
        t.setReason("改名");
        t.setOldAbilityName("旧名");
        t.setNewAbilityName("新名");
        EmpAbility old = new EmpAbility();
        old.setId(1L);
        old.setTagId(5L);
        old.setAbilityName("旧名");
        when(empAbilityMapper.selectById(1L)).thenReturn(old);

        service.saveAbility(dto);

        ArgumentCaptor<PersonAbilityGovernanceEvent> cap = ArgumentCaptor.forClass(PersonAbilityGovernanceEvent.class);
        verify(governanceService).createEvent(cap.capture());
        assertEquals("ABILITY_RENAME", cap.getValue().getModifyType());
        assertEquals("旧名", cap.getValue().getOldTagName());
        assertEquals("新名", cap.getValue().getNewTagName());
    }

    @Test
    @DisplayName("saveAbility：未知修改类型 → 仅记录日志，不调用治理服务")
    void saveAbility_unknownModifyType() {
        EmpAbilitySaveDTO dto = newDtoWithTemplate();
        GovernanceTemplateDTO t = dto.getGovernanceTemplate();
        t.setModifyType("UNKNOWN_TYPE");
        t.setReason("原因");
        doAnswer(inv -> {
            EmpAbility e = inv.getArgument(0);
            e.setId(100L);
            return 1;
        }).when(empAbilityMapper).insert(any(EmpAbility.class));

        service.saveAbility(dto);

        verify(governanceService, never()).createEvent(any(PersonAbilityGovernanceEvent.class));
        verify(governanceService, never()).changeLevel(any(), any(), any(), any(), any());
    }

    private EmpAbilitySaveDTO newDtoWithTemplate() {
        EmpAbilitySaveDTO dto = new EmpAbilitySaveDTO();
        dto.setEmpId(EMP_ID);
        dto.setTagId(5L);
        dto.setAbilityName("Java");
        dto.setEvaluationSource("MANUAL");
        dto.setMasteryLevel(3);
        dto.setGovernanceTemplate(new GovernanceTemplateDTO());
        return dto;
    }

    // ==================== batchSave ====================

    @Test
    @DisplayName("batchSave：null 或空列表 → 直接返回")
    void batchSave_empty() {
        service.batchSave(null);
        service.batchSave(Collections.emptyList());

        verify(abilityEvidenceIngestionService, never()).ingestEmployeeAbility(any(), any());
    }

    @Test
    @DisplayName("batchSave：逐条调用 saveAbility")
    void batchSave_iterates() {
        EmpAbilitySaveDTO dto = new EmpAbilitySaveDTO();
        dto.setEmpId(EMP_ID);
        dto.setAbilityName("Java");
        dto.setEvaluationSource("MANUAL");
        dto.setMasteryLevel(3);
        doAnswer(inv -> {
            EmpAbility e = inv.getArgument(0);
            e.setId(100L);
            return 1;
        }).when(empAbilityMapper).insert(any(EmpAbility.class));

        service.batchSave(List.of(dto));

        verify(abilityEvidenceIngestionService).ingestEmployeeAbility(100L, "EMP_ABILITY");
    }

    // ==================== getProfile ====================

    @Test
    @DisplayName("getProfile：员工不存在 → 抛 EMPLOYEE_NOT_FOUND")
    void getProfile_employeeNotFound() {
        when(empEmployeeMapper.selectById(EMP_ID)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.getProfile(EMP_ID));
    }

    @Test
    @DisplayName("getProfile：无能力记录 → 明细为空、综合评分为 0")
    void getProfile_noAbilities() {
        EmpEmployee emp = new EmpEmployee();
        emp.setId(EMP_ID);
        when(empEmployeeMapper.selectById(EMP_ID)).thenReturn(emp);
        when(empAbilityMapper.selectList(any())).thenReturn(Collections.emptyList());

        EmpAbilityProfileVO vo = service.getProfile(EMP_ID);

        assertTrue(vo.getAbilityDetails().isEmpty());
        assertEquals(0, BigDecimal.ZERO.compareTo(vo.getOverallScore()));
    }

    @Test
    @DisplayName("getProfile：有多条能力 → 计算综合评分 = 平均等级 * 20")
    void getProfile_computesOverallScore() {
        EmpEmployee emp = new EmpEmployee();
        emp.setId(EMP_ID);
        when(empEmployeeMapper.selectById(EMP_ID)).thenReturn(emp);
        EmpAbility a1 = ability(1L, 100L, 2, "Java");
        EmpAbility a2 = ability(2L, 100L, 4, "MySQL");
        when(empAbilityMapper.selectList(any())).thenReturn(List.of(a1, a2));
        when(abilityTagMapper.selectBatchIds(any())).thenReturn(List.of(
                tag(100L, "Java", "TECHNICAL"), tag(100L, "MySQL", "TECHNICAL")));

        EmpAbilityProfileVO vo = service.getProfile(EMP_ID);

        assertEquals(2, vo.getAbilityDetails().size());
        // (2 + 4) / 2 * 20 = 60.00
        assertEquals(0, new BigDecimal("60.00").compareTo(vo.getOverallScore()));
    }

    @Test
    @DisplayName("getProfile：tag_id 为空但名称可精确解析 → 补出分类")
    void getProfile_resolvesByName() {
        EmpEmployee emp = new EmpEmployee();
        emp.setId(EMP_ID);
        when(empEmployeeMapper.selectById(EMP_ID)).thenReturn(emp);
        EmpAbility a = ability(1L, null, 3, "Java");
        when(empAbilityMapper.selectList(any())).thenReturn(List.of(a));
        when(abilityTagMapper.selectList(any())).thenReturn(List.of(tag(2001L, "Java", "TECHNICAL")));

        EmpAbilityProfileVO vo = service.getProfile(EMP_ID);

        assertEquals("TECHNICAL", vo.getAbilityDetails().get(0).getTagCategory());
        assertEquals("Java", vo.getAbilityDetails().get(0).getTagName());
    }

    @Test
    @DisplayName("getProfile：名称规范化回退 → 大小写/分隔符差异也能命中")
    void getProfile_normalizedFallback() {
        EmpEmployee emp = new EmpEmployee();
        emp.setId(EMP_ID);
        when(empEmployeeMapper.selectById(EMP_ID)).thenReturn(emp);
        EmpAbility a = ability(1L, null, 2, "SPRING BOOT");
        when(empAbilityMapper.selectList(any())).thenReturn(List.of(a));
        when(abilityTagMapper.selectList(any())).thenReturn(
                Collections.emptyList(),
                List.of(tag(2002L, "Spring Boot", "TECHNICAL")));

        EmpAbilityProfileVO vo = service.getProfile(EMP_ID);

        assertEquals("TECHNICAL", vo.getAbilityDetails().get(0).getTagCategory());
    }

    @Test
    @DisplayName("getProfile：完全解析不到标签 → 分类留空（不输出 UNKNOWN）")
    void getProfile_unresolvedCategoryIsNull() {
        EmpEmployee emp = new EmpEmployee();
        emp.setId(EMP_ID);
        when(empEmployeeMapper.selectById(EMP_ID)).thenReturn(emp);
        EmpAbility a = ability(1L, null, 1, "某个未入库的能力");
        when(empAbilityMapper.selectList(any())).thenReturn(List.of(a));
        when(abilityTagMapper.selectList(any())).thenReturn(Collections.emptyList(), Collections.emptyList());

        EmpAbilityProfileVO vo = service.getProfile(EMP_ID);

        assertNull(vo.getAbilityDetails().get(0).getTagCategory());
    }

    @Test
    @DisplayName("getProfile：tag_id 指向已删除标签 → 继续按名称回退")
    void getProfile_missingTagIdFallsBackToName() {
        EmpEmployee emp = new EmpEmployee();
        emp.setId(EMP_ID);
        when(empEmployeeMapper.selectById(EMP_ID)).thenReturn(emp);
        EmpAbility a = ability(1L, 9999L, 2, "Redis");
        when(empAbilityMapper.selectList(any())).thenReturn(List.of(a));
        when(abilityTagMapper.selectBatchIds(any())).thenReturn(Collections.emptyList());
        when(abilityTagMapper.selectList(any())).thenReturn(List.of(tag(2003L, "Redis", "TECHNICAL")));

        EmpAbilityProfileVO vo = service.getProfile(EMP_ID);

        assertEquals("TECHNICAL", vo.getAbilityDetails().get(0).getTagCategory());
    }

    // ==================== 既有回归用例（保留原有画像解析断言） ====================

    @Test
    @DisplayName("画像：无系统标签时使用正式能力名")
    void profileUsesFormalAbilityNameWhenAssessmentAbilityHasNoSystemTag() {
        EmpAbility ability = new EmpAbility();
        ability.setTagId(null);
        ability.setAbilityName("Kubernetes 运维");

        assertThat(EmpAbilityServiceImpl.resolveProfileAbilityName(ability, null))
                .isEqualTo("Kubernetes 运维");
    }

    @Test
    @DisplayName("画像：tag_id 为空但能力名能匹配标签 → 补出真实分类")
    void profileResolvesCategoryByNameWhenTagIdMissing() {
        EmpAbility ability = new EmpAbility();
        ability.setEmpId(EMP_ID);
        ability.setTagId(null);
        ability.setAbilityName("Java");
        ability.setMasteryLevel(3);
        stubAbilities(ability);
        when(abilityTagMapper.selectList(any())).thenReturn(List.of(tag(2001L, "Java", "TECHNICAL")));

        EmpAbilityProfileVO profile = service.getProfile(EMP_ID);

        assertThat(profile.getAbilityDetails()).hasSize(1);
        assertThat(profile.getAbilityDetails().get(0).getTagCategory()).isEqualTo("TECHNICAL");
    }

    @Test
    @DisplayName("画像：名称存在大小写/空白差异 → 走规范化回退命中")
    void profileResolvesCategoryByNormalizedName() {
        EmpAbility ability = new EmpAbility();
        ability.setEmpId(EMP_ID);
        ability.setTagId(null);
        ability.setAbilityName("SPRING BOOT");
        ability.setMasteryLevel(2);
        stubAbilities(ability);
        when(abilityTagMapper.selectList(any())).thenReturn(
                List.of(),
                List.of(tag(2002L, "Spring Boot", "TECHNICAL")));

        EmpAbilityProfileVO profile = service.getProfile(EMP_ID);

        assertThat(profile.getAbilityDetails().get(0).getTagCategory()).isEqualTo("TECHNICAL");
    }

    @Test
    @DisplayName("画像：彻底解析不到 → 留空，不输出字面量 UNKNOWN")
    void profileLeavesCategoryNullWhenTagCannotBeResolved() {
        EmpAbility ability = new EmpAbility();
        ability.setEmpId(EMP_ID);
        ability.setTagId(null);
        ability.setAbilityName("某个未入库的能力名");
        ability.setMasteryLevel(1);
        stubAbilities(ability);
        when(abilityTagMapper.selectList(any())).thenReturn(List.of(), List.of());

        EmpAbilityProfileVO profile = service.getProfile(EMP_ID);

        assertThat(profile.getAbilityDetails().get(0).getTagCategory()).isNull();
        assertThat(profile.getAbilityDetails().get(0).getTagCategory()).isNotEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("画像：tag_id 指向已合并/删除的标签 → 仍按名称回退，不判为无分类")
    void profileFallsBackToNameWhenTagIdPointsToMissingTag() {
        EmpAbility ability = new EmpAbility();
        ability.setEmpId(EMP_ID);
        ability.setTagId(9999L);
        ability.setAbilityName("Redis");
        ability.setMasteryLevel(2);
        stubAbilities(ability);
        when(abilityTagMapper.selectBatchIds(any())).thenReturn(List.of());
        when(abilityTagMapper.selectList(any())).thenReturn(List.of(tag(2003L, "Redis", "TECHNICAL")));

        EmpAbilityProfileVO profile = service.getProfile(EMP_ID);

        assertThat(profile.getAbilityDetails().get(0).getTagCategory()).isEqualTo("TECHNICAL");
    }

    @Test
    @DisplayName("resolveProfileAbilityName：能力名缺失时回退标签名，两者皆无返回空串")
    void resolveProfileAbilityName_fallbacks() {
        EmpAbility noName = new EmpAbility();
        assertEquals("Java", EmpAbilityServiceImpl.resolveProfileAbilityName(noName, tag(1L, "Java", "TECHNICAL")));
        assertEquals("", EmpAbilityServiceImpl.resolveProfileAbilityName(noName, null));
        assertEquals("", EmpAbilityServiceImpl.resolveProfileAbilityName(null, null));
    }

    // ==================== listByEmpId ====================

    @Test
    @DisplayName("listByEmpId：返回未删除能力列表")
    void listByEmpId_returnsList() {
        when(empAbilityMapper.selectList(any())).thenReturn(List.of(ability(1L, 5L, 3, "Java")));

        assertEquals(1, service.listByEmpId(EMP_ID).size());
    }

    // ==================== listPendingClaims ====================

    @Test
    @DisplayName("listPendingClaims：旧链路与工作流均无数据 → 返回空列表")
    void listPendingClaims_empty() {
        when(personAbilityClaimMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(claimGroupMapper.selectList(any())).thenReturn(Collections.emptyList());

        assertTrue(service.listPendingClaims(EMP_ID).isEmpty());
    }

    @Test
    @DisplayName("listPendingClaims：合并旧准入链路与聚合组，按组状态推导 harnessDecision 并去重")
    void listPendingClaims_mergesLegacyAndWorkflow() {
        PersonAbilityClaim legacy = claim(1L, null, LocalDateTime.of(2026, 1, 1, 0, 0));
        when(personAbilityClaimMapper.selectList(any()))
                .thenReturn(List.of(legacy))
                .thenReturn(List.of(
                        claim(1L, 10L, LocalDateTime.of(2026, 1, 1, 0, 0)), // 重复 ID，应被 putIfAbsent 跳过
                        claim(2L, 10L, LocalDateTime.of(2026, 1, 2, 0, 0))));

        PersonAbilityClaimGroup review = group(10L, "PENDING_MANUAL_REVIEW");
        PersonAbilityClaimGroup blocked = group(11L, "BLOCKED");
        PersonAbilityClaimGroup pass = group(12L, "READY_FOR_AGGREGATE_HARNESS");
        when(claimGroupMapper.selectList(any())).thenReturn(List.of(review, blocked, pass));

        List<PersonAbilityClaim> result = service.listPendingClaims(EMP_ID);

        assertEquals(2, result.size());
        // 排序：createdTime 倒序 → id=2（1/2）在前；id=1 命中旧链路已存在，putIfAbsent 保留旧对象（无决策）
        assertEquals(2L, result.get(0).getId());
        assertEquals("REVIEW", result.get(0).getHarnessDecision());
        assertEquals(1L, result.get(1).getId());
        assertNull(result.get(1).getHarnessDecision());
    }

    @Test
    @DisplayName("listPendingClaims：BLOCKED → BLOCK，READY_FOR_AGGREGATE_HARNESS → PASS")
    void listPendingClaims_decisionMapping() {
        when(personAbilityClaimMapper.selectList(any()))
                .thenReturn(Collections.emptyList())
                .thenReturn(List.of(claim(2L, 11L, LocalDateTime.now()), claim(3L, 12L, LocalDateTime.now())));
        when(claimGroupMapper.selectList(any())).thenReturn(List.of(
                group(11L, "BLOCKED"), group(12L, "READY_FOR_AGGREGATE_HARNESS")));

        List<PersonAbilityClaim> result = service.listPendingClaims(EMP_ID);

        assertThat(result).extracting(PersonAbilityClaim::getHarnessDecision)
                .containsExactlyInAnyOrder("BLOCK", "PASS");
    }

    private void stubAbilities(EmpAbility... abilities) {
        EmpEmployee emp = new EmpEmployee();
        emp.setId(EMP_ID);
        emp.setRealName("张三");
        when(empEmployeeMapper.selectById(EMP_ID)).thenReturn(emp);
        when(empAbilityMapper.selectList(any())).thenReturn(List.of(abilities));
    }

    private EmpAbility ability(Long id, Long tagId, Integer level, String name) {
        EmpAbility a = new EmpAbility();
        a.setId(id);
        a.setEmpId(EMP_ID);
        a.setTagId(tagId);
        a.setAbilityName(name);
        a.setMasteryLevel(level);
        return a;
    }

    private AbilityTag tag(Long id, String name, String category) {
        AbilityTag tag = new AbilityTag();
        tag.setId(id);
        tag.setCanonicalTagId(id);
        tag.setTagName(name);
        tag.setTagCategory(category);
        return tag;
    }

    private PersonAbilityClaim claim(Long id, Long groupId, LocalDateTime created) {
        PersonAbilityClaim c = new PersonAbilityClaim();
        c.setId(id);
        c.setEmpId(EMP_ID);
        c.setClaimGroupId(groupId);
        c.setCreatedTime(created);
        return c;
    }

    private PersonAbilityClaimGroup group(Long id, String status) {
        PersonAbilityClaimGroup g = new PersonAbilityClaimGroup();
        g.setId(id);
        g.setEmpId(EMP_ID);
        g.setStatus(status);
        return g;
    }
}
