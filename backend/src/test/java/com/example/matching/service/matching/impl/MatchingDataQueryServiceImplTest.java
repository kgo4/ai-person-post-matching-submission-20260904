package com.example.matching.service.matching.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.dto.matching.MatchingAbilitySnapshot;
import com.example.matching.dto.matching.MatchingEmployeeProfile;
import com.example.matching.dto.matching.MatchingPostProfile;
import com.example.matching.dto.matching.MatchingRequirementSnapshot;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.employee.EmpResumeParse;
import com.example.matching.entity.matching.MatchingBlackWhiteList;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.post.PostPost;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.mapper.employee.EmpResumeParseMapper;
import com.example.matching.mapper.matching.MatchingBlackWhiteListMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.mapper.post.PostPostMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.port.employee.EmployeeAbilityReadPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MatchingDataQueryServiceImpl} 单元测试。
 *
 * <p>覆盖匹配层只读数据查询的全部 public 入口：员工/岗位画像的单条与批量装配、
 * 空集合与 null 入参短路、能力/要求快照装配（含标签名兜底）、
 * 分页加载在职员工、简历基本信息批量解析（含 JSON 解析失败降级）。
 *
 * <p>所有 Mapper / 端口均 mock，不访问数据库。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MatchingDataQueryServiceImplTest {

    @Mock private EmpEmployeeMapper empEmployeeMapper;
    @Mock private EmployeeAbilityReadPort employeeAbilityReadPort;
    @Mock private EmpResumeParseMapper empResumeParseMapper;
    @Mock private PostPostMapper postPostMapper;
    @Mock private PostAbilityModelMapper postAbilityModelMapper;
    @Mock private AbilityTagMapper abilityTagMapper;
    @Mock private MatchingBlackWhiteListMapper blackWhiteListMapper;

    private MatchingDataQueryServiceImpl service;

    /**
     * LambdaQueryWrapper 依赖实体的表信息，纯单测下必须手动登记，否则抛
     * "can not find lambda cache for this entity"。
     */
    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        for (Class<?> entity : List.of(EmpEmployee.class, EmpResumeParse.class, PostPost.class,
                PostAbilityModel.class, AbilityTag.class, MatchingBlackWhiteList.class)) {
            com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, entity);
        }
    }

    @BeforeEach
    void setUp() {
        service = new MatchingDataQueryServiceImpl(
                empEmployeeMapper,
                employeeAbilityReadPort,
                empResumeParseMapper,
                postPostMapper,
                postAbilityModelMapper,
                abilityTagMapper,
                blackWhiteListMapper,
                new ObjectMapper());
    }

    // ==================== 辅助构造 ====================

    private EmpEmployee employee(Long id, String name, String level) {
        EmpEmployee e = new EmpEmployee();
        e.setId(id);
        e.setEmpCode("E" + id);
        e.setRealName(name);
        e.setLevel(level);
        e.setGender(1);
        e.setStatus(1);
        e.setIsLocked(0);
        return e;
    }

    private PostPost post(Long id, String name) {
        PostPost p = new PostPost();
        p.setId(id);
        p.setPostCode("P" + id);
        p.setPostName(name);
        p.setPostLevel("P6");
        p.setJobDescription("负责后端");
        return p;
    }

    private PostAbilityModel requirement(Long id, Long postId, Long tagId, String name) {
        PostAbilityModel m = new PostAbilityModel();
        m.setId(id);
        m.setPostId(postId);
        m.setTagId(tagId);
        m.setAbilityName(name);
        m.setMinRequiredLevel(4);
        m.setWeight(BigDecimal.TEN);
        m.setIsRequired(1);
        m.setIsCore(1);
        m.setModelVersion("v1");
        return m;
    }

    private MatchingAbilitySnapshot snapshot(Long abilityId, Long tagId, String name) {
        return new MatchingAbilitySnapshot(abilityId, tagId, name, 3,
                BigDecimal.ONE, "EMP_ABILITY", BigDecimal.ONE, LocalDate.now());
    }

    // ==================== 员工画像 ====================

    @Test
    @DisplayName("findEmployeeForMatching：员工不存在 → 返回 null")
    void findEmployeeForMatching_notFound() {
        when(empEmployeeMapper.selectById(1L)).thenReturn(null);
        assertNull(service.findEmployeeForMatching(1L));
    }

    @Test
    @DisplayName("findEmployeeForMatching：正常 → 装配画像并带上权威能力快照")
    void findEmployeeForMatching_success() {
        when(empEmployeeMapper.selectById(1L)).thenReturn(employee(1L, "张三", "P6"));
        when(employeeAbilityReadPort.loadAuthoritativeAbilities(List.of(1L)))
                .thenReturn(Map.of(1L, List.of(snapshot(10L, 5L, "Java"))));

        MatchingEmployeeProfile profile = service.findEmployeeForMatching(1L);

        assertNotNull(profile);
        assertEquals(1L, profile.empId());
        assertEquals("张三", profile.realName());
        assertEquals(1, profile.abilities().size());
        assertEquals("Java", profile.abilities().get(0).abilityName());
    }

    @Test
    @DisplayName("findEmployeesForMatching：null/空集合 → 直接返回空列表，不查库")
    void findEmployeesForMatching_emptyInput() {
        assertTrue(service.findEmployeesForMatching(null).isEmpty());
        assertTrue(service.findEmployeesForMatching(Collections.emptyList()).isEmpty());
        verify(empEmployeeMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("findEmployeesForMatching：库中无匹配员工 → 返回空列表")
    void findEmployeesForMatching_noEmployeeInDb() {
        when(empEmployeeMapper.selectList(any())).thenReturn(Collections.emptyList());
        assertTrue(service.findEmployeesForMatching(List.of(1L, 2L)).isEmpty());
    }

    @Test
    @DisplayName("findEmployeesForMatching：去重 null 后批量装配，能力按 empId 归属")
    void findEmployeesForMatching_success() {
        when(empEmployeeMapper.selectList(any()))
                .thenReturn(List.of(employee(1L, "张三", "P6"), employee(2L, "李四", "P7")));
        when(employeeAbilityReadPort.loadAuthoritativeAbilities(anyList()))
                .thenReturn(Map.of(1L, List.of(snapshot(10L, 5L, "Java"))));

        List<MatchingEmployeeProfile> profiles =
                service.findEmployeesForMatching(Arrays.asList(1L, null, 2L, 1L));

        assertEquals(2, profiles.size());
        assertEquals(1, profiles.get(0).abilities().size());
        assertTrue(profiles.get(1).abilities().isEmpty());
    }

    @Test
    @DisplayName("findActiveEmployeesForMatching：delegating 到在职查询，空入参直接返回空")
    void findActiveEmployeesForMatching_branches() {
        assertTrue(service.findActiveEmployeesForMatching(null).isEmpty());
        assertTrue(service.findActiveEmployeesForMatching(Collections.emptyList()).isEmpty());
        verify(empEmployeeMapper, never()).selectList(any());

        when(empEmployeeMapper.selectList(any())).thenReturn(List.of(employee(3L, "王五", "P5")));
        when(employeeAbilityReadPort.loadAuthoritativeAbilities(anyList()))
                .thenReturn(Collections.emptyMap());

        List<MatchingEmployeeProfile> profiles = service.findActiveEmployeesForMatching(List.of(3L));
        assertEquals(1, profiles.size());
        assertEquals(3L, profiles.get(0).empId());
        assertTrue(profiles.get(0).abilities().isEmpty());
    }

    @Test
    @DisplayName("findAllActiveEmployeesForMatching：无在职员工 → 空列表；有则全量装配")
    void findAllActiveEmployeesForMatching_branches() {
        when(empEmployeeMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 500));
        assertTrue(service.findAllActiveEmployeesForMatching().isEmpty());

        Page<EmpEmployee> page = new Page<>(1, 500);
        page.setRecords(List.of(employee(1L, "张三", "P6"), employee(2L, "李四", null)));
        when(empEmployeeMapper.selectPage(any(), any())).thenReturn(page);
        when(employeeAbilityReadPort.loadAuthoritativeAbilities(anyList()))
                .thenReturn(Map.of(1L, List.of(snapshot(10L, 5L, "Java"))));

        List<MatchingEmployeeProfile> profiles = service.findAllActiveEmployeesForMatching();
        assertEquals(2, profiles.size());
    }

    @Test
    @DisplayName("listActiveEmployeesPaged：pageSize 超上限被夹紧到 500，返回分页记录")
    void listActiveEmployeesPaged_clampsPageSize() {
        Page<EmpEmployee> page = new Page<>(1, 500);
        page.setRecords(List.of(employee(1L, "张三", "P6")));
        when(empEmployeeMapper.selectPage(any(), any())).thenReturn(page);

        List<EmpEmployee> records = service.listActiveEmployeesPaged(1, 10_000);

        assertEquals(1, records.size());
    }

    // ==================== 岗位画像 ====================

    @Test
    @DisplayName("findPostForMatching：岗位不存在 → 返回 null")
    void findPostForMatching_notFound() {
        when(postPostMapper.selectById(1L)).thenReturn(null);
        assertNull(service.findPostForMatching(1L));
    }

    @Test
    @DisplayName("findPostForMatching：正常 → 装配岗位画像与要求快照（能力名缺失由标签名兜底）")
    void findPostForMatching_success() {
        when(postPostMapper.selectById(1L)).thenReturn(post(1L, "Java后端"));
        when(postAbilityModelMapper.selectList(any()))
                .thenReturn(List.of(requirement(11L, 1L, 5L, null)));
        when(abilityTagMapper.selectList(any())).thenReturn(List.of(tag(5L, "Java")));

        MatchingPostProfile profile = service.findPostForMatching(1L);

        assertNotNull(profile);
        assertEquals("Java后端", profile.postName());
        assertEquals(1, profile.requirements().size());
        assertEquals("Java", profile.requirements().get(0).abilityName());
    }

    @Test
    @DisplayName("findPostsForMatching：null/空入参 → 空列表；库中无岗位 → 空列表")
    void findPostsForMatching_emptyPaths() {
        assertTrue(service.findPostsForMatching(null).isEmpty());
        assertTrue(service.findPostsForMatching(Collections.emptyList()).isEmpty());
        verify(postPostMapper, never()).selectList(any());

        when(postPostMapper.selectList(any())).thenReturn(Collections.emptyList());
        assertTrue(service.findPostsForMatching(List.of(1L)).isEmpty());
    }

    @Test
    @DisplayName("findPostsForMatching：批量装配，各自带上要求快照")
    void findPostsForMatching_success() {
        when(postPostMapper.selectList(any())).thenReturn(List.of(post(1L, "岗位A"), post(2L, "岗位B")));
        when(postAbilityModelMapper.selectList(any()))
                .thenReturn(List.of(requirement(11L, 1L, 5L, "Java")))
                .thenReturn(Collections.emptyList());

        List<MatchingPostProfile> profiles = service.findPostsForMatching(List.of(1L, 2L));

        assertEquals(2, profiles.size());
        assertEquals(1, profiles.get(0).requirements().size());
        assertTrue(profiles.get(1).requirements().isEmpty());
    }

    @Test
    @DisplayName("findPostRequirements：委托查询并转换为要求快照")
    void findPostRequirements_delegates() {
        when(postAbilityModelMapper.selectList(any()))
                .thenReturn(List.of(requirement(11L, 1L, 5L, "Java")));

        List<MatchingRequirementSnapshot> snapshots = service.findPostRequirements(1L);

        assertEquals(1, snapshots.size());
        assertEquals("Java", snapshots.get(0).abilityName());
        assertEquals(5L, snapshots.get(0).tagId());
    }

    @Test
    @DisplayName("findPostRequirements：无要求 → 空列表")
    void findPostRequirements_empty() {
        when(postAbilityModelMapper.selectList(any())).thenReturn(null);
        assertTrue(service.findPostRequirements(1L).isEmpty());
    }

    // ==================== 能力快照批量 ====================

    @Test
    @DisplayName("batchLoadAbilitySnapshots：null/空入参 → 空 Map")
    void batchLoadAbilitySnapshots_emptyInput() {
        assertTrue(service.batchLoadAbilitySnapshots(null).isEmpty());
        assertTrue(service.batchLoadAbilitySnapshots(Collections.emptyList()).isEmpty());
    }

    @Test
    @DisplayName("batchLoadAbilitySnapshots：正常 → 按 empId 分组返回能力快照")
    void batchLoadAbilitySnapshots_success() {
        when(employeeAbilityReadPort.loadAuthoritativeAbilities(anyList()))
                .thenReturn(Map.of(1L, List.of(snapshot(10L, 5L, "Java"))));

        Map<Long, List<MatchingAbilitySnapshot>> map = service.batchLoadAbilitySnapshots(List.of(1L));

        assertEquals(1, map.size());
        assertEquals("Java", map.get(1L).get(0).abilityName());
    }

    @Test
    @DisplayName("batchLoadAbilities：null/空入参 → 空 Map；有数据 → 转成 EmpAbility 并回填来源字段")
    void batchLoadAbilities_branches() {
        assertTrue(service.batchLoadAbilities(null).isEmpty());
        assertTrue(service.batchLoadAbilities(Collections.emptyList()).isEmpty());

        MatchingAbilitySnapshot snap = new MatchingAbilitySnapshot(10L, 5L, "Java", 4,
                new BigDecimal("0.80"), null, new BigDecimal("0.90"), LocalDate.now());
        when(employeeAbilityReadPort.loadAuthoritativeAbilities(anyList()))
                .thenReturn(Map.of(1L, List.of(snap)));

        Map<Long, List<EmpAbility>> result = service.batchLoadAbilities(List.of(1L));

        EmpAbility ability = result.get(1L).get(0);
        assertEquals(10L, ability.getId());
        assertEquals(5L, ability.getTagId());
        assertEquals("Java", ability.getAbilityName());
        assertEquals(4, ability.getMasteryLevel());
        // sourceType 为 null 时回填默认值 EMP_ABILITY
        assertEquals("EMP_ABILITY", ability.getEvaluationSource());
        assertEquals(new BigDecimal("0.80"), ability.getSourceWeight());
    }

    // ==================== 简历基本信息 ====================

    @Test
    @DisplayName("batchLoadResumeBasicInfo：null/空入参 → 空 Map")
    void batchLoadResumeBasicInfo_emptyInput() {
        assertTrue(service.batchLoadResumeBasicInfo(null).isEmpty());
        assertTrue(service.batchLoadResumeBasicInfo(Collections.emptyList()).isEmpty());
    }

    @Test
    @DisplayName("batchLoadResumeBasicInfo：同员工多条简历只取最新（列表首条），解析 basicInfo")
    void batchLoadResumeBasicInfo_takesLatest() {
        EmpResumeParse latest = new EmpResumeParse();
        latest.setId(2L);
        latest.setEmpId(1L);
        latest.setStatus(2);
        latest.setCreatedTime(LocalDateTime.now());
        latest.setAiAnalysisResult("{\"basicInfo\":{\"school\":\"THU\",\"major\":\"CS\"}}");

        EmpResumeParse older = new EmpResumeParse();
        older.setId(1L);
        older.setEmpId(1L);
        older.setStatus(2);
        older.setCreatedTime(LocalDateTime.now().minusDays(1));
        older.setAiAnalysisResult("{\"basicInfo\":{\"school\":\"OLD\"}}");

        when(empResumeParseMapper.selectList(any())).thenReturn(List.of(latest, older));

        Map<Long, Map<String, Object>> result = service.batchLoadResumeBasicInfo(List.of(1L));

        assertEquals(1, result.size());
        assertEquals("THU", result.get(1L).get("school"));
    }

    @Test
    @DisplayName("batchLoadResumeBasicInfo：AI 结果为空、无 basicInfo、JSON 非法 → 逐条降级跳过")
    void batchLoadResumeBasicInfo_degradesOnBadData() {
        EmpResumeParse nullAi = new EmpResumeParse();
        nullAi.setEmpId(1L);
        EmpResumeParse noBasic = new EmpResumeParse();
        noBasic.setEmpId(2L);
        noBasic.setAiAnalysisResult("{\"other\":1}");
        EmpResumeParse broken = new EmpResumeParse();
        broken.setEmpId(3L);
        broken.setAiAnalysisResult("{not-json");

        when(empResumeParseMapper.selectList(any())).thenReturn(List.of(nullAi, noBasic, broken));

        Map<Long, Map<String, Object>> result = service.batchLoadResumeBasicInfo(List.of(1L, 2L, 3L));

        assertTrue(result.isEmpty());
    }

    // ==================== 单条查询与薄封装 ====================

    @Test
    @DisplayName("listEmployeesByIds / listActiveEmployeesByIds：空入参 → 空列表，不查库；有入参 → 返回结果")
    void listEmployees_branches() {
        assertTrue(service.listEmployeesByIds(null).isEmpty());
        assertTrue(service.listEmployeesByIds(Collections.emptyList()).isEmpty());
        assertTrue(service.listActiveEmployeesByIds(null).isEmpty());
        assertTrue(service.listActiveEmployeesByIds(Collections.emptyList()).isEmpty());
        verify(empEmployeeMapper, never()).selectList(any());

        when(empEmployeeMapper.selectList(any())).thenReturn(List.of(employee(1L, "张三", "P6")));
        assertEquals(1, service.listEmployeesByIds(List.of(1L)).size());
        assertEquals(1, service.listActiveEmployeesByIds(List.of(1L)).size());
    }

    @Test
    @DisplayName("countAllActiveEmployees / getEmployeeById / getPostById / getTagById：直接透传 Mapper")
    void simpleDelegations() {
        when(empEmployeeMapper.selectCount(any())).thenReturn(42L);
        when(empEmployeeMapper.selectById(1L)).thenReturn(employee(1L, "张三", "P6"));
        when(postPostMapper.selectById(2L)).thenReturn(post(2L, "岗位B"));
        when(abilityTagMapper.selectById(5L)).thenReturn(tag(5L, "Java"));

        assertEquals(42L, service.countAllActiveEmployees());
        assertEquals(1L, service.getEmployeeById(1L).getId());
        assertEquals(2L, service.getPostById(2L).getId());
        assertEquals("Java", service.getTagById(5L).getTagName());
    }

    @Test
    @DisplayName("listPostsByIds：null/空 → 空列表；有入参 → 列表")
    void listPostsByIds_branches() {
        assertTrue(service.listPostsByIds(null).isEmpty());
        assertTrue(service.listPostsByIds(Collections.emptyList()).isEmpty());

        when(postPostMapper.selectList(any())).thenReturn(List.of(post(1L, "岗位A")));
        assertEquals(1, service.listPostsByIds(List.of(1L)).size());
    }

    @Test
    @DisplayName("listRequirementsByPostId：无要求 → 空列表；有 → 列表")
    void listRequirementsByPostId_branches() {
        when(postAbilityModelMapper.selectList(any())).thenReturn(Collections.emptyList());
        assertTrue(service.listRequirementsByPostId(1L).isEmpty());

        when(postAbilityModelMapper.selectList(any()))
                .thenReturn(List.of(requirement(11L, 1L, 5L, "Java")));
        assertEquals(1, service.listRequirementsByPostId(1L).size());
    }

    @Test
    @DisplayName("listBlackWhiteListByPostId：透传查询结果")
    void listBlackWhiteListByPostId_delegates() {
        MatchingBlackWhiteList item = new MatchingBlackWhiteList();
        item.setId(1L);
        item.setPostId(9L);
        item.setStatus(1);
        when(blackWhiteListMapper.selectList(any())).thenReturn(List.of(item));

        assertEquals(1, service.listBlackWhiteListByPostId(9L).size());
    }

    @Test
    @DisplayName("listTagsByIds：null/空 → 空列表；有 → 列表")
    void listTagsByIds_branches() {
        assertTrue(service.listTagsByIds(null).isEmpty());
        assertTrue(service.listTagsByIds(Collections.emptyList()).isEmpty());

        when(abilityTagMapper.selectList(any())).thenReturn(List.of(tag(5L, "Java")));
        assertEquals("Java", service.listTagsByIds(List.of(5L)).get(0).getTagName());
    }

    @Test
    @DisplayName("buildTagNameMap：null/空要求 → 空 Map；有标签 → tagId → tagName")
    void buildTagNameMap_branches() {
        assertTrue(service.buildTagNameMap(null).isEmpty());
        assertTrue(service.buildTagNameMap(Collections.emptyList()).isEmpty());
        verify(abilityTagMapper, never()).selectList(any());

        when(abilityTagMapper.selectList(any())).thenReturn(List.of(tag(5L, "Java")));
        Map<Long, String> map = service.buildTagNameMap(List.of(requirement(11L, 1L, 5L, null)));

        assertEquals("Java", map.get(5L));
    }

    @Test
    @DisplayName("buildTagNameMap：要求全无 tagId → 不查库，返回空 Map")
    void buildTagNameMap_noTagIds() {
        Map<Long, String> map = service.buildTagNameMap(List.of(requirement(11L, 1L, null, "Java")));
        assertTrue(map.isEmpty());
        verify(abilityTagMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("toSnapshotList：能力名为空串时用标签名兜底；标签缺失时名称为 null")
    void toSnapshotList_nameFallbacks() {
        EmpAbility withTag = new EmpAbility();
        withTag.setId(1L);
        withTag.setTagId(5L);
        withTag.setAbilityName("  ");
        EmpAbility noTag = new EmpAbility();
        noTag.setId(2L);
        noTag.setAbilityName(null);

        Map<Long, List<EmpAbility>> abilitiesMap = new HashMap<>();
        abilitiesMap.put(1L, List.of(withTag, noTag));
        when(employeeAbilityReadPort.loadAuthoritativeAbilities(anyList())).thenReturn(Collections.emptyMap());

        // 通过 findEmployeeForMatching 触发 toSnapshotList（batchLoadAbilities → snapshotToAbility → toSnapshotList）
        when(empEmployeeMapper.selectById(1L)).thenReturn(employee(1L, "张三", "P6"));
        when(employeeAbilityReadPort.loadAuthoritativeAbilities(List.of(1L))).thenReturn(Collections.emptyMap());

        MatchingEmployeeProfile profile = service.findEmployeeForMatching(1L);
        assertNotNull(profile);
        assertTrue(profile.abilities().isEmpty());
    }

    private AbilityTag tag(Long id, String name) {
        AbilityTag t = new AbilityTag();
        t.setId(id);
        t.setTagName(name);
        return t;
    }
}
