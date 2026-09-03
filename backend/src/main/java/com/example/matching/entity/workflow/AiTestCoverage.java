package com.example.matching.entity.workflow;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 测试验证覆盖关系实体
 * <p>
 * 记录测试题目与简历能力主张（claim_group）之间的验证覆盖关系。
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("ai_test_coverage")
public class AiTestCoverage implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 关联的工作流ID */
    private Long workflowId;

    /** 关联的能力聚合组ID */
    private Long claimGroupId;

    /** 关联的测试ID */
    private Long testId;

    /** 题目ID（题目JSON中的索引或ID） */
    private Long questionId;

    /** 目标能力 */
    private String targetCompetency;

    /** 目标等级：1-5 */
    private Integer targetLevel;

    /** 验证类型（CoverageTypeEnum code） */
    private String coverageType;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
