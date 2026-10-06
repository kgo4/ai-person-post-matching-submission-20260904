package com.example.matching.dto.learning;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 员工提交学习成果（HR 匹配闭环 P4）。
 *
 * <p>提交后进入「待复核」，**不立即回写能力证据**；HR 复核通过后才走既有
 * {@code onLearningOutcomeConfirmed} 链路完成回写。</p>
 *
 * <p>不含 empId —— 人员范围由服务端按登录身份固定为本人。</p>
 */
@Data
@Schema(description = "学习成果提交")
public class LearningOutcomeSubmitDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "关联匹配记录ID（若有）")
    private Long matchingRecordId;

    @Schema(description = "能力标签ID")
    private Long tagId;

    @Schema(description = "能力名称（tagId 缺失时使用）")
    private String abilityName;

    @Schema(description = "完成的学习资源ID")
    private Long completedResourceId;

    @Schema(description = "学习前等级 1-5")
    private Integer beforeLevel;

    @Schema(description = "自评达成等级 1-5")
    @NotNull(message = "自评达成等级不能为空")
    @Min(value = 1, message = "自评等级最小为 1")
    @Max(value = 5, message = "自评等级最大为 5")
    private Integer confirmedLevel;

    @Schema(description = "提交说明")
    private String note;

    @Schema(description = "AI 学习建议追溯ID")
    private Long aiSuggestionId;

    @Schema(description = "RAG 检索 chunkIds（JSON 数组）")
    private String ragChunkIds;

    @Schema(description = "AI 建议版本")
    private String aiSuggestionVersion;
}
