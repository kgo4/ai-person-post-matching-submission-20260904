package com.example.matching.service.interview;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 邀请邮件收发件地址判定的回归测试。
 * <p>
 * 这段判断决定「这封邮件会不会被发出去」，判错的后果是员工永远收不到邀请、
 * 而 HR 只看到「已发送」。因此每个分支都必须钉死。
 */
class InterviewMailAddressTest {

    private static final String PLATFORM_FROM = "2253695672@qq.com";

    @Test
    @DisplayName("员工个人邮箱与平台发信邮箱不能判为同一信箱")
    void employeeMailboxIsNotPlatformMailbox() {
        assertThat(InterviewMailAddress.isSameMailbox("zhangsan@example.com", PLATFORM_FROM)).isFalse();
        assertThat(InterviewMailAddress.isSameMailbox("lisi@company.cn", "wangwu@company.cn")).isFalse();
    }

    @Test
    @DisplayName("同一地址必须判为同一信箱")
    void identicalAddressIsSameMailbox() {
        assertThat(InterviewMailAddress.isSameMailbox(PLATFORM_FROM, PLATFORM_FROM)).isTrue();
        assertThat(InterviewMailAddress.isSameMailbox("a@b.com", "a@b.com")).isTrue();
    }

    @Test
    @DisplayName("大小写与首尾空白不影响判定：员工档案里常有多余空格与大小写混写")
    void ignoresCaseAndSurroundingWhitespace() {
        assertThat(InterviewMailAddress.isSameMailbox("  2253695672@QQ.com ", PLATFORM_FROM)).isTrue();
        assertThat(InterviewMailAddress.isSameMailbox("ZhangSan@Example.COM", "zhangsan@example.com")).isTrue();
        assertThat(InterviewMailAddress.isSameMailbox("\tA@B.com\n", "a@b.com")).isTrue();
    }

    @Test
    @DisplayName("空地址是「没填」而不是「同一个信箱」，交给调用方按未维护邮箱提示")
    void blankIsNeverSameMailbox() {
        assertThat(InterviewMailAddress.isSameMailbox(null, PLATFORM_FROM)).isFalse();
        assertThat(InterviewMailAddress.isSameMailbox("", PLATFORM_FROM)).isFalse();
        assertThat(InterviewMailAddress.isSameMailbox("   ", PLATFORM_FROM)).isFalse();
        assertThat(InterviewMailAddress.isSameMailbox(null, null)).isFalse();
        assertThat(InterviewMailAddress.isSameMailbox("", "")).isFalse();
        // 一侧为空时，即便另一侧也是空也不能判相同
        assertThat(InterviewMailAddress.isSameMailbox(PLATFORM_FROM, null)).isFalse();
    }

    @Test
    @DisplayName("归一化：去空白 + 小写；空白输入返回 null")
    void normalize() {
        assertThat(InterviewMailAddress.normalize("  A@B.Com ")).isEqualTo("a@b.com");
        assertThat(InterviewMailAddress.normalize(null)).isNull();
        assertThat(InterviewMailAddress.normalize("")).isNull();
        assertThat(InterviewMailAddress.normalize("   ")).isNull();
    }

    @Test
    @DisplayName("相似但不同的地址不能误判：只差一个字符也必须是不同信箱")
    void nearMissAddressesAreNotSame() {
        assertThat(InterviewMailAddress.isSameMailbox("2253695672@qq.com", "2253695673@qq.com")).isFalse();
        assertThat(InterviewMailAddress.isSameMailbox("2253695672@qq.com", "2253695672@qq.cn")).isFalse();
        assertThat(InterviewMailAddress.isSameMailbox("a.b@qq.com", "ab@qq.com")).isFalse();
    }
}
