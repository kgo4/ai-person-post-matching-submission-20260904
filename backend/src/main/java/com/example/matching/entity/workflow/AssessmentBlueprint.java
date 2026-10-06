package com.example.matching.entity.workflow;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("assessment_blueprint")
public class AssessmentBlueprint {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long workflowId;
    private String scopeHash;
    private String blueprintJson;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
