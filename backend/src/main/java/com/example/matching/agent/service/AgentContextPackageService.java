package com.example.matching.agent.service;

import com.example.matching.agent.dto.AgentContextPackage;

/**
 * Agent上下文包服务接口
 *
 * @author system
 */
public interface AgentContextPackageService {

    /**
     * 为匹配记录构建上下文包
     *
     * @param matchingRecordId 匹配记录ID
     * @return 上下文包
     */
    AgentContextPackage buildForMatchingRecord(Long matchingRecordId);

    /**
     * 为员工构建上下文包
     *
     * @param empId 员工ID
     * @return 上下文包
     */
    AgentContextPackage buildForEmployee(Long empId);

    /**
     * 为岗位构建上下文包
     *
     * @param postId 岗位ID
     * @return 上下文包
     */
    AgentContextPackage buildForPost(Long postId);
}
