package com.example.matching.dto.post;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 新兴岗位定义请求DTO
 */
@Data
@Schema(description = "新兴岗位定义请求")
public class EmergingPostRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "兼容旧接口的岗位名称；岗位趋势发现页面不要求填写")
    private String postName;

    @Schema(description = "岗位描述", example = "负责设计和优化AI大模型的提示词...")
    private String description;

    @Schema(description = "行业/业务方向", example = "人工智能")
    private String industry;

    @Schema(description = "关键职责描述")
    private String keyResponsibilities;

    @Schema(description = "是否创建岗位（true=直接创建，false=仅返回推荐结果）")
    private Boolean createPost;

    @Schema(description = "资料来源类型过滤，例如 OFFICIAL_POLICY、INDUSTRY_REPORT")
    private java.util.List<String> sourceTypes;

    @Schema(description = "指定的已索引知识文档ID")
    private java.util.List<Long> documentIds;

    @Schema(description = "是否纳入已治理市场JD，默认true")
    private Boolean includeMarketJd = Boolean.TRUE;
}
