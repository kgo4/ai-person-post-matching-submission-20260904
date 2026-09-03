package com.example.matching.dto.post;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 导入批次列表项VO
 */
@Data
@Schema(description = "导入批次列表项")
public class PostImportBatchVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "批次ID")
    private Long id;

    @Schema(description = "文件名")
    private String fileName;

    @Schema(description = "解析出的岗位总数")
    private Integer totalRows;

    @Schema(description = "成功导入数")
    private Integer successCount;

    @Schema(description = "失败数")
    private Integer failCount;

    @Schema(description = "导入状态：0-待解析，1-AI解析中，2-待确认，3-导入中，4-导入完成，5-导入失败")
    private Integer importStatus;

    @Schema(description = "取消标志：0正常，1已取消")
    private Integer cancelFlag;

    @Schema(description = "错误信息")
    private String errorMessage;

    @Schema(description = "创建时间")
    private LocalDateTime createdTime;

    @Schema(description = "更新时间")
    private LocalDateTime updatedTime;

    // ===== 统计字段（由Service层计算填充） =====

    @Schema(description = "待分析数量")
    private Integer pendingCount;

    @Schema(description = "分析中数量")
    private Integer analyzingCount;

    @Schema(description = "分析成功数量")
    private Integer successAnalyzedCount;

    @Schema(description = "分析失败数量")
    private Integer failedAnalyzedCount;
}
