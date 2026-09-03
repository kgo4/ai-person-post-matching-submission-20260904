package com.example.matching.entity.common;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 动态来源可信度权重实体
 */
@Data
@TableName("dynamic_credibility_weight")
public class DynamicCredibilityWeight {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String sourceType;
    private Double weight;
    private Integer confirmCount;
    private Integer correctionCount;
    private Long totalFeedback;
    @Version
    private Integer version;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
