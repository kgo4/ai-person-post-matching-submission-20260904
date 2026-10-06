package com.example.matching.entity.system;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A read-only Agent recommendation, applied only after an operator accepts it. */
@Data
@TableName("ability_tag_candidate_placement_proposal")
public class AbilityTagCandidatePlacementProposal {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long candidateId;
    private Integer proposalVersion;
    private String action;
    private Long targetParentDomainId;
    private Long targetTagId;
    private BigDecimal confidence;
    private String rationale;
    private String status;
    private Long finalTagId;
    private Long appliedBy;
    private LocalDateTime appliedTime;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
