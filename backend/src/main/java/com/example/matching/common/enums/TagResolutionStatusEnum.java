package com.example.matching.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 标签解析状态枚举
 *
 * @author system
 */
@Getter
@AllArgsConstructor
public enum TagResolutionStatusEnum {

    RESOLVED("RESOLVED", "已解析"),
    TAG_CANDIDATE_PENDING("TAG_CANDIDATE_PENDING", "标签候选待审核"),
    UNRESOLVED("UNRESOLVED", "未解析");

    private final String code;
    private final String description;

    public static TagResolutionStatusEnum fromCode(String code) {
        if (code == null) return null;
        for (TagResolutionStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
