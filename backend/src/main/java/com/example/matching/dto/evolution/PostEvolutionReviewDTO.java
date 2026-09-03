package com.example.matching.dto.evolution;

import lombok.Data;

/**
 * 岗位演化审核DTO
 *
 * @author system
 */
@Data
public class PostEvolutionReviewDTO {

    /** 确认状态：APPROVED/REJECTED */
    private String confirmStatus;

    /** 审核意见 */
    private String reviewComment;
}
