package com.example.matching.ai.validation;

import com.example.matching.dto.assessment.AssessmentScopeDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 评估题目绑定校验器测试。
 * <p>
 * 覆盖实施计划 Task 2 验收标准：
 * <ul>
 *   <li>未知 abilityTagId / postRequirementId / 空 sourceClaimIds / 岗位无关能力 → 拒绝，不降级成泛化题</li>
 *   <li>scope 内能力必须全部被绑定覆盖，缺失返回 INSUFFICIENT_COVERAGE</li>
 *   <li>非法题比例超阈值返回 INVALID_OUTPUT</li>
 * </ul>
 */
class AssessmentQuestionBindingValidatorTest {

    private AssessmentQuestionBindingValidator validator;
    private AssessmentScopeDTO scope;

    @BeforeEach
    void setUp() {
        validator = new AssessmentQuestionBindingValidator();
        scope = new AssessmentScopeDTO(1L, 100L, 300L,
                List.of(
                        item(10L, "Java", 101L, List.of(1L, 2L)),
                        item(20L, "Python", 102L, List.of(3L)),
                        item(30L, "Go", 103L, List.of(4L))),
                List.of(),
                "hash-1");
    }

    private static AssessmentScopeDTO.AssessmentScopeItem item(
            Long tagId, String name, Long postReqId, List<Long> claimIds) {
        return new AssessmentScopeDTO.AssessmentScopeItem(
                tagId, name, claimIds, 3, postReqId, 3,
                true, true, new BigDecimal("30"), List.of("ref:" + tagId));
    }

    private static Map<String, Object> question(Long tagId, Long postReqId, List<Long> claimIds) {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("question", "测试题");
        q.put("abilityTagId", tagId);
        q.put("postRequirementId", postReqId);
        q.put("sourceClaimIds", claimIds);
        q.put("verificationType", "CLAIM_RECALL");
        return q;
    }

    private static Map<String, Object> multiTagQuestion(List<Long> tagIds, List<Long> claimIds) {
        Map<String, Object> q = question(null, null, claimIds);
        q.remove("abilityTagId");
        q.put("abilityTagIds", tagIds);
        List<Map<String, Object>> bindings = new ArrayList<>();
        for (int i = 0; i < tagIds.size(); i++) {
            bindings.add(Map.of("abilityTagId", tagIds.get(i), "sourceClaimIds", List.of(claimIds.get(i))));
        }
        q.put("verificationBindings", bindings);
        return q;
    }

    @Test
    void validate_unknownAbilityTagId_rejected() {
        List<Map<String, Object>> questions = new ArrayList<>(List.of(
                question(10L, 101L, List.of(1L)),
                question(999L, 101L, List.of(1L)))); // 未知 tagId
        AssessmentQuestionBindingValidator.BindingValidationResult result =
                validator.validate(scope, questions);

        assertThat(result.rejectedCount()).isEqualTo(1);
        assertThat(result.validQuestions()).hasSize(1);
        assertThat(result.violations()).anyMatch(v -> v.contains("abilityTagIds=[999]"));
    }

    @Test
    void validate_unknownPostRequirementId_rejected() {
        List<Map<String, Object>> questions = List.of(
                question(10L, 999L, List.of(1L))); // 未知 postRequirementId
        AssessmentQuestionBindingValidator.BindingValidationResult result =
                validator.validate(scope, questions);

        assertThat(result.rejectedCount()).isEqualTo(1);
        assertThat(result.violations()).anyMatch(v -> v.contains("未知 postRequirementId=999"));
    }

    @Test
    void validate_mismatchedPostRequirementId_rejected() {
        List<Map<String, Object>> questions = List.of(
                question(10L, 102L, List.of(1L))); // tagId=10 对应 101，却给了 102
        AssessmentQuestionBindingValidator.BindingValidationResult result =
                validator.validate(scope, questions);

        assertThat(result.rejectedCount()).isEqualTo(1);
        assertThat(result.violations()).anyMatch(v -> v.contains("不匹配"));
    }

    @Test
    void validate_emptySourceClaimIds_rejected() {
        List<Map<String, Object>> questions = List.of(
                question(10L, 101L, List.of())); // 空 sourceClaimIds
        AssessmentQuestionBindingValidator.BindingValidationResult result =
                validator.validate(scope, questions);

        assertThat(result.rejectedCount()).isEqualTo(1);
        assertThat(result.violations()).anyMatch(v -> v.contains("空 sourceClaimIds"));
    }

