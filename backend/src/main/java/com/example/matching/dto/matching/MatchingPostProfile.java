package com.example.matching.dto.matching;

import java.util.List;

/**
 * 匹配专用岗位画像（M-12）
 *
 * @param postId        岗位ID
 * @param postCode      岗位编码（可空）
 * @param postName      岗位名称
 * @param postLevel     岗位职级（如 P5-P7，可空）
 * @param jobDescription 岗位描述
 * @param extendFields  扩展字段（JSON 字符串，向量召回用，可空）
 * @param requirements  岗位要求快照列表
 */
public record MatchingPostProfile(
        Long postId,
        String postCode,
        String postName,
        String postLevel,
        String jobDescription,
        String extendFields,
        List<MatchingRequirementSnapshot> requirements
) {
}
