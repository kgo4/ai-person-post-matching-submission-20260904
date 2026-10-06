package com.example.matching.dto.assessment;

import com.example.matching.vo.assessment.CapabilityAssessmentVO;
import lombok.Data;

/**
 * 生成验证测试的响应：显式返回 testId、postId，前端凭此轮询测试状态。
 *
 * @author system
 */
@Data
public class GenerateVerificationTestResponse {
    private CapabilityAssessmentVO.StageRunView stageRun;
    private Long testId;
    private Long postId;
}
