package com.example.matching.dto.learning;

import lombok.Data;

/**
 * 项目任务提交请求
 *
 * @author system
 */
@Data
public class LearningProjectSubmitDTO {

    /** 仓库URL */
    private String repoUrl;

    /** 演示URL */
    private String demoUrl;

    /** 报告URL */
    private String reportUrl;

    /** 提交文本说明 */
    private String submissionText;
}
