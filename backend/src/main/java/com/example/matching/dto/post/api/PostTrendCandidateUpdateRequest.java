package com.example.matching.dto.post.api;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 「展开调整」提交的人工修改。
 * <p>
 * 只有真的改过才需要调这个接口：默认态下审核人直接点「确认创建」即可，
 * 零手工填写是常态路径（见设计文档 §2.1）。修改仅允许发生在候选仍为待确认时，
 * 已落地的候选不接受改写，避免候选与岗位画像不一致。
 */
public record PostTrendCandidateUpdateRequest(
        String postName,
        String postDescription,
        List<AbilityItem> abilities) implements Serializable {

    /**
     * 能力项。
     * <p>
     * {@code tagId} 可空（未归位能力），此时只按 {@code abilityName} 落库，
     * 与 V131「岗位能力画像与标签解耦」的口径一致。
     */
    public record AbilityItem(
            String abilityName,
            Long tagId,
            Integer suggestedLevel,
            BigDecimal suggestedWeight,
            Integer isCore) implements Serializable {
    }
}
