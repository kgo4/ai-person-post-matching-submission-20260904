package com.example.matching.common.audit;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 操作日志的「操作模块 / 操作类型」归类。
 *
 * <p>为什么抽成纯函数：这两个字段过去在 {@code OperationLogAspect} 里是**写死的**
 * （{@code operationModule="system"}、{@code operationType="UNKNOWN"}），于是
 * 「操作日志」页面的「操作类型」列**整列显示 UNKNOWN**，模块列永远是小写 {@code system}，
 * 与筛选下拉里的大写选项（SYSTEM/USER/ROLE…）也对不上。
 *
 * <p>归类只能从请求本身推导（HTTP 方法 + URI），没有别的信息源，所以这里做成
 * 「输入字符串、输出字符串」的静态方法，可以脱离 Spring 直接单测。
 *
 * <p>取值口径与**库里已有数据**保持一致（生产/本地已有 {@code DLQ}、{@code OUTBOX}、
 * {@code SCHEDULER}、{@code AI_TEST} 等模块码，以及 {@code ALERT}/{@code UPDATE} 等类型码），
 * 不另造一套命名。
 */
public final class OperationLogClassifier {

    private OperationLogClassifier() {
    }

    // ---------- 操作模块 ----------
    public static final String MODULE_SYSTEM = "SYSTEM";
    public static final String MODULE_USER = "USER";
    public static final String MODULE_ROLE = "ROLE";
    public static final String MODULE_ABILITY_TAG = "ABILITY_TAG";
    public static final String MODULE_EXTEND_FIELD = "EXTEND_FIELD";
    public static final String MODULE_EMPLOYEE = "EMPLOYEE";
    public static final String MODULE_POST = "POST";
    public static final String MODULE_MATCHING = "MATCHING";
    public static final String MODULE_LEARNING = "LEARNING";
    public static final String MODULE_RAG = "RAG";
    public static final String MODULE_KG = "KG";
    public static final String MODULE_AI_TEST = "AI_TEST";
    public static final String MODULE_GOVERNANCE = "GOVERNANCE";
    public static final String MODULE_CRAWLER = "CRAWLER";
    public static final String MODULE_NOTIFICATION = "NOTIFICATION";
    public static final String MODULE_OTHER = "OTHER";

    // ---------- 操作类型 ----------
    public static final String TYPE_CREATE = "CREATE";
    public static final String TYPE_UPDATE = "UPDATE";
    public static final String TYPE_DELETE = "DELETE";
    /** 历史遗留值，仅用于识别与兜底展示。 */
    public static final String TYPE_UNKNOWN = "UNKNOWN";

    /**
     * 模块码 → 中文名。前端筛选下拉与表格展示共用这一份口径（前端同名映射见
     * {@code frontend/src/views/system/operation-log/index.vue}）。
     */
    private static final Map<String, String> MODULE_LABELS = new LinkedHashMap<>();
    private static final Map<String, String> TYPE_LABELS = new LinkedHashMap<>();

    static {
        MODULE_LABELS.put(MODULE_SYSTEM, "系统管理");
        MODULE_LABELS.put(MODULE_USER, "用户管理");
        MODULE_LABELS.put(MODULE_ROLE, "角色权限");
        MODULE_LABELS.put(MODULE_ABILITY_TAG, "能力标签");
        MODULE_LABELS.put(MODULE_EXTEND_FIELD, "扩展字段");
        MODULE_LABELS.put(MODULE_EMPLOYEE, "人员管理");
        MODULE_LABELS.put(MODULE_POST, "岗位管理");
        MODULE_LABELS.put(MODULE_MATCHING, "人岗匹配");
        MODULE_LABELS.put(MODULE_LEARNING, "学习成长");
        MODULE_LABELS.put(MODULE_RAG, "知识资产");
        MODULE_LABELS.put(MODULE_KG, "知识图谱");
        MODULE_LABELS.put(MODULE_AI_TEST, "AI 测试");
        MODULE_LABELS.put(MODULE_GOVERNANCE, "AI 治理");
        MODULE_LABELS.put(MODULE_CRAWLER, "数据采集");
        MODULE_LABELS.put(MODULE_NOTIFICATION, "通知面试");
        MODULE_LABELS.put("DLQ", "死信队列");
        MODULE_LABELS.put("OUTBOX", "事件发件箱");
        MODULE_LABELS.put("SCHEDULER", "定时任务");
        MODULE_LABELS.put(MODULE_OTHER, "其他");

        TYPE_LABELS.put(TYPE_CREATE, "新增");
        TYPE_LABELS.put(TYPE_UPDATE, "修改");
        TYPE_LABELS.put(TYPE_DELETE, "删除");
        TYPE_LABELS.put("QUERY", "查询");
        TYPE_LABELS.put("EXECUTE", "执行");
        TYPE_LABELS.put("ALERT", "告警");
        TYPE_LABELS.put(TYPE_UNKNOWN, "其他");
    }

