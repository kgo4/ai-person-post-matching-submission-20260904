package com.example.matching.common.util;

import com.example.matching.agent.dto.person.PersonAbilityClaim;
import com.example.matching.agent.dto.person.PersonAbilityExtractionResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 人员能力提取结果统一规范器（纯工具，位于 common 避免 service 切片环依赖）。
 * <p>
 * 唯一规范格式：{@link PersonAbilityExtractionResult#getClaims()}（claims[]，
 * 每条含 abilityName + masteryLevel(1-5)）。本类集中承载历史兼容：
 * 旧格式 abilities/abilityClaims/skills 数组（tagName -> abilityName, level -> masteryLevel）
 * 在此统一映射，禁止在业务服务中再次探测旧字段。
 * <p>
 * 无效声明（缺能力名 / 缺等级 / 等级越界 / 等级非数字）被剔除并输出 reason 级日志；
 * 返回结果的 claims 永不为 null（可为空）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PersonAbilityClaimNormalizer {

    /** 掌握程度领域范围：1-5（见 PersonAbilityClaim.masteryLevel 注释） */
    private static final int MASTERY_LEVEL_MIN = 1;
    private static final int MASTERY_LEVEL_MAX = 5;

    /** 旧格式顶层能力数组字段名（按优先级探测） */
    private static final List<String> LEGACY_ARRAY_FIELDS = List.of("abilities", "abilityClaims", "skills");

    private final ObjectMapper objectMapper;

    /**
     * 将任意历史格式的提取结果 JSON 规范化为 {@link PersonAbilityExtractionResult}。
     * 规范格式直接清洗返回；旧格式映射后再清洗；两者都无法解析时抛异常由调用方降级。
     *
     * @param json 简历解析 AI 结果 JSON
     * @return 规范结果，claims 非 null
     * @throws JsonProcessingException JSON 非法时抛出
     */
    public PersonAbilityExtractionResult normalize(String json) throws JsonProcessingException {
        PersonAbilityExtractionResult current = tryReadCanonical(json);
        if (current != null && current.getClaims() != null) {
            return sanitize(current);
        }
        Map<String, Object> root = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        List<Map<String, Object>> legacy = firstList(root);
        return sanitize(mapLegacyClaims(legacy));
    }

    private PersonAbilityExtractionResult tryReadCanonical(String json) {
        try {
            // Agent 返回 JSON 可能携带类中未声明的扩展字段（reviewNeededClaims、validClaims 等），
            // 关闭未知属性失败，避免整体反序列化抛异常导致 claims 解析为空（NO_EVIDENCE）。
            ObjectMapper lenient = objectMapper.copy()
                    .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            return lenient.readValue(json, PersonAbilityExtractionResult.class);
        } catch (JsonProcessingException e) {
            log.debug("按规范格式解析失败，尝试旧格式兼容: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 清洗：claims 非 null；剔除 null 元素、空能力名、等级缺失或越界的声明。
     * 有效声明的证据文本与来源引用原样保留。
     */
    private PersonAbilityExtractionResult sanitize(PersonAbilityExtractionResult result) {
        if (result == null) {
            result = new PersonAbilityExtractionResult();
        }
        List<PersonAbilityClaim> claims = result.getClaims();
        List<PersonAbilityClaim> valid = new ArrayList<>();
        if (claims != null) {
            for (PersonAbilityClaim claim : claims) {
                if (claim == null) {
                    continue;
                }
                String name = firstNonBlank(claim.getNormalizedAbilityName(), claim.getAbilityName());
                if (name == null) {
                    log.debug("剔除无效声明: 能力名为空");
                    continue;
                }
                if (claim.getMasteryLevel() == null
                        || claim.getMasteryLevel() < MASTERY_LEVEL_MIN
                        || claim.getMasteryLevel() > MASTERY_LEVEL_MAX) {
                    log.debug("剔除无效声明: abilityName={}, 等级越界 masteryLevel={}",
                            name, claim.getMasteryLevel());
                    continue;
                }
                valid.add(claim);
            }
        }
        result.setClaims(valid);
        return result;
    }

    private PersonAbilityExtractionResult mapLegacyClaims(List<Map<String, Object>> legacy) {
        PersonAbilityExtractionResult result = new PersonAbilityExtractionResult();
        List<PersonAbilityClaim> claims = new ArrayList<>();
        if (legacy != null) {
            for (Map<String, Object> item : legacy) {
                if (item == null) {
                    continue;
                }
                String abilityName = firstNonBlankObj(
                        item.get("abilityName"),
                        item.get("normalizedAbilityName"),
                        item.get("tagName"),
                        item.get("name"),
                        item.get("skillName")
                );
                Integer level = firstIntObj(
                        item.get("masteryLevel"),
                        item.get("level"),
                        item.get("claimedLevel"),
                        item.get("currentLevel")
                );
                if (abilityName == null || level == null) {
                    log.debug("跳过无效能力项: abilityName={}, level={}", abilityName, level);
                    continue;
                }
                String evidenceText = firstNonBlankObj(
                        item.get("evidenceText"),
                        item.get("evidence"),
                        item.get("extractReason"),
                        item.get("reason"),
                        item.get("description")
                );
                if (evidenceText == null) {
                    evidenceText = "从简历解析导入：" + abilityName;
                }

                PersonAbilityClaim claim = new PersonAbilityClaim();
                claim.setAbilityName(abilityName);
                claim.setNormalizedAbilityName(abilityName);
                claim.setMasteryLevel(level);
                claim.setEvidenceText(evidenceText);
                claims.add(claim);
            }
        }
        result.setClaims(claims);
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> firstList(Map<String, Object> root) {
        for (String fieldName : LEGACY_ARRAY_FIELDS) {
            Object value = root.get(fieldName);
            if (value instanceof List) {
                return (List<Map<String, Object>>) (List<?>) value;
            }
        }
        return null;
    }

    private String firstNonBlankObj(Object... values) {
        for (Object v : values) {
            if (v != null) {
                String s = v.toString().trim();
                if (!s.isEmpty()) {
                    return s;
                }
            }
        }
        return null;
    }

    private Integer firstIntObj(Object... values) {
        for (Object v : values) {
            if (v != null) {
                if (v instanceof Number number) {
                    return number.intValue();
                }
                try {
                    return Integer.parseInt(v.toString().trim());
                } catch (NumberFormatException ignored) {
                    log.debug("等级字段非数字: value={}", v);
                }
            }
        }
        return null;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }
}
