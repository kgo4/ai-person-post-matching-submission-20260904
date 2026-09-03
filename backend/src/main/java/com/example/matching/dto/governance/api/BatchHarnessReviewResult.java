package com.example.matching.dto.governance.api;

import java.io.Serializable;
import java.util.List;

/** 批量审核逐项执行，单项失败不回滚已成功的独立审核事务。 */
public record BatchHarnessReviewResult(
        int successCount,
        int failedCount,
        List<ItemResult> results
) implements Serializable {
    public record ItemResult(Long id, boolean success, String reason) implements Serializable {
    }
}
