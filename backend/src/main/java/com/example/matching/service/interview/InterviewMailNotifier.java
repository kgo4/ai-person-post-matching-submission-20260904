package com.example.matching.service.interview;

import com.example.matching.dto.matching.MatchingPostProfile;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.interview.EmpCommunicationInterview;
import com.example.matching.entity.system.SysUser;
import com.example.matching.event.CommunicationInterviewCreatedEvent;
import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.mapper.interview.EmpCommunicationInterviewMapper;
import com.example.matching.mapper.system.SysUserMapper;
import com.example.matching.service.matching.MatchingDataQueryService;
import jakarta.mail.internet.InternetAddress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 视频终面邀请邮件（QQ 邮箱 SMTP）。
 *
 * <p>定位：站内通知之外的**补充通道**。HR 发起终面时，除了给员工写站内通知，
 * 还要往员工档案里的邮箱发一封邀请邮件，提醒他按约定时间登录平台参加视频终面，
 * 并在平台内「接受 / 放弃」。</p>
 *
 * <p>复用既有异步基建而不是另起一套：监听 {@link CommunicationInterviewCreatedEvent}，
 * 用 {@code @Async("applicationTaskExecutor")} + {@code AFTER_COMMIT} 在事务提交后发送 ——
 * 与 {@code CommunicationInterviewBriefingListener} 完全同范式。因此：</p>
 * <ul>
 *   <li>发起邀约（用户可感知的主流程）立刻返回，不被 SMTP 往返拖慢；</li>
 *   <li>异步任务读到的一定是已提交的终面记录；</li>
 *   <li>SMTP 超时/授权码失效等任何异常都只留痕 + 记日志，绝不回抛，
 *       更不会让「HR 看到发起失败、真实原因却与会议毫无关系」。</li>
 * </ul>
 *
 * <p>发件地址固定为平台发信邮箱（{@code communication-interview.mail.from-address}），
 * 收件地址取自员工档案里的<b>个人邮箱</b>（档案为空时回落到登录账号邮箱），两者含义完全不同。
 * 若两者恰好相同，邮件会被<b>拦下</b>并记为失败 —— 那种邮件实际是发给平台自己，员工永远不会收到，
 * 而 HR 只看到「已发送」，会把「员工没收到」误解成对方没看邮件。</p>
 *
 * <p>未配置发信账号、员工档案与登录账号都没有邮箱时，邮件状态记为「已跳过」，
 * 原因写入 {@code inviteMailError}，便于 HR 在追踪视图上看出「为什么员工没收到邮件」。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewMailNotifier {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm");
    private static final int MAX_ERROR_LENGTH = 450;

    private final EmpCommunicationInterviewMapper interviewMapper;
    private final EmpEmployeeMapper empEmployeeMapper;
    private final SysUserMapper sysUserMapper;
    private final MatchingDataQueryService dataQuery;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    /** 邮件总开关：关掉后不发邀请邮件，站内通知与业务流转完全不受影响 */
    @Value("${communication-interview.mail.enabled:true}")
    private boolean mailEnabled;

    /** 发件人显示名；真实发件地址取 {@link #fromAddress} */
    @Value("${communication-interview.mail.from-name:多源异构岗位与能力图谱}")
    private String fromName;

    /** 平台入口地址（可选）：配置后邮件正文会带一个「前往平台确认」按钮 */
    @Value("${communication-interview.mail.platform-url:}")
    private String platformUrl;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    /**
     * 发件邮箱：全平台固定的平台发信地址，与员工个人邮箱无关。
     * <p>
     * 此前发件地址直接取 {@code spring.mail.username}（SMTP 认证账号），
     * 于是「这封信到底是谁发的」取决于环境变量配了什么，线上排查「为什么收发件是同一个邮箱」
     * 时无从下手。这里显式声明，再单独校验它与认证账号的一致性。
     */
    @Value("${communication-interview.mail.from-address:${spring.mail.username:}}")
    private String fromAddress;

    /**
     * 终面发起后异步发邀请邮件。
     * <p>范式与 {@code CommunicationInterviewBriefingListener} 一致：AFTER_COMMIT + 主线程池。</p>
     */
    @Async("applicationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onInterviewCreated(CommunicationInterviewCreatedEvent event) {
        try {
            sendInviteMailQuietly(event.interviewId());
        } catch (Exception e) {
            // 双保险：sendInviteMailQuietly 内部已逐层兜底，这里只防「连兜底都抛了」的极端情况
            log.warn("视频终面邀请邮件任务异常（不影响已发起的终面）: interviewId={}",
                    event.interviewId(), e);
        }
    }

    /**
     * 发送邀请邮件并回写发送状态。
     *
     * <p>方法本身不抛异常：邮件是补充通道，失败只能留痕，不能影响终面记录。</p>
     */
    public void sendInviteMailQuietly(Long interviewId) {
        EmpCommunicationInterview interview = interviewMapper.selectById(interviewId);
        if (interview == null) {
            log.warn("邀请邮件跳过：终面记录不存在 interviewId={}", interviewId);
            return;
        }

        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (!mailEnabled || sender == null || !StringUtils.hasText(mailUsername)) {
            markMailStatus(interviewId, EmpCommunicationInterview.MAIL_STATUS_SKIPPED,
                    "未配置发信账号（需设置 spring.mail.username 与 spring.mail.password 授权码）");
            return;
        }

        String from = resolveFromAddress();
        if (!StringUtils.hasText(from)) {
            markMailStatus(interviewId, EmpCommunicationInterview.MAIL_STATUS_SKIPPED,
                    "未配置发件邮箱（需设置 communication-interview.mail.from-address 或 spring.mail.username）");
            return;
        }
        if (!InterviewMailAddress.isSameMailbox(from, mailUsername)) {
            // 不直接判失败——少数邮件服务允许用已验证的别名发信。但必须留痕：
            // 一旦被拒，服务商给的是「553 Mail from must equal authorized user」这类报错，
            // 对使用者毫无指向性，只看这行日志才能立刻明白是配置冲突。
            log.warn("发件邮箱与 SMTP 认证账号不一致，可能被邮件服务商拒发: interviewId={}, from={}, username={}",
                    interviewId, from, mailUsername);
        }

        EmpEmployee employee = empEmployeeMapper.selectById(interview.getEmpId());
        String to = employee == null ? null : employee.getEmail();
        if (!StringUtils.hasText(to)) {
            // 档案邮箱为空时回落到账号邮箱：员工在个人中心维护的邮箱历史上未必同步进档案，
            // 此时发得出去远好过静默跳过（HR 会把「跳过」当成系统坏了）。
            to = resolveAccountEmail(employee);
        }
        if (!StringUtils.hasText(to)) {
            markMailStatus(interviewId, EmpCommunicationInterview.MAIL_STATUS_SKIPPED,
                    "员工档案与登录账号都没有维护邮箱，无法发送邀请邮件");
            return;
        }
        if (InterviewMailAddress.isSameMailbox(to, from)) {
            // 必须拦下来，不能照发：收件人等于发件人时这封信实际是发给了平台自己，
            // 员工永远收不到，而 HR 那边显示「已发送」，会把「员工没收到」误解成对方没看邮件。
            markMailStatus(interviewId, EmpCommunicationInterview.MAIL_STATUS_FAILED,
                    "收件邮箱与发件邮箱相同（" + to.trim() + "）：员工档案里的邮箱未维护，"
                            + "或误填成了平台发信邮箱。请在「人员档案」中改为该员工的个人邮箱后重新发起终面。");
            log.warn("邀请邮件已拦截：收件邮箱与发件邮箱相同: interviewId={}, empId={}, mail={}",
                    interviewId, interview.getEmpId(), to.trim());
            return;
        }

        try {
            MimeMessageHelper helper = new MimeMessageHelper(
                    sender.createMimeMessage(), false, StandardCharsets.UTF_8.name());
            helper.setFrom(buildFrom());
            helper.setTo(to.trim());
            helper.setSubject(buildSubject(interview));
            helper.setText(buildHtml(employee, interview), true);
            sender.send(helper.getMimeMessage());

            markMailStatus(interviewId, EmpCommunicationInterview.MAIL_STATUS_SENT, null);
            log.info("视频终面邀请邮件已发送: interviewId={}, to={}", interviewId, to);
        } catch (Exception e) {
            markMailStatus(interviewId, EmpCommunicationInterview.MAIL_STATUS_FAILED, e.getMessage());
            log.warn("视频终面邀请邮件发送失败（站内通知与终面记录均不受影响）: interviewId={}, to={}",
                    interviewId, to, e);
        }
    }

    /* ===================== 内部 ===================== */

    private InternetAddress buildFrom() throws Exception {
        String address = resolveFromAddress();
        String personal = StringUtils.hasText(fromName) ? fromName : address;
        return new InternetAddress(address, personal, StandardCharsets.UTF_8.name());
    }

    /**
     * 真实发件地址：优先用显式配置的平台发件邮箱，未配置时回落到 SMTP 认证账号。
     *
     * @return 发件地址；两者都未配置时返回 {@code null}
     */
    private String resolveFromAddress() {
        if (StringUtils.hasText(fromAddress)) {
            return fromAddress.trim();
        }
        return StringUtils.hasText(mailUsername) ? mailUsername.trim() : null;
    }

    /**
     * 员工档案没邮箱时的回落取数：读账号（{@code sys_user}）上的邮箱。
     * <p>
     * 档案邮箱（{@code emp_employee.email}）是首选取数源 —— HR 在人员档案里维护、
     * 员工在个人中心改邮箱时也会同步过去。但历史数据里存在「员工改过账号邮箱、档案仍为空」的行，
     * 直接判为「未维护邮箱」会让 HR 以为系统坏了。
     *
     * @return 账号邮箱；员工未绑定账号或账号也没有邮箱时返回 {@code null}
     */
    private String resolveAccountEmail(EmpEmployee employee) {
        if (employee == null || employee.getUserId() == null) {
            return null;
        }
        try {
            SysUser user = sysUserMapper.selectById(employee.getUserId());
            return user == null ? null : user.getEmail();
        } catch (Exception e) {
            // 回落查询失败不该让整封信变成异常：按「未维护邮箱」处理即可
            log.debug("邀请邮件：回落读取账号邮箱失败: empId={}", employee.getId(), e);
            return null;
        }
    }

    private String buildSubject(EmpCommunicationInterview interview) {
        if (interview.getScheduledTime() == null) {
            return "【视频终面邀请】请确认是否参加";
        }
        return "【视频终面邀请】请确认是否参加（" + TIME_FORMAT.format(interview.getScheduledTime()) + "）";
    }

    /** 邮件正文：内联样式，兼容常见邮箱客户端 */
    private String buildHtml(EmpEmployee employee, EmpCommunicationInterview interview) {
        String name = StringUtils.hasText(employee.getRealName()) ? employee.getRealName() : "你好";
        String timeText = interview.getScheduledTime() == null
                ? "待定（HR 将再与你确认具体时间）"
                : TIME_FORMAT.format(interview.getScheduledTime());
        String postName = resolvePostName(interview.getPostId());
        String meetingUrl = interview.getMeetingUrl();

        StringBuilder sb = new StringBuilder(1024);
        sb.append("<div style=\"font-family:'Microsoft YaHei',Arial,sans-serif;font-size:14px;"
                + "color:#1f2329;line-height:1.8;\">");
        sb.append("<p>").append(escapeHtml(name)).append("，你好：</p>");
        sb.append("<p>HR 邀请你参加一次<strong>视频终面</strong>");
        if (StringUtils.hasText(postName)) {
            sb.append("（岗位：").append(escapeHtml(postName)).append("）");
        }
        sb.append("，请按约定时间参加。</p>");

        sb.append("<table style=\"border-collapse:collapse;margin:14px 0;\">");
        appendRow(sb, "面试时间", escapeHtml(timeText));
        appendRow(sb, "会议链接", "<a href=\"" + escapeHtml(meetingUrl) + "\">"
                + escapeHtml(meetingUrl) + "</a>");
        sb.append("</table>");

        sb.append("<p>请登录平台，在「我的能力 → 我的视频沟通」中点击<strong>接受</strong>"
                + "或<strong>放弃</strong>，以便 HR 及时安排后续流程；"
                + "若选择放弃，可一并说明原因。</p>");
        if (StringUtils.hasText(platformUrl)) {
            sb.append("<p style=\"margin:18px 0;\"><a href=\"").append(escapeHtml(platformUrl))
                    .append("\" style=\"display:inline-block;padding:9px 20px;background:#3370ff;"
                            + "color:#ffffff;border-radius:4px;text-decoration:none;\">前往平台确认</a></p>");
        }
        sb.append("<p style=\"color:#8f959e;font-size:12px;\">本邮件由系统自动发送，"
                + "请勿直接回复。如需改期，请与 HR 联系。</p>");
        sb.append("</div>");
        return sb.toString();
    }

    private void appendRow(StringBuilder sb, String label, String valueHtml) {
        sb.append("<tr>")
                .append("<td style=\"padding:7px 14px;background:#f5f6f7;border:1px solid #e5e6eb;")
                .append("white-space:nowrap;\">").append(label).append("</td>")
                .append("<td style=\"padding:7px 14px;border:1px solid #e5e6eb;\">")
                .append(valueHtml).append("</td>")
                .append("</tr>");
    }

    /** 岗位名是「锦上添花」：取不到就省略，绝不因为它失败而把整封邮件判为发送失败 */
    private String resolvePostName(Long postId) {
        if (postId == null) {
            return null;
        }
        try {
            return dataQuery.findPostsForMatching(List.of(postId)).stream()
                    .findFirst()
                    .map(MatchingPostProfile::postName)
                    .orElse(null);
        } catch (Exception e) {
            log.debug("邀请邮件：岗位名补全失败（正文省略岗位名）: postId={}", postId, e);
            return null;
        }
    }

    private void markMailStatus(Long interviewId, int status, String error) {
        try {
            EmpCommunicationInterview update = new EmpCommunicationInterview();
            update.setId(interviewId);
            update.setInviteMailStatus(status);
            update.setInviteMailTime(LocalDateTime.now());
            update.setInviteMailError(abbreviate(error));
            interviewMapper.updateById(update);
        } catch (Exception e) {
            log.warn("邀请邮件状态回写失败（不影响终面记录）: interviewId={}", interviewId, e);
        }
    }

    private String abbreviate(String error) {
        if (!StringUtils.hasText(error)) {
            return null;
        }
        String flat = error.replaceAll("\\s+", " ").trim();
        return flat.length() <= MAX_ERROR_LENGTH ? flat : flat.substring(0, MAX_ERROR_LENGTH);
    }

    private String escapeHtml(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}


