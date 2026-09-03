package com.example.matching.entity.governance;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("governance_filter_rule")
public class GovernanceFilterRule implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String scope;
    private String ruleType;
    private String ruleName;
    private String patternValue;
    private Integer weight;
    private Integer blockEnabled;
    private Integer enabled;
    private String source;
    private String reviewStatus;
    private Integer sampleCount;
    private String aiRationale;
    private String description;
    private Long createdBy;
    private Long updatedBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
