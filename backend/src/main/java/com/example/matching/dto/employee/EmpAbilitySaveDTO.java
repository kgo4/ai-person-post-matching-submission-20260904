package com.example.matching.dto.employee;

import com.example.matching.dto.ability.GovernanceTemplateDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 员工能力保存请求DTO
 */
@Data
@Schema(description = "员工能力保存请求，用于录入或更新员工的单项能力评价记录，包括能力标签、掌握等级、评价来源及权重信息")
public class EmpAbilitySaveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "员工能力记录主键ID，更新时必传，新增时不传（留空由后端自动生成）", example = "1")
    private Long id;

    @NotNull(message = "员工ID不能为空")
    @Schema(description = "员工ID，关联员工信息表的主键，标识该能力记录所属的员工", example = "10001")
    private Long empId;

    @Schema(description = "关联标签ID，可选；能力名称可独立于标签库维护", example = "101")
    private Long tagId;

    @Schema(description = "人员正式能力名称，作为业务展示和匹配的权威名称", example = "缓存架构设计")
    private String abilityName;

    @NotNull(message = "掌握等级不能为空")
    @Schema(description = "掌握等级，评估员工对该能力的掌握程度：1-入门（初步了解），2-熟悉（能独立完成简单任务），3-掌握（能独立完成常规任务），4-精通（能解决复杂问题），5-专家（具备培训和指导他人的能力）", example = "4")
    private Integer masteryLevel;

    @NotBlank(message = "评价来源不能为空")
    @Schema(description = "评价来源，使用统一编码：RESUME_PARSE、AI_TEST、AI_PROJECT、AI_INTERVIEW、LEARNING_PROJECT、MANUAL、PERFORMANCE、PROFILE_FUSED。旧编码会在后端兼容转换。", example = "MANUAL")
    private String evaluationSource;

    @Schema(description = "来源权重，当同一能力存在多个评价来源时用于加权计算综合评分，取值范围0.00-1.00", example = "0.80")
    private BigDecimal sourceWeight;

    @Schema(description = "评价时间，记录该能力评价产生的日期，默认取当前日期", example = "2025-06-15")
    private LocalDate evaluationDate;

    @Schema(description = "备注，用于记录评价依据、原因说明或其他补充信息", example = "根据2025年Q2绩效评估结果评定")
    private String remark;

    @Schema(description = "治理模板，人工修改能力时必须填写的结构化信息，包含修改类型、原因、证据等")
    private GovernanceTemplateDTO governanceTemplate;
}
