package com.example.matching.dto.post;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * Excel导入确认DTO
 */
@Data
@Schema(description = "Excel导入确认请求")
public class PostImportConfirmDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "导入批次ID")
    private Long batchId;

    @Schema(description = "确认导入的明细列表（可编辑后确认）")
    private List<ConfirmItem> items;

    @Schema(description = "是否将本批次已确认的JD纳入市场岗位发现；复用本次已解析结果，不重复调用AI")
    private Boolean includeMarketJd;

    @Data
    @Schema(description = "确认导入的单条明细")
    public static class ConfirmItem implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "明细ID")
        private Long itemId;

        @Schema(description = "岗位名称（可编辑）")
        private String postName;

        @Schema(description = "岗位描述（可编辑）")
        private String postDescription;

        @Schema(description = "是否确认导入此项")
        private Boolean confirmed;

        @Schema(description = "用户编辑后的能力项列表（可选，覆盖AI分析结果）")
        private List<JdAbilityItemDTO> abilities;
    }
}
