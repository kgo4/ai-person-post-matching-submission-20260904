package com.example.matching.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 标签关系类型枚举
 *
 * @author system
 */
@Getter
@AllArgsConstructor
public enum RelationTypeEnum {

    /** 语义等价，可视为同义标签 */
    SAME_AS("SAME_AS", "语义等价"),

    /** 语义相近，但不应完全等价 */
    SIMILAR("SIMILAR", "语义相近");

    private final String code;
    private final String description;

    public static RelationTypeEnum fromCode(String code) {
        if (code == null) return null;
        for (RelationTypeEnum type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
