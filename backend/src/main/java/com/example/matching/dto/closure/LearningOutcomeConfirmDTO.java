package com.example.matching.dto.closure;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Data
public class LearningOutcomeConfirmDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull
    private Long empId;

    private Long tagId;

    private String abilityName;

    private Long completedResourceId;

    @Min(1)
    @Max(5)
    private Integer beforeLevel;

    @NotNull
    @Min(1)
    @Max(5)
    private Integer confirmedLevel;

    private String confirmationSource;

    private String note;

    // ===== AI 学习建议追溯字段 =====

    /** AI 学习建议日志ID（用于追溯） */
    private Long aiSuggestionId;

    /** RAG 检索的 chunkIds（JSON数组格式） */
    private String ragChunkIds;

    /** AI 建议版本（用于后续分析） */
    private String aiSuggestionVersion;
}
