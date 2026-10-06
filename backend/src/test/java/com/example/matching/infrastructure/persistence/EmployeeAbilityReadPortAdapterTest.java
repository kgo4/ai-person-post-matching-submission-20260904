package com.example.matching.infrastructure.persistence;

import com.example.matching.dto.matching.MatchingAbilitySnapshot;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.system.AbilityTag;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 权威能力端口测试：匹配仅消费 emp_ability 正式人员能力表。
 */
class EmployeeAbilityReadPortAdapterTest {

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, EmpAbility.class);
        TableInfoHelper.initTableInfo(assistant, AbilityTag.class);
    }

    private EmpAbility empAbility(long empId, long tagId, int level) {
        EmpAbility a = new EmpAbility();
        a.setId(empId * 10 + tagId);
        a.setEmpId(empId);
        a.setTagId(tagId);
        a.setMasteryLevel(level);
        a.setEvaluationSource("MANUAL");
        a.setSourceWeight(new BigDecimal("0.8"));
        a.setIsDeleted(0);
        return a;
    }

    @Test
    @DisplayName("匹配只读取正式能力表：画像等级不得覆盖人工维护的 emp_ability")
    void empAbilityIsAuthoritativeForMatching() {
        EmpAbilityMapper abilityMapper = mock(EmpAbilityMapper.class);
        when(abilityMapper.selectList(any())).thenReturn(List.of(empAbility(1L, 11L, 2)));
        AbilityTagMapper tagMapper = mock(AbilityTagMapper.class);
        when(tagMapper.selectList(any())).thenReturn(List.of(tag(11L, "Java")));

        EmployeeAbilityReadPortAdapter adapter = new EmployeeAbilityReadPortAdapter(
                abilityMapper, tagMapper);
        Map<Long, List<MatchingAbilitySnapshot>> result =
                adapter.loadAuthoritativeAbilities(List.of(1L));

        MatchingAbilitySnapshot snapshot = result.get(1L).get(0);
        assertThat(snapshot.level()).isEqualTo(2);
        assertThat(snapshot.sourceType()).isEqualTo("MANUAL");
        assertThat(snapshot.abilityName()).isEqualTo("Java");
    }

    @Test
    @DisplayName("无标准标签的正式评估能力也进入匹配快照")
    void untaggedAssessmentAbilityIsIncludedInMatchingSnapshot() {
        EmpAbility untagged = new EmpAbility();
        untagged.setId(12L);
        untagged.setEmpId(1L);
        untagged.setAbilityName("服务器部署");
        untagged.setMasteryLevel(3);
        untagged.setEvaluationSource("PROFILE_FUSED");
        untagged.setIsDeleted(0);
        EmpAbilityMapper abilityMapper = mock(EmpAbilityMapper.class);
        when(abilityMapper.selectList(any())).thenReturn(List.of(untagged));

        EmployeeAbilityReadPortAdapter adapter = new EmployeeAbilityReadPortAdapter(
                abilityMapper, mock(AbilityTagMapper.class));

        MatchingAbilitySnapshot snapshot = adapter.loadAuthoritativeAbilities(List.of(1L)).get(1L).get(0);
        assertThat(snapshot.abilityId()).isEqualTo(12L);
        assertThat(snapshot.tagId()).isNull();
        assertThat(snapshot.abilityName()).isEqualTo("服务器部署");
    }

    private AbilityTag tag(long id, String name) {
        AbilityTag tag = new AbilityTag();
        tag.setId(id);
        tag.setTagName(name);
        return tag;
    }
}
