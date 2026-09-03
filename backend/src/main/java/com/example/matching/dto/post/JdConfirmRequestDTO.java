package com.example.matching.dto.post;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * JD分析结果确认请求DTO
 */
@Data
@Schema(description = "JD分析结果确认请求，用户编辑确认后提交，将能力项写入岗位能力模型")
public class JdConfirmRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "岗位ID不能为空")
    @Schema(description = "岗位ID")
    private Long postId;

    @NotNull(message = "能力项列表不能为空")
    @Schema(description = "确认后的能力项列表（用户可编辑AI分析结果后再提交）")
    private List<JdAbilityItemDTO> items;
}
