package com.example.matching.dto.learning;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 学习路径生成请求
 *
 * @author system
 */
@Data
public class LearningPathGenerateRequest {

    /** 匹配记录ID */
    @NotNull(message = "matchingRecordId cannot be null")
    private Long matchingRecordId;

    /** 目标匹配分 */
    private BigDecimal targetScore;

    /** 是否包含项目实践任务 */
    private Boolean includeProjectTasks;

    /** 是否强制重新生成 */
    private Boolean forceRegenerate;

    /** 是否使用 AI 增强生成（LLM + 知识图谱 + RAG），默认 false 使用确定性规则 */
    private Boolean useAi;
}
