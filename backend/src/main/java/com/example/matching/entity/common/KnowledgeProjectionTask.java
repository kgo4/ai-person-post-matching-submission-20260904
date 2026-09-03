package com.example.matching.entity.common;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_projection_task")
public class KnowledgeProjectionTask {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String projection;
    private String aggregateType;
    private Long aggregateId;
    private Long targetRevision;
    private String operation;
    private String payloadHash;
    private String status;
    private Integer attemptCount;
    private Integer maxAttempts;
    private LocalDateTime nextRetryTime;
    private LocalDateTime leaseUntil;
    private String errorMessage;
    private LocalDateTime completedTime;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    public enum Projection { MILVUS_RAG, NEO4J_GRAPH }
    public enum Operation { UPSERT, DELETE }
    public enum Status { PENDING, PROCESSING, SUCCEEDED, FAILED }
}
