package com.example.matching.dto.matching.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.util.List;

/**
 * 匹配执行响应：候选池统计 + 结果列表。
 * <p>
 * candidateScope/candidateCount/totalActiveCount/truncated 用于暴露
 * 候选池是否被截断；excludedCount 表示因"无正式能力"被排除的员工数；
 * taskId 非空表示请求已转交异步匹配任务（大规模全量）。
 */
@Schema(description = "匹配执行响应（含候选池统计）")
public record MatchingExecuteResultResponse(
        @Schema(description = "匹配结果记录列表（转异步时为空）") List<MatchingRecordResponse> records,
        @Schema(description = "候选范围：ALL_ACTIVE/VECTOR_RECALL/EXPLICIT_EMPLOYEES") String candidateScope,
        @Schema(description = "实际进入候选池的员工数") int candidateCount,
        @Schema(description = "在职员工总数") long totalActiveCount,
        @Schema(description = "候选池是否被截断（VECTOR_RECALL topK 截断为 true）") boolean truncated,
        @Schema(description = "转交异步任务的任务ID（大规模全量场景）") String taskId,
        @Schema(description = "是否已转交异步任务") boolean async,
        @Schema(description = "因无正式能力被排除的员工数（全量/召回模式下过滤）") int excludedCount
) implements Serializable {

    private static final long serialVersionUID = 1L;
}
