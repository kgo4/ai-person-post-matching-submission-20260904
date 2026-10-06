package com.example.matching.ai.validation;

import com.example.matching.agent.dto.post.PostAbilityClaim;
import com.example.matching.agent.dto.post.PostAbilityExtractionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 岗位能力提取结果校验器
 * <p>
 * 校验规则：
 * <ul>
 *   <li>abilityName 非空且长度不超过 100</li>
 *   <li>requiredLevel 非空且在 1-5 范围</li>
 *   <li>weight 非空且在 0-1 范围</li>
 *   <li>evidenceText 非空且能在 sourceText 中定位（去除空白后包含）</li>
 *   <li>sourceRefs 必须是服务端受控引用的子集；模型无引用时不报错（服务端回填）</li>
 *   <li>confidenceScore 为 0-100（为空按默认策略处理，但不得接受越界值）</li>
 *   <li>同批次能力名称/规范化名称去重：重复项合并为一条，保留证据更长且置信度更高的项</li>
 * </ul>
 * <p>
 * 注意：不校验 abilityName 是否存在于 ability_tag —— 开放词表，
 * 未知能力只要原文有连续证据即可通过，由服务端标签准入决定去向。
 */
@Slf4j
@Component
public class PostAbilityExtractionValidator {

    public static final String SCENARIO = "POST_ABILITY_EXTRACTION";

    private static final int MAX_ABILITY_NAME_LENGTH = 100;
    private static final int MIN_LEVEL = 1;
    private static final int MAX_LEVEL = 5;
    private static final Pattern QUALIFICATION_NAME_PATTERN = Pattern.compile(
            "(?:本科|大专|硕士|博士|学历|学位|专业(?:不限|相关|要求)?|应届(?:生)?|毕业(?:生)?|"
                    + "(?:[0-9一二三四五六七八九十]+)\\s*年(?:以上)?(?:工作|开发|从业)?经验|"
                    + "(?:工作|开发|从业)经验.*?(?:[0-9一二三四五六七八九十]+)\\s*年)",
            Pattern.CASE_INSENSITIVE);

    public record RejectedClaim(PostAbilityClaim claim, String reason) {
    }

    public record ValidationResult(List<PostAbilityClaim> acceptedClaims,
                                   List<RejectedClaim> rejectedClaims) {
    }

    /**
     * 校验并规范化岗位提取结果；不合法时抛出 {@link AiOutputValidationException}。
     *
     * @param result        模型返回的提取结果
     * @param sourceText    原始来源文本（服务端输入）
     * @param controlledRefs 服务端受控来源引用列表
     */
    public void validate(PostAbilityExtractionResult result, String sourceText, List<String> controlledRefs) {
        validateAgainstTrustedSource(result, sourceText, controlledRefs);
    }

    /**
     * Validates claims against the server-owned source material only.
     * Agent context may contain taxonomy or retrieval hints, neither is evidence.
     */
    public void validateAgainstTrustedSource(PostAbilityExtractionResult result,
                                             String trustedSourceText,
                                             List<String> controlledRefs) {
        if (result == null) {
            throw new AiOutputValidationException(SCENARIO, "result", "提取结果为空");
        }
        List<PostAbilityClaim> claims = result.getClaims();
        if (claims == null || claims.isEmpty()) {
            throw new AiOutputValidationException(SCENARIO, "claims", "能力声明列表为空");
        }

        String normalizedSource = normalize(trustedSourceText);
        List<PostAbilityClaim> deduplicated = new ArrayList<>();
        Map<String, PostAbilityClaim> byKey = new LinkedHashMap<>();
        for (int i = 0; i < claims.size(); i++) {
            PostAbilityClaim claim = claims.get(i);
            validateClaim(claim, i, normalizedSource, controlledRefs);
            String key = dedupKey(claim);
            PostAbilityClaim existing = byKey.get(key);
            if (existing == null) {
                byKey.put(key, claim);
            } else {
                PostAbilityClaim merged = mergeClaims(existing, claim);
                byKey.put(key, merged);
                log.warn("[POST_ABILITY_EXTRACTION] 同批次重复能力合并: name={}, 保留证据更长/置信度更高项",
                        claim.getAbilityName());
            }
        }
        deduplicated.addAll(byKey.values());
        result.setClaims(deduplicated);
    }

    /**
     * Validates each claim independently so one malformed model item cannot
     * erase the source-grounded capabilities extracted from the same JD.
     */
    public ValidationResult validateIndividually(List<PostAbilityClaim> claims,
                                                 String trustedSourceText,
                                                 List<String> controlledRefs) {
        String normalizedSource = normalize(trustedSourceText);
        List<PostAbilityClaim> accepted = new ArrayList<>();
        List<RejectedClaim> rejected = new ArrayList<>();
        if (claims == null) {
            return new ValidationResult(accepted, rejected);
        }
        for (int index = 0; index < claims.size(); index++) {
            PostAbilityClaim claim = claims.get(index);
            try {
                validateClaim(claim, index, normalizedSource, controlledRefs);
                accepted.add(claim);
            } catch (AiOutputValidationException exception) {
                rejected.add(new RejectedClaim(claim, exception.getMessage()));
            }
        }
        return new ValidationResult(List.copyOf(accepted), List.copyOf(rejected));
    }

