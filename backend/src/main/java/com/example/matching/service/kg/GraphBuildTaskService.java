package com.example.matching.service.kg;

import com.example.matching.dto.kg.GraphBuildTaskStatusDTO;

public interface GraphBuildTaskService {
    GraphBuildTaskStatusDTO requestFullRebuild(Long requestedBy);

    GraphBuildTaskStatusDTO getTaskStatus(String taskCode);

    void executeQueuedTask(String taskCode);
}
