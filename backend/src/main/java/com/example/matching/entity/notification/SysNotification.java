package com.example.matching.entity.notification;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 站内通知。
 * <p>
 * HR 匹配闭环 P1 基础设施：提醒评估、报告就绪、学习成果复核、会议邀请、匹配推送
 * 等场景共用的通知模型。防重依赖唯一键 {@code uk_notification_dedup}（接收人 + 类型 +
 * 业务类型 + 业务ID），因此服务层发送时必须提供 bizType 与 bizId。
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("sys_notification")
public class SysNotification implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 通知类型常量，避免调用方散落魔法字符串 */
    public static final String TYPE_ASSESSMENT_REMINDER = "ASSESSMENT_REMINDER";
    public static final String TYPE_REPORT_READY = "REPORT_READY";
    public static final String TYPE_LEARNING_REVIEW = "LEARNING_REVIEW";
    public static final String TYPE_INVITE_MEETING = "INVITE_MEETING";

    /**
     * 视频终面结论已更新（P5）。
     * <p>必须与 {@link #TYPE_INVITE_MEETING} 分开：两者 receiver / bizType / bizId 完全相同，
     * 若共用同一 type，结论通知会被防重唯一键当成重复发送拦掉。</p>
     */
    public static final String TYPE_INTERVIEW_RESULT = "INTERVIEW_RESULT";

    /**
     * 员工已响应视频终面邀请（接受 / 放弃）—— 发给发起该场终面的 HR。
     * <p>同样必须与 {@link #TYPE_INVITE_MEETING}、{@link #TYPE_INTERVIEW_RESULT} 分开：
     * 三者 receiver 可能是不同人但 bizType/bizId 相同，共用 type 会被防重唯一键拦掉。</p>
     * <p>员工对本场终面的响应是**一次性的**（见 {@code CommunicationInterviewService.respond}），
     * 因此 (HR, 该 type, INTERVIEW, 终面ID) 天然只会写入一条，不会互相顶掉。</p>
     */
    public static final String TYPE_INTERVIEW_RESPONSE = "INTERVIEW_RESPONSE";
    public static final String TYPE_MATCHING_PUBLISHED = "MATCHING_PUBLISHED";

    /** 业务类型常量：通知点击后的跳转去向 */
    public static final String BIZ_EMPLOYEE = "EMPLOYEE";
    public static final String BIZ_MATCHING_RECORD = "MATCHING_RECORD";
    public static final String BIZ_REPORT = "REPORT";
    public static final String BIZ_INTERVIEW = "INTERVIEW";

    /**
     * 学习成果提交单（P4 复核闭环）。
     * <p>用 bizId = 提交单ID 而非 empId —— 同一员工多次提交是不同单据，各自都要触发通知，
     * 若用 empId 会被防重唯一键拦掉第二次。</p>
     */
    public static final String BIZ_LEARNING_OUTCOME = "LEARNING_OUTCOME";

    /** 未读 / 已读 */
    public static final int READ_STATUS_UNREAD = 0;
    public static final int READ_STATUS_READ = 1;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 接收人用户ID */
    private Long receiverUserId;

    /** 通知类型，见本类 TYPE_ 常量 */
    private String type;

    private String title;

    private String content;

    /** 业务类型，见本类 BIZ_ 常量；前端据此跳转 */
    private String bizType;

    /** 业务主键 */
    private Long bizId;

    /** 0未读 1已读 */
    private Integer readStatus;

    private LocalDateTime readTime;

    /** 发送人用户ID；系统自动触发时为空 */
    private Long createdBy;

    private LocalDateTime createdTime;
}
