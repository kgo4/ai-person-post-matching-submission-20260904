package com.example.matching.service.interview;

import java.util.Locale;

/**
 * 视频终面邀请邮件的收发件地址判定。
 * <p>
 * 抽成独立的纯逻辑类是为了能被单测直接覆盖：这段判断决定「这封邮件会不会被发出去」，
 * 一旦判错，后果是员工永远收不到邀请而 HR 只看到「已发送」。
 *
 * @author system
 */
public final class InterviewMailAddress {

    private InterviewMailAddress() {
    }

    /**
     * 归一化邮箱地址：去掉首尾空白并统一小写。
     *
     * @return 归一化后的地址；{@code null} / 空白返回 {@code null}
     */
    public static String normalize(String address) {
        if (address == null) {
            return null;
        }
        String trimmed = address.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    /**
     * 两个地址是否指向同一个信箱。
     * <p>
     * 忽略大小写与首尾空白：邮箱域名部分本就大小写不敏感；本地部分虽然 RFC 允许敏感，
     * 但所有主流邮件服务商实际都按不敏感处理，而这里唯一的目的是「别把邮件发给自己」，
     * 宁可判得宽一点也不能漏拦。
     * <p>
     * 任一侧为空时返回 {@code false}：空地址是「没填」，不是「同一个信箱」，
     * 交给调用方用「未维护邮箱」的原因单独提示。
     */
    public static boolean isSameMailbox(String left, String right) {
        String a = normalize(left);
        String b = normalize(right);
        return a != null && a.equals(b);
    }
}
