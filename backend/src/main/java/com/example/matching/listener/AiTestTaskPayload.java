package com.example.matching.listener;

import java.io.Serializable;

public class AiTestTaskPayload implements Serializable {

    private static final long serialVersionUID = 1L;

    private String taskType;
    private Long testId;

    public AiTestTaskPayload() {
    }

    public AiTestTaskPayload(String taskType, Long testId) {
        this.taskType = taskType;
        this.testId = testId;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public Long getTestId() {
        return testId;
    }

    public void setTestId(Long testId) {
        this.testId = testId;
    }
}
