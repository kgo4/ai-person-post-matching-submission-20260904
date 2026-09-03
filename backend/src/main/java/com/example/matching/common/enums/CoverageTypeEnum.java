package com.example.matching.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 测试验证覆盖类型枚举
 *
 * @author system
 */
@Getter
@AllArgsConstructor
public enum CoverageTypeEnum {

    MULTIPLE_CHOICE("MULTIPLE_CHOICE", "选择题"),
    SITUATIONAL("SITUATIONAL", "情境题"),
    PRACTICAL("PRACTICAL", "实操题"),
    REASONING("REASONING", "推理题");

    private final String code;
    private final String description;

    public static CoverageTypeEnum fromCode(String code) {
        if (code == null) return null;
        for (CoverageTypeEnum type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
