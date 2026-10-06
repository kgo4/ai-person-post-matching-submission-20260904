package com.example.matching.dto.learning;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * HR 复核学习成果（HR 匹配闭环 P4）。
 *
 * <p>通过时 {@code comment} 可选；驳回时必填（由服务层校验）。</p>
 */
@Data
@Schema(description = "学习成果复核")
public class LearningOutcomeReviewDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "复核意见：通过时可选，驳回时必填")
    private String comment;
}
