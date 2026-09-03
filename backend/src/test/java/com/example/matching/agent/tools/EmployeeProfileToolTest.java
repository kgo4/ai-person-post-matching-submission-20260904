package com.example.matching.agent.tools;

import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmployeeProfileToolTest {

    @Test
    void getEmployeeInfoReturnsStructuredNotFoundResult() {
        EmpEmployeeMapper employeeMapper = mock(EmpEmployeeMapper.class);
        EmployeeProfileTool tool = new EmployeeProfileTool(employeeMapper,
                mock(EmpAbilityMapper.class), mock(AbilityTagMapper.class));

        Map<String, Object> result = tool.getEmployeeInfo(99L);

        // 实现返回结构化结果：available=true + found=false + reason
        assertThat(result.get("found")).isEqualTo(false);
        assertThat(result.get("available")).isEqualTo(true);
        assertThat(result.get("reason")).isNotNull();
    }

    @Test
    void getEmployeeAbilitiesLoadsTagNamesInOneBatch() {
        EmpAbilityMapper abilityMapper = mock(EmpAbilityMapper.class);
        AbilityTagMapper tagMapper = mock(AbilityTagMapper.class);
        EmployeeProfileTool tool = new EmployeeProfileTool(mock(EmpEmployeeMapper.class), abilityMapper, tagMapper);

        EmpAbility javaAbility = new EmpAbility();
        javaAbility.setId(1L);
        javaAbility.setTagId(101L);
        EmpAbility sqlAbility = new EmpAbility();
        sqlAbility.setId(2L);
        sqlAbility.setTagId(102L);
        when(abilityMapper.selectList(any())).thenReturn(List.of(javaAbility, sqlAbility));

        AbilityTag javaTag = new AbilityTag();
        javaTag.setId(101L);
        javaTag.setTagName("Java");
        AbilityTag sqlTag = new AbilityTag();
        sqlTag.setId(102L);
        sqlTag.setTagName("SQL");
        when(tagMapper.selectBatchIds(List.of(101L, 102L))).thenReturn(List.of(javaTag, sqlTag));

        Map<String, Object> result = tool.getEmployeeAbilities(9L);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> abilities = (List<Map<String, Object>>) result.get("items");
        assertThat(abilities).extracting(item -> item.get("abilityName"))
                .containsExactly("Java", "SQL");
        verify(tagMapper).selectBatchIds(List.of(101L, 102L));
        verify(tagMapper, never()).selectById(any());
    }

    @Test
    void getEmployeeInfoReturnsStructuredErrorWhenDbFails() {
        EmpEmployeeMapper employeeMapper = mock(EmpEmployeeMapper.class);
        when(employeeMapper.selectById(7L)).thenThrow(new RuntimeException("connection refused"));
        EmployeeProfileTool tool = new EmployeeProfileTool(employeeMapper,
                mock(EmpAbilityMapper.class), mock(AbilityTagMapper.class));

        Map<String, Object> result = tool.getEmployeeInfo(7L);

        assertThat(result.get("found")).isEqualTo(false);
        assertThat(result.get("reason")).isNotNull();
    }

    @Test
    void getEmployeeAbilitiesReturnsStructuredErrorWhenDbFails() {
        EmpAbilityMapper abilityMapper = mock(EmpAbilityMapper.class);
        when(abilityMapper.selectList(any())).thenThrow(new RuntimeException("connection refused"));
        EmployeeProfileTool tool = new EmployeeProfileTool(mock(EmpEmployeeMapper.class), abilityMapper,
                mock(AbilityTagMapper.class));

        Map<String, Object> result = tool.getEmployeeAbilities(9L);

        assertThat(result.get("reason")).isNotNull();
        assertThat(result.get("items")).isNotNull();
    }
}
