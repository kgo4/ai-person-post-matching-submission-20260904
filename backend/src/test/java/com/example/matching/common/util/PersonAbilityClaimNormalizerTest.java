package com.example.matching.common.util;

import com.example.matching.agent.dto.person.PersonAbilityClaim;
import com.example.matching.agent.dto.person.PersonAbilityExtractionResult;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PersonAbilityClaimNormalizer 单元测试。
 * <p>
 * 规范格式：PersonAbilityExtractionResult.claims[]（abilityName + masteryLevel 1-5）。
 * 兼容格式：abilities/abilityClaims/skills 数组（tagName -> abilityName, level -> masteryLevel）。
 * 无效声明（缺名称 / 缺等级 / 等级越界 / 等级非数字）必须被剔除，claims 永不为 null。
 * normalize 对非法 JSON 抛 JsonProcessingException，由调用方决定降级策略。
 */
class PersonAbilityClaimNormalizerTest {

    private PersonAbilityClaimNormalizer normalizer;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        normalizer = new PersonAbilityClaimNormalizer(mapper);
    }

    @Test
    @DisplayName("规范格式 claims[] 原样保留")
    void canonicalClaimsParsedAndPreserved() throws Exception {
        String json = """
                {"claims":[{"abilityName":"Java","masteryLevel":4,"evidenceText":"三年后端经验","sourceRefs":["source:RESUME_PARSE:1"]}]}
                """;
        PersonAbilityExtractionResult result = normalizer.normalize(json);
        assertThat(result).isNotNull();
        assertThat(result.getClaims()).isNotNull().hasSize(1);
        PersonAbilityClaim claim = result.getClaims().get(0);
        assertThat(claim.getAbilityName()).isEqualTo("Java");
        assertThat(claim.getMasteryLevel()).isEqualTo(4);
        assertThat(claim.getEvidenceText()).isEqualTo("三年后端经验");
        assertThat(claim.getSourceRefs()).containsExactly("source:RESUME_PARSE:1");
    }

    @Test
    @DisplayName("空 claims[] 返回非 null 空列表")
    void canonicalEmptyClaimsIsNonNull() throws Exception {
        PersonAbilityExtractionResult result = normalizer.normalize("{\"claims\":[]}");
        assertThat(result).isNotNull();
        assertThat(result.getClaims()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("旧格式 abilities[] 映射 tagName->abilityName, level->masteryLevel")
    void legacyAbilitiesMapped() throws Exception {
        String json = """
                {"abilities":[{"tagName":"Java","level":4}]}
                """;
        PersonAbilityExtractionResult result = normalizer.normalize(json);
        assertThat(result).isNotNull();
        assertThat(result.getClaims()).isNotNull().hasSize(1);
        PersonAbilityClaim claim = result.getClaims().get(0);
        assertThat(claim.getAbilityName()).isEqualTo("Java");
        assertThat(claim.getMasteryLevel()).isEqualTo(4);
    }

    @Test
    @DisplayName("旧格式支持备选字段名（abilityName/name/skillName, claimedLevel/currentLevel, 字符串数字等级）")
    void legacyAlternateFieldNamesMapped() throws Exception {
        String json = """
                {"abilityClaims":[
                    {"name":"Python","currentLevel":"3"},
                    {"skillName":"Go","claimedLevel":2}
                ]}
                """;
        PersonAbilityExtractionResult result = normalizer.normalize(json);
        assertThat(result).isNotNull();
        assertThat(result.getClaims()).hasSize(2);
        assertThat(result.getClaims().get(0).getAbilityName()).isEqualTo("Python");
        assertThat(result.getClaims().get(0).getMasteryLevel()).isEqualTo(3);
        assertThat(result.getClaims().get(1).getAbilityName()).isEqualTo("Go");
        assertThat(result.getClaims().get(1).getMasteryLevel()).isEqualTo(2);
    }

    @Test
    @DisplayName("旧格式 skills[] 同样支持")
    void legacySkillsArraySupported() throws Exception {
        String json = """
                {"skills":[{"tagName":"SQL","level":2}]}
                """;
        PersonAbilityExtractionResult result = normalizer.normalize(json);
        assertThat(result.getClaims()).hasSize(1);
        assertThat(result.getClaims().get(0).getAbilityName()).isEqualTo("SQL");
        assertThat(result.getClaims().get(0).getMasteryLevel()).isEqualTo(2);
    }

    @Test
    @DisplayName("缺少能力名称的声明被剔除")
    void missingAbilityNameDropped() throws Exception {
        String json = """
                {"claims":[
                    {"masteryLevel":4,"evidenceText":"有证据"},
                    {"abilityName":"Java","masteryLevel":3}
                ]}
                """;
        PersonAbilityExtractionResult result = normalizer.normalize(json);
        assertThat(result.getClaims()).isNotNull().hasSize(1);
        assertThat(result.getClaims().get(0).getAbilityName()).isEqualTo("Java");
    }

    @Test
    @DisplayName("缺少等级的声明被剔除")
    void missingLevelDropped() throws Exception {
        String json = """
                {"abilities":[
                    {"tagName":"Java","evidenceText":"无等级"},
                    {"tagName":"Go","level":2}
                ]}
                """;
        PersonAbilityExtractionResult result = normalizer.normalize(json);
        assertThat(result.getClaims()).hasSize(1);
        assertThat(result.getClaims().get(0).getAbilityName()).isEqualTo("Go");
    }

    @Test
    @DisplayName("非数字等级被剔除")
    void nonNumericLevelDropped() throws Exception {
        String json = """
                {"abilities":[
                    {"tagName":"Java","level":"abc"},
                    {"tagName":"Go","level":"4"}
                ]}
                """;
        PersonAbilityExtractionResult result = normalizer.normalize(json);
        assertThat(result.getClaims()).hasSize(1);
        assertThat(result.getClaims().get(0).getAbilityName()).isEqualTo("Go");
        assertThat(result.getClaims().get(0).getMasteryLevel()).isEqualTo(4);
    }

    @Test
    @DisplayName("等级越界（0 或 6+）被剔除，领域范围为 1-5")
    void levelOutOfDomainRangeDropped() throws Exception {
        String json = """
                {"claims":[
                    {"abilityName":"Low","masteryLevel":0},
                    {"abilityName":"High","masteryLevel":6},
                    {"abilityName":"Ok","masteryLevel":5}
                ]}
                """;
        PersonAbilityExtractionResult result = normalizer.normalize(json);
        assertThat(result.getClaims()).hasSize(1);
        assertThat(result.getClaims().get(0).getAbilityName()).isEqualTo("Ok");
        assertThat(result.getClaims().get(0).getMasteryLevel()).isEqualTo(5);
    }

    @Test
    @DisplayName("空对象 JSON 返回非 null 空 claims；非法 JSON 抛异常")
    void emptyObjectYieldsEmptyClaimsAndInvalidJsonThrows() {
        assertThatThrownBy(() -> normalizer.normalize("not-json{")).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("空对象 JSON 返回非 null 空 claims")
    void emptyObjectYieldsEmptyClaims() throws Exception {
        PersonAbilityExtractionResult result = normalizer.normalize("{\"foo\":1}");
        assertThat(result).isNotNull();
        assertThat(result.getClaims()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("旧格式缺失证据时保留原服务兜底文案")
    void legacyEvidenceFallbackApplied() throws Exception {
        String json = """
                {"abilities":[{"tagName":"Java","level":4}]}
                """;
        PersonAbilityExtractionResult result = normalizer.normalize(json);
        assertThat(result.getClaims().get(0).getEvidenceText()).isEqualTo("从简历解析导入：Java");
    }

    @Test
    @DisplayName("claims 中存在 null 元素时安全剔除")
    void nullClaimElementDropped() throws Exception {
        PersonAbilityExtractionResult result = normalizer.normalize("{\"claims\":[null,{\"abilityName\":\"Java\",\"masteryLevel\":4}]}");
        assertThat(result.getClaims()).hasSize(1);
        assertThat(result.getClaims().get(0).getAbilityName()).isEqualTo("Java");
    }

    @Test
    @DisplayName("有效声明保留规范化能力名与来源引用")
    void normalizedNameAndSourceRefsPreserved() throws Exception {
        String json = """
                {"claims":[{"abilityName":"raw","normalizedAbilityName":"Java","masteryLevel":3,"sourceRefs":["a","b"]}]}
                """;
        PersonAbilityExtractionResult result = normalizer.normalize(json);
        assertThat(result.getClaims()).hasSize(1);
        PersonAbilityClaim claim = result.getClaims().get(0);
        assertThat(claim.getNormalizedAbilityName()).isEqualTo("Java");
        assertThat(claim.getSourceRefs()).containsExactly("a", "b");
    }

    @Test
    @DisplayName("生产默认严格 ObjectMapper 下，含未知扩展字段的 Agent JSON 仍能解析出 claims")
    void strictMapperWithUnknownFieldsStillParsesClaims() throws Exception {
        // 回归：Agent 返回 JSON 携带类中未声明的扩展字段（reviewNeededClaims、validClaims 等），
        // 生产注入的 ObjectMapper 默认 FAIL_ON_UNKNOWN_PROPERTIES=true，若 normalize 不处理，
        // canonical 反序列化抛异常 -> 降级 legacy -> claims 为空 -> 工作流错误进入 NO_EVIDENCE。
        ObjectMapper strictMapper = new ObjectMapper(); // 保持生产默认（严格）
        PersonAbilityClaimNormalizer strictNormalizer = new PersonAbilityClaimNormalizer(strictMapper);

        String json = """
                {"empId":29,"sourceType":"RESUME_PARSE","sourceRefId":41,
                 "claims":[{"abilityName":"Java","masteryLevel":3,"evidenceText":"三年Java后端开发经验","sourceRefs":["source:RESUME_PARSE:41"]}],
                 "summary":"简历摘要","validClaims":[{}],"validClaimCount":2,"reviewNeededClaims":[],"unmatchedClaims":[],
                 "matchedClaims":[],"claimCount":2,"fallbackUsed":false,"rawModelOutput":"...","durationMs":123,"failedChunkCount":0}
                """;
        ObjectMapper lenient = strictMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try {
            PersonAbilityExtractionResult direct = lenient.readValue(json, PersonAbilityExtractionResult.class);
            System.out.println("DIRECT readValue OK, claims=" + (direct == null ? "null" : direct.getClaims().size()));
        } catch (Exception e) {
            System.out.println("DIRECT readValue FAILED: " + e.getClass().getName() + ": " + e.getMessage());
        }
        PersonAbilityExtractionResult result = strictNormalizer.normalize(json);
        assertThat(result).isNotNull();
        assertThat(result.getClaims()).isNotNull().hasSize(1);
        assertThat(result.getClaims().get(0).getAbilityName()).isEqualTo("Java");
        assertThat(result.getClaims().get(0).getMasteryLevel()).isEqualTo(3);
    }
}
