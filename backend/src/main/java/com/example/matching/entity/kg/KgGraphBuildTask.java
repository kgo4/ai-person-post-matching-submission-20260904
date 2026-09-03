package com.example.matching.entity.kg;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("kg_graph_build_task")
public class KgGraphBuildTask {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String taskCode;
    private String taskStatus;
    private Long requestedBy;
    private LocalDateTime startedTime;
    private LocalDateTime completedTime;
    private String resultJson;
    private String errorMessage;
    private Integer retryCount;
    @TableField("created_time")
    private LocalDateTime createdTime;
    @TableField("updated_time")
    private LocalDateTime updatedTime;
}
