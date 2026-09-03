package com.example.matching.dto.matching;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 结构化复核请求DTO
 * <p>
 * 替代原有的 MatchingRecord 瞬态字段传递方式，
 * 提供完整的结构化复核数据。
 */
@Data
public class StructuredReviewDTO {

    /** 匹配记录ID */
    private Long matchingRecordId;

    /** 人工最终匹配分 */
    private BigDecimal finalMatchScore;

    /** 人工最终匹配状态：1强适配，2适配，3待观察，4不适配 */
    private Integer matchStatus;

    /** 维度修正列表 */
    private List<DimensionCorrectionDTO> dimensionCorrections;

    /** 人工补充说明 */
    private String feedbackComment;

    /** 是否用于训练（整体标记） */
    /** 是否允许导出为校准数据（默认 false，需人工显式确认） */
    private Boolean exportEnabled = false;

    /** 人工备注 */
    private String manualRemark;
}
