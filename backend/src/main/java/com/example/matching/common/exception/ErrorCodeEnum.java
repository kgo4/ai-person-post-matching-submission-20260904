package com.example.matching.common.exception;

import lombok.Getter;

/**
 * 错误码枚举
 */
@Getter
public enum ErrorCodeEnum {

    /** 通用错误 */
    SUCCESS(200, "操作成功"),
    PARAM_ERROR(400, "参数校验失败"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "服务器内部错误"),
    SYSTEM_ERROR(500, "系统错误"),
    STATE_CONFLICT(409, "状态冲突，请刷新后重试"),
    IMPORT_ERROR(422, "导入失败"),
    EXPORT_ERROR(422, "导出失败"),

    /** 业务错误（10000+） */
    USER_NOT_FOUND(10001, "用户不存在"),
    USER_PASSWORD_ERROR(10002, "密码错误"),
    USER_ACCOUNT_DISABLED(10003, "账号已被禁用"),

    EMPLOYEE_NOT_FOUND(10101, "员工不存在"),
    EMPLOYEE_CODE_DUPLICATE(10102, "员工工号已存在"),

    POST_NOT_FOUND(10201, "岗位不存在"),
    POST_CODE_DUPLICATE(10202, "岗位编码已存在"),

    ABILITY_TAG_NOT_FOUND(10301, "能力标签不存在"),
    ABILITY_TAG_CODE_DUPLICATE(10302, "标签编码已存在"),

    MATCHING_RECORD_NOT_FOUND(10401, "匹配记录不存在"),
    MATCHING_ALREADY_LOCKED(10402, "匹配记录已被锁定"),
    MATCHING_NO_ABILITY_DATA(10403, "员工无能力数据，无法匹配"),
    MATCHING_CONCURRENT_MODIFICATION(10404, "记录已被其他用户修改，请刷新后重试"),

    AI_SERVICE_ERROR(10501, "AI服务调用失败"),
    VECTOR_EMBEDDING_ERROR(10502, "向量嵌入失败"),

    POST_MODEL_TAG_DUPLICATE(10601, "岗位能力标签重复"),
    POST_MODEL_WEIGHT_INVALID(10602, "岗位能力权重总和不合法，应在95-105之间"),
    POST_MODEL_NO_CORE(10603, "至少配置1个核心能力项"),
    POST_MODEL_REQUIRED_WEIGHT_ZERO(10604, "必填能力项的权重不能为0"),
    POST_MODEL_CORE_LOW_WEIGHT(10605, "核心能力项权重建议不低于15"),
    POST_MODEL_INCOMPLETE(10606, "岗位能力模型不完整，无法执行正式匹配"),

    GOVERNANCE_REVIEW_FAILED(10701, "治理复审未通过，状态未变更"),
    ;

    private final int code;
    private final String message;

    ErrorCodeEnum(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
