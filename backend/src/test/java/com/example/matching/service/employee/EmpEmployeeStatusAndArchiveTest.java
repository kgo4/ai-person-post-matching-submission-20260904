package com.example.matching.service.employee;

import com.example.matching.common.exception.BusinessException;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.service.employee.impl.EmpEmployeeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 员工「禁用 / 启用」与「作废档案」口径测试（人岗匹配闭环设计 S1 / G1）。
 *
 * <p>锁定的业务口径：HR 不允许删除员工，只能禁用。</p>
 * <ul>
 *   <li>禁用只改 {@code status}，绝不触碰 {@code is_deleted}；</li>
 *   <li>作废档案是纯逻辑删除（{@code is_deleted=1}），历史业务数据全部保留；</li>
 *   <li>在职人员必须先禁用才能作废；</li>
 *   <li>任何路径都不得级联物理删除业务数据（历史实现会 DELETE 25 张表）。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class EmpEmployeeStatusAndArchiveTest {

    @Mock
    private EmpEmployeeMapper employeeMapper;

    private EmpEmployeeServiceImpl employeeService;

    @BeforeEach
    void setUp() {
        employeeService = new EmpEmployeeServiceImpl();
        ReflectionTestUtils.setField(employeeService, "baseMapper", employeeMapper);
    }

    private static EmpEmployee employee(Long id, Integer status) {
        EmpEmployee emp = new EmpEmployee();
        emp.setId(id);
        emp.setEmpCode("E001");
        emp.setRealName("张三");
        emp.setStatus(status);
        return emp;
    }

    @Test
    @DisplayName("禁用：只写 status=0，不触碰 isDeleted，不走删除路径")
    void disableOnlyWritesStatus() {
        when(employeeMapper.selectById(1L)).thenReturn(employee(1L, 1));
        when(employeeMapper.updateById(any(EmpEmployee.class))).thenReturn(1);

        employeeService.updateStatus(1L, 0);

        ArgumentCaptor<EmpEmployee> captor = ArgumentCaptor.forClass(EmpEmployee.class);
        verify(employeeMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isZero();
        assertThat(captor.getValue().getIsDeleted()).isNull();
        verify(employeeMapper, never()).deleteById(any(Serializable.class));
    }

    @Test
    @DisplayName("启用：写 status=1")
    void enableWritesStatusOne() {
        when(employeeMapper.selectById(1L)).thenReturn(employee(1L, 0));
        when(employeeMapper.updateById(any(EmpEmployee.class))).thenReturn(1);

        employeeService.updateStatus(1L, 1);

        ArgumentCaptor<EmpEmployee> captor = ArgumentCaptor.forClass(EmpEmployee.class);
        verify(employeeMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(1);
    }

    @Test
    @DisplayName("禁用：非法状态值被拒绝")
    void rejectsIllegalStatus() {
        assertThatThrownBy(() -> employeeService.updateStatus(1L, 2))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("0（禁用）或 1（启用）");
        assertThatThrownBy(() -> employeeService.updateStatus(1L, null))
                .isInstanceOf(BusinessException.class);

        verify(employeeMapper, never()).updateById(any(EmpEmployee.class));
    }

    @Test
    @DisplayName("禁用：员工不存在时报错")
    void rejectsUnknownEmployee() {
        when(employeeMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> employeeService.updateStatus(99L, 0))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("员工不存在");
    }

    @Test
    @DisplayName("作废：原因必填")
    void archiveRequiresReason() {
        assertThatThrownBy(() -> employeeService.archive(1L, "   "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("作废原因不能为空");

        verify(employeeMapper, never()).deleteById(any(Serializable.class));
    }

    @Test
    @DisplayName("作废：在职人员必须先禁用")
    void archiveRejectsActiveEmployee() {
        when(employeeMapper.selectById(1L)).thenReturn(employee(1L, 1));

        assertThatThrownBy(() -> employeeService.archive(1L, "误建档案"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请先『禁用』");

        verify(employeeMapper, never()).deleteById(any(Serializable.class));
    }

    @Test
    @DisplayName("作废：已禁用员工走逻辑删除，不做级联物理删除")
    void archiveDisabledEmployeeIsLogicalDeleteOnly() {
        when(employeeMapper.selectById(1L)).thenReturn(employee(1L, 0));
        when(employeeMapper.deleteById(anyLong())).thenReturn(1);

        employeeService.archive(1L, "误建档案");

        // 只调用一次 deleteById（@TableLogic 会转成 UPDATE is_deleted=1）
        verify(employeeMapper).deleteById(anyLong());
        verify(employeeMapper, never()).updateById(any(EmpEmployee.class));
    }

    @Test
    @DisplayName("作废：员工不存在时报错")
    void archiveRejectsUnknownEmployee() {
        when(employeeMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> employeeService.archive(99L, "误建档案"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("员工不存在");
    }
}