    @Test
    void validate_scopeOuterClaimIds_rejected() {
        List<Map<String, Object>> questions = List.of(
                question(10L, 101L, List.of(888L))); // scope 外 Claim
        AssessmentQuestionBindingValidator.BindingValidationResult result =
                validator.validate(scope, questions);

        assertThat(result.rejectedCount()).isEqualTo(1);
        assertThat(result.violations()).anyMatch(v -> v.contains("scope 外 Claim"));
    }

    @Test
    void validate_missingCoverage_insufficientCoverage() {
        // scope 3 个能力，只覆盖 2 个
        List<Map<String, Object>> questions = List.of(
                question(10L, 101L, List.of(1L)),
                question(20L, 102L, List.of(3L)));
        AssessmentQuestionBindingValidator.BindingValidationResult result =
                validator.validate(scope, questions);

        assertThat(result.outputValid()).isTrue();
        assertThat(result.coverageSufficient()).isFalse();
        assertThat(result.uncoveredAbilityTagIds()).containsExactly(30L);
    }

    @Test
    void validate_allBound_valid() {
        List<Map<String, Object>> questions = List.of(
                question(10L, 101L, List.of(1L)),
                question(20L, 102L, List.of(3L)),
                question(30L, 103L, List.of(4L)));
        AssessmentQuestionBindingValidator.BindingValidationResult result =
                validator.validate(scope, questions);

        assertThat(result.outputValid()).isTrue();
        assertThat(result.coverageSufficient()).isTrue();
        assertThat(result.rejectedCount()).isZero();
        assertThat(result.uncoveredAbilityTagIds()).isEmpty();
    }

    @Test
    void validate_multiTagQuestion_coversEachResumeClaimedAbility() {
        List<Map<String, Object>> questions = List.of(
                multiTagQuestion(List.of(10L, 20L), List.of(1L, 3L)),
                question(30L, 103L, List.of(4L)));

        AssessmentQuestionBindingValidator.BindingValidationResult result =
                validator.validate(scope, questions);

        assertThat(result.outputValid()).isTrue();
        assertThat(result.coverageSufficient()).isTrue();
        assertThat(result.coveredAbilityTagIds()).containsExactlyInAnyOrder(10L, 20L, 30L);
    }

    @Test
    void validate_multiTagQuestion_rejectsClaimFromAnotherTag() {
        List<Map<String, Object>> questions = List.of(
                multiTagQuestion(List.of(10L, 20L), List.of(1L, 1L)));

        AssessmentQuestionBindingValidator.BindingValidationResult result =
                validator.validate(scope, questions);

        assertThat(result.rejectedCount()).isEqualTo(1);
        assertThat(result.violations()).anyMatch(v -> v.contains("sourceClaimIds"));
    }

    @Test
    void validate_resumeAbilityWithoutPostRequirement_acceptsNullPostRequirementId() {
        AssessmentScopeDTO resumeOnlyScope = new AssessmentScopeDTO(1L, 100L, 300L,
                List.of(item(30L, "Spring", null, List.of(4L))), List.of(), "hash-2");

        AssessmentQuestionBindingValidator.BindingValidationResult result = validator.validate(
                resumeOnlyScope, List.of(question(30L, null, List.of(4L))));

        assertThat(result.outputValid()).isTrue();
        assertThat(result.coverageSufficient()).isTrue();
        assertThat(result.rejectedCount()).isZero();
    }

    @Test
    void validate_invalidRatioExceeded_outputInvalid() {
        // 3 题中 2 题非法（67% > 30%）
        List<Map<String, Object>> questions = new ArrayList<>(List.of(
                question(10L, 101L, List.of(1L)),
                question(999L, 101L, List.of(1L)),
                question(888L, 999L, List.of())));
        AssessmentQuestionBindingValidator.BindingValidationResult result =
                validator.validate(scope, questions);

        assertThat(result.outputValid()).isFalse();
        assertThat(result.rejectedCount()).isEqualTo(2);
    }

    @Test
    void validate_emptyQuestions_invalidAndInsufficient() {
        AssessmentQuestionBindingValidator.BindingValidationResult result =
                validator.validate(scope, List.of());

        assertThat(result.outputValid()).isFalse();
        assertThat(result.coverageSufficient()).isFalse();
    }
}
