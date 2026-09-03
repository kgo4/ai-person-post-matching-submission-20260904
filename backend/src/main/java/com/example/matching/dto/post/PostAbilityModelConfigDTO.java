package com.example.matching.dto.post;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 岗位能力模型配置请求DTO
 */
@Data
@Schema(description = "岗位能力模型配置请求，用于定义岗位所需的能力要求，包括各能力项的最低等级、权重占比，以及是否为核心/必填项，是匹配算法的核心配置数据")
public class PostAbilityModelConfigDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "岗位能力配置主键ID，更新时必传，新增时不传（留空由后端自动生成）", example = "1")
    private Long id;

    @NotNull(message = "岗位ID不能为空")
    @Schema(description = "岗位ID，关联岗位信息表的主键，标识该条能力要求适用于哪个岗位", example = "2001")
    private Long postId;

    @Schema(description = "可选的能力标签ID；岗位画像能力不依赖系统标签库", example = "101")
    private Long tagId;

    @Schema(description = "岗位能力名称；tagId为空时必填，作为岗位画像能力的正式名称", example = "接口自动化测试")
    private String abilityName;

    @Schema(description = "技术栈，用于岗位全景图谱聚合，不依赖标签库", example = "Spring")
    private String techStack;

    @NotNull(message = "最低要求等级不能为空")
    @Schema(description = "最低要求等级，该岗位对该项能力的最低接受标准：1-入门，2-熟悉，3-掌握，4-精通，5-专家，匹配时员工等级低于此值将扣分", example = "3")
    private Integer minRequiredLevel;

    @NotNull(message = "权重占比不能为空")
    @Schema(description = "权重占比，该项能力在岗位整体能力模型中的重要程度，取值范围0.00-100.00，同一岗位下所有能力项的权重之和建议为100", example = "25.00")
    private BigDecimal weight;

    @Schema(description = "是否必填：0-非必填（员工不具备该能力时不强制要求），1-必填（员工必须具备该能力，否则不列入匹配结果）", example = "1")
    private Integer isRequired = 0;

    @Schema(description = "是否核心项：0-普通能力项，1-核心能力项（核心项在匹配算法中享有更高的计算权重，不具备核心能力将显著降低匹配度）", example = "1")
    private Integer isCore = 0;

    @Schema(description = "备注，用于记录该能力要求的设置理由或其他补充说明", example = "此项为该岗位的核心技术能力，必须具备")
    private String remark;

    @Schema(description = "是否将本次人工确认的文本解释泛化为未来JD提取规则；默认false，岗位等级、权重和核心性修改不会生成全局规则")
    private boolean generateFutureJdRule;

    @Schema(description = "岗位模型版本号（只读，由后端在batchConfig时自动生成），格式：vyyyyMMddHHmmss", accessMode = Schema.AccessMode.READ_ONLY)
    private String modelVersion;
}
