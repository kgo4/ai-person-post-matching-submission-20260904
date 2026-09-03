package com.example.matching.agent.dto.person;

/**
 * 证据验证结果诊断
 * <p>
 * 每条能力声明的证据验证都会产生一个确定性的验证结果，用于诊断和可观察性。
 * 只有 GROUNDED 的声明才能作为可采信证据进入后续评估流程。
 *
 * @author system
 */
public enum EvidenceValidationResult {

    /** 证据已定位到原文：evidenceText 在 sourceText 中可定位，偏移有效 */
    GROUNDED("GROUNDED", "证据已定位"),

    /** 证据偏移越界：evidenceStart/evidenceEnd 不在 [0, sourceText.length()] 范围内 */
    OFFSET_INVALID("OFFSET_INVALID", "证据偏移越界"),

    /** 证据文本未找到：evidenceText 不在 sourceText 中（即使用 normalized lookup 也找不到） */
    TEXT_NOT_FOUND("TEXT_NOT_FOUND", "证据文本未能定位"),

    /** 来源引用无效：sourceRefs 为空或不在服务端受控引用集合中 */
    SOURCE_REF_INVALID("SOURCE_REF_INVALID", "来源引用无效"),

    /** 能力声明列表为空：模型返回了空的 claims */
    EMPTY_CLAIMS("EMPTY_CLAIMS", "能力声明为空"),

    /** 模型输出不合法：模型返回的JSON格式不合法或缺失必要字段 */
    MODEL_OUTPUT_INVALID("MODEL_OUTPUT_INVALID", "模型输出不合法"),

    /** 未验证：claim 尚未经过服务端验证流程 */
    NOT_VALIDATED("NOT_VALIDATED", "未验证");

    private final String code;
    private final String description;

    EvidenceValidationResult(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static EvidenceValidationResult fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (EvidenceValidationResult result : values()) {
            if (result.code.equals(code)) {
                return result;
            }
        }
        return null;
    }
}
