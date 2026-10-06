package com.example.matching.dto.post;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 岗位分析完整结果DTO
 * <p>
 * 包含结构化的岗位定义和能力项列表，
 * 用于新兴岗位定义和JD分析的完整响应。
 */
@Data
@Schema(description = "岗位分析完整结果")
public class PostAnalysisResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "岗位摘要")
    private String jobSummary;

    @Schema(description = "核心职责列表")
    private List<String> coreResponsibilities;

    @Schema(description = "必备技能列表")
    private List<String> requiredSkills;

    @Schema(description = "加分技能列表")
    private List<String> bonusSkills;

    @Schema(description = "典型行业应用场景列表")
    private List<String> industryScenarios;

    @Schema(description = "AI推荐的能力项列表")
    private List<JdAbilityItemDTO> abilities;
}
