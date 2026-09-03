package com.example.matching.dto.post;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 岗位数据清洗记录分页查询参数
 */
@Data
@Schema(description = "清洗记录分页查询参数")
public class PostCleaningRecordPageQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "当前页码，默认1")
    private Integer pageNum = 1;

    @Schema(description = "每页大小，默认20")
    private Integer pageSize = 20;

    @Schema(description = "来源类型筛选：JD_TEXT / EXCEL_IMPORT / EMERGING_POST / API")
    private String sourceType;

    @Schema(description = "重复状态筛选：NONE / SUSPECTED / DUPLICATE_BLOCKED")
    private String duplicateStatus;

    @Schema(description = "是否被阻断筛选：true / false")
    private Boolean blocked;

    @Schema(description = "是否进入Agent筛选：true / false")
    private Boolean enteredAgent;

    @Schema(description = "岗位名称模糊搜索")
    private String postName;

    @Schema(description = "质量评分下限")
    private Double qualityScoreMin;

    @Schema(description = "质量评分上限")
    private Double qualityScoreMax;
}
