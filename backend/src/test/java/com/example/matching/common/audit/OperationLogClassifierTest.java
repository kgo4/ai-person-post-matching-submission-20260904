package com.example.matching.common.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 操作日志归类器的回归测试。
 *
 * <p>背景：{@code OperationLogAspect} 过去把 {@code operationModule} 写死成 {@code "system"}、
 * {@code operationType} 写死成 {@code "UNKNOWN"}，导致「操作日志」页的「操作类型」整列显示 UNKNOWN。
 * 这里锁定两件事：① 归类结果符合预期；② **具体前缀优先于宽前缀**（例如
 * {@code /api/system/user} 不能被 {@code /api/system} 先吃掉，{@code /api/employee/ability/ai-test}
 * 不能被 {@code /api/employee} 先吃掉）。
 */
class OperationLogClassifierTest {

    // ------------------------------------------------------------------ 模块

    @Test
    @DisplayName("按 URI 归类模块：具体前缀优先于宽前缀")
    void classifiesModuleWithMostSpecificPrefixFirst() {
        assertEquals(OperationLogClassifier.MODULE_USER,
                OperationLogClassifier.classifyModule("/api/system/user"));
        assertEquals(OperationLogClassifier.MODULE_USER,
                OperationLogClassifier.classifyModule("/api/system/user/page"));
        assertEquals(OperationLogClassifier.MODULE_ROLE,
                OperationLogClassifier.classifyModule("/api/system/role/1/permissions"));
        assertEquals(OperationLogClassifier.MODULE_EXTEND_FIELD,
                OperationLogClassifier.classifyModule("/api/system/extend-field"));
        assertEquals(OperationLogClassifier.MODULE_ABILITY_TAG,
                OperationLogClassifier.classifyModule("/api/system/tag-candidate/9/proposals"));
        // 未被上述具体规则命中的 /api/system 一律是系统管理
        assertEquals(OperationLogClassifier.MODULE_SYSTEM,
                OperationLogClassifier.classifyModule("/api/system/operation-log"));
        assertEquals(OperationLogClassifier.MODULE_SYSTEM,
                OperationLogClassifier.classifyModule("/api/admin/prompts/reload"));
    }

    @Test
    @DisplayName("AI 测试与爬虫必须先于它们的宽前缀命中")
    void classifiesNestedPrefixesBeforeBroaderOnes() {
        assertEquals(OperationLogClassifier.MODULE_AI_TEST,
                OperationLogClassifier.classifyModule("/api/employee/ability/ai-test/12/redeliver"));
        assertEquals(OperationLogClassifier.MODULE_EMPLOYEE,
                OperationLogClassifier.classifyModule("/api/employee/ability-profile"));
        assertEquals(OperationLogClassifier.MODULE_CRAWLER,
                OperationLogClassifier.classifyModule("/api/post/evolution/crawler/commands"));
        assertEquals(OperationLogClassifier.MODULE_CRAWLER,
                OperationLogClassifier.classifyModule("/api/internal/crawler/heartbeat"));
        assertEquals(OperationLogClassifier.MODULE_POST,
                OperationLogClassifier.classifyModule("/api/post/evolution/tasks"));
    }

    @Test
    @DisplayName("业务模块归类覆盖主要前缀")
    void classifiesBusinessModules() {
        assertEquals(OperationLogClassifier.MODULE_MATCHING,
                OperationLogClassifier.classifyModule("/api/matching/record/execute"));
        assertEquals(OperationLogClassifier.MODULE_LEARNING,
                OperationLogClassifier.classifyModule("/api/learning/path-enhanced/generate-by-mastery"));
        assertEquals(OperationLogClassifier.MODULE_RAG,
                OperationLogClassifier.classifyModule("/api/rag/knowledge"));
        assertEquals(OperationLogClassifier.MODULE_KG,
                OperationLogClassifier.classifyModule("/api/kg/snapshots"));
        assertEquals(OperationLogClassifier.MODULE_GOVERNANCE,
                OperationLogClassifier.classifyModule("/api/ai-governance/harness/checks/1/review"));
        assertEquals(OperationLogClassifier.MODULE_NOTIFICATION,
                OperationLogClassifier.classifyModule("/api/notifications/assessment-reminder"));
    }

    @Test
    @DisplayName("URI 为空或未知前缀落到 OTHER，不抛异常")
    void fallsBackToOther() {
        assertEquals(OperationLogClassifier.MODULE_OTHER, OperationLogClassifier.classifyModule(null));
        assertEquals(OperationLogClassifier.MODULE_OTHER, OperationLogClassifier.classifyModule("  "));
        assertEquals(OperationLogClassifier.MODULE_OTHER, OperationLogClassifier.classifyModule("/actuator/health"));
    }

