package com.example.matching.dto.interview;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * HR 发起视频终面（HR 匹配闭环 P5）。
 *
 * <p>会议链接由 HR 在讯飞会议侧创建后粘贴进来 —— 平台零集成、零凭据。
 * 链接域名受可配置白名单约束（{@code communication-interview.allowed-meeting-hosts}）。</p>
 */
@Data
@Schema(description = "发起视频终面")
public class CreateInterviewRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "员工档案ID")
    @NotNull(message = "员工档案ID不能为空")
    private Long empId;

    @Schema(description = "关联岗位ID（若有）")
    private Long postId;

    @Schema(description = "关联匹配记录ID（若有）")
    private Long matchingRecordId;

    @Schema(description = "会议邀请链接；可直接粘贴讯飞会议「复制邀请信息」给出的整段多行文本，"
            + "服务端会自动抽取其中的入会链接")
    @NotBlank(message = "会议链接不能为空")
    private String meetingUrl;

    @Schema(description = "预约沟通时间；空=尽快")
    private LocalDateTime scheduledTime;
}
