package com.example.matching.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Excel导入批次状态枚举
 */
@Getter
@AllArgsConstructor
public enum PostImportStatusEnum {

    PENDING_ANALYZE(0, "待分析"),
    ANALYZING(1, "分析中"),
    PENDING_CONFIRM(2, "待确认"),
    IMPORTING(3, "导入中"),
    COMPLETED(4, "导入完成"),
    FAILED(5, "失败");

    private final int code;
    private final String name;

    public static PostImportStatusEnum fromCode(int code) {
        for (PostImportStatusEnum e : values()) {
            if (e.code == code) return e;
        }
        return null;
    }
}
