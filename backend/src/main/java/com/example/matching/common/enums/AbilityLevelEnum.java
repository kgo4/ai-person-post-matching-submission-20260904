package com.example.matching.common.enums;

import lombok.Getter;

/**
 * 能力等级枚举
 */
@Getter
public enum AbilityLevelEnum {

    BEGINNER(1, "入门"),
    FAMILIAR(2, "熟悉"),
    MASTERED(3, "掌握"),
    PROFICIENT(4, "精通"),
    EXPERT(5, "专家");

    private final int level;
    private final String name;

    AbilityLevelEnum(int level, String name) {
        this.level = level;
        this.name = name;
    }

    public static String getNameByLevel(int level) {
        for (AbilityLevelEnum e : values()) {
            if (e.getLevel() == level) {
                return e.getName();
            }
        }
        return "未知";
    }
}
