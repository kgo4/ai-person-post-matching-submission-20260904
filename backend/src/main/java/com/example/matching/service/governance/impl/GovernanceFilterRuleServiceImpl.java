package com.example.matching.service.governance.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.entity.governance.GovernanceFilterRule;
import com.example.matching.mapper.governance.GovernanceFilterRuleMapper;
import com.example.matching.service.governance.GovernanceFilterRuleEngine;
import com.example.matching.service.governance.GovernanceFilterRuleService;
import com.example.matching.infrastructure.llm.EnterpriseChatLanguageModel;
import com.example.matching.infrastructure.llm.LlmResponseParser;
import com.example.matching.agent.json.JsonFewShotRegistry;
import com.example.matching.port.evolution.MarketJdQueryPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class GovernanceFilterRuleServiceImpl implements GovernanceFilterRuleService {
    private static final String AI_SCENE = "GOVERNANCE_FILTER_RULE_SUGGESTION";
    private final GovernanceFilterRuleMapper mapper;
    private final EnterpriseChatLanguageModel chatLanguageModel;
    private final ObjectMapper objectMapper;
    private final LlmResponseParser llmResponseParser;
    private final MarketJdQueryPort marketJdQueryPort;
    private final GovernanceFilterRuleEngine engine = new GovernanceFilterRuleEngine();
    private final Map<String, List<GovernanceFilterRule>> cache = new ConcurrentHashMap<>();

    @Override
    public List<GovernanceFilterRule> activeRules(String scope) {
        try {
            List<GovernanceFilterRule> rules = mapper.selectList(Wrappers.<GovernanceFilterRule>lambdaQuery()
                    .eq(GovernanceFilterRule::getScope, scope)
                    .eq(GovernanceFilterRule::getEnabled, 1)
                    .eq(GovernanceFilterRule::getReviewStatus, "APPROVED")
                    .orderByAsc(GovernanceFilterRule::getId));
            cache.put(scope, rules);
            return rules;
        } catch (RuntimeException ex) {
            return cache.getOrDefault(scope, List.of());
        }
    }

    @Override
    public GovernanceFilterRuleEngine.PostNoiseResult evaluatePost(String text) {
        return engine.evaluatePost(text, activeRules(POST_JD), 70);
    }

    @Override
    public GovernanceFilterRuleEngine.PersonFilterResult evaluatePersonAbility(String abilityName) {
        return engine.shouldFilterPersonAbility(abilityName, activeRules(PERSON_ABILITY));
    }

    @Override
    public List<GovernanceFilterRule> list(String scope, String reviewStatus) {
        return mapper.selectList(Wrappers.<GovernanceFilterRule>lambdaQuery()
                .eq(scope != null && !scope.isBlank(), GovernanceFilterRule::getScope, scope)
                .eq(reviewStatus != null && !reviewStatus.isBlank(), GovernanceFilterRule::getReviewStatus, reviewStatus)
                .orderByDesc(GovernanceFilterRule::getUpdatedTime));
    }

    @Override
    public List<String> sampleTexts(String scope, int limit) {
        if (POST_JD.equals(scope)) {
            return marketJdQueryPort.findFilteredTexts(limit);
        }
        return List.of();
    }

    @Override
    @Transactional
    public GovernanceFilterRule save(GovernanceFilterRule rule, Long operatorId) {
        if (rule.getScope() == null || (!POST_JD.equals(rule.getScope()) && !PERSON_ABILITY.equals(rule.getScope()))) {
            throw new IllegalArgumentException("规则作用域无效");
        }
        if (rule.getWeight() == null || rule.getWeight() < 0 || rule.getWeight() > 100) {
            throw new IllegalArgumentException("规则权重必须在0到100之间");
        }
        rule.setUpdatedBy(operatorId);
        rule.setSource(rule.getSource() == null ? "CUSTOM" : rule.getSource());
        rule.setReviewStatus("APPROVED");
        rule.setEnabled(rule.getEnabled() == null ? 1 : rule.getEnabled());
        if (rule.getId() == null) mapper.insert(rule); else mapper.updateById(rule);
        cache.remove(rule.getScope());
        return rule;
    }

    @Override
    @Transactional
    public void deleteCustom(Long id, Long operatorId) {
        GovernanceFilterRule rule = mapper.selectById(id);
        if (rule == null) return;
        if ("SYSTEM".equalsIgnoreCase(rule.getSource())) {
            throw new IllegalArgumentException("系统内置规则不能删除，请停用规则");
        }
        mapper.deleteById(id);
        cache.remove(rule.getScope());
    }

    @Override
    @org.springframework.scheduling.annotation.Async("aiTaskExecutor")
    @Transactional
    public CompletableFuture<Integer> generateAiSuggestions(String scope, List<String> samples, Long operatorId) {
        if (samples == null || samples.isEmpty()) return CompletableFuture.completedFuture(0);
        String prompt = "场景标识：" + AI_SCENE + "\n" + JsonFewShotRegistry.forScene(AI_SCENE)
                + "\n你是数据治理规则分析器。请仅根据下面提供的被过滤样本，识别可重复验证的清洗模式，提出最多5条候选规则。推荐依据必须来自样本中实际出现的重复关键词、长度异常、缺少职责段落或重复宣传话术；不得凭空编造。"
                + "\n只允许 ruleType=KEYWORD、REGEX、LENGTH、SECTION_MISSING、EXACT。"
                + "\n必须严格返回 JSON 数组，禁止 Markdown、解释文字、代码围栏。每项必须完整包含："
                + "ruleName(string), ruleType(string), patternValue(string), weight(integer 0-100), description(string), aiRationale(string)。"
                + "\npatternValue 必须是样本中可定位的关键词、合法正则、长度数字或段落关键词；无法证明的规则不要返回。样本：\n"
                + String.join("\n---\n", samples.stream().filter(s -> s != null && !s.isBlank()).limit(50).toList());
        try {
            JsonNode root = parseSuggestions(chatLanguageModel.chat(prompt));
            if (root == null || !root.isArray()) return CompletableFuture.completedFuture(0);
            int count = 0;
            for (JsonNode item : root) {
                if (!isValidSuggestion(item)) continue;
                GovernanceFilterRule rule = new GovernanceFilterRule();
                rule.setScope(scope);
                rule.setRuleType(item.path("ruleType").asText("KEYWORD"));
                rule.setRuleName(item.path("ruleName").asText("AI建议规则"));
                rule.setPatternValue(item.path("patternValue").asText());
                rule.setWeight(Math.max(0, Math.min(100, item.path("weight").asInt(10))));
                rule.setDescription(item.path("description").asText());
                rule.setAiRationale(item.path("aiRationale").asText());
                rule.setSource("AI_SUGGESTION");
                rule.setReviewStatus("PENDING");
                rule.setEnabled(0);
                rule.setCreatedBy(operatorId);
                if (rule.getPatternValue().isBlank()) continue;
                mapper.insert(rule);
                count++;
            }
            return CompletableFuture.completedFuture(count);
        } catch (Exception firstFailure) {
            // AI 返回格式不稳定时只重试一次，仍使用统一 JSON 提取器，避免建议被静默丢弃。
            try {
                String retryPrompt = prompt + "\n上一次输出无法解析。请重新生成，严格遵守上述 JSON 数组结构和允许的 ruleType。";
                JsonNode root = parseSuggestions(chatLanguageModel.chat(retryPrompt));
                int count = 0;
                for (JsonNode item : root) {
                    String pattern = item.path("patternValue").asText("").trim();
                    if (!isValidSuggestion(item) || pattern.isBlank()) continue;
                    GovernanceFilterRule rule = new GovernanceFilterRule();
                    rule.setScope(scope);
                    rule.setRuleType(item.path("ruleType").asText("KEYWORD"));
                    rule.setRuleName(item.path("ruleName").asText("AI建议规则"));
                    rule.setPatternValue(pattern);
                    rule.setWeight(Math.max(0, Math.min(100, item.path("weight").asInt(10))));
                    rule.setDescription(item.path("description").asText(""));
                    rule.setAiRationale(item.path("aiRationale").asText(""));
                    rule.setSource("AI_SUGGESTION");
                    rule.setReviewStatus("PENDING");
                    rule.setEnabled(0);
                    rule.setCreatedBy(operatorId);
                    mapper.insert(rule);
                    count++;
                }
                return CompletableFuture.completedFuture(count);
            } catch (Exception retryFailure) {
                throw new IllegalStateException("清洗规则 AI 推荐解析失败，已重试一次", retryFailure);
            }
        }
    }

    private JsonNode parseSuggestions(String response) {
        JsonNode root;
        try {
            root = llmResponseParser.parseObject(response);
        } catch (Exception ex) {
            throw new IllegalArgumentException("AI 建议不是合法 JSON", ex);
        }
        if (root.isObject() && root.has("rules")) root = root.get("rules");
        if (!root.isArray()) throw new IllegalArgumentException("AI 建议根节点必须为数组");
        return root;
    }

    private boolean isValidSuggestion(JsonNode item) {
        if (item == null || !item.isObject()) return false;
        String type = item.path("ruleType").asText("").trim().toUpperCase();
        String pattern = item.path("patternValue").asText("").trim();
        int weight = item.path("weight").asInt(-1);
        return !item.path("ruleName").asText("").isBlank()
                && !pattern.isBlank()
                && java.util.Set.of("KEYWORD", "REGEX", "LENGTH", "SECTION_MISSING", "EXACT").contains(type)
                && weight >= 0 && weight <= 100;
    }

    @Override
    @Transactional
    public void reviewSuggestion(Long id, boolean approve, Long operatorId) {
        GovernanceFilterRule rule = mapper.selectById(id);
        if (rule == null || !"AI_SUGGESTION".equalsIgnoreCase(rule.getSource())) {
            throw new IllegalArgumentException("AI建议不存在");
        }
        rule.setReviewStatus(approve ? "APPROVED" : "REJECTED");
        rule.setEnabled(approve ? 1 : 0);
        rule.setUpdatedBy(operatorId);
        mapper.updateById(rule);
        cache.remove(rule.getScope());
    }
}
