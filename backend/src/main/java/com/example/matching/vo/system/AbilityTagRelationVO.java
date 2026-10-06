package com.example.matching.vo.system;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 标签关系 VO（含标签名称）
 */
@Data
public class AbilityTagRelationVO {

    private Long id;
    private Long sourceTagId;
    private Long targetTagId;
    private String sourceTagName;
    private String targetTagName;
    private String relationType;
    private BigDecimal similarityScore;
    private String status;
    private String evidenceSource;
    private String remark;
    private LocalDateTime createdTime;
}
