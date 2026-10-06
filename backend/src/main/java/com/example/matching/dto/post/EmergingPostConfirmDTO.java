package com.example.matching.dto.post;

import com.example.matching.dto.post.JdAbilityItemDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@Schema(description = "新兴岗位确认请求")
public class EmergingPostConfirmDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "人工确认后的正式岗位名称，趋势候选暂定名称不能直接入库")
    private String postName;

    @Schema(description = "岗位描述")
    private String description;

    @Schema(description = "能力项列表")
    private List<JdAbilityItemDTO> abilities;
}
