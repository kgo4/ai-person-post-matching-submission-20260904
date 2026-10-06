package com.example.matching.dto.employee.video;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 视频面试问题生成请求DTO
 */
@Data
@Schema(description = "视频面试问题生成请求，用于为面试会话生成面试问题集")
public class VideoInterviewQuestionGenerateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "生成模式：POST_BASED-基于岗位能力模型生成，GENERAL-使用通用面试模板", example = "POST_BASED")
    private String mode = "POST_BASED";

    @Schema(description = "是否包含通用问题，当岗位能力标签数量不足时用通用问题补充", example = "true")
    private Boolean includeGeneralQuestions = true;

    /** Legacy client/test alias; the service derives the effective count from session settings. */
    private Integer questionCount;
}
