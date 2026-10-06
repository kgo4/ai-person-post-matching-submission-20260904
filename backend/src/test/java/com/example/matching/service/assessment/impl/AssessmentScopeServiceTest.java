package com.example.matching.service.assessment.impl;

import com.example.matching.dto.assessment.AssessmentScopeDTO;
import com.example.matching.entity.ability.PersonAbilityClaim;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.entity.workflow.PersonAbilityClaimGroup;
import com.example.matching.entity.workflow.PersonCapabilityWorkflow;
import com.example.matching.mapper.ability.PersonAbilityClaimMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.mapper.workflow.PersonAbilityClaimGroupMapper;
import com.example.matching.port.post.PostQueryPort;
import com.example.matching.service.assessment.CapabilityAssessmentWorkflowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 评估范围服务测试。
 * <p>
 * 覆盖实施计划 Task 1 验收标准：
 * <ul>
 *   <li>岗位要求与简历 Claim 按 abilityTagId 取交集</li>
 *   <li>同名不同 ID 不得合并</li>
 *   <li>无岗位要求的简历标签不进入 scope</li>
 *   <li>岗位未覆盖能力进入 uncoveredRequirements</li>
 *   <li>缺少岗位/员工/岗位要求时返回明确业务错误，不构造空 scope</li>
 *   <li>scopeHash 确定性（重试复用同一 hash）</li>
 * </ul>
 */
class AssessmentScopeServiceTest {

    private CapabilityAssessmentWorkflowService workflowService;
    private PostQueryPort postQueryPort;
    private PersonAbilityClaimMapper claimMapper;
    private PersonAbilityClaimGroupMapper claimGroupMapper;
    private AbilityTagMapper abilityTagMapper;
    private AssessmentScopeServiceImpl service;

    @BeforeEach
    void setUp() {
        workflowService = mock(CapabilityAssessmentWorkflowService.class);
        postQueryPort = mock(PostQueryPort.class);
        claimMapper = mock(PersonAbilityClaimMapper.class);
        claimGroupMapper = mock(PersonAbilityClaimGroupMapper.class);
        abilityTagMapper = mock(AbilityTagMapper.class);
        service = new AssessmentScopeServiceImpl(workflowService, postQueryPort,
                claimMapper, claimGroupMapper, abilityTagMapper, new ObjectMapper());
    }

    private PersonCapabilityWorkflow workflow(Long workflowId, Long empId) {
        PersonCapabilityWorkflow w = new PersonCapabilityWorkflow();
        w.setId(workflowId);
        w.setEmpId(empId);
        return w;
    }

    private PostQueryPort.PostAbilityDTO req(Long id, Long tagId, int level, int required, int core) {
        return new PostQueryPort.PostAbilityDTO(id, 300L, tagId, level, new BigDecimal("30"),
                required, core, "v1", null, null);
    }

    private PersonAbilityClaim resumeClaim(Long id, Long tagId, String name, int level) {
        PersonAbilityClaim c = new PersonAbilityClaim();
        c.setId(id);
        c.setWorkflowId(1L);
        c.setEmpId(100L);
        c.setSourceType("RESUME_PARSE");
        c.setStatus("ACTIVE");
        c.setTagId(tagId);
        c.setNormalizedAbilityName(name);
        c.setAbilityName(name);
        c.setClaimedLevel(level);
        c.setSourceRefsJson("[\"source:RESUME_PARSE:200:0\"]");
        return c;
    }

