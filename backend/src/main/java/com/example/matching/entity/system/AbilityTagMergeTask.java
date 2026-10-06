package com.example.matching.entity.system;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ability_tag_merge_task")
public class AbilityTagMergeTask {
    @TableId(value = "id", type = IdType.AUTO) private Long id;
    private String taskCode;
    private Double threshold;
    private LocalDateTime scheduledTime;
    private String status;
    private LocalDateTime startedTime;
    private LocalDateTime completedTime;
    private String resultSummary;
    private String errorMessage;
    private Long createdBy;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
