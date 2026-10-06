package com.example.matching.dto.matching;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 校准数据查询 VO（Controller 不得直接返回 entity）。
 */
public record CalibrationRecordVO(
        Long id,
        Long matchingRecordId,
        Long empId,
        Long postId,
        BigDecimal aiMatchScore,
        BigDecimal finalMatchScore,
        Integer finalMatchStatus,
        Integer adoptionStatus,
        String feedbackReasons,
        String feedbackComment,
        String calibrationSource,
        String calibrationTemplateVersion,
        Integer exportEnabled,
        LocalDateTime feedbackTime
) {
}
