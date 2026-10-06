package com.example.matching.dto.interview;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 员工响应视频终面邀请（接受 / 放弃）。
 *
 * <p>员工侧唯一的写入口。响应是**一次性**的：已响应过再调用会被状态机拒绝，
 * 需要变更时由 HR 取消后重新发起。</p>
 */
@Data
@Schema(description = "员工响应视频终面邀请")
public class InterviewResponseRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "响应：1接受 2放弃")
    @NotNull(message = "请选择接受或放弃")
    private Integer response;

    @Schema(description = "放弃原因（可选，仅放弃时有意义）")
    @Size(max = 500, message = "放弃原因不能超过 500 字")
    private String comment;
}
