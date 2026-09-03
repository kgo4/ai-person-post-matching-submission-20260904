package com.example.matching.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Set;

/**
 * Harness 审核决策枚举
 *
 * @author system
 */
@Getter
@AllArgsConstructor
public enum HarnessDecisionEnum {

    PASS("PASS", "通过"),
    REVIEW("REVIEW", "待复核"),
    BLOCK("BLOCK", "阻止");

    private final String code;
    private final String description;

    private static final Set<HarnessDecisionEnum> ALLOWED_FOR_AUTO_CONFIRM = Set.of(PASS);

    public static HarnessDecisionEnum fromCode(String code) {
        if (code == null) return null;
        for (HarnessDecisionEnum decision : values()) {
            if (decision.code.equals(code)) {
                return decision;
            }
        }
        return null;
    }

    public boolean allowsAutoConfirm() {
        return ALLOWED_FOR_AUTO_CONFIRM.contains(this);
    }
}
