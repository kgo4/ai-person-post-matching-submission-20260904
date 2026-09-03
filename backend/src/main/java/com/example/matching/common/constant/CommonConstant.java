package com.example.matching.common.constant;

/**
 * 通用常量
 */
public final class CommonConstant {

    private CommonConstant() {
    }

    /** 系统用户ID */
    public static final Long SYSTEM_USER_ID = 0L;

    /** 默认密码 */
    public static final String DEFAULT_PASSWORD = "123456";

    /** 请求头Token */
    public static final String TOKEN_HEADER = "Authorization";

    /** Token前缀 */
    public static final String TOKEN_PREFIX = "Bearer ";

    /** 删除状态：未删除 */
    public static final int NOT_DELETED = 0;

    /** 删除状态：已删除 */
    public static final int DELETED = 1;

    /** 状态：启用 */
    public static final int STATUS_ENABLED = 1;

    /** 状态：停用 */
    public static final int STATUS_DISABLED = 0;
}
