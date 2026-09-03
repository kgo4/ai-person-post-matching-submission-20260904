package com.example.matching.entity.kg;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("kg_graph_change_set")
public class KgGraphChangeSet {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String changeCode;
    private String sourceType;
    private String entityType;
    private Long entityId;
    private String operationType;
    private String payloadJson;
    private String graphVersion;
    private String processStatus;
    private Integer retryCount;
    private Integer affectedNodeCount;
    private Integer affectedEdgeCount;
    private LocalDateTime startedTime;
    private LocalDateTime completedTime;
    private String errorMessage;
    private Long createdBy;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
