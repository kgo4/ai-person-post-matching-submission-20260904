package com.example.matching.entity.matching;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("matching_task_outbox")
public class MatchingTaskOutbox {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String taskId;
    private String routingKey;
    private String payload;
    private String status;
    private Integer attemptCount;
    private LocalDateTime nextRetryTime;
    private String errorMessage;
    private LocalDateTime lastFailedTime;
    private LocalDateTime createdTime;
    private LocalDateTime publishedTime;
    private LocalDateTime updatedTime;
}
