package com.example.matching.dto.interview;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * HR 录入终面结论（HR 匹配闭环 P5）。
 *
 * <p>平台无法感知员工是否真的入会，终面完成与否以 HR 手动录入为准。</p>
 */
@Data
@Schema(description = "录入终面结论")
public class InterviewResultRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "结论：1通过(人工沟通确认) 2不通过 3待定")
    @NotNull(message = "结论不能为空")
    private Integer result;

    @Schema(description = "沟通纪要与评价（员工侧可见原文）")
    private String comment;
}
