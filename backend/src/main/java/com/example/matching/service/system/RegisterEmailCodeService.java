package com.example.matching.service.system;

import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Value;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * 注册邮箱验证码：发送、冷却与校验。
 *
 * <p>三条硬约束（都对应真实会被滥用的点）：
 * <ol>
 *   <li><b>60 秒冷却</b>：同一邮箱 60 秒内只能获取一次。用 Redis {@code SET NX EX} 实现，
 *       保证并发请求下也只有一个能成功（先查后写会有竞态窗口）。</li>
 *   <li><b>有效期 5 分钟</b>：验证码本身带 TTL，过期自动失效。</li>
 *   <li><b>错误次数上限</b>：连续输错 5 次即作废该验证码，防止 6 位码被暴力枚举。</li>
 * </ol>
 *
 * <p><b>降级策略</b>：Redis 不可用时**拒绝发送**，而不是放行。
 * 验证码是安全凭证，缓存挂了还继续发码会出现"无冷却、无限次重试"的窗口；
 * 宁可让注册暂时不可用（可感知、可修），也不要静默放开。
 *
 * <p>开发环境通过 {@code auth.register.code-expose-in-response=true} 把验证码一并返回，
 * 便于没有邮件通道时联调；**生产必须保持 false**。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegisterEmailCodeService {

    /** 验证码有效期 */
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    /** 获取冷却 */
    private static final Duration SEND_COOLDOWN = Duration.ofSeconds(60);
    /** 单个验证码的最大校验失败次数 */
    private static final int MAX_VERIFY_ATTEMPTS = 5;

    private static final String KEY_CODE = "auth:register:code:";
    private static final String KEY_COOLDOWN = "auth:register:cd:";
    private static final String KEY_ATTEMPT = "auth:register:try:";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ObjectProvider<org.springframework.data.redis.core.StringRedisTemplate> redisProvider;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final com.example.matching.config.RegisterProperties registerProperties;

    /**
     * 固定 SMTP 发件账号。注册表单中的邮箱只允许作为收件人，不能参与 From。
     * QQ SMTP 会校验 From 必须与授权账号一致。
     */
    @Value("${spring.mail.username:}")
    private String mailUsername;

    /**
     * 发送验证码。
     *
     * @param email 目标邮箱（已由调用方做格式校验）
     * @return 生成的验证码；正常路径下**不应**回传给前端（仅联调开关打开时回传）
     */
    public String send(String email) {
        String normalized = normalize(email);
        var redis = redisProvider.getIfAvailable();
        if (redis == null) {
            // 见类注释：缓存不可用时不放行，避免冷却与枚举保护同时失效
            throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "验证码服务暂时不可用，请稍后重试");
        }

        String cooldownKey = KEY_COOLDOWN + normalized;
        Boolean acquired = redis.opsForValue()
                .setIfAbsent(cooldownKey, "1", SEND_COOLDOWN);
        if (!Boolean.TRUE.equals(acquired)) {
            Long ttl = redis.getExpire(cooldownKey);
            long remain = (ttl != null && ttl > 0) ? ttl : SEND_COOLDOWN.toSeconds();
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                    "验证码发送过于频繁，请 " + remain + " 秒后再试");
        }

        String code = randomCode();
        redis.opsForValue().set(KEY_CODE + normalized, code, CODE_TTL);
        // 重新发码即重置失败计数，否则上一次的输错会把新码一起废掉
        redis.delete(KEY_ATTEMPT + normalized);

        try {
            sendMail(normalized, code);
        } catch (RuntimeException e) {
            // 发信失败要**把冷却一起清掉**，否则用户被白等 60 秒，而验证码根本没发出去
            redis.delete(cooldownKey);
            redis.delete(KEY_CODE + normalized);
            log.warn("注册验证码邮件发送失败: email={}, error={}", normalized, e.getMessage());
            throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "验证码发送失败，请稍后重试");
        }

        log.info("注册验证码已发送: email={}", normalized);
        return code;
    }

    /**
     * 校验验证码。校验通过后**立即删除**，保证一次性使用。
     *
     * <p>失败时递增尝试次数，达到上限直接删除验证码 —— 之后即使输对也必须重新获取，
     * 避免 6 位码被在线枚举。
     */
    public void verify(String email, String providedCode) {
        String normalized = normalize(email);
        if (!StringUtils.hasText(providedCode)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "请填写邮箱验证码");
        }
        var redis = redisProvider.getIfAvailable();
        if (redis == null) {
            throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "验证码服务暂时不可用，请稍后重试");
        }

        String key = KEY_CODE + normalized;
        String expected = redis.opsForValue().get(key);
        if (!StringUtils.hasText(expected)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "验证码已过期，请重新获取");
        }
        if (!expected.equals(providedCode.trim())) {
            Long attempts = redis.opsForValue().increment(KEY_ATTEMPT + normalized);
            if (attempts != null && attempts >= MAX_VERIFY_ATTEMPTS) {
                redis.delete(key);
                redis.delete(KEY_ATTEMPT + normalized);
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "验证码错误次数过多，请重新获取");
            }
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "邮箱验证码不正确");
        }

        // 一次性：用过即作废，防止同一验证码注册多个账号
        redis.delete(key);
        redis.delete(KEY_ATTEMPT + normalized);
    }

    /** 联调开关：是否在响应里回传验证码（生产必须 false）。 */
    public boolean isCodeExposedInResponse() {
        return registerProperties.isCodeExposeInResponse();
    }

    private void sendMail(String email, String code) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("未配置邮件发送器（spring.mail.*）");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        if (StringUtils.hasText(mailUsername)) {
            message.setFrom(mailUsername.trim());
        }
        message.setTo(email);
        message.setSubject("【多源异构岗位与能力图谱平台】注册验证码");
        message.setText("您的注册验证码是：" + code + "\n\n"
                + "验证码 5 分钟内有效，请勿转发给他人。\n"
                + "如非本人操作，请忽略本邮件。");
        sender.send(message);
    }

    private static String randomCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private static String normalize(String email) {
        if (!StringUtils.hasText(email)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "请填写邮箱");
        }
        return email.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