    @Test
    void build_includesAllResumeClaimsAndEnrichesMatchingPostRequirements() {
        when(workflowService.getWorkflow(1L)).thenReturn(workflow(1L, 100L));
        when(postQueryPort.listRequirementsByPostId(300L)).thenReturn(List.of(
                req(501L, 10L, 3, 1, 1),  // Java L3 核心必填
                req(502L, 20L, 4, 0, 0))); // Python L4
        when(claimMapper.selectList(any())).thenReturn(List.of(
                resumeClaim(1L, 10L, "Java", 4),
                resumeClaim(2L, 30L, "Spring", 3))); // Spring 无岗位要求
        when(claimGroupMapper.selectList(any())).thenReturn(List.of());

        AssessmentScopeDTO scope = service.build(1L, 100L, 300L);

        assertThat(scope.items()).hasSize(2);
        assertThat(scope.items().get(0).abilityTagId()).isEqualTo(10L);
        assertThat(scope.items().get(0).abilityName()).isEqualTo("Java");
        assertThat(scope.items().get(0).claimedLevel()).isEqualTo(4);
        assertThat(scope.items().get(0).postRequirementId()).isEqualTo(501L);
        assertThat(scope.items().get(0).requiredLevel()).isEqualTo(3);
        assertThat(scope.items().get(0).required()).isTrue();
        assertThat(scope.items().get(0).core()).isTrue();
        assertThat(scope.items().get(0).resumeClaimIds()).containsExactly(1L);

        assertThat(scope.items().get(1).abilityTagId()).isEqualTo(30L);
        assertThat(scope.items().get(1).abilityName()).isEqualTo("Spring");
        assertThat(scope.items().get(1).postRequirementId()).isNull();
        assertThat(scope.items().get(1).requiredLevel()).isNull();
        assertThat(scope.items().get(1).required()).isFalse();

        assertThat(scope.uncoveredRequirements()).hasSize(1);
        assertThat(scope.uncoveredRequirements().get(0).abilityTagId()).isEqualTo(20L);
        assertThat(scope.uncoveredRequirements().get(0).reason()).isEqualTo("RESUME_NO_CLAIM");
    }

    @Test
    void build_keepsResumeTagWhenItsIdDoesNotMatchPostRequirement() {
        when(workflowService.getWorkflow(1L)).thenReturn(workflow(1L, 100L));
        when(postQueryPort.listRequirementsByPostId(300L)).thenReturn(List.of(
                req(501L, 10L, 3, 1, 1)));
        // 简历声明名为 "Java" 但 tagId=99（与岗位要求 tagId=10 同名不同 ID）
        when(claimMapper.selectList(any())).thenReturn(List.of(
                resumeClaim(1L, 99L, "Java", 4)));
        when(claimGroupMapper.selectList(any())).thenReturn(List.of());

        AssessmentScopeDTO scope = service.build(1L, 100L, 300L);

        assertThat(scope.items()).singleElement()
                .extracting(AssessmentScopeDTO.AssessmentScopeItem::abilityTagId)
                .isEqualTo(99L);
        assertThat(scope.uncoveredRequirements()).hasSize(1);
        assertThat(scope.uncoveredRequirements().get(0).abilityTagId()).isEqualTo(10L);
    }

    @Test
    void build_resumeTagWithoutPostRequirement_remainsInVerificationScope() {
        when(workflowService.getWorkflow(1L)).thenReturn(workflow(1L, 100L));
        when(postQueryPort.listRequirementsByPostId(300L)).thenReturn(List.of(
                req(501L, 10L, 3, 1, 1)));
        when(claimMapper.selectList(any())).thenReturn(List.of(
                resumeClaim(1L, 10L, "Java", 4),
                resumeClaim(2L, 30L, "Spring", 3)));
        when(claimGroupMapper.selectList(any())).thenReturn(List.of());

        AssessmentScopeDTO scope = service.build(1L, 100L, 300L);

        assertThat(scope.items()).hasSize(2);
        assertThat(scope.items().get(0).abilityTagId()).isEqualTo(10L);
        assertThat(scope.items().get(1).abilityTagId()).isEqualTo(30L);
        assertThat(scope.items().get(1).postRequirementId()).isNull();
        // Spring 无岗位要求，但简历已有该标签，仍应被核验。
        assertThat(scope.uncoveredRequirements()).isEmpty();
    }

