package com.example.matching.ai.validation;

import com.example.matching.agent.dto.person.PersonAbilityClaim;
import com.example.matching.agent.dto.person.PersonAbilityExtractionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;

/**
 * 员工能力提取结果校验器
 * <p>
 * 校验规则：
 * <ul>
 *   <li>能力名称、等级、证据均必填；等级在 1-5 范围</li>
 *   <li>证据必须能在 sourceText 中定位（去除空白后包含），或匹配受控 sourceRefs</li>
 * </ul>
 */
@Slf4j
@Component
public class EmployeeAbilityExtractionValidator {

    public static final String SCENARIO = "EMPLOYEE_ABILITY_EXTRACTION";

    /**
     * 校验提取结果中的能力声明；不合法时抛出 {@link AiOutputValidationException}
     *
     * @param result        模型返回的提取结果
     * @param sourceText    原始来源文本（服务端输入）
     * @param controlledRefs 服务端受控来源引用列表
     */
    public void validate(PersonAbilityExtractionResult result, String sourceText, List<String> controlledRefs) {
        validate(result, sourceText, controlledRefs, false);
    }

    /**
     * 校验提取结果中的能力声明。
     *
     * @param ocrDerived 是否为 OCR 生成的文本；仅影响字符形态归一化，不放宽证据必须可定位的约束
     */
    public void validate(PersonAbilityExtractionResult result, String sourceText, List<String> controlledRefs,
                         boolean ocrDerived) {
        if (result == null) {
            throw new AiOutputValidationException(SCENARIO, "result", "提取结果为空");
        }
        List<PersonAbilityClaim> claims = result.getClaims();
        if (claims == null || claims.isEmpty()) {
            throw new AiOutputValidationException(SCENARIO, "claims", "能力声明列表为空");
        }

        String normalizedSource = normalize(sourceText, ocrDerived);
        for (int i = 0; i < claims.size(); i++) {
            validateClaim(claims.get(i), i, normalizedSource, controlledRefs, ocrDerived);
        }
    }

    private void validateClaim(PersonAbilityClaim claim, int index, String normalizedSource,
                               List<String> controlledRefs, boolean ocrDerived) {
        String prefix = "claims[" + index + "]";

        if (claim.getAbilityName() == null || claim.getAbilityName().isBlank()) {
            throw new AiOutputValidationException(SCENARIO, prefix + ".abilityName", "能力名称缺失");
        }
        if (claim.getAbilityName().length() > 100) {
            throw new AiOutputValidationException(SCENARIO, prefix + ".abilityName", "能力名称超长");
        }

        if (claim.getMasteryLevel() == null || claim.getMasteryLevel() < 1 || claim.getMasteryLevel() > 5) {
            throw new AiOutputValidationException(SCENARIO, prefix + ".masteryLevel",
                    "等级缺失或超出范围 1-5: " + claim.getMasteryLevel());
        }

        if (claim.getEvidenceText() == null || claim.getEvidenceText().isBlank()) {
            throw new AiOutputValidationException(SCENARIO, prefix + ".evidenceText", "证据缺失");
        }

        validateEvidenceLocatable(claim, index, normalizedSource, ocrDerived);
        validateSourceRefs(claim, index, controlledRefs);
    }

    /**
     * 证据必须能在 sourceText 中定位（去除空白后包含）。
     * <p>
     * 删除原 matchesControlledRefs 旁路：sourceRefs 只用于校验引用集合，
     * 不能作为证据不在原文时的替代通道。
     */
    private void validateEvidenceLocatable(PersonAbilityClaim claim, int index,
                                           String normalizedSource, boolean ocrDerived) {
        String evidence = normalize(claim.getEvidenceText(), ocrDerived);
        boolean locatableInSource = !evidence.isBlank()
                && normalizedSource != null
                && !normalizedSource.isBlank()
                && normalizedSource.contains(evidence);

        if (!locatableInSource) {
            throw new AiOutputValidationException(SCENARIO, "claims[" + index + "].evidenceText",
                    "证据无法在 sourceText 中定位");
        }
    }

    /**
     * 模型 sourceRefs 必须是服务端 controlledRefs 的子集；
     * 模型没有引用时不报错，由服务端随后统一回填标准引用。
     */
    private void validateSourceRefs(PersonAbilityClaim claim, int index, List<String> controlledRefs) {
        List<String> refs = claim.getSourceRefs();
        if (refs == null || refs.isEmpty()) {
            return;
        }
        if (controlledRefs == null || !controlledRefs.containsAll(refs)) {
            throw new AiOutputValidationException(SCENARIO, "claims[" + index + "].sourceRefs",
                    "sourceRefs 超出服务端受控引用集合: " + refs);
        }
    }

    private String normalize(String text, boolean ocrDerived) {
        if (text == null) {
            return null;
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
                .replaceAll("\\s+", "")
                .trim();
        // OCR 经常将中英文标点、全角符号和断行识别为不同形式。仅在 OCR 路径移除这些差异，
        // 其余字符仍必须按顺序完整出现在原文中。
        return ocrDerived ? normalized.replaceAll("[\\p{P}\\p{S}]", "") : normalized;
    }
}
