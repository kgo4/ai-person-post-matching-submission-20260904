package com.example.matching.entity.interview;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * HR-员工视频终面记录（HR 匹配闭环 P5）。
 *
 * <p>平台不做媒体：只管「发起邀请 → 通知 → 留痕 → 定论」，媒体走讯飞会议（链接由 HR 粘贴）。
 * 设计文档：docs/hr-matching-closed-loop-design.md 5.3 节。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("emp_communication_interview")
public class EmpCommunicationInterview implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 待沟通 */
    public static final int STATUS_PENDING = 0;
    /** 已完成（HR 已录入结论） */
    public static final int STATUS_FINISHED = 1;
    /** 已取消 */
    public static final int STATUS_CANCELLED = 2;

    /** 结论：通过（人工沟通确认） */
    public static final int RESULT_PASS = 1;
    /** 结论：不通过 */
    public static final int RESULT_FAIL = 2;
    /** 结论：待定（可改期再谈） */
    public static final int RESULT_UNDECIDED = 3;

    /** 员工响应：接受终面 */
    public static final int RESPONSE_ACCEPTED = 1;
    /** 员工响应：放弃终面（记录随之置为已取消） */
    public static final int RESPONSE_DECLINED = 2;

    /** 邀请邮件：尚未发送（初始值） */
    public static final int MAIL_STATUS_NOT_SENT = 0;
    /** 邀请邮件：已发送成功 */
    public static final int MAIL_STATUS_SENT = 1;
    /** 邀请邮件：发送失败（SMTP 报错，原因存 inviteMailError） */
    public static final int MAIL_STATUS_FAILED = 2;
    /** 邀请邮件：跳过（未配置发信账号 / 员工档案没有邮箱） */
    public static final int MAIL_STATUS_SKIPPED = 3;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long empId;

    /** 关联岗位；沟通可发生在匹配记录之外时为空 */
    private Long postId;

    /** 关联的匹配记录（若有） */
    private Long matchingRecordId;

    /** 会议邀请链接（域名走可配置白名单） */
    private String meetingUrl;

    /** MANUAL 手动粘贴 / API 自动创建（预留） */
    private String meetingSource;

    /** 预约沟通时间；空=尽快 */
    private LocalDateTime scheduledTime;

    /** 0待沟通 1已完成 2已取消 */
    private Integer status;

    /** 沟通结论：1通过 2不通过 3待定 */
    private Integer result;

    /** HR 沟通纪要与评价（员工侧可读原文） */
    private String comment;

    /** 发起时的沟通要点快照 */
    private String briefingSnapshot;

    /** 员工响应：1接受 2放弃；空=尚未响应 */
    private Integer employeeResponse;

    /** 员工放弃时的说明（可选） */
    private String employeeResponseComment;

    /** 员工响应时间 */
    private LocalDateTime employeeRespondedTime;

    /** 邀请邮件状态：0未发送 1已发送 2发送失败 3已跳过（未配置发信账号 / 员工无邮箱） */
    private Integer inviteMailStatus;

    /** 邀请邮件最近一次尝试时间 */
    private LocalDateTime inviteMailTime;

    /** 邀请邮件失败原因（截断存储，仅用于排查） */
    private String inviteMailError;

    /** 发起的 HR 用户ID */
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.UPDATE)
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    /** 录入结论时间 */
    private LocalDateTime finishedTime;

    @TableLogic
    private Integer isDeleted;

    @Version
    private Integer version;
}
