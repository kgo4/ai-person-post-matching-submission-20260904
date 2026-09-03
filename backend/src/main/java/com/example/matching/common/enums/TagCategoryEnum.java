package com.example.matching.common.enums;

import lombok.Getter;

/**
 * 能力标签分类枚举
 */
@Getter
public enum TagCategoryEnum {

    TECHNICAL("TECHNICAL", "技术能力"),
    SOFT("SOFT", "软技能"),
    BUSINESS("BUSINESS", "业务能力");

    private final String code;
    private final String name;

    TagCategoryEnum(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static String getNameByCode(String code) {
        for (TagCategoryEnum e : values()) {
            if (e.getCode().equals(code)) {
                return e.getName();
            }
        }
        return "未知";
    }
}
