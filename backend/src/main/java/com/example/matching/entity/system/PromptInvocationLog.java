package com.example.matching.entity.system;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Prompt 调用埋点日志 —— 每次 LLM 调用自动记录
 * <p>
 * 用于 Prompt A/B 测试效果评估和调用链路追踪。
 */
@Data
@TableName("prompt_invocation_log")
public class PromptInvocationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** Prompt 模板名（如 matching-prompt） */
    private String promptName;

    /** Prompt 版本号（从文件第 1 行注释提取） */
    private String promptVersion;

    /** 调用场景：MATCHING / INTERVIEW / JD_EXTRACT / LEARNING 等 */
    private String scenario;

    /** 使用的模型 */
    private String modelName;

    /** 调用耗时（毫秒） */
    private Long latencyMs;

    /** Tool调用总耗时（毫秒） */
    private Long toolLatencyMs;

    /** 模型推理轮次（含Tool调用回填数） */
    private Integer modelRounds;

    /** 重试次数 */
    private Integer retryCount;

    /** 任务排队等待时间（毫秒） */
    private Long queueWaitMs;

    /** 是否命中缓存 */
    private Boolean cacheHit;

    /** 是否调用成功 */
    private Boolean success;

    /** 是否走了降级 fallback */
    private Boolean fallbackUsed;

    /** 输入 Prompt 长度（字符数） */
    private Integer inputChars;

    /** 输出响应长度（字符数） */
    private Integer outputChars;

    /** 发起调用的用户 ID（0 = 系统） */
    private Long userId;

    /** 追踪ID */
    private String traceId;

    /** 后续人工反馈分（延迟回填，匹配场景来自 MatchingFeedbackDataset） */
    private Integer feedbackScore;

    /** 创建时间 */
    private LocalDateTime createdTime;
}