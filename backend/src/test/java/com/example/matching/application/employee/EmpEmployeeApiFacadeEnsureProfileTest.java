package com.example.matching.application.employee;

import com.example.matching.dto.employee.api.EmployeeResponse;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.service.common.EmpCodeGenerator;
import com.example.matching.service.employee.EmpEmployeeService;
import com.example.matching.service.matching.MatchingRecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link EmpEmployeeApiFacade#ensureProfileForUser} 单元测试。
 *
 * <p>背景：超级管理员/平台管理员由后台建号，**不带人员档案**，但被授予了员工侧功能
 * （我的能力 / 我的评估流程 / 发起匹配 / 我的学习路径）。若本人入口不按需建档，
 * 前端 {@code empId} 恒为空，页面会静默无响应（典型：点「开始评估」没反应）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmpEmployeeApiFacadeEnsureProfileTest {

    @Mock private EmpEmployeeService empEmployeeService;
    @Mock private EmpCodeGenerator empCodeGenerator;
    @Mock private MatchingRecordService matchingRecordService;

    private EmpEmployeeApiFacade facade;

    @BeforeEach
    void setUp() {
        facade = new EmpEmployeeApiFacade(empEmployeeService, empCodeGenerator, null,
                matchingRecordService, null);
    }

    @Test
    @DisplayName("无档案时按需建档：生成工号、姓名用账号名占位、组织信息留空")
    void createsProfileWhenMissing() {
        when(empEmployeeService.getByUserId(7L)).thenReturn(null, buildEmployee(77L, 7L));
        when(empCodeGenerator.generateNext()).thenReturn("EMP00099");
        when(empEmployeeService.save(any(EmpEmployee.class))).thenAnswer(inv -> {
            EmpEmployee e = inv.getArgument(0);
            e.setId(77L);
            return true;
        });

        EmployeeResponse res = facade.ensureProfileForUser(7L);

        assertNotNull(res);
        org.mockito.ArgumentCaptor<EmpEmployee> cap =
                org.mockito.ArgumentCaptor.forClass(EmpEmployee.class);
        verify(empEmployeeService).save(cap.capture());
        EmpEmployee saved = cap.getValue();
        assertEquals(7L, saved.getUserId());
        assertEquals("EMP00099", saved.getEmpCode());
        // 姓名占位不能为 null，否则人员档案列表出现空白行
        assertNotNull(saved.getRealName());
        // 组织归属留空交 HR 维护
        assertNull(saved.getDepartmentId());
        assertNull(saved.getCurrentPostId());
        verify(empCodeGenerator).generateNext();
    }

    @Test
    @DisplayName("已有档案时幂等返回，不重复建档")
    void returnsExistingWithoutCreating() {
        EmpEmployee existing = buildEmployee(55L, 7L);
        when(empEmployeeService.getByUserId(7L)).thenReturn(existing);

        EmployeeResponse res = facade.ensureProfileForUser(7L);

        assertNotNull(res);
        assertEquals(55L, res.id());
        verify(empEmployeeService, never()).save(any(EmpEmployee.class));
        verify(empCodeGenerator, never()).generateNext();
    }

    @Test
    @DisplayName("userId 为空 → 返回 null 且不建档")
    void nullUserIdReturnsNull() {
        assertNull(facade.ensureProfileForUser(null));
        verify(empEmployeeService, never()).save(any(EmpEmployee.class));
    }

    @Test
    @DisplayName("建档后仍查不到（异常场景）→ 不抛错，返回 null")
    void returnsNullWhenStillMissing() {
        when(empEmployeeService.getByUserId(7L)).thenReturn(null);
        when(empEmployeeService.save(any(EmpEmployee.class))).thenReturn(true);

        // 第二次 getByUserId 仍返回 null（模拟写入未生效）
        assertNull(facade.ensureProfileForUser(7L));
    }

    private EmpEmployee buildEmployee(Long id, Long userId) {
        EmpEmployee e = new EmpEmployee();
        e.setId(id);
        e.setUserId(userId);
        e.setRealName("张三");
        e.setEmpCode("EMP00001");
        e.setStatus(1);
        e.setIsLocked(0);
        return e;
    }
}
