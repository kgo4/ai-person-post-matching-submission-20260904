package com.example.matching.entity.workflow;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("assessment_agent_artifact")
public class AssessmentAgentArtifact {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long workflowId;
    private Long stageRunId;
    private String artifactType;
    private String contentJson;
    private String contentHash;
    private LocalDateTime createdTime;
}
