package com.example.matching.dto.post.api;

import java.io.Serializable;

/**
 * 候选落地结果。
 *
 * @param candidateId    候选ID
 * @param candidateType  NEW_POST / ABILITY_CHANGE
 * @param postId         落地后的岗位ID：新岗位候选为新建岗位，能力变更为被变更的既有岗位
 * @param postName       岗位名称
 * @param abilityCount   写入岗位能力画像的能力项数量
 * @param createdNewPost 是否新建了岗位（用于界面区分「已创建」与「已更新」）
 */
public record PostTrendLandResult(
        Long candidateId,
        String candidateType,
        Long postId,
        String postName,
        int abilityCount,
        boolean createdNewPost) implements Serializable {
}
