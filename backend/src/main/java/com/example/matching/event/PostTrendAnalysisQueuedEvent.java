package com.example.matching.event;

import java.util.List;

/**
 * 岗位趋势解析任务在数据库提交后投递的触发事件。
 * <p>
 * 投递内容是解析所需的输入（材料文档ID与类别），执行线程据此跑完整条解析链路。
 */
public record PostTrendAnalysisQueuedEvent(Long taskId, List<Long> sourceDocumentIds,
                                           List<String> sourceCategories) {
}
