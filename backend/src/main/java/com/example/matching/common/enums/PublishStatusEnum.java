package com.example.matching.common.enums;

import lombok.Getter;

/**
 * 匹配结果推送状态枚举。
 *
 * <p>与 {@link ApprovalStatusEnum} 正交：审批通过只代表 HR 认可匹配结论，
 * 是否让员工本人看到由推送状态决定，HR 可以选择审核通过但不推送。
 * 员工侧所有依赖匹配结果的功能（详情、差距诊断、学习路径等）统一要求
 * {@code approvalStatus = APPROVED AND publishStatus = PUBLISHED}。</p>
 */
@Getter
public enum PublishStatusEnum {

    /** 未推送：员工侧不可见 */
    UNPUBLISHED(0, "未推送"),

    /** 已推送：员工侧可见 */
    PUBLISHED(1, "已推送");

    private final int code;
    private final String name;

    PublishStatusEnum(int code, String name) {
        this.code = code;
        this.name = name;
    }

    public static String getNameByCode(int code) {
        for (PublishStatusEnum e : values()) {
            if (e.getCode() == code) {
                return e.getName();
            }
        }
        return "未知";
    }
}
