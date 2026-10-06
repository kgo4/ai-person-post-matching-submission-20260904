package com.example.matching.entity.kg;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("kg_relation_candidate")
public class KgRelationCandidate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String candidateCode;
    private String sourceNodeKey;
    private String targetNodeKey;
    private String relationType;
    private String discoveryMethod;
    private BigDecimal semanticScore;
    private String sourceRefsJson;
    private String reviewStatus;
    private String reviewReason;
    private Long reviewedBy;
    private LocalDateTime reviewedTime;
    private Long createdBy;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
