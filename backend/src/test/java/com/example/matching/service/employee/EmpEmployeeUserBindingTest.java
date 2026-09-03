package com.example.matching.service.employee;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.service.employee.impl.EmpEmployeeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 员工-用户账号绑定关系测试。
 * <p>
 * 验证通过系统用户ID可以查找到绑定的员工。
 */
@ExtendWith(MockitoExtension.class)
class EmpEmployeeUserBindingTest {

    @Mock
    private EmpEmployeeMapper employeeMapper;

    private EmpEmployeeServiceImpl employeeService;

    @BeforeEach
    void setUp() {
        employeeService = new EmpEmployeeServiceImpl();
        ReflectionTestUtils.setField(employeeService, "baseMapper", employeeMapper);
    }

    @Test
    void findsExactlyOneEmployeeForAUserId() {
        EmpEmployee emp = new EmpEmployee();
        emp.setId(7L);
        emp.setUserId(42L);
        emp.setEmpCode("E001");
        emp.setRealName("张三");

        when(employeeMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(emp);

        EmpEmployee result = employeeService.getByUserId(42L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(7L);
        assertThat(result.getUserId()).isEqualTo(42L);
    }

    @Test
    void returnsNullWhenNoEmployeeBoundToUserId() {
        when(employeeMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        EmpEmployee result = employeeService.getByUserId(999L);

        assertThat(result).isNull();
    }
}
