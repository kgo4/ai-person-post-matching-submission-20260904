package com.example.matching.service.matching;

import com.example.matching.dto.matching.MatchingAbilitySnapshot;
import com.example.matching.dto.matching.MatchingEmployeeProfile;
import com.example.matching.dto.matching.MatchingPostProfile;
import com.example.matching.dto.matching.MatchingRequirementSnapshot;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.post.PostPost;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M-12 测试：Entity -> 匹配专用 DTO 映射
 * <p>
 * 覆盖字段完整性、null 处理、数值精度、sourceType。
 */
class MatchingSnapshotAssemblerTest {

    @Test
    @DisplayName("EmpAbility -> 能力快照：字段完整且精度不丢失")
    void empAbilityToSnapshot_fieldsCompleteAndPrecise() {
        EmpAbility ability = new EmpAbility();
        ability.setId(100L);
        ability.setTagId(10L);
        ability.setMasteryLevel(4);
        ability.setAbilityLevel(3);
        ability.setEvaluationSource("MANUAL");
        ability.setSourceWeight(new BigDecimal("0.85"));
        ability.setEvaluationDate(LocalDate.of(2026, 1, 15));

        MatchingAbilitySnapshot snapshot = MatchingSnapshotAssembler.toAbilitySnapshot(ability, "Java并发");

        assertThat(snapshot.abilityId()).isEqualTo(100L);
        assertThat(snapshot.tagId()).isEqualTo(10L);
        assertThat(snapshot.abilityName()).isEqualTo("Java并发");
        assertThat(snapshot.level()).isEqualTo(4);
        assertThat(snapshot.confidence()).isEqualByComparingTo("0.85");
        assertThat(snapshot.sourceType()).isEqualTo("MANUAL");
        assertThat(snapshot.sourceWeight()).isEqualByComparingTo("0.85");
        assertThat(snapshot.evaluationDate()).isEqualTo(LocalDate.of(2026, 1, 15));
    }

    @Test
    @DisplayName("EmpAbility -> 能力快照：null 字段安全处理")
    void empAbilityToSnapshot_nullFieldsHandled() {
        EmpAbility ability = new EmpAbility();
        ability.setTagId(10L);
        ability.setEvaluationSource("AI_TEST");

        MatchingAbilitySnapshot snapshot = MatchingSnapshotAssembler.toAbilitySnapshot(ability, null);

        assertThat(snapshot.tagId()).isEqualTo(10L);
        assertThat(snapshot.abilityName()).isNull();
        assertThat(snapshot.level()).isNull();
        assertThat(snapshot.confidence()).isNull();
        assertThat(snapshot.evaluationDate()).isNull();
        assertThat(MatchingSnapshotAssembler.toAbilitySnapshot((EmpAbility) null, "x")).isNull();
    }

    @Test
    @DisplayName("岗位要求 -> 要求快照：字段完整")
    void requirementToSnapshot_fieldsComplete() {
        PostAbilityModel model = new PostAbilityModel();
        model.setTagId(20L);
        model.setMinRequiredLevel(3);
        model.setWeight(new BigDecimal("15.50"));
        model.setIsRequired(1);
        model.setIsCore(1);

        MatchingRequirementSnapshot snapshot = MatchingSnapshotAssembler.toRequirementSnapshot(model, "Java");

        assertThat(snapshot.tagId()).isEqualTo(20L);
        assertThat(snapshot.abilityName()).isEqualTo("Java");
        assertThat(snapshot.minRequiredLevel()).isEqualTo(3);
        assertThat(snapshot.weight()).isEqualByComparingTo("15.50");
        assertThat(snapshot.isRequired()).isEqualTo(1);
        assertThat(snapshot.isCore()).isEqualTo(1);
        assertThat(MatchingSnapshotAssembler.toRequirementSnapshot(null, "x")).isNull();
    }

    @Test
    @DisplayName("员工 -> 员工画像：能力快照按 empId 装配")
    void employeeToProfile_abilitiesAssembledByEmpId() {
        EmpEmployee employee = new EmpEmployee();
        employee.setId(1L);
        employee.setRealName("张三");
        MatchingAbilitySnapshot snapshot = new MatchingAbilitySnapshot(
                100L, 10L, "Java", 4, new BigDecimal("0.85"), "MANUAL", new BigDecimal("0.85"), null);

        MatchingEmployeeProfile profile = MatchingSnapshotAssembler.toEmployeeProfile(
                employee, Map.of(1L, List.of(snapshot)));

        assertThat(profile.empId()).isEqualTo(1L);
        assertThat(profile.realName()).isEqualTo("张三");
        assertThat(profile.abilities()).containsExactly(snapshot);
        assertThat(MatchingSnapshotAssembler.toEmployeeProfile(null, Map.of())).isNull();
    }

    @Test
    @DisplayName("岗位 -> 岗位画像：要求快照完整")
    void postToProfile_requirementsComplete() {
        PostPost post = new PostPost();
        post.setId(2L);
        post.setPostCode("P-002");
        post.setPostName("Java开发");
        post.setJobDescription("负责后端开发");
        MatchingRequirementSnapshot snapshot = new MatchingRequirementSnapshot(
                20L, "Java", 3, new BigDecimal("15.50"), 1, 1, "v1");

        MatchingPostProfile profile = MatchingSnapshotAssembler.toPostProfile(post, List.of(snapshot));

        assertThat(profile.postId()).isEqualTo(2L);
        assertThat(profile.postCode()).isEqualTo("P-002");
        assertThat(profile.postName()).isEqualTo("Java开发");
        assertThat(profile.jobDescription()).isEqualTo("负责后端开发");
        assertThat(profile.requirements()).containsExactly(snapshot);
        assertThat(MatchingSnapshotAssembler.toPostProfile(null, List.of())).isNull();
    }
}
