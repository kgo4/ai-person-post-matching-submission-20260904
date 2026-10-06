package com.example.matching.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 注册角色授权码规则。
 *
 * <p>这块是**安全边界**（注册端点 permitAll），所以断言要覆盖"配错了会怎样"，
 * 而不只是"配对了能过"：
 * <ul>
 *   <li>未配置的角色必须**不可注册**（白名单语义）—— 漏配的后果必须是"不能用"，
 *       不能是"随便用"；</li>
 *   <li>授权码为空串时同样不可注册（环境变量存在但没填值的情况很常见）；</li>
 *   <li>授权码大小写敏感、必须全等，不能出现"前缀对就算过"。</li>
 * </ul>
 */
class RegisterRoleCodePropertiesTest {

    private static RegisterRoleCodeProperties props(Map<String, String> codes) {
        RegisterRoleCodeProperties p = new RegisterRoleCodeProperties();
        p.setRoleCodes(new java.util.LinkedHashMap<>(codes));
        return p;
    }

    @Test
    @DisplayName("员工始终可注册且不需要授权码")
    void employeeAlwaysOpenAndNeedsNoCode() {
        RegisterRoleCodeProperties p = props(Map.of());

        assertThat(p.isRoleOpenForRegistration("EMPLOYEE")).isTrue();
        assertThat(p.isRoleOpenForRegistration("employee")).isTrue();
        // 员工：即使什么都不传也判定通过
        assertThat(p.matchesCode("EMPLOYEE", null)).isTrue();
        assertThat(p.matchesCode("employee", "")).isTrue();
    }

    @Test
    @DisplayName("未配置的角色一律不可注册（白名单，而不是黑名单）")
    void unconfiguredRoleIsClosed() {
        RegisterRoleCodeProperties p = props(Map.of());

        assertThat(p.isRoleOpenForRegistration("PLATFORM_ADMIN")).isFalse();
        assertThat(p.isRoleOpenForRegistration("HR_SPECIALIST")).isFalse();
        assertThat(p.isRoleOpenForRegistration("JOB_ARCHITECT")).isFalse();
        // 连未知角色也不能通过
        assertThat(p.isRoleOpenForRegistration("SOMETHING_ELSE")).isFalse();
        assertThat(p.matchesCode("PLATFORM_ADMIN", "anything")).isFalse();
    }

    @Test
    @DisplayName("配置为空串等同于未配置（环境变量存在但没填值）")
    void blankConfiguredCodeIsTreatedAsClosed() {
        RegisterRoleCodeProperties p = props(Map.of("PLATFORM_ADMIN", "   "));

        assertThat(p.isRoleOpenForRegistration("PLATFORM_ADMIN")).isFalse();
        assertThat(p.matchesCode("PLATFORM_ADMIN", "   ")).isFalse();
        assertThat(p.matchesCode("PLATFORM_ADMIN", "")).isFalse();
    }

    @Test
    @DisplayName("配置了授权码的角色：码对才可注册，且大小写敏感、不接受前缀")
    void configuredRoleRequiresExactCode() {
        RegisterRoleCodeProperties p = props(Map.of(
                "HR_SPECIALIST", "HR-2026-KB",
                "JOB_ARCHITECT", "JA-2026-KB",
                "PLATFORM_ADMIN", "PA-2026-KB"));

        assertThat(p.isRoleOpenForRegistration("HR_SPECIALIST")).isTrue();
        assertThat(p.matchesCode("HR_SPECIALIST", "HR-2026-KB")).isTrue();
        // 前后空格容忍（用户复制粘贴常带空格），但内容必须完全一致
        assertThat(p.matchesCode("HR_SPECIALIST", "  HR-2026-KB  ")).isTrue();
        // 大小写敏感：这是凭证，不该有歧义
        assertThat(p.matchesCode("HR_SPECIALIST", "hr-2026-kb")).isFalse();
        // 前缀不算（防止 "HR-2026-KB-xxx" 这种放大试探）
        assertThat(p.matchesCode("HR_SPECIALIST", "HR-2026-KBX")).isFalse();
        assertThat(p.matchesCode("HR_SPECIALIST", "HR-2026")).isFalse();
        // 空值不算
        assertThat(p.matchesCode("HR_SPECIALIST", null)).isFalse();
        assertThat(p.matchesCode("HR_SPECIALIST", "")).isFalse();
        // 拿 A 角色的码注册 B 角色必须失败
        assertThat(p.matchesCode("PLATFORM_ADMIN", "HR-2026-KB")).isFalse();
    }

