package com.example.matching.mapper.post;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.matching.entity.post.PostTrendCandidate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 岗位趋势候选 Mapper。
 * <p>
 * 两个状态写入都走条件更新（CAS），而不是乐观锁重试：
 * 「确认创建」与「驳回」可能被两个管理员同时点到同一张卡上，
 * 若不做前置状态校验，后一次点击会把已经建好的岗位再建一遍。
 */
@Mapper
public interface PostTrendCandidateMapper extends BaseMapper<PostTrendCandidate> {

    /** 审核：仅 PENDING 可改状态，返回 0 表示已被他人处理。 */
    @Update("UPDATE post_trend_candidate SET confirm_status = #{confirmStatus}, "
            + "review_comment = #{reviewComment}, reviewed_by = #{reviewedBy}, reviewed_at = NOW() "
            + "WHERE id = #{candidateId} AND confirm_status = 'PENDING' AND is_deleted = 0")
    int reviewPending(@Param("candidateId") Long candidateId,
                      @Param("confirmStatus") String confirmStatus,
                      @Param("reviewComment") String reviewComment,
                      @Param("reviewedBy") Long reviewedBy);

    /**
     * 落地上报：把候选从 PENDING 直接置为 APPROVED 并回写落地产物。
     * <p>
     * 必须带 {@code confirm_status = 'PENDING'} 与 {@code created_post_id IS NULL}：
     * 前者防重复确认，后者防「已落地但状态被回退」的情况下二次创建。
     */
    @Update("UPDATE post_trend_candidate SET confirm_status = 'APPROVED', created_post_id = #{postId}, "
            + "review_comment = #{reviewComment}, reviewed_by = #{reviewedBy}, reviewed_at = NOW() "
            + "WHERE id = #{candidateId} AND confirm_status = 'PENDING' "
            + "AND created_post_id IS NULL AND is_deleted = 0")
    int markLanded(@Param("candidateId") Long candidateId,
                   @Param("postId") Long postId,
                   @Param("reviewComment") String reviewComment,
                   @Param("reviewedBy") Long reviewedBy);

    /** 保存人工调整后的载荷。仅待确认状态可改，避免已落地的候选被改写后与岗位画像不一致。 */
    @Update("UPDATE post_trend_candidate SET candidate_payload = #{payload}, "
            + "post_name = #{postName}, post_description = #{postDescription} "
            + "WHERE id = #{candidateId} AND confirm_status = 'PENDING' AND is_deleted = 0")
    int updatePendingPayload(@Param("candidateId") Long candidateId,
                             @Param("payload") String payload,
                             @Param("postName") String postName,
                             @Param("postDescription") String postDescription);
}

