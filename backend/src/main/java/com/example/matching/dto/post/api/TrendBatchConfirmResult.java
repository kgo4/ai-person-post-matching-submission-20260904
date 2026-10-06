package com.example.matching.dto.post.api;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 批量确认结果。
 * <p>
 * 把「成功的」与「被跳过的」分开返回，而不是只回一个数量：
 * 批量操作里最怕的是「点了 8 个，只有 3 个生效」而界面没有任何提示。
 *
 * @param landed  成功落地的结果
 * @param skipped 被跳过的候选及原因
 */
public record TrendBatchConfirmResult(
        List<PostTrendLandResult> landed,
        List<Skipped> skipped) implements Serializable {

    /**
     * 被跳过的候选。
     *
     * @param candidateId 候选ID
     * @param postName    候选岗位名（便于界面直接显示是哪一条）
     * @param reason      中文原因，如「治理判定为待复核，需逐条人工确认」
     */
    public record Skipped(Long candidateId, String postName, String reason) implements Serializable {
    }

    public static TrendBatchConfirmResult empty() {
        return new TrendBatchConfirmResult(new ArrayList<>(), new ArrayList<>());
    }
}
