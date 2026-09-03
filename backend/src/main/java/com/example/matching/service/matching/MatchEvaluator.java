package com.example.matching.service.matching;

import com.example.matching.dto.matching.MatchingAbilitySnapshot;
import com.example.matching.dto.matching.MatchingRequirementSnapshot;
import com.example.matching.entity.matching.MatchingBlackWhiteList;
import com.example.matching.entity.matching.MatchingRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 统一匹配评估器 —— 唯一的匹配评分入口。
 *
 * <p>为执行匹配和推荐预览提供一致、可复现的评分逻辑。
 * 所有新的匹配路径必须通过此评估器获取权威分数。</p>
 *
 * <p>执行匹配（{@link com.example.matching.service.matching.impl.MatchingExecuteServiceImpl}）
 * 和推荐预览（{@link com.example.matching.service.matching.impl.EmployeeRecommendServiceImpl}、
 * {@link com.example.matching.service.matching.impl.EmployeePostRecommendServiceImpl}）
 * 均使用此评估器消除评分行为漂移。</p>
 */
@Slf4j
@Service
public class MatchEvaluator {

    private final MatchingAlgorithmService algorithmService;
    private final MatchingScoreService scoreService;
    private final MatchingEvidenceScoreCalculator evidenceScoreCalculator;
    private final MatchingTrainingWeightProfileStore weightProfileStore;

    public MatchEvaluator(MatchingAlgorithmService algorithmService,
                          MatchingScoreService scoreService,
                          MatchingEvidenceScoreCalculator evidenceScoreCalculator,
                          MatchingTrainingWeightProfileStore weightProfileStore) {
        this.algorithmService = algorithmService;
        this.scoreService = scoreService;
        this.evidenceScoreCalculator = evidenceScoreCalculator;
        this.weightProfileStore = weightProfileStore;
    }

    /**
     * 评估单个候选人与岗位的确定性匹配度。
     *
     * @return 填充了 L2 评分字段的 MatchingRecord（含 matcher name 用于可追溯性）
     */
    public EvaluatedMatch evaluate(EvaluationContext ctx) {
        MatchingRecord l2Record = algorithmService.matchWithAbilities(
                ctx.empId(), ctx.postId(), ctx.abilities(), ctx.requirements(),
                ctx.blackWhiteList(), ctx.batchNo(), ctx.vectorScore());

        BigDecimal evidenceScore = evidenceScoreCalculator.computeEvidenceScoreFromSnapshots(ctx.abilities());
        BigDecimal semanticScore = ctx.vectorScore();
        MatchingTrainingWeightProfileStore.WeightProfile profile = weightProfileStore.currentProfile();

        MatchScoreResult scoreResult = scoreService.score(
                MatchScoreInput.deterministic(l2Record.getPostModelScore(), semanticScore,
                        evidenceScore, profile));

        return new EvaluatedMatch(
                l2Record, evidenceScore, semanticScore, scoreResult, profile);
    }

    /**
     * 评估单个候选人与岗位的匹配度（含受约束的 AI 分数）。
     */
    public EvaluatedMatch evaluateWithLlm(EvaluationContext ctx, BigDecimal llmScore) {
        MatchingRecord l2Record = algorithmService.matchWithAbilities(
                ctx.empId(), ctx.postId(), ctx.abilities(), ctx.requirements(),
                ctx.blackWhiteList(), ctx.batchNo(), ctx.vectorScore());

        BigDecimal evidenceScore = evidenceScoreCalculator.computeEvidenceScoreFromSnapshots(ctx.abilities());
        BigDecimal semanticScore = ctx.vectorScore();
        MatchingTrainingWeightProfileStore.WeightProfile profile = weightProfileStore.currentProfile();

        MatchScoreResult scoreResult = scoreService.score(
                MatchScoreInput.withAi(l2Record.getPostModelScore(), semanticScore,
                        evidenceScore, llmScore, profile));

        return new EvaluatedMatch(
                l2Record, evidenceScore, semanticScore, scoreResult, profile);
    }

    public int determineStatus(BigDecimal finalScore) {
        return algorithmService.determineMatchStatus(finalScore);
    }

    /**
     * 评估上下文 —— 封装评估所需的所有输入（M-12：只消费匹配专用 DTO）。
     */
    public record EvaluationContext(
            Long empId,
            Long postId,
            String batchNo,
            List<MatchingAbilitySnapshot> abilities,
            List<MatchingRequirementSnapshot> requirements,
            List<MatchingBlackWhiteList> blackWhiteList,
            BigDecimal vectorScore
    ) {}

    /**
     * 评估结果 —— 包含 L2 记录、证据/语义分数和权重快照。
     */
    public record EvaluatedMatch(
            MatchingRecord l2Record,
            BigDecimal evidenceScore,
            BigDecimal semanticScore,
            MatchScoreResult scoreResult,
            MatchingTrainingWeightProfileStore.WeightProfile weightProfile
    ) {}
}
