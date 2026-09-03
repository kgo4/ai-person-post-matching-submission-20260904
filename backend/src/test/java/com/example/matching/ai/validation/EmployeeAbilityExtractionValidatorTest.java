package com.example.matching.ai.validation;

import com.example.matching.agent.dto.person.PersonAbilityClaim;
import com.example.matching.agent.dto.person.PersonAbilityExtractionResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmployeeAbilityExtractionValidatorTest {

    private final EmployeeAbilityExtractionValidator validator = new EmployeeAbilityExtractionValidator();

    private PersonAbilityClaim claim(String abilityName, Integer level, String evidence, List<String> sourceRefs) {
        PersonAbilityClaim claim = new PersonAbilityClaim();
        claim.setAbilityName(abilityName);
        claim.setMasteryLevel(level);
        claim.setEvidenceText(evidence);
        claim.setSourceRefs(sourceRefs);
        return claim;
    }

    @Test
    void acceptsClaimWithEvidenceLocatableInSourceText() {
        PersonAbilityExtractionResult result = new PersonAbilityExtractionResult();
        result.setClaims(List.of(claim("Java", 4, "负责Java后端开发", null)));
        assertThatCode(() -> validator.validate(result, "负责Java后端开发，参与三个项目", null))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsOcrEvidenceWhenOnlyTypographyDiffersFromSourceText() {
        PersonAbilityExtractionResult result = new PersonAbilityExtractionResult();
        result.setClaims(List.of(claim("Java", 4, "熟悉Java、Spring Boot及MySQL开发", null)));

        assertThatCode(() -> validator.validate(result,
                "熟悉 Java, Spring Boot 及 MySQL 开发", null, true))
                .doesNotThrowAnyException();
    }

    @Test
    void keepsStrictEvidenceMatchingForNonOcrSource() {
        PersonAbilityExtractionResult result = new PersonAbilityExtractionResult();
        result.setClaims(List.of(claim("Java", 4, "熟悉Java、Spring Boot及MySQL开发", null)));

        assertThatThrownBy(() -> validator.validate(result,
                "熟悉 Java, Spring Boot 及 MySQL 开发", null, false))
                .isInstanceOf(AiOutputValidationException.class)
                .hasMessageContaining("evidenceText");
    }

    @Test
    void rejectsEvidenceNotLocatableEvenWithControlledRefs() {
        // Task2：证据必须能在原文中定位；即使 sourceRefs 合法也不能旁路证据校验
        PersonAbilityExtractionResult result = new PersonAbilityExtractionResult();
        result.setClaims(List.of(claim("Java", 4, "Java 开发经验丰富（原文无此句）", List.of("source:RESUME_PARSE:8"))));
        assertThatThrownBy(() -> validator.validate(result, "负责Java后端开发，参与三个项目",
                List.of("source:RESUME_PARSE:8")))
                .isInstanceOf(AiOutputValidationException.class)
                .hasMessageContaining("evidenceText");
    }

    @Test
    void acceptsNewAbilityNameWithLocatableContinuousEvidence() {
        // Task2：开放词表——系统不存在的新能力名称，只要原文包含连续证据，仍通过校验
        PersonAbilityExtractionResult result = new PersonAbilityExtractionResult();
        result.setClaims(List.of(claim("Rust高性能并发编程", 3,
                "使用 Rust 编写了高并发网关服务", null)));
        assertThatCode(() -> validator.validate(result,
                "使用 Rust 编写了高并发网关服务，QPS 提升 40%", null))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsSourceRefsOutsideControlledSet() {
        // Task2：模型 sourceRefs 必须 ⊆ 服务端受控引用集合
        PersonAbilityExtractionResult result = new PersonAbilityExtractionResult();
        result.setClaims(List.of(claim("Java", 4, "负责Java后端开发", List.of("source:OTHER:99"))));
        assertThatThrownBy(() -> validator.validate(result, "负责Java后端开发",
                List.of("source:RESUME_PARSE:8")))
                .isInstanceOf(AiOutputValidationException.class)
                .hasMessageContaining("sourceRefs");
    }

    @Test
    void acceptsMissingSourceRefsForServerBackfill() {
        // Task2：模型没有引用时不报错，由服务端随后统一回填标准引用
        PersonAbilityExtractionResult result = new PersonAbilityExtractionResult();
        result.setClaims(List.of(claim("Java", 4, "负责Java后端开发", null)));
        assertThatCode(() -> validator.validate(result, "负责Java后端开发",
                List.of("source:RESUME_PARSE:8")))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingAbilityName() {
        PersonAbilityExtractionResult result = new PersonAbilityExtractionResult();
        result.setClaims(List.of(claim(null, 4, "负责Java后端开发", null)));
        assertThatThrownBy(() -> validator.validate(result, "负责Java后端开发", null))
                .isInstanceOf(AiOutputValidationException.class)
                .hasMessageContaining("abilityName");
    }

    @Test
    void rejectsLevelOutOfRange() {
        PersonAbilityExtractionResult result = new PersonAbilityExtractionResult();
        result.setClaims(List.of(claim("Java", 9, "负责Java后端开发", null)));
        assertThatThrownBy(() -> validator.validate(result, "负责Java后端开发", null))
                .isInstanceOf(AiOutputValidationException.class)
                .hasMessageContaining("masteryLevel");
    }

    @Test
    void rejectsEvidenceNotLocatableAndUncontrolledRefs() {
        PersonAbilityExtractionResult result = new PersonAbilityExtractionResult();
        result.setClaims(List.of(claim("Java", 4, "无法定位的编造证据", List.of("source:OTHER:99"))));
        assertThatThrownBy(() -> validator.validate(result, "负责Java后端开发", List.of("source:RESUME_PARSE:8")))
                .isInstanceOf(AiOutputValidationException.class)
                .hasMessageContaining("evidenceText");
    }

    @Test
    void rejectsEmptyClaims() {
        PersonAbilityExtractionResult result = new PersonAbilityExtractionResult();
        result.setClaims(List.of());
        assertThatThrownBy(() -> validator.validate(result, "文本", null))
                .isInstanceOf(AiOutputValidationException.class)
                .hasMessageContaining("claims");
    }
}
