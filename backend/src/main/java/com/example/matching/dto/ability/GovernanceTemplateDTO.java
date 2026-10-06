package com.example.matching.dto.ability;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 治理模板DTO
 * <p>
 * 人工修改能力时必须填写的结构化模板，用于记录修改原因和生成Agent记忆。
 *
 * @author system
 */
@Data
@Schema(description = "治理模板，人工修改能力时必须填写的结构化信息")
public class GovernanceTemplateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "修改类型不能为空")
    @Schema(description = "修改类型：MANUAL_ADD-人工新增, ABILITY_RENAME-能力名称修改, TAG_REPLACE-标签替换, LEVEL_UP-等级上调, LEVEL_DOWN-等级下调, DELETE_ABILITY-删除能力, EVIDENCE_UPDATE-证据修改",
            allowableValues = {"MANUAL_ADD", "ABILITY_RENAME", "TAG_REPLACE", "LEVEL_UP", "LEVEL_DOWN", "DELETE_ABILITY", "EVIDENCE_UPDATE"},
            example = "LEVEL_UP")
    private String modifyType;

    @NotBlank(message = "修改原因不能为空")
    @Schema(description = "修改原因，必填", example = "PMS中存在独立负责核心模块和线上问题闭环证据")
    private String reason;

    // ========== 标签替换相关 ==========

    @Schema(description = "原标签ID（标签替换时必填）", example = "100")
    private Long oldTagId;

    @Schema(description = "原标签名称", example = "SpringBoot开发能力")
    private String oldTagName;

    @Schema(description = "新标签ID（标签替换时必填）", example = "200")
    private Long newTagId;

    @Schema(description = "新标签名称", example = "Spring Boot")
    private String newTagName;

    @Schema(description = "是否保留原标签为别名", example = "true")
    private Boolean keepOldAsAlias;

    /**
     * 仅用于简历解析来源的标签替换：将本次名称修正保存为后续简历提取纠偏规则。
     */
    private Boolean rememberResumeNameCorrection;

    @Schema(description = "典型触发表达列表", example = "[\"SpringBoot\", \"Spring Boot开发\"]")
    private List<String> triggerExpressions;

    @Schema(description = "不应再生成的表达列表", example = "[\"SpringBoot开发能力\"]")
    private List<String> negativeExpressions;

    // ========== 等级修改相关 ==========

    @Schema(description = "原等级（等级修改时）", example = "3")
    private Integer oldLevel;

    @Schema(description = "新等级（等级修改时必填）", example = "4")
    private Integer newLevel;

    @Schema(description = "支持证据", example = "核心工单3个，线上缺陷修复2次")
    private String supportEvidence;

    @Schema(description = "反证证据（等级下调时）", example = "实际项目中未独立承担核心模块")
    private String counterEvidence;

    @Schema(description = "主要依据来源列表", example = "[\"PMS_ANALYSIS\", \"AI_TEST\"]")
    private List<String> mainEvidenceSources;

    // ========== 删除相关 ==========

    @Schema(description = "删除原因选项", example = "泛化描述",
            allowableValues = {"证据不足", "标签重复", "能力不相关", "泛化描述", "来源误判"})
    private String deleteReason;

    @Schema(description = "误判来源", example = "RESUME_PARSE")
    private String misjudgedSource;

    @Schema(description = "是否加入拒绝规则", example = "true")
    private Boolean addToRejectRule;

    @Schema(description = "替代建议", example = "建议使用具体技术栈标签替代")
    private String replacementSuggestion;

    // ========== 证据修改相关 ==========

    @Schema(description = "新增的证据说明", example = "新增PMS项目中的独立负责模块证据")
    private String addedEvidence;

    @Schema(description = "删除的证据说明", example = "移除过期的项目经历")
    private String removedEvidence;

    // ========== 通用 ==========

    @Schema(description = "来源权重建议", example = "PMS证据权重应高于简历自述")
    private String sourceWeightAdvice;

    @Schema(description = "附加说明", example = "其他补充信息")
    private String additionalNotes;

    /** 能力名称修改前后的正式名称；名称修改时用于审计，不依赖标签库。 */
    private String oldAbilityName;
    private String newAbilityName;
}
