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

    /**
     * 业务角色编码，与 db/migration 中的 sys_role.role_code 保持一致。
     * 代码中需要按编码定位角色时统一引用，避免魔法字符串散落各处。
     */
    public static final class RoleCode {

        private RoleCode() {
        }

        /** 员工：仅本人能力评估、本人通知与本人学习 */
        public static final String EMPLOYEE = "EMPLOYEE";
    }
}