    private void validateClaim(PostAbilityClaim claim, int index, String normalizedSource,
                               List<String> controlledRefs) {
        String prefix = "claims[" + index + "]";

        if (claim.getAbilityName() == null || claim.getAbilityName().isBlank()) {
            throw new AiOutputValidationException(SCENARIO, prefix + ".abilityName", "能力名称缺失");
        }
        if (claim.getAbilityName().length() > MAX_ABILITY_NAME_LENGTH) {
            throw new AiOutputValidationException(SCENARIO, prefix + ".abilityName",
                    "能力名称超长（>100）: " + claim.getAbilityName().length());
        }
        if (!isAssessableAbility(claim.getAbilityName(), claim.getAbilityType())) {
            throw new AiOutputValidationException(SCENARIO, prefix + ".abilityName",
                    "准入条件不得作为岗位能力写入能力模型: " + claim.getAbilityName());
        }

        if (claim.getRequiredLevel() == null
                || claim.getRequiredLevel() < MIN_LEVEL || claim.getRequiredLevel() > MAX_LEVEL) {
            throw new AiOutputValidationException(SCENARIO, prefix + ".requiredLevel",
                    "要求等级缺失或超出范围 1-5: " + claim.getRequiredLevel());
        }

        if (claim.getWeight() == null) {
            throw new AiOutputValidationException(SCENARIO, prefix + ".weight", "权重缺失");
        }
        if (claim.getWeight().compareTo(BigDecimal.ZERO) < 0
                || claim.getWeight().compareTo(BigDecimal.ONE) > 0) {
            throw new AiOutputValidationException(SCENARIO, prefix + ".weight",
                    "权重超出范围 0-1: " + claim.getWeight());
        }

        if (claim.getEvidenceText() == null || claim.getEvidenceText().isBlank()) {
            throw new AiOutputValidationException(SCENARIO, prefix + ".evidenceText", "证据缺失");
        }

        // 证据必须在原文中定位（开放词表：不校验能力名称是否在标签库）
        String evidence = normalize(claim.getEvidenceText());
        boolean locatable = !evidence.isBlank()
                && normalizedSource != null
                && !normalizedSource.isBlank()
                && normalizedSource.contains(evidence);
        if (!locatable) {
            throw new AiOutputValidationException(SCENARIO, prefix + ".evidenceText",
                    "证据无法在 sourceText 中定位");
        }

        if (claim.getEvidenceAnchor() != null && !claim.getEvidenceAnchor().isBlank()
                && !evidence.contains(normalize(claim.getEvidenceAnchor()))) {
            throw new AiOutputValidationException(SCENARIO, prefix + ".evidenceAnchor",
                    "evidenceAnchor 不在 evidenceText 中定位");
        }

        // 引用集合校验：模型 sourceRefs ⊆ 受控集合；无引用不报错
        List<String> refs = claim.getSourceRefs();
        if (refs != null && !refs.isEmpty()
                && (controlledRefs == null || !controlledRefs.containsAll(refs))) {
            throw new AiOutputValidationException(SCENARIO, prefix + ".sourceRefs",
                    "sourceRefs 超出服务端受控引用集合: " + refs);
        }

        // confidenceScore：为空按默认策略处理，但不得接受越界值
        if (claim.getConfidenceScore() != null
                && (claim.getConfidenceScore().compareTo(BigDecimal.ZERO) < 0
                || claim.getConfidenceScore().compareTo(new BigDecimal("100")) > 0)) {
            throw new AiOutputValidationException(SCENARIO, prefix + ".confidenceScore",
                    "置信度超出范围 0-100: " + claim.getConfidenceScore());
        }
    }

    /**
     * 去重键：规范化名称优先，其次原始名称（null 安全）。
     */
    private String dedupKey(PostAbilityClaim claim) {
        String normalized = claim.getNormalizedAbilityName() != null
                ? claim.getNormalizedAbilityName() : claim.getAbilityName();
        return normalize(normalized);
    }

    /**
     * 合并重复项：保留证据更长、置信度更高、等级更明确的项；
     * 权重取明确值（优先非 null，其次较高置信度项的值）。
     */
    private PostAbilityClaim mergeClaims(PostAbilityClaim a, PostAbilityClaim b) {
        PostAbilityClaim better = a;
        PostAbilityClaim other = b;
        boolean bBetter = (evidenceLength(b) > evidenceLength(a))
                || (evidenceLength(b) == evidenceLength(a)
                && confidence(b).compareTo(confidence(a)) > 0);
        if (bBetter) {
            better = b;
            other = a;
        }
        if (better.getWeight() == null && other.getWeight() != null) {
            better.setWeight(other.getWeight());
        }
        if (better.getRequiredLevel() == null && other.getRequiredLevel() != null) {
            better.setRequiredLevel(other.getRequiredLevel());
        }
        if (better.getSourceRefs() == null || better.getSourceRefs().isEmpty()) {
            better.setSourceRefs(other.getSourceRefs());
        }
        return better;
    }

    private int evidenceLength(PostAbilityClaim claim) {
        return claim.getEvidenceText() != null ? claim.getEvidenceText().length() : 0;
    }

    private BigDecimal confidence(PostAbilityClaim claim) {
        return claim.getConfidenceScore() != null ? claim.getConfidenceScore() : BigDecimal.ZERO;
    }

    private String normalize(String text) {
        if (text == null) {
            return null;
        }
        return text.replaceAll("\\s+", "").trim();
    }

    /**
     * 岗位能力模型只承载可评估的技能、方法和业务/软能力。
     * 学历、专业、年限等属于准入条件，应走岗位硬性条件规则而非能力模型。
     */
    public static boolean isAssessableAbility(String abilityName, String abilityType) {
        if (abilityName == null || abilityName.isBlank()) {
            return false;
        }
        if (abilityType != null && "QUALIFICATION".equals(abilityType.trim().toUpperCase(Locale.ROOT))) {
            return false;
        }
        return !QUALIFICATION_NAME_PATTERN.matcher(abilityName.trim()).find();
    }
}
