package com.example.matching.service.governance;

import com.example.matching.entity.governance.GovernanceFilterRule;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface GovernanceFilterRuleService {
    String POST_JD = "POST_JD";
    String PERSON_ABILITY = "PERSON_ABILITY";

    List<GovernanceFilterRule> activeRules(String scope);

    GovernanceFilterRuleEngine.PostNoiseResult evaluatePost(String text);

    GovernanceFilterRuleEngine.PersonFilterResult evaluatePersonAbility(String abilityName);

    List<GovernanceFilterRule> list(String scope, String reviewStatus);

    List<String> sampleTexts(String scope, int limit);

    GovernanceFilterRule save(GovernanceFilterRule rule, Long operatorId);

    void deleteCustom(Long id, Long operatorId);

    CompletableFuture<Integer> generateAiSuggestions(String scope, List<String> samples, Long operatorId);

    void reviewSuggestion(Long id, boolean approve, Long operatorId);
}
