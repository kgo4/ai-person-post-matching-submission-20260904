package com.example.matching.agent.service;

import com.example.matching.agent.dto.LearningPathAgentRequest;
import com.example.matching.agent.dto.LearningPathAgentResult;

/**
 * 学习路径Agent服务接口
 *
 * @author system
 */
public interface LearningPathAgentService {

    /**
     * 预览学习路径
     *
     * @param request 请求
     * @return 学习路径预览结果
     */
    LearningPathAgentResult preview(LearningPathAgentRequest request);
}
