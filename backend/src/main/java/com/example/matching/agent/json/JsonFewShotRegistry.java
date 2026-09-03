package com.example.matching.agent.json;

import java.util.Map;

/** 第 1 层 few-shot 标准件：每场景 1 正例 + 1 反例。 */
public final class JsonFewShotRegistry {

    private JsonFewShotRegistry() {
    }

    private static final Map<String, String> SCENES = Map.of(
            "EMPLOYEE_ABILITY_EXTRACTION",
            "GOOD: {\"claims\":[{\"abilityName\":\"Java\",\"masteryLevel\":4,\"evidenceText\":\"...\"}]}\n"
                    + "BAD:  Here is the result: ```json {\"claims\":[{\"abilityName\":\"Java\",\"masteryLevel\":4,}]}``` "
                    + "(wrong: markdown fence, trailing comma, leading prose)",
            "MATCHING_ANALYSIS",
            "GOOD: {\"matched\":true,\"score\":82.5,\"reasons\":[\"skill overlap\"]}\n"
                    + "BAD:  matched true, score 82.5 (wrong: not JSON)",
            "POST_EVOLUTION",
            "GOOD: {\"suggestions\":[{\"abilityName\":\"向量检索\",\"action\":\"UPDATE_LEVEL\",\"newLevel\":4,\"reason\":\"证据显示岗位要求提升\",\"evidenceRef\":0}]}\n"
                    + "BAD: {\"suggestions\":[{\"abilityName\":\"未知能力\",\"action\":\"ADD\",\"evidenceRef\":99}]} (wrong: evidenceRef must exist and ability must be grounded)",
            "POST_ABILITY_EXTRACTION",
            "GOOD: {\"claims\":[{\"abilityName\":\"Spring Boot\",\"level\":4,\"weight\":0.8,\"evidenceText\":\"负责Spring Boot服务开发\"}]}\n"
                    + "BAD: {\"ability\":\"Spring Boot\"} (wrong: use claims schema)",
            "LEARNING_PATH",
            "GOOD: {\"steps\":[{\"abilityName\":\"Kafka\",\"title\":\"消息可靠性\",\"resources\":[]}]}\n"
                    + "BAD: {\"masteryLevel\":5,\"abilityName\":\"Kafka\"} (wrong: learning Agent cannot change ability levels)",
            "PMS_ABILITY_ANALYSIS",
            "GOOD: {\"claims\":[{\"abilityName\":\"项目管理\",\"masteryLevel\":3,\"evidenceText\":\"项目材料中的可定位证据\"}]}\n"
                    + "BAD: {\"claims\":[{\"abilityName\":\"凭空能力\",\"masteryLevel\":5}]} (wrong: no project evidence)",
            "EVIDENCE_GOVERNANCE",
            "GOOD: {\"decision\":\"PASS\",\"reasonCodes\":[],\"evidenceRefs\":[\"source:JD:1\"]}\n"
                    + "BAD: {\"decision\":\"PASS\",\"evidenceRefs\":[\"source:UNKNOWN:9\"]} (wrong: evidence must be in context)",
            "GOVERNANCE_FILTER_RULE_SUGGESTION",
            "GOOD: [{\"ruleName\":\"福利宣传话术\",\"ruleType\":\"KEYWORD\",\"patternValue\":\"五险一金\",\"weight\":20,\"description\":\"识别福利类噪声\",\"aiRationale\":\"样本中高频出现\"}]\n"
                    + "BAD: {\"suggestions\":[{\"pattern\":\"未知规则\"}]} (wrong: root must be array and fields must match filter-rule contract)"
    );

    public static String forScene(String scene) {
        return SCENES.getOrDefault(scene, generic());
    }

    private static String generic() {
        return "GOOD: {\"key\":\"value\"}\n"
                + "BAD:  ```json {\"key\":\"value\"}``` (wrong: markdown fence)";
    }
}
