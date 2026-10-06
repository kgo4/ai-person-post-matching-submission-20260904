package com.example.matching.dto.post;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Excel结构识别结果DTO
 * <p>
 * AI识别Excel结构后返回的字段映射信息。
 */
@Data
@Schema(description = "Excel结构识别结果")
public class ExcelStructureDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "识别出的sheet列表")
    private List<SheetStructure> sheets;

    @Data
    @Schema(description = "Sheet结构")
    public static class SheetStructure implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "Sheet名称")
        private String sheetName;

        @Schema(description = "表头行号（0-based）")
        private Integer headerRowIndex;

        @Schema(description = "数据起始行号（0-based）")
        private Integer dataStartRowIndex;

        @Schema(description = "字段映射：字段名 -> 列名")
        private Map<String, Object> columns;

        @Schema(description = "识别出的列信息列表")
        private List<ColumnInfo> columnInfos;
    }

    @Data
    @Schema(description = "列信息")
    public static class ColumnInfo implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "列索引（0-based）")
        private Integer columnIndex;

        @Schema(description = "原始列名")
        private String columnName;

        @Schema(description = "映射到的字段名：postName/responsibility/requirement/industry/description")
        private String mappedField;
    }
}
