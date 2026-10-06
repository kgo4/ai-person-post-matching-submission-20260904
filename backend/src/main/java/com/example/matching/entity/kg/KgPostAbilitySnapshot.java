package com.example.matching.entity.kg;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("kg_post_ability_snapshot")
public class KgPostAbilitySnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String snapshotCode;
    private String snapshotType;
    private Long postId;
    private Long abilityTagId;
    private String relationType;
    private BigDecimal weightValue;
    private Integer minRequiredLevel;
    private Integer isRequired;
    private Integer evidenceCount;
    private BigDecimal averageConfidence;
    private String graphVersion;
    private LocalDateTime snapshotTime;
    private Long createdBy;
    private LocalDateTime createdTime;
}
