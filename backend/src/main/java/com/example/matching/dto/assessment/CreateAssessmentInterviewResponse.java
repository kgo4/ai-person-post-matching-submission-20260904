package com.example.matching.dto.assessment;

import com.example.matching.vo.assessment.CapabilityAssessmentVO;
import lombok.Data;

/**
 * 创建能力评估 AI 面试的响应：显式返回 sessionId、postId，前端凭此进入面试页面。
 *
 * @author system
 */
@Data
public class CreateAssessmentInterviewResponse {
    private CapabilityAssessmentVO.StageRunView stageRun;
    private Long sessionId;
    private Long postId;
}
