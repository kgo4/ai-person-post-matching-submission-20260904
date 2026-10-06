package com.example.matching.ai.validation;

import com.example.matching.agent.dto.post.PostAbilityClaim;
import com.example.matching.agent.dto.post.PostAbilityExtractionResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 岗位提取校验器测试（方案 Task 3）。
 */
class PostAbilityExtractionValidatorTest {

    private final PostAbilityExtractionValidator validator = new PostAbilityExtractionValidator();

    private PostAbilityClaim claim(String name, Integer level, String weight,
                                   String evidence, List<String> sourceRefs) {
        PostAbilityClaim claim = new PostAbilityClaim();
        claim.setAbilityName(name);
        claim.setRequiredLevel(level);
        claim.setWeight(weight != null ? new BigDecimal(weight) : null);
        claim.setEvidenceText(evidence);
        claim.setSourceRefs(sourceRefs);
        claim.setConfidenceScore(new BigDecimal("80"));
        return claim;
    }

    private PostAbilityExtractionResult result(List<PostAbilityClaim> claims) {
        PostAbilityExtractionResult result = new PostAbilityExtractionResult();
        result.setClaims(claims);
        return result;
    }

    @Test
    void rejectsAbilityNotInSourceText() {
        PostAbilityExtractionResult result = result(List.of(
                claim("Kubernetes", 3, "0.5", "原文不存在的编造证据", null)));
        assertThatThrownBy(() -> validator.validate(result, "负责 Java 后端开发", null))
                .isInstanceOf(AiOutputValidationException.class)
                .hasMessageContaining("evidenceText");
    }

    @Test
    void rejectsEvidenceThatOnlyAppearsInAgentContextNotTrustedSource() {
        PostAbilityExtractionResult result = result(List.of(
                claim("Kubernetes", 3, "0.5", "掌握 Kubernetes 容器编排", null)));

        assertThatThrownBy(() -> validator.validateAgainstTrustedSource(
                result,
                "岗位原文：负责 Java 后端服务开发",
                List.of("source:JD_IMPORT:5")))
                .isInstanceOf(AiOutputValidationException.class)
                .hasMessageContaining("evidenceText");
    }

    @Test
    void rejectsNegativeWeight() {
        PostAbilityExtractionResult result = result(List.of(
                claim("Java", 3, "-0.1", "负责Java后端开发", null)));
        assertThatThrownBy(() -> validator.validate(result, "负责Java后端开发", null))
                .isInstanceOf(AiOutputValidationException.class)
                .hasMessageContaining("weight");
    }

    @Test
    void rejectsWeightAboveOne() {
        PostAbilityExtractionResult result = result(List.of(
                claim("Java", 3, "1.5", "负责Java后端开发", null)));
        assertThatThrownBy(() -> validator.validate(result, "负责Java后端开发", null))
                .isInstanceOf(AiOutputValidationException.class)
                .hasMessageContaining("weight");
    }

    @Test
    void rejectsLevelZeroAndSix() {
        PostAbilityExtractionResult zero = result(List.of(
                claim("Java", 0, "0.5", "负责Java后端开发", null)));
        assertThatThrownBy(() -> validator.validate(zero, "负责Java后端开发", null))
                .isInstanceOf(AiOutputValidationException.class)
                .hasMessageContaining("requiredLevel");

        PostAbilityExtractionResult six = result(List.of(
                claim("Java", 6, "0.5", "负责Java后端开发", null)));
        assertThatThrownBy(() -> validator.validate(six, "负责Java后端开发", null))
                .isInstanceOf(AiOutputValidationException.class)
                .hasMessageContaining("requiredLevel");
    }

    @Test
    void rejectsSourceRefsOutsideControlledSet() {
        PostAbilityExtractionResult result = result(List.of(
                claim("Java", 3, "0.5", "负责Java后端开发", List.of("source:OTHER:99"))));
        assertThatThrownBy(() -> validator.validate(result, "负责Java后端开发",
                List.of("source:JD_IMPORT:5")))
                .isInstanceOf(AiOutputValidationException.class)
                .hasMessageContaining("sourceRefs");
    }

