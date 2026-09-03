package com.example.matching.service.matching;

import com.example.matching.dto.matching.MatchingExecuteDTO;

public interface MatchingExecuteService {

    /**
     * 执行匹配：返回结果列表与候选池统计。
     * <p>
     * 默认候选范围为 {@code ALL_ACTIVE}（全量在职员工，分页加载，无隐藏截断）；
     * 大规模全量任务自动转交异步 MatchingTask，此时返回的 {@link MatchingExecuteResult}
     * 携带 taskId 且 records 为空。
     */
    MatchingExecuteResult execute(MatchingExecuteDTO dto);
}
