package com.example.matching.service.post;

import com.example.matching.dto.post.api.PostTrendCandidateUpdateRequest;
import com.example.matching.dto.post.api.PostTrendLandResult;

/**
 * 趋势候选的人工确认与落地。
 * <p>
 * 这是整条趋势发现链路上唯一写业务数据的地方，三件事必须同时成立：
 * <ol>
 *   <li><b>人工确认才落地</b> —— 没有任何自动创建路径，解析只产出候选；</li>
 *   <li><b>落地原子</b> —— 建岗位 / 写能力画像 / 回写候选在同一事务里，
 *       否则中途失败会留下「有岗位、无能力」的脏数据（见设计文档 §6）；</li>
 *   <li><b>幂等</b> —— 两个管理员同时点同一张卡，只能建出一个岗位。</li>
 * </ol>
 */
public interface PostTrendConfirmService {

    /**
     * 落地候选：新岗位候选 → 新建岗位并写入能力画像；能力变更候选 → 直接改写既有岗位能力画像。
     *
     * @param reviewComment 审核意见，为空时写入默认说明
     */
    PostTrendLandResult land(Long candidateId, String reviewComment, Long operatorId);

    /**
     * 只改审核状态、不落地。
     * <p>
     * 用于「暂不考虑这个候选」（REJECTED）或「先标记通过、稍后再落」。
     * 状态流转走 CAS，已被他人处理的候选返回 false，不覆盖对方的结论。
     *
     * @param confirmStatus APPROVED 或 REJECTED
     * @return 是否实际生效
     */
    boolean review(Long candidateId, String confirmStatus, String reviewComment, Long operatorId);

    /**
     * 保存人工调整后的岗位名 / 描述 / 能力清单。
     * <p>
     * 默认路径（管理员直接点确认）不会调到这里；这里是「展开调整」的写入口。
     * 调整后会重新计算变更类型，保证抽屉里看到的 diff 与落地时写入的一致。
     */
    void updatePayload(Long candidateId, PostTrendCandidateUpdateRequest request);

    /**
     * 「采用现有标签」：把本次解析提出的能力归位到一个既有正式标签。
     * <p>
     * 同时会把这个归位结果回填到本任务下仍待确认的候选载荷里，
     * 否则管理员点完「采用」再落地，写进去的 tagId 依然是空的，动作等于没做。
     */
    void adoptNewTag(Long candidateId, Long tagId, String comment, Long operatorId);

    /** 「忽略」：认为该能力名不该进标签体系，候选置为已驳回。 */
    void ignoreNewTag(Long candidateId, String comment, Long operatorId);
}
