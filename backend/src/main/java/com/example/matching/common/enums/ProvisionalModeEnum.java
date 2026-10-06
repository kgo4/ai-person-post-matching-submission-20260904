package com.example.matching.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 临时能力使用模式枚举
 *
 * @author system
 */
@Getter
@AllArgsConstructor
public enum ProvisionalModeEnum {

    EXCLUDE("EXCLUDE", "排除临时能力"),
    INCLUDE_SOFT_EVIDENCE("INCLUDE_SOFT_EVIDENCE", "软证据参与评分");

    private final String code;
    private final String description;

    public static ProvisionalModeEnum fromCode(String code) {
        if (code == null) return null;
        for (ProvisionalModeEnum mode : values()) {
            if (mode.code.equals(code)) {
                return mode;
            }
        }
        return null;
    }
}
