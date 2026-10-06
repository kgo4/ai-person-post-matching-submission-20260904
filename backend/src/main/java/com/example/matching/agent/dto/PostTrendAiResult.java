package com.example.matching.agent.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 岗位趋势抽取的 LLM 输出契约。
 * <p>
 * 与 {@link PostEvolutionAiResult} 的区别：演化是「给定一个岗位，判断它的能力怎么变」，
 * 本契约是「给定一批材料，找出里面有哪些岗位以及它们要什么能力」——**只做抽取，不做判定**。
 * 新岗位 / 能力变更的分流由向量检索决定（见 §7.1），不能让 LLM 自己声称。
 */
@Data
public class PostTrendAiResult {

    private List<TrendPost> posts = new ArrayList<>();

    @Data
    public static class TrendPost {

        /** 岗位名称，保持材料原文措辞 */
        private String postName;

        /** 岗位描述草案 */
        private String postDescription;

        /** 材料内强调度 0-10 */
        private BigDecimal emphasisScore;

        /** 强调度依据（引用材料原话） */
        private String emphasisReason;

        private List<String> responsibilities = new ArrayList<>();

        private List<String> businessScenarios = new ArrayList<>();

        /** 支持该岗位的材料片段编号，必须存在于上下文 */
        private Integer evidenceRef;

        private List<TrendAbility> abilities = new ArrayList<>();
    }

    @Data
    public static class TrendAbility {

        private String abilityName;

        /** 建议等级 1-5 */
        private Integer suggestedLevel;

        /** 建议权重 0-100 */
        private BigDecimal suggestedWeight;

        /** 是否核心 0/1 */
        private Integer isCore;

        /** 支持该能力的材料片段编号；为空时回退到所属岗位的 evidenceRef */
        private Integer evidenceRef;
    }
}
