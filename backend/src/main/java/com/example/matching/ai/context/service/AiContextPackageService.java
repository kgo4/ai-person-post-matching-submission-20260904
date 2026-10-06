package com.example.matching.ai.context.service;

import com.example.matching.ai.context.dto.AiContextPackageDTO;

/**
 * AI上下文包服务
 *
 * @author system
 */
public interface AiContextPackageService {

    /**
     * 为匹配记录构建上下文包
     *
     * @param matchingRecordId 匹配记录ID
     * @return 上下文包
     */
    AiContextPackageDTO buildForMatching(Long matchingRecordId);

    /**
     * 为员工构建上下文包
     *
     * @param empId 员工ID
     * @return 上下文包
     */
    AiContextPackageDTO buildForEmployee(Long empId);

    /**
     * 为岗位构建上下文包
     *
     * @param postId 岗位ID
     * @return 上下文包
     */
    AiContextPackageDTO buildForPost(Long postId);

    /**
     * 为学习路径构建上下文包
     *
     * @param matchingRecordId 匹配记录ID
     * @return 上下文包
     */
    AiContextPackageDTO buildForLearningPath(Long matchingRecordId);
}