    /**
     * 按请求 URI 归类操作模块。
     *
     * <p>⚠️ 规则顺序 = 优先级，**具体前缀必须排在宽前缀之前**（与本项目 SecurityConfig
     * 的同类约定一致）：{@code /api/system/user} 必须早于 {@code /api/system}。
     */
    public static String classifyModule(String uri) {
        if (uri == null || uri.isBlank()) {
            return MODULE_OTHER;
        }
        String path = uri.toLowerCase();

        // 采集链路（本地爬虫主动推送/轮询）——必须先于 /api/post 判断
        if (path.startsWith("/api/internal/crawler") || path.startsWith("/api/internal/market-jd")
                || path.startsWith("/api/post/evolution/crawler")) {
            return MODULE_CRAWLER;
        }
        // AI 测试（人工重放等）——必须先于 /api/employee 判断
        if (path.startsWith("/api/employee/ability/ai-test")) {
            return MODULE_AI_TEST;
        }
        if (path.startsWith("/api/system/user")) {
            return MODULE_USER;
        }
        if (path.startsWith("/api/system/role")) {
            return MODULE_ROLE;
        }
        if (path.startsWith("/api/system/ability-tag") || path.startsWith("/api/system/tag-candidate")
                || path.startsWith("/api/system/tag-governance") || path.startsWith("/api/system/skill-taxonomy")) {
            return MODULE_ABILITY_TAG;
        }
        if (path.startsWith("/api/system/extend-field")) {
            return MODULE_EXTEND_FIELD;
        }
        if (path.startsWith("/api/system") || path.startsWith("/api/admin")) {
            return MODULE_SYSTEM;
        }
        if (path.startsWith("/api/employee") || path.startsWith("/api/ability")) {
            return MODULE_EMPLOYEE;
        }
        if (path.startsWith("/api/post")) {
            return MODULE_POST;
        }
        if (path.startsWith("/api/matching")) {
            return MODULE_MATCHING;
        }
        if (path.startsWith("/api/learning")) {
            return MODULE_LEARNING;
        }
        if (path.startsWith("/api/rag")) {
            return MODULE_RAG;
        }
        if (path.startsWith("/api/kg")) {
            return MODULE_KG;
        }
        if (path.startsWith("/api/ai-governance") || path.startsWith("/api/governance")
                || path.startsWith("/api/capability-closure")) {
            return MODULE_GOVERNANCE;
        }
        if (path.startsWith("/api/notifications") || path.startsWith("/api/communication-interviews")) {
            return MODULE_NOTIFICATION;
        }
        return MODULE_OTHER;
    }

    /** 按 HTTP 方法归类操作类型；未知方法返回 {@link #TYPE_UNKNOWN}。 */
    public static String classifyType(String httpMethod) {
        if (httpMethod == null) {
            return TYPE_UNKNOWN;
        }
        return switch (httpMethod.trim().toUpperCase()) {
            case "POST" -> TYPE_CREATE;
            case "PUT", "PATCH" -> TYPE_UPDATE;
            case "DELETE" -> TYPE_DELETE;
            case "GET" -> "QUERY";
            default -> TYPE_UNKNOWN;
        };
    }

    /** 中文模块名；未知模块码原样返回（不吞掉信息）。 */
    public static String moduleLabel(String moduleCode) {
        if (moduleCode == null || moduleCode.isBlank()) {
            return MODULE_LABELS.get(MODULE_OTHER);
        }
        String key = moduleCode.trim().toUpperCase();
        return MODULE_LABELS.getOrDefault(key, moduleCode);
    }

    /** 中文类型名；未知类型码原样返回。历史 {@code UNKNOWN} 显示为「其他」。 */
    public static String typeLabel(String typeCode) {
        if (typeCode == null || typeCode.isBlank()) {
            return TYPE_LABELS.get(TYPE_UNKNOWN);
        }
        String key = typeCode.trim().toUpperCase();
        return TYPE_LABELS.getOrDefault(key, typeCode);
    }

    /**
     * 默认操作描述：`新增 · 用户管理（POST /api/system/user）`。
     *
     * <p>用「类型 · 模块」而不是写死「自动记录」——后者在列表里看不出任何信息。
     */
    public static String describe(String moduleCode, String typeCode, String httpMethod, String uri) {
        String prefix = typeLabel(typeCode) + " · " + moduleLabel(moduleCode);
        if (httpMethod == null || uri == null) {
            return prefix;
        }
        return prefix + "（" + httpMethod.toUpperCase() + " " + uri + "）";
    }

    /** 该值是否「有信息量」——空、UNKNOWN、旧的小写兜底值 system 都算没有。 */
    public static boolean isMeaningfulType(String typeCode) {
        return typeCode != null && !typeCode.isBlank()
                && !TYPE_UNKNOWN.equalsIgnoreCase(typeCode.trim());
    }

    /**
     * 模块值是否需要从 URI 重新推导。
     *
     * <p>历史行的模块是写死的小写 {@code system}（任何 URI 都是它），因此它等同于「没填」。
     * ⚠️ 比较必须**忽略大小写**：库里的历史值确实是小写，但同一条规则也会被
     * 「SYSTEM」「System」这类写法命中，大小写敏感会漏掉它们（被单测抓到过）。
     */
    public static boolean needsModuleBackfill(String moduleCode) {
        return moduleCode == null || moduleCode.isBlank()
                || "system".equalsIgnoreCase(moduleCode.trim());
    }

    /** 供前端构造筛选下拉：模块码 → 中文名。 */
    public static Map<String, String> moduleLabels() {
        return Map.copyOf(MODULE_LABELS);
    }

    /** 供前端构造筛选下拉：类型码 → 中文名。 */
    public static Map<String, String> typeLabels() {
        return Map.copyOf(TYPE_LABELS);
    }
}
