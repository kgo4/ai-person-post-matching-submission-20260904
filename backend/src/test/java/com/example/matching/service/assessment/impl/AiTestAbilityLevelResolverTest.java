package com.example.matching.service.assessment.impl;

import com.example.matching.entity.workflow.PersonAbilityClaimGroup;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 测试逐项核验等级确定性归并器测试。
 * <p>
 * 覆盖 spec §4.3 验收：单题封顶 L2、多题各档、封顶 L3、无覆盖能力忽略。
 */
class AiTestAbilityLevelResolverTest {

    private final AiTestAbilityLevelResolver resolver =
            new AiTestAbilityLevelResolver(new ObjectMapper());

    private PersonAbilityClaimGroup group(long id, Long tagId, String name) {
        PersonAbilityClaimGroup g = new PersonAbilityClaimGroup();
        g.setId(id);
        g.setCanonicalTagId(tagId);
        g.setNormalizedAbilityName(name);
        return g;
    }

    // ── levelFor 纯函数 ──

    @Test
    void levelFor_singleQuestion_fullScore_cappedAt2() {
        assertThat(AiTestAbilityLevelResolver.levelFor(1, 1.0)).isEqualTo(2);
        assertThat(AiTestAbilityLevelResolver.levelFor(1, 0.8)).isEqualTo(2);
    }

    @Test
    void levelFor_singleQuestion_wrong_is1() {
        assertThat(AiTestAbilityLevelResolver.levelFor(1, 0.0)).isEqualTo(1);
        assertThat(AiTestAbilityLevelResolver.levelFor(1, 0.5)).isEqualTo(1);
    }

    @Test
    void levelFor_multiQuestion_highRate_is3() {
        assertThat(AiTestAbilityLevelResolver.levelFor(3, 0.9)).isEqualTo(3);
        assertThat(AiTestAbilityLevelResolver.levelFor(2, 0.75)).isEqualTo(3);
    }

    @Test
    void levelFor_multiQuestion_medium_is2() {
        assertThat(AiTestAbilityLevelResolver.levelFor(3, 0.6)).isEqualTo(2);
        assertThat(AiTestAbilityLevelResolver.levelFor(2, 0.5)).isEqualTo(2);
    }

    @Test
    void levelFor_multiQuestion_low_is1() {
        assertThat(AiTestAbilityLevelResolver.levelFor(3, 0.4)).isEqualTo(1);
    }

    @Test
    void levelFor_cappedAtSourceCeiling3() {
        assertThat(AiTestAbilityLevelResolver.levelFor(5, 1.0)).isEqualTo(3);
    }

    // ── resolve 归并（字符串 JSON 输入 + groups） ──

    @Test
    void resolve_groupsByTagAndMapsLevels() {
        List<PersonAbilityClaimGroup> groups = List.of(
                group(10L, 1L, "Java"), group(20L, 2L, "Redis"));
        String questions = "[{\"tagId\":1,\"score\":10},{\"tagId\":1,\"score\":10},"
                + "{\"tagId\":2,\"score\":10},{\"tagId\":99,\"score\":10}]";
        String evaluation = "{\"questionResults\":[{\"questionIndex\":0,\"score\":10},"
                + "{\"questionIndex\":1,\"score\":7},{\"questionIndex\":2,\"score\":0},"
                + "{\"questionIndex\":3,\"score\":10}]}";

        List<AiTestAbilityLevelResolver.ResolvedAbility> resolved =
                resolver.resolve(questions, evaluation, groups, 3);

        assertThat(resolved).hasSize(2);
        AiTestAbilityLevelResolver.ResolvedAbility java = resolved.stream()
                .filter(r -> r.abilityName().equals("Java")).findFirst().orElseThrow();
        // Java: 17/20 = 0.85 -> L3
        assertThat(java.level()).isEqualTo(3);
        assertThat(java.questionIndexes()).containsExactly(0, 1);
        assertThat(java.tagId()).isEqualTo(1L);
        AiTestAbilityLevelResolver.ResolvedAbility redis = resolved.stream()
                .filter(r -> r.abilityName().equals("Redis")).findFirst().orElseThrow();
        // Redis: 0/10 = 0 -> L1
        assertThat(redis.level()).isEqualTo(1);
    }

    @Test
    void resolve_questionWithoutGroup_ignored() {
        List<PersonAbilityClaimGroup> groups = List.of(group(10L, 1L, "Java"));
        String questions = "[{\"tagId\":999,\"score\":10}]";
        String evaluation = "{\"questionResults\":[{\"questionIndex\":0,\"score\":10}]}";
        assertThat(resolver.resolve(questions, evaluation, groups, 3)).isEmpty();
    }

    @Test
    void resolve_singleQuestionFullScore_isLevel2() {
        List<PersonAbilityClaimGroup> groups = List.of(group(10L, 1L, "Java"));
        String questions = "[{\"tagId\":1,\"score\":10}]";
        String evaluation = "{\"questionResults\":[{\"questionIndex\":0,\"score\":10}]}";
        List<AiTestAbilityLevelResolver.ResolvedAbility> resolved =
                resolver.resolve(questions, evaluation, groups, 3);
        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0).level()).isEqualTo(2);
        assertThat(resolved.get(0).evidenceText()).contains("L2");
    }
}
