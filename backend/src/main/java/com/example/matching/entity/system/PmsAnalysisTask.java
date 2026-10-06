package com.example.matching.entity.system;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * PMS分析任务表实体
 * <p>
 * 记录从PMS系统分析员工项目工作数据的任务。
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("pms_analysis_task")
public class PmsAnalysisTask implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 员工ID */
    private Long empId;

    /** PMS用户ID */
    private Long pmsUserId;

    /** 分析状态：0-待分析，1-分析中，2-成功，3-失败 */
    private Integer analysisStatus;

    /** 分析时间范围(月) */
    private Integer dateRangeMonths;

    /** 分析的工单数 */
    private Integer workOrderCount;

    /** 分析的Bug数 */
    private Integer bugCount;

    /** 分析的测试用例数 */
    private Integer testCaseCount;

    /** 参与的项目数 */
    private Integer projectCount;

    /** 提取的能力数 */
    private Integer extractedAbilityCount;

    /** AI原始响应JSON */
    private String aiRawResponse;

    /** 失败原因 */
    private String errorMessage;

    /** 创建人ID */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
