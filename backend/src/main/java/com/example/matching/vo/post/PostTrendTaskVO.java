package com.example.matching.vo.post;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 岗位趋势解析任务视图对象（列表用）。
 */
@Data
public class PostTrendTaskVO {

    private Long id;

    private String taskCode;

    private String taskName;

    /** PENDING/RUNNING/WAIT_CONFIRM/APPLIED/PARTIALLY_APPLIED/FAILED/CANCELLED */
    private String taskStatus;

    private String progressStatus;

    private Integer progressPercent;

    private Integer candidateCount;

    private Integer newPostCount;

    private Integer changeCount;

    /** 解析诊断；无候选时为必读原因说明 */
    private String diagnostics;

    private String errorMessage;

    private LocalDateTime createdTime;

    private LocalDateTime finishedTime;
}
