package com.example.matching.common.enums;

import lombok.Getter;

/**
 * 匹配状态枚举
 */
@Getter
public enum MatchStatusEnum {

    PENDING_REVIEW(0, "待审核"),
    STRONG_MATCH(1, "强适配"),
    MATCH(2, "适配"),
    OBSERVE(3, "待观察"),
    NO_MATCH(4, "不适配");

    private final int code;
    private final String name;

    MatchStatusEnum(int code, String name) {
        this.code = code;
        this.name = name;
    }

    public static String getNameByCode(int code) {
        for (MatchStatusEnum e : values()) {
            if (e.getCode() == code) {
                return e.getName();
            }
        }
        return "未知";
    }
}
