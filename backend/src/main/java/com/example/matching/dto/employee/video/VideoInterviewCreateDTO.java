package com.example.matching.dto.employee.video;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 视频面试创建请求DTO
 */
@Data
@Schema(description = "视频面试创建请求，用于创建一个新的AI视频面试会话，可选择绑定岗位进行针对性面试")
public class VideoInterviewCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "员工ID不能为空")
    @Schema(description = "员工ID，关联员工信息表的主键，标识该面试会话所属的员工", example = "10001")
    private Long empId;

    @Schema(description = "岗位ID，当面试模式为POST_BASED时必填，用于根据岗位能力模型生成针对性问题", example = "2001")
    private Long postId;

    @Schema(description = "关联的能力评估工作流ID（工作流面试专用，可选）", example = "1001")
    private Long workflowId;

    @NotBlank(message = "会话名称不能为空")
    @Schema(description = "会话名称，用于标识本次面试会话，建议包含岗位和轮次信息", example = "Java后端岗位视频面试-第一轮")
    private String sessionName;

    @NotBlank(message = "面试模式不能为空")
    @Schema(description = "面试模式：POST_BASED-基于岗位（根据岗位能力模型生成问题），GENERAL-通用面试（使用通用面试问题模板）", example = "POST_BASED")
    private String interviewMode;
}
