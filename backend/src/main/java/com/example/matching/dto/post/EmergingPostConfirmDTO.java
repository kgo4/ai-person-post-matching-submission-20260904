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

    @Schema(description = "岗位名称")
    private String postName;

    @Schema(description = "岗位描述")
    private String description;

    @Schema(description = "能力项列表")
    private List<JdAbilityItemDTO> abilities;
}
