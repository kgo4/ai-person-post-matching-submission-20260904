package com.example.matching.dto.employee.video;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Real-time video frame captured during an interview.
 */
@Data
public class VideoInterviewFrameDTO {

    @NotNull(message = "questionOrder不能为空")
    private Integer questionOrder;

    /** 当前追问ID；为空表示预设题。用于将追问视觉帧与原题分开分析。 */
    private Long followUpId;

    @NotNull(message = "captureSecond不能为空")
    private Integer captureSecond;

    @NotBlank(message = "imageDataUrl不能为空")
    private String imageDataUrl;
}
