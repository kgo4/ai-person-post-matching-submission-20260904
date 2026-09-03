package com.example.matching.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 变更类型枚举
 * <p>
 * 用于岗位演化、知识图谱变更等场景中标记变更方向。
 * 取代散落的 "ADDED"/"REMOVED"/"MODIFIED" 魔法字符串。
 *
 * @author system
 */
@Getter
@AllArgsConstructor
public enum ChangeTypeEnum {

    /** 新增 */
    ADDED("ADDED", "新增"),

    /** 移除 */
    REMOVED("REMOVED", "移除"),

    /** 修改 */
    MODIFIED("MODIFIED", "修改");

    private final String code;
    private final String description;

    public static ChangeTypeEnum fromCode(String code) {
        if (code == null) return null;
        for (ChangeTypeEnum type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
