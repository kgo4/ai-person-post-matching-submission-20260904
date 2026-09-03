package com.example.matching.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Set;

/**
 * 最终等级决策状态枚举
 *
 * @author system
 */
@Getter
@AllArgsConstructor
public enum DecisionStatusEnum {

    AUTO_CONFIRMED("AUTO_CONFIRMED", "自动确认"),
    PENDING_MANUAL_REVIEW("PENDING_MANUAL_REVIEW", "待人工复核"),
    HUMAN_CONFIRMED("HUMAN_CONFIRMED", "人工确认"),
    REJECTED("REJECTED", "已拒绝");

    private final String code;
    private final String description;

    private static final Set<DecisionStatusEnum> PROJECTABLE_STATUSES = Set.of(
            AUTO_CONFIRMED, HUMAN_CONFIRMED
    );

    public static DecisionStatusEnum fromCode(String code) {
        if (code == null) return null;
        for (DecisionStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }

    /** 是否可投影到正式画像 */
    public boolean isProjectable() {
        return PROJECTABLE_STATUSES.contains(this);
    }
}
