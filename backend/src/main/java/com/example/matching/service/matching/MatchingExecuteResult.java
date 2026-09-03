package com.example.matching.service.matching;

import com.example.matching.dto.matching.CandidateScope;
import com.example.matching.entity.matching.MatchingRecord;

import java.util.List;

/**
 * 匹配执行结果（含候选池统计与异步转交信息）。
 * <p>
 * candidateCount/totalActiveCount/truncated 用于暴露候选池是否被截断；
 * excludedCount 表示因"无正式能力"被排除的员工数（全量/召回模式下过滤，不产生匹配记录）；
 * taskId 非空表示本次请求已转交异步匹配任务（大规模全量场景）。
 */
public record MatchingExecuteResult(
        List<MatchingRecord> records,
        CandidateScope candidateScope,
        int candidateCount,
        long totalActiveCount,
        boolean truncated,
        String taskId,
        int excludedCount
) {

    public static MatchingExecuteResult sync(List<MatchingRecord> records, CandidateScope scope,
                                             int candidateCount, long totalActiveCount, boolean truncated) {
        return new MatchingExecuteResult(records, scope, candidateCount, totalActiveCount, truncated, null, 0);
    }

    public static MatchingExecuteResult sync(List<MatchingRecord> records, CandidateScope scope,
                                             int candidateCount, long totalActiveCount, boolean truncated,
                                             int excludedCount) {
        return new MatchingExecuteResult(records, scope, candidateCount, totalActiveCount, truncated, null, excludedCount);
    }

    public static MatchingExecuteResult async(String taskId, CandidateScope scope,
                                              int candidateCount, long totalActiveCount, boolean truncated) {
        return new MatchingExecuteResult(List.of(), scope, candidateCount, totalActiveCount, truncated, taskId, 0);
    }

    public boolean isAsync() {
        return taskId != null;
    }
}
