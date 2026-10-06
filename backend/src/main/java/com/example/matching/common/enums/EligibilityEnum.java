package com.example.matching.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 能力可用性枚举
 *
 * @author system
 */
@Getter
@AllArgsConstructor
public enum EligibilityEnum {

    DISPLAY_ONLY("DISPLAY_ONLY", "仅展示"),
    CONFIRMED("CONFIRMED", "已确认"),
    MATCH_SNAPSHOT_ONLY("MATCH_SNAPSHOT_ONLY", "仅匹配快照");

    private final String code;
    private final String description;

    public static EligibilityEnum fromCode(String code) {
        if (code == null) return null;
        for (EligibilityEnum eligibility : values()) {
            if (eligibility.code.equals(code)) {
                return eligibility;
            }
        }
        return null;
    }
}