    @Test
    void rejectsConfidenceOutsideRange() {
        PostAbilityClaim claim = claim("Java", 3, "0.5", "负责Java后端开发", null);
        claim.setConfidenceScore(new BigDecimal("120"));
        PostAbilityExtractionResult result = result(List.of(claim));
        assertThatThrownBy(() -> validator.validate(result, "负责Java后端开发", null))
                .isInstanceOf(AiOutputValidationException.class)
                .hasMessageContaining("confidenceScore");
    }

    @Test
    void deduplicatesSameAbilityKeepingLongerEvidenceAndHigherConfidence() {
        PostAbilityClaim shortClaim = claim("Java", 3, "0.4", "负责Java后端开发", null);
        shortClaim.setConfidenceScore(new BigDecimal("70"));
        PostAbilityClaim longClaim = claim("Java", 3, "0.6", "负责Java后端开发与性能优化", null);
        longClaim.setConfidenceScore(new BigDecimal("90"));

        PostAbilityExtractionResult result = result(List.of(shortClaim, longClaim));
        validator.validate(result, "负责Java后端开发与性能优化，并参与性能优化", null);

        assertThat(result.getClaims()).hasSize(1);
        assertThat(result.getClaims().get(0).getEvidenceText()).isEqualTo("负责Java后端开发与性能优化");
        assertThat(result.getClaims().get(0).getConfidenceScore()).isEqualByComparingTo("90");
        assertThat(result.getClaims().get(0).getWeight()).isEqualByComparingTo("0.6");
    }

    @Test
    void acceptsUnknownAbilityWithLocatableContinuousEvidence() {
        // 开放词表：系统不存在的新能力名称，只要原文包含连续证据，仍通过校验
        PostAbilityExtractionResult result = result(List.of(
                claim("Rust高性能并发编程", 3, "0.5", "使用 Rust 编写了高并发网关服务", null)));
        assertThatCode(() -> validator.validate(result,
                "使用 Rust 编写了高并发网关服务，QPS 提升 40%", null))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsMissingSourceRefsForServerBackfill() {
        PostAbilityExtractionResult result = result(List.of(
                claim("Java", 3, "0.5", "负责Java后端开发", null)));
        assertThatCode(() -> validator.validate(result, "负责Java后端开发",
                List.of("source:JD_IMPORT:5")))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsQualificationClaimSoItCannotPollutePostAbilityModel() {
        PostAbilityClaim qualification = claim("本科及以上学历", 3, "0.8", "本科及以上学历", null);
        qualification.setAbilityType("QUALIFICATION");

        assertThatThrownBy(() -> validator.validate(result(List.of(qualification)),
                "任职要求：本科及以上学历，2年以上Node.js开发经验", null))
                .isInstanceOf(AiOutputValidationException.class)
                .hasMessageContaining("准入条件");
    }

    @Test
    void validatesClaimsIndividuallyWithoutDiscardingGroundedClaims() {
        PostAbilityClaim grounded = claim("Java", 3, "0.5", "负责Java后端开发", null);
        grounded.setEvidenceAnchor("Java");
        PostAbilityClaim invalid = claim("Kubernetes", 3, "0.5", "不存在的证据", null);
        invalid.setEvidenceAnchor("Kubernetes");

        PostAbilityExtractionValidator.ValidationResult validation = validator.validateIndividually(
                List.of(grounded, invalid), "负责Java后端开发", List.of("source:JD_IMPORT:5"));

        assertThat(validation.acceptedClaims()).containsExactly(grounded);
        assertThat(validation.rejectedClaims()).hasSize(1);
        assertThat(validation.rejectedClaims().get(0).claim()).isSameAs(invalid);
        assertThat(validation.rejectedClaims().get(0).reason()).contains("evidenceText");
    }

    @Test
    void rejectsUnknownAbilityWithoutAnExactEvidenceAnchor() {
        PostAbilityClaim claim = claim("Rust高性能并发编程", 3, "0.5", "使用 Rust 编写网关服务", null);
        claim.setEvidenceAnchor("并发编程");

        PostAbilityExtractionValidator.ValidationResult validation = validator.validateIndividually(
                List.of(claim), "使用 Rust 编写网关服务", List.of("source:JD_IMPORT:5"));

        assertThat(validation.acceptedClaims()).isEmpty();
        assertThat(validation.rejectedClaims()).singleElement()
                .extracting(PostAbilityExtractionValidator.RejectedClaim::reason)
                .asString().contains("evidenceAnchor");
    }
}
