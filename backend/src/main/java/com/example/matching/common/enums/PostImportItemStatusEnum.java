package com.example.matching.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Excel导入明细分析状态枚举
 */
@Getter
@AllArgsConstructor
public enum PostImportItemStatusEnum {

    PENDING(0, "待分析"),
    ANALYZING(1, "分析中"),
    SUCCESS(2, "分析成功"),
    FAILED(3, "分析失败");

    private final int code;
    private final String name;

    public static PostImportItemStatusEnum fromCode(int code) {
        for (PostImportItemStatusEnum e : values()) {
            if (e.code == code) return e;
        }
        return null;
    }
}
