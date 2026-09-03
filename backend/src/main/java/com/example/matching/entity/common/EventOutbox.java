package com.example.matching.entity.common;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通用事件 Outbox 实体
 * <p>
 * 用于可靠消息投递：业务状态变更与 outbox 记录在同一事务写入，
 * 提交后由调度器投递到 RabbitMQ。
 */
@Data
@TableName("event_outbox")
public class EventOutbox {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String eventType;
    private String exchange;
    private String routingKey;
    private String payload;
    private String status;
    private Integer attemptCount;
    private Integer maxAttempts;
    private LocalDateTime nextRetryTime;
    private String errorMessage;
    private LocalDateTime lastFailedTime;
    private LocalDateTime createdTime;
    private LocalDateTime publishedTime;
    private LocalDateTime updatedTime;
}
