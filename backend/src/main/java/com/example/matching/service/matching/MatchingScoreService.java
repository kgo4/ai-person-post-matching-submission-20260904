package com.example.matching.service.matching;

/**
 * 统一匹配评分服务 —— 唯一评分入口（M-09）。
 * <p>
 * 执行匹配、推荐预览、训练回放必须通过 {@link #score(MatchScoreInput)} 获取权威分数。
 */
public interface MatchingScoreService {

    /**
     * 计算权威匹配分数。
     * <p>
     * 每个匹配结果只保留一个分数和算法版本。
     * 算法改动时须递增 {@link MatchScoreResult#CURRENT_VERSION} 并记录旧新分数差异。
     *
     * @param input 评分输入（统一入口，禁止直接拼装 ScoreBreakdown）
     * @return 包含各维度得分和算法版本的结果
     */
    MatchScoreResult score(MatchScoreInput input);
}
