package com.example.matching.dto.kg;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GraphBuildTaskStatusDTO {
    private String taskCode;
    private String taskStatus;
    private GraphBuildResultDTO result;
    private String errorMessage;
    private LocalDateTime createdTime;
    private LocalDateTime startedTime;
    private LocalDateTime completedTime;
}
