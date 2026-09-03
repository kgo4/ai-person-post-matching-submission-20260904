package com.example.matching.entity.rag;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("rag_query_log")
public class RagQueryLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String queryCode;

    private String queryId;

    private String scenario;

    private String queryText;

    private Integer topK;

    private String providerMode;

    private Boolean isDegraded;

    private Integer requestedTopK;

    private String retrievedChunkIds;

    private String normalizedScores;

    private String contextText;

    private String contextHash;

    private Integer contextTokenEstimate;

    private String promptSnapshot;

    private String responseSnapshot;

    private Long latencyMs;

    private Integer hitCount;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
