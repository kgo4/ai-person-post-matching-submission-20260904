package com.example.matching.vo.matching;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 匹配结果视图
 */
@Data
@Schema(description = "匹配结果视图，展示一次人岗匹配的结果记录，包含员工与岗位的基本信息、AI匹配度、最终匹配度及审批状态")
public class MatchingResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "匹配记录ID，匹配结果在系统中的唯一标识", example = "5001")
    private Long id;

    @Schema(description = "批次号，每次匹配执行生成的唯一批次标识，用于关联同一批次的所有匹配记录", example = "BATCH20250615_001")
    private String batchNo;

    @Schema(description = "员工ID，参与匹配的员工的唯一标识", example = "10001")
    private Long empId;

    @Schema(description = "员工姓名", example = "张三")
    private String empName;

    @Schema(description = "员工工号", example = "EMP2020001")
    private String empCode;

    @Schema(description = "岗位ID，目标匹配岗位的唯一标识", example = "2001")
    private Long postId;

    @Schema(description = "岗位名称", example = "高级Java开发工程师")
    private String postName;

    @Schema(description = "AI匹配度，由匹配算法根据员工能力数据与岗位能力模型自动计算得出的原始匹配分数，取值范围0.00-100.00", example = "78.50")
    private BigDecimal aiMatchScore;

    @Schema(description = "最终匹配度，在AI匹配度的基础上经过人工调整或审批修正后的最终匹配分数，取值范围0.00-100.00", example = "82.00")
    private BigDecimal finalMatchScore;

    @Schema(description = "匹配状态编码：0-待审批，1-匹配通过，2-匹配不通过，3-已退回", example = "1")
    private Integer matchStatus;

    @Schema(description = "匹配状态的中文显示名称，如待审批、匹配通过等", example = "匹配通过")
    private String matchStatusName;

    @Schema(description = "审批状态编码：0-未提交审批，1-审批通过，2-审批驳回", example = "1")
    private Integer approvalStatus;

    @Schema(description = "匹配执行的时间，记录该条匹配结果生成的时刻", example = "2025-06-15T14:30:00")
    private LocalDateTime createdTime;
}
