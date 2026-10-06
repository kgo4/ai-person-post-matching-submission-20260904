package com.example.matching.entity.system;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ability_tag_governance_notification")
public class AbilityTagGovernanceNotification {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long recipientUserId;
    private Long candidateId;
    private Long proposalId;
    private String title;
    private String content;
    private String status;
    private LocalDateTime createdTime;
    private LocalDateTime readTime;
}
