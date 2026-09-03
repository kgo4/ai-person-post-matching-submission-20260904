package com.example.matching.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 标签关系状态枚举
 *
 * @author system
 */
@Getter
@AllArgsConstructor
public enum RelationStatusEnum {

    /** 待审核 */
    PENDING("PENDING", "待审核"),

    /** 已确认 */
    CONFIRMED("CONFIRMED", "已确认"),

    /** 已拒绝 */
    REJECTED("REJECTED", "已拒绝");

    private final String code;
    private final String description;

    public static RelationStatusEnum fromCode(String code) {
        if (code == null) return null;
        for (RelationStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
