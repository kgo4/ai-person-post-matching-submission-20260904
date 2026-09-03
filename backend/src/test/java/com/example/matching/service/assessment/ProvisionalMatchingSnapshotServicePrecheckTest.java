package com.example.matching.service.assessment;

import com.example.matching.common.enums.EvidenceStatusEnum;
import com.example.matching.dto.assessment.EligibilityPrecheckResult;
import com.example.matching.dto.matching.MatchingAbilitySnapshot;
import com.example.matching.entity.workflow.PersonAbilityClaimGroup;
import com.example.matching.mapper.workflow.PersonAbilityClaimGroupMapper;
import com.example.matching.port.employee.EmployeeAbilityReadPort;
import com.example.matching.service.assessment.impl.ProvisionalMatchingSnapshotServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 匹配资格预检测试（正式能力口径与匹配引擎一致：person_ability_profile 优先，回退 emp_ability）。
 * <p>
 * 回归点：只有 emp_ability 正式能力（无 person_ability_profile）的员工不得被误判为
 * "无正式能力且无待确立能力"（FORBIDDEN）。
 */
class ProvisionalMatchingSnapshotServicePrecheckTest {

    private EmployeeAbilityReadPort employeeAbilityReadPort;
    private PersonAbilityClaimGroupMapper claimGroupMapper;
    private AbilityEvidenceCollectionService evidenceCollectionService;
    private ProvisionalMatchingSnapshotService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        employeeAbilityReadPort = mock(EmployeeAbilityReadPort.class);
        claimGroupMapper = mock(PersonAbilityClaimGroupMapper.class);
        evidenceCollectionService = mock(AbilityEvidenceCollectionService.class);
        ObjectProvider<StringRedisTemplate> redisProvider = mock(ObjectProvider.class);
        service = new ProvisionalMatchingSnapshotServiceImpl(
                employeeAbilityReadPort, claimGroupMapper, evidenceCollectionService, redisProvider);
    }

    private MatchingAbilitySnapshot snapshot(Long tagId, int level) {
        return new MatchingAbilitySnapshot(
                null, tagId, "Java", level, new BigDecimal("0.8"),
                "EMP_ABILITY", new BigDecimal("0.8"), LocalDate.now());
    }

    private PersonAbilityClaimGroup group(Long id, String status) {
        PersonAbilityClaimGroup group = new PersonAbilityClaimGroup();
        group.setId(id);
        group.setEmpId(1L);
        group.setWorkflowId(1L);
        group.setNormalizedAbilityName("Java");
        group.setCanonicalTagId(10L);
        group.setStatus(status);
        return group;
    }

    @Test
    @DisplayName("仅有 emp_ability 正式能力（无 person_ability_profile）：应判定有正式能力，允许匹配")
    void onlyEmpAbilityIsConfirmed() {
        when(employeeAbilityReadPort.loadAuthoritativeAbilities(List.of(1L)))
                .thenReturn(Map.of(1L, List.of(snapshot(10L, 3))));
        when(claimGroupMapper.selectList(any())).thenReturn(List.of());

        List<EligibilityPrecheckResult> results = service.precheck(List.of(1L), List.of(2L));

        assertThat(results).hasSize(1);
        EligibilityPrecheckResult result = results.get(0);
        assertThat(result.getHasConfirmedAbilities()).isTrue();
        assertThat(result.getHasProvisionalAbilities()).isFalse();
        assertThat(result.getDefaultAction()).isEqualTo("NORMAL_MATCH");
    }

    @Test
    @DisplayName("无正式能力且无待确立能力：仍禁止匹配（FORBIDDEN）")
    void noAbilitiesIsForbidden() {
        when(employeeAbilityReadPort.loadAuthoritativeAbilities(List.of(1L)))
                .thenReturn(Map.of());
        when(claimGroupMapper.selectList(any())).thenReturn(List.of());

        List<EligibilityPrecheckResult> results = service.precheck(List.of(1L), List.of(2L));

        EligibilityPrecheckResult result = results.get(0);
        assertThat(result.getHasConfirmedAbilities()).isFalse();
        assertThat(result.getHasProvisionalAbilities()).isFalse();
        assertThat(result.getDefaultAction()).isEqualTo("FORBIDDEN");
        assertThat(result.getRiskFlags()).contains("NO_ABILITIES");
    }

    @Test
    @DisplayName("仅有待确立能力：人工确认后强制匹配（MANUAL_CONFIRM_REQUIRED）")
    void provisionalOnlyRequiresManualConfirm() {
        when(employeeAbilityReadPort.loadAuthoritativeAbilities(List.of(1L)))
                .thenReturn(Map.of());
        when(claimGroupMapper.selectList(any()))
                .thenReturn(List.of(group(100L, EvidenceStatusEnum.COLLECTED.getCode())));
        when(evidenceCollectionService.listClaimsByGroup(100L)).thenReturn(List.of());

        List<EligibilityPrecheckResult> results = service.precheck(List.of(1L), List.of(2L));

        EligibilityPrecheckResult result = results.get(0);
        assertThat(result.getHasConfirmedAbilities()).isFalse();
        assertThat(result.getHasProvisionalAbilities()).isTrue();
        assertThat(result.getProvisionalAbilityCount()).isEqualTo(1);
        assertThat(result.getDefaultAction()).isEqualTo("MANUAL_CONFIRM_REQUIRED");
    }

    @Test
    @DisplayName("同时有正式能力与待确立能力：默认仅正式能力匹配（CONFIRMED_ONLY）")
    void confirmedPlusProvisionalDefaultsToConfirmedOnly() {
        when(employeeAbilityReadPort.loadAuthoritativeAbilities(List.of(1L)))
                .thenReturn(Map.of(1L, List.of(snapshot(10L, 3))));
        when(claimGroupMapper.selectList(any()))
                .thenReturn(List.of(group(100L, EvidenceStatusEnum.PENDING_MANUAL_REVIEW.getCode())));
        when(evidenceCollectionService.listClaimsByGroup(100L)).thenReturn(List.of());

        List<EligibilityPrecheckResult> results = service.precheck(List.of(1L), List.of(2L));

        EligibilityPrecheckResult result = results.get(0);
        assertThat(result.getHasConfirmedAbilities()).isTrue();
        assertThat(result.getHasProvisionalAbilities()).isTrue();
        assertThat(result.getDefaultAction()).isEqualTo("CONFIRMED_ONLY");
    }
}