    @Test
    void build_resolvesTagIdFromClaimGroupWhenClaimHasNoTagId() {
        when(workflowService.getWorkflow(1L)).thenReturn(workflow(1L, 100L));
        when(postQueryPort.listRequirementsByPostId(300L)).thenReturn(List.of(
                req(501L, 10L, 3, 1, 1)));
        PersonAbilityClaim claim = resumeClaim(1L, null, "Java", 4);
        claim.setClaimGroupId(900L);
        when(claimMapper.selectList(any())).thenReturn(List.of(claim));
        PersonAbilityClaimGroup group = new PersonAbilityClaimGroup();
        group.setId(900L);
        group.setCanonicalTagId(10L);
        when(claimGroupMapper.selectList(any())).thenReturn(List.of(group));

        AssessmentScopeDTO scope = service.build(1L, 100L, 300L);

        assertThat(scope.items()).hasSize(1);
        assertThat(scope.items().get(0).abilityTagId()).isEqualTo(10L);
    }

    @Test
    void build_withoutPost_keepsAllResumeAbilities() {
        when(workflowService.getWorkflow(1L)).thenReturn(workflow(1L, 100L));
        when(claimMapper.selectList(any())).thenReturn(List.of(
                resumeClaim(1L, 10L, "Java", 4),
                resumeClaim(2L, 30L, "Spring", 3)));
        when(claimGroupMapper.selectList(any())).thenReturn(List.of());

        AssessmentScopeDTO scope = service.build(1L, 100L, null);

        assertThat(scope.postId()).isNull();
        assertThat(scope.items()).extracting(AssessmentScopeDTO.AssessmentScopeItem::abilityTagId)
                .containsExactly(10L, 30L);
        assertThat(scope.items()).allSatisfy(item -> assertThat(item.postRequirementId()).isNull());
        assertThat(scope.uncoveredRequirements()).isEmpty();
    }

    @Test
    void build_empMismatch_throwsBusinessError() {
        when(workflowService.getWorkflow(1L)).thenReturn(workflow(1L, 100L));

        assertThatThrownBy(() -> service.build(1L, 999L, 300L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("归属员工不匹配");
    }

    @Test
    void build_postWithoutRequirements_stillBuildsResumeVerificationScope() {
        when(workflowService.getWorkflow(1L)).thenReturn(workflow(1L, 100L));
        when(postQueryPort.listRequirementsByPostId(300L)).thenReturn(List.of());
        when(claimMapper.selectList(any())).thenReturn(List.of(resumeClaim(1L, 10L, "Java", 4)));
        when(claimGroupMapper.selectList(any())).thenReturn(List.of());

        AssessmentScopeDTO scope = service.build(1L, 100L, 300L);

        assertThat(scope.items()).singleElement()
                .extracting(AssessmentScopeDTO.AssessmentScopeItem::abilityTagId)
                .isEqualTo(10L);
    }

    @Test
    void build_scopeHashIsDeterministic() {
        when(workflowService.getWorkflow(1L)).thenReturn(workflow(1L, 100L));
        when(postQueryPort.listRequirementsByPostId(300L)).thenReturn(List.of(
                req(501L, 10L, 3, 1, 1),
                req(502L, 20L, 4, 0, 0)));
        when(claimMapper.selectList(any())).thenReturn(List.of(
                resumeClaim(1L, 10L, "Java", 4)));
        when(claimGroupMapper.selectList(any())).thenReturn(List.of());
        when(abilityTagMapper.selectBatchIds(any())).thenReturn(List.of(tag(20L, "Python")));

        AssessmentScopeDTO first = service.build(1L, 100L, 300L);
        AssessmentScopeDTO second = service.build(1L, 100L, 300L);

        assertThat(first.scopeHash()).isNotBlank();
        assertThat(second.scopeHash()).isEqualTo(first.scopeHash());
    }

    private AbilityTag tag(Long id, String name) {
        AbilityTag t = new AbilityTag();
        t.setId(id);
        t.setTagName(name);
        return t;
    }
}
