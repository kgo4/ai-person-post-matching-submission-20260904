package com.example.matching.entity.system;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ability_tag_candidate_member")
public class AbilityTagCandidateMember {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long candidateId;
    private Long postId;
    private String abilityName;
    private String sourceType;
    private Long sourceRefId;
    private String evidenceText;
    private BigDecimal similarityScore;
    private LocalDateTime createdTime;
}