    // ------------------------------------------------------------------ 类型

    @Test
    @DisplayName("按 HTTP 方法归类操作类型")
    void classifiesTypeByHttpMethod() {
        assertEquals(OperationLogClassifier.TYPE_CREATE, OperationLogClassifier.classifyType("POST"));
        assertEquals(OperationLogClassifier.TYPE_UPDATE, OperationLogClassifier.classifyType("PUT"));
        assertEquals(OperationLogClassifier.TYPE_UPDATE, OperationLogClassifier.classifyType("patch"));
        assertEquals(OperationLogClassifier.TYPE_DELETE, OperationLogClassifier.classifyType("DELETE"));
        assertEquals("QUERY", OperationLogClassifier.classifyType("GET"));
        assertEquals(OperationLogClassifier.TYPE_UNKNOWN, OperationLogClassifier.classifyType("TRACE"));
        assertEquals(OperationLogClassifier.TYPE_UNKNOWN, OperationLogClassifier.classifyType(null));
    }

    // ----------------------------------------------------------- 回填判定

    @Test
    @DisplayName("历史行的 module=system / type=UNKNOWN 判为需要回填")
    void detectsLegacyRowsNeedingBackfill() {
        // 切面过去写死的小写 system —— 任何 URI 都是它，等同于"没填"
        assertTrue(OperationLogClassifier.needsModuleBackfill("system"));
        assertTrue(OperationLogClassifier.needsModuleBackfill(" SYSTEM "));
        assertTrue(OperationLogClassifier.needsModuleBackfill(null));
        assertTrue(OperationLogClassifier.needsModuleBackfill(""));

        // 其他写入方（DLQ / OUTBOX / SCHEDULER / AI_TEST）给出的模块码必须保留
        assertFalse(OperationLogClassifier.needsModuleBackfill("DLQ"));
        assertFalse(OperationLogClassifier.needsModuleBackfill("OUTBOX"));
        assertFalse(OperationLogClassifier.needsModuleBackfill("USER"));

        assertFalse(OperationLogClassifier.isMeaningfulType("UNKNOWN"));
        assertFalse(OperationLogClassifier.isMeaningfulType(" unknown "));
        assertFalse(OperationLogClassifier.isMeaningfulType(null));
        assertTrue(OperationLogClassifier.isMeaningfulType("ALERT"));
        assertTrue(OperationLogClassifier.isMeaningfulType("UPDATE"));
    }

    // ------------------------------------------------------------------ 文案

    @Test
    @DisplayName("模块/类型中文名：已知码翻译，未知码原样透出")
    void producesChineseLabels() {
        assertEquals("用户管理", OperationLogClassifier.moduleLabel("USER"));
        assertEquals("用户管理", OperationLogClassifier.moduleLabel("user"));
        assertEquals("死信队列", OperationLogClassifier.moduleLabel("DLQ"));
        // 未知码不吞信息：原样返回，前端不会显示成空白
        assertEquals("SOMETHING_NEW", OperationLogClassifier.moduleLabel("SOMETHING_NEW"));
        assertEquals("其他", OperationLogClassifier.moduleLabel(null));

        assertEquals("新增", OperationLogClassifier.typeLabel("CREATE"));
        assertEquals("修改", OperationLogClassifier.typeLabel("UPDATE"));
        assertEquals("删除", OperationLogClassifier.typeLabel("DELETE"));
        assertEquals("告警", OperationLogClassifier.typeLabel("ALERT"));
        // 历史遗留值显示为「其他」，而不是把 UNKNOWN 直接甩给用户
        assertEquals("其他", OperationLogClassifier.typeLabel("UNKNOWN"));
    }

    @Test
    @DisplayName("默认操作描述带类型、模块与请求行，不再是写死的「自动记录」")
    void buildsReadableDescription() {
        String desc = OperationLogClassifier.describe(
                OperationLogClassifier.MODULE_USER,
                OperationLogClassifier.TYPE_CREATE,
                "POST",
                "/api/system/user");
        assertEquals("新增 · 用户管理（POST /api/system/user）", desc);

        // 缺 method/uri 时退化为「类型 · 模块」，不出现 null
        String partial = OperationLogClassifier.describe(
                OperationLogClassifier.MODULE_USER, OperationLogClassifier.TYPE_CREATE, null, null);
        assertEquals("新增 · 用户管理", partial);
    }

    @Test
    @DisplayName("前端筛选字典非空且包含主要模块码")
    void exposesLabelDictionaries() {
        assertTrue(OperationLogClassifier.moduleLabels().containsKey("USER"));
        assertTrue(OperationLogClassifier.moduleLabels().containsKey("DLQ"));
        assertTrue(OperationLogClassifier.typeLabels().containsKey("CREATE"));
    }
}
