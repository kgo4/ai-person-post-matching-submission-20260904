package com.example.matching.service.governance;

import com.example.matching.entity.governance.GovernanceFilterRule;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class GovernanceFilterRuleEngine {

    public PostNoiseResult evaluatePost(String text, List<GovernanceFilterRule> rules, int threshold) {
        String content = text == null ? "" : text;
        int score = 0;
        List<RuleHit> hits = new ArrayList<>();
        for (GovernanceFilterRule rule : rules) {
            if (!isEnabled(rule) || !matches(rule, content)) continue;
            int contribution = rule.getWeight() == null ? 0 : Math.max(0, rule.getWeight());
            score = Math.min(100, score + contribution);
            hits.add(new RuleHit(rule.getId(), rule.getRuleName(), contribution));
        }
        return new PostNoiseResult(score, score >= Math.max(0, threshold), hits);
    }

    public PersonFilterResult shouldFilterPersonAbility(String abilityName, List<GovernanceFilterRule> rules) {
        String value = abilityName == null ? "" : abilityName.trim();
        for (GovernanceFilterRule rule : rules) {
            if (!isEnabled(rule) || !matches(rule, value)) continue;
            return new PersonFilterResult(true, rule.getId(), rule.getRuleName());
        }
        return new PersonFilterResult(false, null, null);
    }

    private boolean isEnabled(GovernanceFilterRule rule) {
        return rule != null && rule.getEnabled() != null && rule.getEnabled() == 1
                && "APPROVED".equalsIgnoreCase(rule.getReviewStatus());
    }

    private boolean matches(GovernanceFilterRule rule, String value) {
        if (!StringUtils.hasText(rule.getPatternValue())) return false;
        String pattern = rule.getPatternValue();
        if ("REGEX".equalsIgnoreCase(rule.getRuleType())) {
            try {
                return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(value).find();
            } catch (RuntimeException ignored) {
                return false;
            }
        }
        if ("LENGTH".equalsIgnoreCase(rule.getRuleType())) {
            try {
                return value.length() < Integer.parseInt(pattern.trim());
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        if ("SECTION_MISSING".equalsIgnoreCase(rule.getRuleType())) {
            return rule.getPatternValue().split("\\|").length > 0
                    && java.util.Arrays.stream(rule.getPatternValue().split("\\|"))
                    .map(String::trim).filter(s -> !s.isEmpty()).noneMatch(value::contains);
        }
        if ("EXACT".equalsIgnoreCase(rule.getRuleType())) {
            return value.trim().equalsIgnoreCase(pattern.trim());
        }
        return value.toLowerCase(Locale.ROOT).contains(pattern.toLowerCase(Locale.ROOT));
    }

    public record RuleHit(Long ruleId, String ruleName, int contribution) {}
    public record PostNoiseResult(int score, boolean blocked, List<RuleHit> hits) {}
    public record PersonFilterResult(boolean filtered, Long ruleId, String ruleName) {}
}
