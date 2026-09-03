package com.example.matching.common.enums;

import lombok.Getter;

/**
 * 审批状态枚举
 */
@Getter
public enum ApprovalStatusEnum {

    NOT_INITIATED(0, "未发起"),
    IN_PROGRESS(1, "审批中"),
    APPROVED(2, "审批通过"),
    REJECTED(3, "审批驳回");

    private final int code;
    private final String name;

    ApprovalStatusEnum(int code, String name) {
        this.code = code;
        this.name = name;
    }

    public static String getNameByCode(int code) {
        for (ApprovalStatusEnum e : values()) {
            if (e.getCode() == code) {
                return e.getName();
            }
        }
        return "未知";
    }
}
