package com.example.matching.service.impl;

import com.example.matching.dto.employee.api.PmsRosterItemResponse;
import com.example.matching.dto.employee.api.PmsRosterResponse;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.system.PmsAnalysisTask;
import com.example.matching.entity.system.PmsUserMapping;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.mapper.system.PmsAnalysisTaskMapper;
import com.example.matching.mapper.system.PmsUserMappingMapper;
import com.example.matching.repository.PmsDataRepository;
import com.example.matching.service.employee.PmsAbilityAnalysisAgent;
import com.example.matching.service.employee.impl.PmsAbilityAnalysisServiceImpl;
import com.example.matching.service.employee.impl.PmsAbilityImportEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PMS 花名册与同步的回归测试。
 *
 * <p>锁住两件事：
 * <ol>
 *   <li><b>同步不再写人员库</b>：旧实现在 syncPmsUsers 里 findOrCreateEmployee，
 *       真的往 emp_employee 插行，把 PMS 上的人混进本系统人员列表。
 *       本测试断言 employeeMapper 的写方法<b>一次都没被调用</b>。</li>
 *   <li><b>花名册把「已同步未绑定」当一等公民</b>：未绑定的人也在列表里、排在最前面，
 *       并且 PMS 库不可用时必须带诊断 message，而不是静默返回空数组。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PmsRosterSyncTest {

    /**
     * MyBatis-Plus 的 lambda 列名缓存（{@code LambdaUtils.COLUMN_CACHE_MAP}）平时由 Mapper 扫描阶段
     * 的 {@code TableInfoHelper} 填充；纯 Mockito 单测不启动 MyBatis，缓存为空。
     *
     * <p><b>为什么只有部分用例会炸</b>：{@code LambdaQueryWrapper} 的列名是**惰性**解析的
     * （包装成 Supplier，只有 {@code getSqlSegment()} 被调用时才展开），而这些 wrapper 在这里只作为
     * mock 参数传递、从不序列化 → 不报错；{@code LambdaUpdateWrapper.set(字段, 值)} 则是**立即**解析
     * （见 {@code AbstractLambdaWrapper.columnToString}）→ 抛 "can not find lambda cache"。
     * 所以别以为「其余用例都过了就说明不需要初始化」。这里覆盖本类会走到的三个实体。
     */
    @org.junit.jupiter.api.BeforeAll
    static void initMybatisPlusLambdaCache() {
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, PmsUserMapping.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, PmsAnalysisTask.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, EmpEmployee.class);
    }

    @InjectMocks
    private PmsAbilityAnalysisServiceImpl service;

    @Mock private PmsDataRepository pmsDataRepository;
    @Mock private PmsUserMappingMapper userMappingMapper;
    @Mock private PmsAnalysisTaskMapper analysisTaskMapper;
    @Mock private EmpEmployeeMapper employeeMapper;
    @Mock private EmpAbilityMapper empAbilityMapper;
    @Mock private PmsAbilityAnalysisAgent pmsAbilityAnalysisAgent;
    @Mock private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        PmsAbilityImportEngine engine = new PmsAbilityImportEngine(
                userMappingMapper, analysisTaskMapper, empAbilityMapper, objectMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "importEngine", engine);
    }

    private static Map<String, Object> pmsUser(long id, String employeeId, String nickname) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", id);
        user.put("username", "pms_" + id);
        user.put("nickname", nickname);
        user.put("employee_id", employeeId);
        user.put("email", "pms" + id + "@example.com");
        user.put("phone", "1380000000" + id);
        user.put("role", "DEV");
        return user;
    }

    @Test
    void syncNeverWritesEmployeeTableAndLeavesEmpIdNull() {
        when(pmsDataRepository.isAvailable()).thenReturn(true);
        when(pmsDataRepository.findAllUsers()).thenReturn(List.of(
                pmsUser(5L, "EMP001", "张三"),
                pmsUser(6L, "EMP002", "李四")));

        List<PmsUserMapping> inserted = new ArrayList<>();
        doAnswer(invocation -> {
            PmsUserMapping mapping = invocation.getArgument(0);
            mapping.setId((long) (inserted.size() + 1));
            inserted.add(mapping);
            return 1;
        }).when(userMappingMapper).insert(any(PmsUserMapping.class));

        when(userMappingMapper.selectList(any())).thenReturn(List.of());
        when(userMappingMapper.selectByPmsUserId(anyLong())).thenReturn(null);
        // 工号在本系统里没有唯一命中 → 不自动绑定，留给 HR 手工绑定。
        when(employeeMapper.selectList(any())).thenReturn(List.of());

        int[] result = service.syncPmsUsers();

        assertThat(result[0]).isEqualTo(2);   // 新落映射
        assertThat(result[1]).isEqualTo(2);   // PMS 总数
        assertThat(result[2]).isZero();       // 此前已同步
        assertThat(result[3]).isZero();       // 自动绑定

        assertThat(inserted).hasSize(2);
        assertThat(inserted).allSatisfy(mapping -> assertThat(mapping.getEmpId()).isNull());
        assertThat(inserted).extracting(PmsUserMapping::getPmsNickname)
                .containsExactly("张三", "李四");

        // 核心断言：同步绝不触碰人员库。
        verify(employeeMapper, never()).insert(any(EmpEmployee.class));
        verify(employeeMapper, never()).updateById(any(EmpEmployee.class));
    }

    @Test
    void syncAutoBindsOnlyWhenEmpCodeUniquelyMatches() {
        when(pmsDataRepository.isAvailable()).thenReturn(true);
        when(pmsDataRepository.findAllUsers()).thenReturn(List.of(pmsUser(5L, "EMP001", "张三")));

        PmsUserMapping stored = new PmsUserMapping();
        stored.setId(11L);
        stored.setPmsUserId(5L);
        stored.setPmsNickname("张三");
        when(userMappingMapper.selectByPmsUserId(5L)).thenReturn(null);
        doAnswer(invocation -> {
            PmsUserMapping mapping = invocation.getArgument(0);
            mapping.setId(11L);
            return 1;
        }).when(userMappingMapper).insert(any(PmsUserMapping.class));
        when(userMappingMapper.selectList(any())).thenReturn(List.of());

        EmpEmployee matched = new EmpEmployee();
        matched.setId(100L);
        matched.setEmpCode("EMP001");
        matched.setRealName("张三");
        when(employeeMapper.selectList(any())).thenReturn(List.of(matched));

        int[] result = service.syncPmsUsers();

        assertThat(result[0]).isEqualTo(1);
        assertThat(result[3]).isEqualTo(1);
        verify(userMappingMapper).update(any(), any());
        verify(employeeMapper, never()).insert(any(EmpEmployee.class));
    }

    @Test
    void syncSkipsWhenPmsDataSourceUnavailable() {
        when(pmsDataRepository.isAvailable()).thenReturn(false);

        int[] result = service.syncPmsUsers();

        assertThat(result).containsExactly(0, 0, 0, 0);
        verify(userMappingMapper, never()).insert(any(PmsUserMapping.class));
    }

    @Test
    void rosterListsUnboundFirstAndReportsCounts() {
        when(pmsDataRepository.isAvailable()).thenReturn(true);
        when(pmsDataRepository.findAllUsers()).thenReturn(List.of(
                pmsUser(5L, "EMP001", "张三"),
                pmsUser(6L, "EMP002", "李四")));

        PmsUserMapping boundMapping = new PmsUserMapping();
        boundMapping.setId(1L);
        boundMapping.setPmsUserId(6L);
        boundMapping.setPmsNickname("李四");
        boundMapping.setEmpId(100L);
        when(userMappingMapper.selectList(any())).thenReturn(List.of(boundMapping));

        EmpEmployee employee = new EmpEmployee();
        employee.setId(100L);
        employee.setRealName("李四");
        employee.setEmpCode("EMP002");
        employee.setUserId(7L);
        when(employeeMapper.selectBatchIds(any())).thenReturn(List.of(employee));

        PmsAnalysisTask task = new PmsAnalysisTask();
        task.setId(20L);
        task.setPmsUserId(6L);
        task.setEmpId(100L);
        task.setAnalysisStatus(2);
        task.setCreatedTime(LocalDateTime.of(2026, 10, 1, 9, 0));
        when(analysisTaskMapper.selectList(any())).thenReturn(List.of(task));

        PmsRosterResponse roster = service.listRoster();

        assertThat(roster.pmsConnected()).isTrue();
        assertThat(roster.totalPmsUsers()).isEqualTo(2);
        assertThat(roster.boundCount()).isEqualTo(1);
        assertThat(roster.unboundCount()).isEqualTo(1);
        assertThat(roster.analyzedCount()).isEqualTo(1);
        assertThat(roster.boundEmpIds()).containsExactly(100L);
        assertThat(roster.message()).isNull();

        List<PmsRosterItemResponse> items = roster.items();
        assertThat(items).hasSize(2);
        // 未绑定的人排在最前面 —— HR 打开这一页要找的就是他们。
        assertThat(items.get(0).bound()).isFalse();
        assertThat(items.get(0).pmsUserId()).isEqualTo(5L);
        assertThat(items.get(0).empName()).isNull();
        assertThat(items.get(0).analysisCount()).isZero();

        assertThat(items.get(1).bound()).isTrue();
        assertThat(items.get(1).empId()).isEqualTo(100L);
        assertThat(items.get(1).empName()).isEqualTo("李四");
        assertThat(items.get(1).empHasAccount()).isTrue();
        assertThat(items.get(1).analysisCount()).isEqualTo(1);
        assertThat(items.get(1).lastAnalysisStatus()).isEqualTo(2);
    }

    @Test
    void rosterReturnsDiagnosticsInsteadOfSilentEmptyWhenPmsUnavailable() {
        when(pmsDataRepository.isAvailable()).thenReturn(false);

        PmsRosterResponse roster = service.listRoster();

        assertThat(roster.items()).isEmpty();
        assertThat(roster.message()).contains("未配置或不可用");
    }

    @Test
    void rosterReturnsDiagnosticsWhenPmsHasNoUsers() {
        when(pmsDataRepository.isAvailable()).thenReturn(true);
        when(pmsDataRepository.findAllUsers()).thenReturn(List.of());

        PmsRosterResponse roster = service.listRoster();

        assertThat(roster.items()).isEmpty();
        assertThat(roster.message()).contains("没有可同步的人员");
    }

    @Test
    void unbindClearsEmpIdThroughUpdateWrapperNotNullThroughEntity() {
        PmsUserMapping mapping = new PmsUserMapping();
        mapping.setId(1L);
        mapping.setPmsUserId(6L);
        mapping.setEmpId(100L);
        when(userMappingMapper.selectByPmsUserId(6L)).thenReturn(mapping);

        service.unbindRosterUser(6L);

        // 必须走 update wrapper 显式 set(null)：实体 updateById 会忽略 null 字段，
        // 「解绑」会变成一次无声的 no-op。
        verify(userMappingMapper).update(any(), any());
        verify(userMappingMapper, never()).updateById(any(PmsUserMapping.class));
    }
}
