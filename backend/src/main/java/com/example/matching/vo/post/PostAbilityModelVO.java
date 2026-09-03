package com.example.matching.vo.post;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 岗位能力模型视图
 */
@Data
@Schema(description = "岗位能力模型视图，展示岗位的基本信息及其能力要求模型，包括各能力项的最低等级、权重和是否核心/必填等属性")
public class PostAbilityModelVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "岗位ID，岗位在系统中的唯一标识", example = "2001")
    private Long postId;

    @Schema(description = "岗位名称", example = "高级Java开发工程师")
    private String postName;

    @Schema(description = "岗位编码，用于系统内部标识和关联引用", example = "POST_JAVA_SENIOR")
    private String postCode;

    @Schema(description = "能力要求明细列表，包含该岗位所需的各项能力及其具体要求，每条记录对应一个能力标签的配置")
    private List<AbilityRequirementDetail> abilityRequirements;

    @Data
    @Schema(description = "能力要求明细，描述一个具体能力标签在岗位模型中的要求标准")
    public static class AbilityRequirementDetail implements Serializable {

        private static final long serialVersionUID = 1L;

        @Schema(description = "岗位能力模型行ID，用于稳定定位未关联系统标签的岗位能力", example = "3001")
        private Long modelId;

        @Schema(description = "能力标签ID，关联能力标签配置表", example = "101")
        private Long tagId;

        @Schema(description = "岗位能力名称；未关联系统标签时作为岗位画像的正式展示名称", example = "接口自动化测试")
        private String abilityName;

        @Schema(description = "技术栈，不依赖系统标签分类", example = "Spring")
        private String techStack;

        @Schema(description = "能力标签名称", example = "Java编程")
        private String tagName;

        @Schema(description = "最低要求等级，该岗位对员工在此能力上的最低接受标准：1-入门，2-熟悉，3-掌握，4-精通，5-专家", example = "4")
        private Integer minRequiredLevel;

        @Schema(description = "权重占比，该项能力在岗位整体评价中的重要程度，数值越大越关键", example = "30.00")
        private BigDecimal weight;

        @Schema(description = "是否必填：0-非必填（员工可缺失），1-必填（必须具备，否则淘汰）", example = "1")
        private Integer isRequired;

        @Schema(description = "是否核心项：0-普通项，1-核心项（匹配计算中享有更高权重）", example = "1")
        private Integer isCore;
    }
}
