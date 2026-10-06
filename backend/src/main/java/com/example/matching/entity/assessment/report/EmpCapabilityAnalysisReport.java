package com.example.matching.entity.assessment.report;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 员工全面能力分析报告快照。
 * <p>
 * HR 完成某员工全部待审 harness 能力项后由 {@code CapabilityAnalysisReportService}
 * 自动聚合生成；重审产生新版本（version_no 递增），不覆盖旧版本。
 * {@code sourceFingerprint} 为审核数据指纹，同指纹重复触发按幂等跳过。
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("emp_capability_analysis_report")
public class EmpCapabilityAnalysisReport implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 员工档案ID（emp_employee.id） */
    private Long empId;

    /** 版本号，从 1 开始递增 */
    private Integer versionNo;

    /** harness 自动通过项 JSON（能力名/等级/证据链） */
    private String autoPassedJson;

    /** 人工确认通过项 JSON（能力名/等级/审核人/审核理由） */
    private String manualConfirmedJson;

    /** 人工拒绝项 JSON（能力名/拒绝理由） */
    private String manualRejectedJson;

    /** 各能力项最终等级汇总 JSON */
    private String finalLevelsJson;

    /** 整体结论摘要 */
    private String summary;

    /** 审核数据指纹（幂等防重） */
    private String sourceFingerprint;

    /** 触发生成的审核人（最后一条决策的 reviewedBy） */
    private Long createdBy;

    private LocalDateTime createdTime;
}
