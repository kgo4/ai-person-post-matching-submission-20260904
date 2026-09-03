package com.example.matching.entity.post;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Immutable audit record for a role capability's JD grounding decision. */
@Data
@TableName("post_ability_grounding_record")
public class PostAbilityGroundingRecord {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long postId;
    private String abilityName;
    private String normalizedAbilityName;
    private Long abilityTagId;
    private String sourceType;
    private Long sourceRefId;
    private String evidenceText;
    private String evidenceAnchor;
    private Integer evidenceStart;
    private Integer evidenceEnd;
    private String validationStatus;
    private String validationReason;
    private LocalDateTime createdTime;
}
