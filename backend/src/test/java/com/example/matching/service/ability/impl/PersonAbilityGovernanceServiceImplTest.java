package com.example.matching.service.ability.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.entity.ability.AgentMemory;
import com.example.matching.entity.ability.PersonAbilityGovernanceEvent;
import com.example.matching.entity.ability.PersonAbilityProfile;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.ability.PersonAbilityGovernanceEventMapper;
import com.example.matching.mapper.ability.PersonAbilityProfileMapper;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.service.ability.AgentMemoryService;
import com.example.matching.service.system.AbilityTagService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link PersonAbilityGovernanceServiceImpl} 单元测试。
 *
 * <p>覆盖标签替换 / 等级变更 / 标签删除 / 标签重命名四条治理链路，
 * 以及治理事件生成 Agent 记忆的状态机分支（含空入参、未知类型、证据更新跳过）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PersonAbilityGovernanceServiceImplTest {

    /**
     * MyBatis-Plus 的 LambdaQueryWrapper 需要实体已登记表信息，
     * 否则纯单测下会抛 "can not find lambda cache for this entity"。
     */
    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        TableInfoHelper.initTableInfo(assistant, EmpAbility.class);
        TableInfoHelper.initTableInfo(assistant, PersonAbilityProfile.class);
        TableInfoHelper.initTableInfo(assistant, PersonAbilityGovernanceEvent.class);
        TableInfoHelper.initTableInfo(assistant, AbilityTag.class);
    }

    @Mock private PersonAbilityGovernanceEventMapper eventMapper;
    @Mock private EmpAbilityMapper empAbilityMapper;
    @Mock private PersonAbilityProfileMapper personAbilityProfileMapper;
    @Mock private AbilityTagService abilityTagService;
    @Mock private AgentMemoryService agentMemoryService;

    private PersonAbilityGovernanceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PersonAbilityGovernanceServiceImpl(eventMapper, empAbilityMapper,
                personAbilityProfileMapper, abilityTagService, agentMemoryService, new ObjectMapper());
        // 事件 insert 回填主键，供后续 updateById / memory 关联使用
        doAnswer(inv -> {
            PersonAbilityGovernanceEvent e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(500L);
            }
            return 1;
        }).when(eventMapper).insert(any(PersonAbilityGovernanceEvent.class));
        when(agentMemoryService.createMemory(any(AgentMemory.class))).thenReturn(900L);
    }

    private EmpAbility ability(Long empId, Long tagId, Integer level, String source) {
        EmpAbility a = new EmpAbility();
        a.setId(1L);
        a.setEmpId(empId);
        a.setTagId(tagId);
        a.setMasteryLevel(level);
        a.setEvaluationSource(source);
        a.setSourceWeight(new BigDecimal("0.80"));
        return a;
    }

    private AbilityTag tag(Long id, String name) {
        AbilityTag t = new AbilityTag();
        t.setId(id);
        t.setTagName(name);
        return t;
    }

    // ==================== replaceTag ====================

    @Test
    @DisplayName("replaceTag：能力记录不存在 → 抛 NOT_FOUND 业务异常")
    void replaceTag_notFound() {
        when(empAbilityMapper.selectOne(any())).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.replaceTag(1L, 10L, 20L, "reason", 9L));
    }

    @Test
    @DisplayName("replaceTag：正常替换 → 落库事件、能力与画像同步更新，不生成记忆")
    void replaceTag_success() {
        when(empAbilityMapper.selectOne(any())).thenReturn(ability(1L, 10L, 3, "AI_TEST"));
        when(abilityTagService.getById(10L)).thenReturn(tag(10L, "旧标签"));
        when(abilityTagService.getById(20L)).thenReturn(tag(20L, "新标签"));
        PersonAbilityProfile profile = new PersonAbilityProfile();
        profile.setId(7L);
        when(personAbilityProfileMapper.selectOne(any())).thenReturn(profile);

        PersonAbilityGovernanceEvent event = service.replaceTag(1L, 10L, 20L, "归一", 9L);

        assertEquals("TAG_REPLACE", event.getModifyType());
        assertEquals("旧标签", event.getOldTagName());
        assertEquals("新标签", event.getNewTagName());
        assertEquals(0, event.getGenerateMemory());
        assertEquals(20L, profile.getTagId());
        assertEquals("新标签", profile.getAbilityName());
        verify(empAbilityMapper).updateById(any(EmpAbility.class));
        verify(personAbilityProfileMapper).updateById(profile);
        verify(agentMemoryService, never()).createMemory(any(AgentMemory.class));
    }

    @Test
    @DisplayName("replaceTag：标签查不到 → 名称降级为「未知」，画像不存在时不更新")
    void replaceTag_unknownTagNames_noProfile() {
        when(empAbilityMapper.selectOne(any())).thenReturn(ability(1L, 10L, 3, "AI_TEST"));
        when(abilityTagService.getById(any())).thenReturn(null);
        when(personAbilityProfileMapper.selectOne(any())).thenReturn(null);

        PersonAbilityGovernanceEvent event = service.replaceTag(1L, 10L, 20L, "原因", 9L);

        assertEquals("未知", event.getOldTagName());
        assertEquals("未知", event.getNewTagName());
        verify(personAbilityProfileMapper, never()).updateById(any(PersonAbilityProfile.class));
    }

    @Test
    @DisplayName("replaceTag：简历来源 + 泛化规则 → 生成归一记忆并回写 memoryId")
    void replaceTag_generalizeResumeRule() {
        when(empAbilityMapper.selectOne(any())).thenReturn(ability(1L, 10L, 3, "RESUME_PARSE"));
        when(abilityTagService.getById(10L)).thenReturn(tag(10L, "旧名"));
        when(abilityTagService.getById(20L)).thenReturn(tag(20L, "新名"));
        when(personAbilityProfileMapper.selectOne(any())).thenReturn(null);

        PersonAbilityGovernanceEvent event = service.replaceTag(1L, 10L, 20L, "原因", 9L, true);

        assertEquals(1, event.getGenerateMemory());
        assertEquals(900L, event.getMemoryId());
        verify(agentMemoryService).createMemory(any(AgentMemory.class));
        verify(eventMapper).updateById(event);
    }

    @Test
    @DisplayName("replaceTag：泛化规则但来源非简历 → 不生成记忆")
    void replaceTag_generalizeButNotResume() {
        when(empAbilityMapper.selectOne(any())).thenReturn(ability(1L, 10L, 3, "AI_TEST"));
        when(abilityTagService.getById(any())).thenReturn(tag(10L, "x"));
        when(personAbilityProfileMapper.selectOne(any())).thenReturn(null);

        PersonAbilityGovernanceEvent event = service.replaceTag(1L, 10L, 20L, "原因", 9L, true);

        assertNull(event.getMemoryId());
        verify(agentMemoryService, never()).createMemory(any(AgentMemory.class));
    }

    @Test
    @DisplayName("replaceTag：来源权重为空 → 置信度字段留空")
    void replaceTag_nullSourceWeight() {
        EmpAbility a = ability(1L, 10L, 3, "AI_TEST");
        a.setSourceWeight(null);
        when(empAbilityMapper.selectOne(any())).thenReturn(a);
        when(abilityTagService.getById(any())).thenReturn(null);
        when(personAbilityProfileMapper.selectOne(any())).thenReturn(null);

        PersonAbilityGovernanceEvent event = service.replaceTag(1L, 10L, 20L, "原因", 9L);

        assertNull(event.getOldConfidence());
        assertNull(event.getNewConfidence());
    }

    // ==================== changeLevel ====================

    @Test
    @DisplayName("changeLevel：能力记录不存在 → 抛 NOT_FOUND")
    void changeLevel_notFound() {
        when(empAbilityMapper.selectOne(any())).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.changeLevel(1L, 2L, 4, "reason", 9L));
    }

    @Test
    @DisplayName("changeLevel：新等级为空 → 抛 PARAM_ERROR")
    void changeLevel_nullNewLevel() {
        when(empAbilityMapper.selectOne(any())).thenReturn(ability(1L, 2L, 3, "AI_TEST"));

        assertThrows(BusinessException.class, () -> service.changeLevel(1L, 2L, null, "reason", 9L));
    }

    @Test
    @DisplayName("changeLevel：升级 → LEVEL_UP 且生成等级下限记忆")
    void changeLevel_levelUp() {
        when(empAbilityMapper.selectOne(any())).thenReturn(ability(1L, 2L, 2, "AI_TEST"));
        when(abilityTagService.getById(2L)).thenReturn(tag(2L, "Java"));
        when(personAbilityProfileMapper.selectOne(any())).thenReturn(null);

        PersonAbilityGovernanceEvent event = service.changeLevel(1L, 2L, 4, "上升", 9L);

        assertEquals("LEVEL_UP", event.getModifyType());
        assertEquals(900L, event.getMemoryId());
        verify(agentMemoryService).createMemory(any(AgentMemory.class));
        verify(empAbilityMapper).updateById(any(EmpAbility.class));
    }

    @Test
    @DisplayName("changeLevel：降级 → LEVEL_DOWN 且画像 finalLevel 同步更新")
    void changeLevel_levelDown_updatesProfile() {
        when(empAbilityMapper.selectOne(any())).thenReturn(ability(1L, 2L, 4, "AI_TEST"));
        when(abilityTagService.getById(2L)).thenReturn(tag(2L, "Java"));
        PersonAbilityProfile profile = new PersonAbilityProfile();
        profile.setId(7L);
        when(personAbilityProfileMapper.selectOne(any())).thenReturn(profile);

        PersonAbilityGovernanceEvent event = service.changeLevel(1L, 2L, 2, "下调", 9L);

        assertEquals("LEVEL_DOWN", event.getModifyType());
        assertEquals(2, profile.getFinalLevel());
        verify(personAbilityProfileMapper).updateById(profile);
    }

    @Test
    @DisplayName("changeLevel：原等级为空 → LEVEL_SET")
    void changeLevel_levelSet() {
        when(empAbilityMapper.selectOne(any())).thenReturn(ability(1L, 2L, null, "AI_TEST"));
        when(abilityTagService.getById(2L)).thenReturn(null);
        when(personAbilityProfileMapper.selectOne(any())).thenReturn(null);

        PersonAbilityGovernanceEvent event = service.changeLevel(1L, 2L, 3, "初始化", 9L);

        assertEquals("LEVEL_SET", event.getModifyType());
    }

    @Test
    @DisplayName("changeLevel：等级未变化 → LEVEL_UNCHANGED 且不生成记忆")
    void changeLevel_unchanged() {
        when(empAbilityMapper.selectOne(any())).thenReturn(ability(1L, 2L, 3, "AI_TEST"));
        when(abilityTagService.getById(2L)).thenReturn(tag(2L, "Java"));
        when(personAbilityProfileMapper.selectOne(any())).thenReturn(null);

        PersonAbilityGovernanceEvent event = service.changeLevel(1L, 2L, 3, "确认", 9L);

        assertEquals("LEVEL_UNCHANGED", event.getModifyType());
        assertNull(event.getMemoryId());
        verify(agentMemoryService, never()).createMemory(any(AgentMemory.class));
    }

    @Test
    @DisplayName("changeLevel：显式传入等级上限 → 优先作为 maxLevelCap 生成记忆")
    void changeLevel_explicitMaxLevelCap() {
        when(empAbilityMapper.selectOne(any())).thenReturn(ability(1L, 2L, 2, "AI_TEST"));
        when(abilityTagService.getById(2L)).thenReturn(tag(2L, "Java"));
        when(personAbilityProfileMapper.selectOne(any())).thenReturn(null);

        PersonAbilityGovernanceEvent event = service.changeLevel(1L, 2L, 4, "上调", 9L, true, 5);

        assertEquals(900L, event.getMemoryId());
        ArgumentCaptor<AgentMemory> cap = ArgumentCaptor.forClass(AgentMemory.class);
        verify(agentMemoryService).createMemory(cap.capture());
        assertThat(cap.getValue().getContent()).contains("等级上限 5");
    }

    // ==================== removeTag ====================

    @Test
    @DisplayName("removeTag：能力记录不存在 → 抛 NOT_FOUND")
    void removeTag_notFound() {
        when(empAbilityMapper.selectOne(any())).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.removeTag(1L, 2L, "reason", 9L));
    }

    @Test
    @DisplayName("removeTag：正常删除 → 删除能力记录、软删画像并生成拒绝记忆")
    void removeTag_success() {
        EmpAbility a = ability(1L, 2L, 3, "AI_TEST");
        when(empAbilityMapper.selectOne(any())).thenReturn(a);
        when(abilityTagService.getById(2L)).thenReturn(tag(2L, "Java"));
        PersonAbilityProfile profile = new PersonAbilityProfile();
        profile.setId(7L);
        when(personAbilityProfileMapper.selectOne(any())).thenReturn(profile);

        PersonAbilityGovernanceEvent event = service.removeTag(1L, 2L, "误判", 9L);

        assertEquals("REMOVE_TAG", event.getModifyType());
        assertEquals(1, profile.getIsDeleted());
        assertEquals(900L, event.getMemoryId());
        verify(empAbilityMapper).deleteById(1L);
        verify(personAbilityProfileMapper).updateById(profile);
    }

    @Test
    @DisplayName("removeTag：标签与画像都不存在 → 名称降级为「未知」，仍能完成删除")
    void removeTag_missingTagAndProfile() {
        when(empAbilityMapper.selectOne(any())).thenReturn(ability(1L, 2L, 3, "AI_TEST"));
        when(abilityTagService.getById(2L)).thenReturn(null);
        when(personAbilityProfileMapper.selectOne(any())).thenReturn(null);

        PersonAbilityGovernanceEvent event = service.removeTag(1L, 2L, "原因", 9L, true);

        assertEquals("未知", event.getOldTagName());
        verify(personAbilityProfileMapper, never()).updateById(any(PersonAbilityProfile.class));
    }

    // ==================== renameTag ====================

    @Test
    @DisplayName("renameTag：标签不存在 → 抛 NOT_FOUND")
    void renameTag_notFound() {
        when(abilityTagService.getById(5L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.renameTag(5L, "新名", "reason", 9L));
    }

    @Test
    @DisplayName("renameTag：正常重命名 → 更新标签、别名、画像与各能力记录事件")
    void renameTag_success() {
        AbilityTag tag = tag(5L, "旧名");
        when(abilityTagService.getById(5L)).thenReturn(tag);
        EmpAbility a1 = ability(1L, 5L, 3, "AI_TEST");
        EmpAbility a2 = ability(2L, 5L, 2, "AI_TEST");
        when(empAbilityMapper.selectList(any())).thenReturn(List.of(a1, a2));
        PersonAbilityProfile p1 = new PersonAbilityProfile();
        p1.setId(11L);
        when(personAbilityProfileMapper.selectList(any())).thenReturn(List.of(p1));

        List<PersonAbilityGovernanceEvent> events = service.renameTag(5L, "新名", "规范命名", 9L);

        assertEquals(2, events.size());
        assertEquals("TAG_RENAME", events.get(0).getModifyType());
        assertEquals("新名", tag.getTagName());
        assertEquals("新名", p1.getAbilityName());
        assertThat(events).allMatch(e -> Long.valueOf(900L).equals(e.getMemoryId()));
        verify(abilityTagService).updateById(tag);
        verify(abilityTagService).addAlias(5L, "旧名", "GOVERNANCE");
        verify(personAbilityProfileMapper).updateById(p1);
    }

    @Test
    @DisplayName("renameTag：无能力记录与画像 → 返回空列表，但仍生成归一记忆")
    void renameTag_emptyImpact() {
        when(abilityTagService.getById(5L)).thenReturn(tag(5L, "旧名"));
        when(empAbilityMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(personAbilityProfileMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<PersonAbilityGovernanceEvent> events = service.renameTag(5L, "新名", "原因", 9L);

        assertTrue(events.isEmpty());
        verify(agentMemoryService).createMemory(any(AgentMemory.class));
    }

    // ==================== 查询 ====================

    @Test
    @DisplayName("getGovernanceHistory：按员工查询治理历史")
    void getGovernanceHistory_returnsList() {
        when(eventMapper.selectList(any())).thenReturn(List.of(new PersonAbilityGovernanceEvent()));

        assertEquals(1, service.getGovernanceHistory(1L).size());
    }

    @Test
    @DisplayName("getGovernanceByTag：按标签查询治理事件")
    void getGovernanceByTag_returnsList() {
        when(eventMapper.selectList(any())).thenReturn(Collections.emptyList());

        assertTrue(service.getGovernanceByTag(1L).isEmpty());
    }

    // ==================== createEvent ====================

    @Test
    @DisplayName("createEvent：缺省时间与记忆标记 → 自动补齐并落库")
    void createEvent_defaults() {
        PersonAbilityGovernanceEvent event = new PersonAbilityGovernanceEvent();
        event.setEmpId(1L);
        event.setModifyType("TAG_REPLACE");

        PersonAbilityGovernanceEvent saved = service.createEvent(event);

        assertNotNull(saved.getCreatedTime());
        assertEquals(0, saved.getGenerateMemory());
        assertEquals(500L, saved.getId());
        verify(eventMapper).insert(event);
    }

    @Test
    @DisplayName("createEvent：已有时间与标记 → 保持原值")
    void createEvent_keepsProvidedValues() {
        PersonAbilityGovernanceEvent event = new PersonAbilityGovernanceEvent();
        event.setCreatedTime(java.time.LocalDateTime.of(2026, 1, 1, 0, 0));
        event.setGenerateMemory(1);

        PersonAbilityGovernanceEvent saved = service.createEvent(event);

        assertEquals(java.time.LocalDateTime.of(2026, 1, 1, 0, 0), saved.getCreatedTime());
        assertEquals(1, saved.getGenerateMemory());
    }

    // ==================== generateAgentMemory ====================

    @Test
    @DisplayName("generateAgentMemory：事件为空或类型为空 → 直接返回，不生成记忆")
    void generateAgentMemory_nullGuard() {
        service.generateAgentMemory(null);
        service.generateAgentMemory(new PersonAbilityGovernanceEvent());

        verify(agentMemoryService, never()).createMemory(any(AgentMemory.class));
    }

    @Test
    @DisplayName("generateAgentMemory：TAG_REPLACE → 生成归一记忆并回写事件")
    void generateAgentMemory_tagReplace() {
        PersonAbilityGovernanceEvent event = new PersonAbilityGovernanceEvent();
        event.setId(1L);
        event.setModifyType("TAG_REPLACE");
        event.setOldTagName("旧名");
        event.setNewTagName("新名");
        event.setOldTagId(10L);
        event.setNewTagId(20L);

        service.generateAgentMemory(event);

        assertEquals(900L, event.getMemoryId());
        verify(agentMemoryService).createMemory(any(AgentMemory.class));
        verify(eventMapper).updateById(event);
    }

    @Test
    @DisplayName("generateAgentMemory：ABILITY_RENAME → 生成归一记忆")
    void generateAgentMemory_abilityRename() {
        PersonAbilityGovernanceEvent event = new PersonAbilityGovernanceEvent();
        event.setId(1L);
        event.setModifyType("ABILITY_RENAME");
        event.setOldTagName("A");
        event.setNewTagName("B");

        service.generateAgentMemory(event);

        assertEquals(900L, event.getMemoryId());
    }

    @Test
    @DisplayName("generateAgentMemory：LEVEL_UP → 生成等级下限记忆")
    void generateAgentMemory_levelUp() {
        when(abilityTagService.getById(2L)).thenReturn(tag(2L, "Java"));
        PersonAbilityGovernanceEvent event = new PersonAbilityGovernanceEvent();
        event.setId(1L);
        event.setModifyType("LEVEL_UP");
        event.setOldTagId(2L);
        event.setNewLevel(4);

        service.generateAgentMemory(event);

        ArgumentCaptor<AgentMemory> cap = ArgumentCaptor.forClass(AgentMemory.class);
        verify(agentMemoryService).createMemory(cap.capture());
        assertEquals("LEVEL_RULE", cap.getValue().getMemoryType());
        assertThat(cap.getValue().getContent()).contains("等级下限 4");
    }

    @Test
    @DisplayName("generateAgentMemory：LEVEL_DOWN 且标签缺失 → 名称降级「未知」仍生成上限记忆")
    void generateAgentMemory_levelDown_unknownTag() {
        when(abilityTagService.getById(2L)).thenReturn(null);
        PersonAbilityGovernanceEvent event = new PersonAbilityGovernanceEvent();
        event.setId(1L);
        event.setModifyType("LEVEL_DOWN");
        event.setOldTagId(2L);
        event.setNewLevel(2);

        service.generateAgentMemory(event);

        ArgumentCaptor<AgentMemory> cap = ArgumentCaptor.forClass(AgentMemory.class);
        verify(agentMemoryService).createMemory(cap.capture());
        assertThat(cap.getValue().getContent()).contains("等级上限 2");
    }

    @Test
    @DisplayName("generateAgentMemory：REMOVE_TAG / DELETE_ABILITY → 生成拒绝记忆")
    void generateAgentMemory_removeTag() {
        PersonAbilityGovernanceEvent event = new PersonAbilityGovernanceEvent();
        event.setId(1L);
        event.setModifyType("REMOVE_TAG");
        event.setOldTagName("垃圾标签");
        event.setOldTagId(3L);

        service.generateAgentMemory(event);

        ArgumentCaptor<AgentMemory> cap = ArgumentCaptor.forClass(AgentMemory.class);
        verify(agentMemoryService).createMemory(cap.capture());
        assertEquals("TAG_REJECT", cap.getValue().getMemoryType());
    }

    @Test
    @DisplayName("generateAgentMemory：MANUAL_ADD → 生成正例标签记忆")
    void generateAgentMemory_manualAdd() {
        PersonAbilityGovernanceEvent event = new PersonAbilityGovernanceEvent();
        event.setId(1L);
        event.setModifyType("MANUAL_ADD");
        event.setNewTagName("新能力");

        service.generateAgentMemory(event);

        ArgumentCaptor<AgentMemory> cap = ArgumentCaptor.forClass(AgentMemory.class);
        verify(agentMemoryService).createMemory(cap.capture());
        assertEquals("TERM_INTERPRETATION", cap.getValue().getMemoryType());
    }

    @Test
    @DisplayName("generateAgentMemory：EVIDENCE_UPDATE → 不生成记忆")
    void generateAgentMemory_evidenceUpdate() {
        PersonAbilityGovernanceEvent event = new PersonAbilityGovernanceEvent();
        event.setId(1L);
        event.setModifyType("EVIDENCE_UPDATE");

        service.generateAgentMemory(event);

        verify(agentMemoryService, never()).createMemory(any(AgentMemory.class));
    }

    @Test
    @DisplayName("generateAgentMemory：未知修改类型 → 不生成记忆")
    void generateAgentMemory_unknownType() {
        PersonAbilityGovernanceEvent event = new PersonAbilityGovernanceEvent();
        event.setId(1L);
        event.setModifyType("SOMETHING_ELSE");

        service.generateAgentMemory(event);

        verify(agentMemoryService, never()).createMemory(any(AgentMemory.class));
    }

    // ==================== 既有回归用例 ====================

    @Test
    @DisplayName("changeLevel：区分等级未变化与原先等级未知两种情况")
    void changeLevel_distinguishesUnchangedAndPreviouslyUnknownLevels() {
        AbilityTag tag2 = tag(2L, "Java");
        when(abilityTagService.getById(2L)).thenReturn(tag2);
        EmpAbility a = ability(1L, 2L, 3, "AI_TEST");
        when(empAbilityMapper.selectOne(any())).thenReturn(a);

        PersonAbilityGovernanceEvent unchanged = service.changeLevel(1L, 2L, 3, "confirm", 9L);
        assertEquals("LEVEL_UNCHANGED", unchanged.getModifyType());

        a.setMasteryLevel(null);
        PersonAbilityGovernanceEvent initialized = service.changeLevel(1L, 2L, 3, "initialize", 9L);
        assertEquals("LEVEL_SET", initialized.getModifyType());
    }

    @Test
    @DisplayName("PersonAbilityClaimNormalizer：同名 claims 归入同组，异名归入不同组")
    void claimNormalizerPreservesSameNameClaimsInSameGroupAndDifferentNamesInSeparateGroups() throws Exception {
        com.example.matching.common.util.PersonAbilityClaimNormalizer normalizer =
                new com.example.matching.common.util.PersonAbilityClaimNormalizer(new ObjectMapper());

        String json = """
                {
                  "claims": [
                    {"abilityName": "Redis", "normalizedAbilityName": "Redis", "masteryLevel": 3,
                     "evidenceText": "Used Redis for caching", "sourceRefs": ["source:RESUME_PARSE:1"]},
                    {"abilityName": "Redis", "normalizedAbilityName": "Redis", "masteryLevel": 4,
                     "evidenceText": "Redis cluster management", "sourceRefs": ["source:RESUME_PARSE:1"]},
                    {"abilityName": "Redis Cluster", "normalizedAbilityName": "Redis Cluster", "masteryLevel": 4,
                     "evidenceText": "Managed Redis Cluster deployment", "sourceRefs": ["source:RESUME_PARSE:1"]}
                  ]
                }
                """;

        com.example.matching.agent.dto.person.PersonAbilityExtractionResult result = normalizer.normalize(json);

        assertThat(result.getClaims()).hasSize(3);
        long redisGroupCount = result.getClaims().stream()
                .filter(c -> "Redis".equals(c.getNormalizedAbilityName())).count();
        long redisClusterGroupCount = result.getClaims().stream()
                .filter(c -> "Redis Cluster".equals(c.getNormalizedAbilityName())).count();
        assertThat(redisGroupCount).isEqualTo(2);
        assertThat(redisClusterGroupCount).isEqualTo(1);
        assertThat(result.getClaims().get(0).getEvidenceText()).isEqualTo("Used Redis for caching");
        assertThat(result.getClaims().get(1).getEvidenceText()).isEqualTo("Redis cluster management");
        assertThat(result.getClaims().get(2).getEvidenceText()).isEqualTo("Managed Redis Cluster deployment");
    }
}
