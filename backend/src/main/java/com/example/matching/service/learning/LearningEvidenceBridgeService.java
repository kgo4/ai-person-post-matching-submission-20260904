package com.example.matching.service.learning;

import com.example.matching.entity.learning.LearningPathStep;
import com.example.matching.entity.learning.LearningProjectSubmission;
import com.example.matching.entity.learning.LearningProjectTask;

/**
 * 学习证据桥接服务接口
 * <p>
 * 负责把员工提交的项目材料转化为能力证据。
 * <p>
 * ⚠️ 证据的产生<b>不再依赖人工审核</b>：项目材料提交即生成证据，
 * 置信度按材料完整度计（{@code LearningEvidenceConfidencePolicy#calculateByCompleteness}）。
 * 证据只代表「员工提交了这些材料」，<b>不代表能力等级已被确认</b> ——
 * 等级更新只在「能力提升申请」经 HR 复核通过时发生（唯一的画像写入路径）。
 *
 * @author system
 */
public interface LearningEvidenceBridgeService {

    /**
     * 为一条项目材料提交创建能力证据。
     *
     * @param submission    提交记录
     * @param task          项目任务
     * @param step          学习步骤
     * @param confidence    证据置信度 (0-100)
     * @param credibility   证据可信度 (0-100)
     * @return 证据ID
     */
    Long createEvidenceForSubmission(LearningProjectSubmission submission,
                                     LearningProjectTask task,
                                     LearningPathStep step,
                                     Integer confidence,
                                     Integer credibility);

    /**
     * 作废一条证据（逻辑删除）。
     *
     * <p>用于「同一任务被重新提交」的场景：材料更新后，旧证据不再代表最新事实。
     * 不作废的话，一条任务会留下多条分数不同、内容互相矛盾的「当前证据」，
     * HR 复核时无从判断该采信哪一条。</p>
     *
     * @param evidenceId 证据ID；为空时不做任何事
     */
    void deprecateEvidence(Long evidenceId);
}
