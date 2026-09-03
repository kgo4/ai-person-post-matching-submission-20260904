package com.example.matching.dto.post;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * Excel导入预览DTO
 * <p>
 * 返回给前端的批量解析预览结果。
 */
@Data
@Schema(description = "Excel导入预览结果")
public class PostImportPreviewDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "导入批次ID")
    private Long batchId;

    @Schema(description = "文件名")
    private String fileName;

    @Schema(description = "解析出的岗位总数")
    private Integer totalRows;

    @Schema(description = "AI识别的结构信息")
    private ExcelStructureDTO structure;

    @Schema(description = "解析出的岗位列表（含能力分析结果）")
    private List<PostImportItemPreview> items;

    @Schema(description = "导入状态")
    private Integer importStatus;

    @Schema(description = "成功导入数")
    private Integer successCount;

    @Schema(description = "失败数")
    private Integer failCount;

    @Schema(description = "批次错误信息")
    private String errorMessage;

    @Data
    @Schema(description = "单条岗位导入预览")
    public static class PostImportItemPreview implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "明细ID")
        private Long itemId;

        @Schema(description = "Excel行号")
        private Integer rowIndex;

        @Schema(description = "岗位名称")
        private String postName;

        @Schema(description = "岗位描述")
        private String postDescription;

        @Schema(description = "分析状态：0-待分析，1-分析中，2-成功，3-失败")
        private Integer analysisStatus;

        @Schema(description = "AI提取的能力项列表")
        private List<JdAbilityItemDTO> abilities;

        @Schema(description = "错误信息")
        private String errorMessage;
    }
}
