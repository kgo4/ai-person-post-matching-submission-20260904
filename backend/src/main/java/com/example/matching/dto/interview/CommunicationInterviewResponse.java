package com.example.matching.dto.interview;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 视频终面记录视图（HR 匹配闭环 P5）。
 *
 * <p>员工侧只读消费：一经发起即可见状态与邀约信息，HR 录入结论后可读最终结果与
 * HR 评价**原文**（需求方明确要求不脱敏）。</p>
 */
@Schema(description = "视频终面记录")
public record CommunicationInterviewResponse(
        @Schema(description = "记录ID") Long id,
        @Schema(description = "员工档案ID") Long empId,
        @Schema(description = "员工姓名（瞬态补全）") String empName,
        @Schema(description = "岗位ID") Long postId,
        @Schema(description = "岗位名称（瞬态补全）") String postName,
        @Schema(description = "关联匹配记录ID") Long matchingRecordId,
        @Schema(description = "会议邀请链接") String meetingUrl,
        @Schema(description = "链接来源：MANUAL / API") String meetingSource,
        @Schema(description = "预约沟通时间") LocalDateTime scheduledTime,
        @Schema(description = "状态：0待沟通 1已完成 2已取消") Integer status,
        @Schema(description = "状态名称") String statusName,
        @Schema(description = "结论：1通过 2不通过 3待定") Integer result,
        @Schema(description = "结论名称") String resultName,
        @Schema(description = "HR 沟通纪要与评价（员工侧可读原文）") String comment,
        @Schema(description = "员工响应：1接受 2放弃；空=尚未响应") Integer employeeResponse,
        @Schema(description = "员工响应名称") String employeeResponseName,
        @Schema(description = "员工放弃终面的说明") String employeeResponseComment,
        @Schema(description = "员工响应时间") LocalDateTime employeeRespondedTime,
        @Schema(description = "邀请邮件状态：0未发送 1已发送 2发送失败 3已跳过") Integer inviteMailStatus,
        @Schema(description = "邀请邮件状态名称") String inviteMailStatusName,
        @Schema(description = "邀请邮件的收件邮箱（取自员工档案的个人邮箱，瞬态补全）") String employeeEmail,
        @Schema(description = "邀请邮件未发出的原因（发送失败或跳过时的可读说明；发送成功为 null）")
        String inviteMailError,
        @Schema(description = "发起的 HR 用户ID") Long createdBy,
        @Schema(description = "发起时间") LocalDateTime createdTime,
        @Schema(description = "结论录入时间") LocalDateTime finishedTime) {

    public static String statusName(Integer status) {
        if (status == null) {
            return "--";
        }
        return switch (status) {
            case 0 -> "待沟通";
            case 1 -> "已完成";
            case 2 -> "已取消";
            default -> "未知";
        };
    }

    /**
     * 员工响应名称。
     * <p>未响应返回「待响应」而不是「--」：HR 需要一眼看出「邀请已发出、员工还没表态」，
     * 这是需要他去催的状态，不是「无数据」。</p>
     */
    public static String employeeResponseName(Integer employeeResponse) {
        if (employeeResponse == null) {
            return "待响应";
        }
        return switch (employeeResponse) {
            case 1 -> "已接受";
            case 2 -> "已放弃";
            default -> "未知";
        };
    }

    /**
     * 邀请邮件状态名称。
     * <p>只透出「发没发出去」，SMTP 原始报错请见 {@code inviteMailError} ——
     * 两者分工：状态给颜色，原因给排障。</p>
     */
    public static String mailStatusName(Integer inviteMailStatus) {
        if (inviteMailStatus == null) {
            return "未记录";
        }
        return switch (inviteMailStatus) {
            case 0 -> "未发送";
            case 1 -> "已发送";
            case 2 -> "发送失败";
            case 3 -> "已跳过";
            default -> "未知";
        };
    }

    public static String resultName(Integer result) {
        if (result == null) {
            return "--";
        }
        return switch (result) {
            case 1 -> "人工沟通确认通过";
            case 2 -> "未通过本次终面";
            case 3 -> "沟通结论待定";
            default -> "未知";
        };
    }
}
