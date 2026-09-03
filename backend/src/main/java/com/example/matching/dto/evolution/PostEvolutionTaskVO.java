package com.example.matching.dto.evolution;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 岗位演化任务VO
 *
 * @author system
 */
@Data
public class PostEvolutionTaskVO {

    private Long id;
    private String taskCode;
    private Long postId;
    private String taskName;
    private String baselineVersion;
    private String taskStatus;
    private String summaryJson;
    private String errorMessage;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
