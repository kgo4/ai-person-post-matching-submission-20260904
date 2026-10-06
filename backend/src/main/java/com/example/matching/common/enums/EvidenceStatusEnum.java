package com.example.matching.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 证据状态枚举
 *
 * @author system
 */
@Getter
@AllArgsConstructor
public enum EvidenceStatusEnum {

    COLLECTED("COLLECTED", "已收集"),
    READY_FOR_AGGREGATE_HARNESS("READY_FOR_AGGREGATE_HARNESS", "待聚合审核"),
    CONFIRMED("CONFIRMED", "已确认"),
    PENDING_MANUAL_REVIEW("PENDING_MANUAL_REVIEW", "待人工复核"),
    BLOCKED("BLOCKED", "已阻止"),
    UNCLASSIFIED_OBSERVATION("UNCLASSIFIED_OBSERVATION", "未归类观察");

    private final String code;
    private final String description;

    public static EvidenceStatusEnum fromCode(String code) {
        if (code == null) return null;
        for (EvidenceStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