    @Test
    @DisplayName("角色码大小写与空格要容错（前端可能传小写）")
    void roleCodeNormalization() {
        RegisterRoleCodeProperties p = props(Map.of("PLATFORM_ADMIN", "PA-2026-KB"));

        assertThat(p.isRoleOpenForRegistration(" platform_admin ")).isTrue();
        assertThat(p.matchesCode(" platform_admin ", "PA-2026-KB")).isTrue();
    }

    @Test
    @DisplayName("空角色码不可注册（避免不传角色即绕过）")
    void blankRoleCodeIsClosed() {
        RegisterRoleCodeProperties p = props(Map.of("PLATFORM_ADMIN", "PA-2026-KB"));

        assertThat(p.isRoleOpenForRegistration(null)).isFalse();
        assertThat(p.isRoleOpenForRegistration("")).isFalse();
        assertThat(p.isRoleOpenForRegistration("   ")).isFalse();
    }

    /* ============ 可诊断性：区分「未开放」与「管理员漏配环境变量」 ============ */

    @Test
    @DisplayName("配置表里占位但值为空 → 判定为「漏配」，用于给出正确提示")
    void configuredButBlankDetected() {
        // application.yml 把三个角色都占了位，环境变量没配时值就是空串 ——
        // 这正是注册页报「该角色未开放自助注册」的真实原因。
        RegisterRoleCodeProperties p = props(new java.util.LinkedHashMap<>(Map.of(
                "HR_SPECIALIST", "",
                "JOB_ARCHITECT", "",
                "PLATFORM_ADMIN", "")));

        assertThat(p.isRoleOpenForRegistration("HR_SPECIALIST")).isFalse();
        assertThat(p.isRoleConfiguredButBlank("HR_SPECIALIST")).isTrue();
        assertThat(p.isRoleConfiguredButBlank("JOB_ARCHITECT")).isTrue();
        assertThat(p.isRoleConfiguredButBlank("PLATFORM_ADMIN")).isTrue();
        // 全空白也算漏配（不是"配了个空格码"）
        assertThat(props(Map.of("HR_SPECIALIST", "   ")).isRoleConfiguredButBlank("HR_SPECIALIST")).isTrue();
    }

    @Test
    @DisplayName("配了有效码 → 不算漏配；配了对的码必须能过")
    void configuredWithValueIsNotBlank() {
        RegisterRoleCodeProperties p = props(Map.of("HR_SPECIALIST", "HR-2026-KB"));

        assertThat(p.isRoleConfiguredButBlank("HR_SPECIALIST")).isFalse();
        assertThat(p.isRoleOpenForRegistration("HR_SPECIALIST")).isTrue();
        assertThat(p.matchesCode("HR_SPECIALIST", "HR-2026-KB")).isTrue();
    }

    @Test
    @DisplayName("完全不在表里的角色（SUPER_ADMIN/未知）不算「漏配」，属真「未开放」")
    void unknownRoleIsNotBlankConfigured() {
        RegisterRoleCodeProperties p = props(Map.of("HR_SPECIALIST", "HR-2026-KB"));

        // SUPER_ADMIN 刻意不开放注册，也不该被当成"忘了配"
        assertThat(p.isRoleConfiguredButBlank("SUPER_ADMIN")).isFalse();
        assertThat(p.isRoleConfiguredButBlank("SOMETHING_ELSE")).isFalse();
        assertThat(p.isRoleConfiguredButBlank(null)).isFalse();
        assertThat(p.isRoleConfiguredButBlank("")).isFalse();
        // 员工本就不需要授权码，不算漏配
        assertThat(p.isRoleConfiguredButBlank("EMPLOYEE")).isFalse();
    }

    @Test
    @DisplayName("openRoleCodes 只列已配有效码的角色（启动自检用）")
    void openRoleCodesListsOnlyConfigured() {
        RegisterRoleCodeProperties p = props(new java.util.LinkedHashMap<>(Map.of(
                "HR_SPECIALIST", "HR-2026-KB",
                "JOB_ARCHITECT", "",
                "PLATFORM_ADMIN", "PA-2026-KB")));

        assertThat(p.openRoleCodes()).containsExactly("HR_SPECIALIST", "PLATFORM_ADMIN");
        // 全未配置时为空 —— 说明除员工外都注册不了
        assertThat(props(Map.of()).openRoleCodes()).isEmpty();
    }
}
