package com.example.matching.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 标签关系证据来源枚举
 *
 * @author system
 */
@Getter
@AllArgsConstructor
public enum EvidenceSourceEnum {

    /** 人工提交 */
    MANUAL("MANUAL", "人工"),

    /** 向量发现 */
    VECTOR_DISCOVERY("VECTOR_DISCOVERY", "向量发现"),

    /** AI建议 */
    AI_SUGGESTION("AI_SUGGESTION", "AI建议");

    private final String code;
    private final String description;

    public static EvidenceSourceEnum fromCode(String code) {
        if (code == null) return null;
        for (EvidenceSourceEnum source : values()) {
            if (source.code.equals(code)) {
                return source;
            }
        }
        return null;
    }
}
