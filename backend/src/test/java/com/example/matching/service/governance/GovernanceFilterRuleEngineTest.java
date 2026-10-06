package com.example.matching.service.governance;

import com.example.matching.entity.governance.GovernanceFilterRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GovernanceFilterRuleEngineTest {

    private final GovernanceFilterRuleEngine engine = new GovernanceFilterRuleEngine();

    @Test
    void calculatesPostNoiseScoreFromEnabledConfiguredRules() {
        GovernanceFilterRule rule = rule("POST_JD", "KEYWORD", "福利待遇", 10);
        GovernanceFilterRule shortText = rule("POST_JD", "LENGTH", "100", 30);

        GovernanceFilterRuleEngine.PostNoiseResult result =
                engine.evaluatePost("福利待遇", List.of(rule, shortText), 70);

        assertEquals(40, result.score());
        assertFalse(result.blocked());
        assertEquals(2, result.hits().size());
    }

    @Test
    void filtersOnlyMatchingPersonAbilityRule() {
        GovernanceFilterRule rule = rule("PERSON_ABILITY", "KEYWORD", "chatgpt", 0);

        assertTrue(engine.shouldFilterPersonAbility("ChatGPT", List.of(rule)).filtered());
        assertFalse(engine.shouldFilterPersonAbility("大模型应用开发", List.of(rule)).filtered());
    }

    private GovernanceFilterRule rule(String scope, String type, String value, int weight) {
        GovernanceFilterRule rule = new GovernanceFilterRule();
        rule.setScope(scope);
        rule.setRuleType(type);
        rule.setPatternValue(value);
        rule.setWeight(weight);
        rule.setEnabled(1);
        rule.setReviewStatus("APPROVED");
        return rule;
    }
}
