package com.example.matching.service.learning;

import com.example.matching.dto.learning.LearningPathItemDTO;
import com.example.matching.dto.learning.LearningPathRequestDTO;

import java.util.List;

/**
 * 学习路径服务接口
 * <p>
 * P1阶段为基于检索的学习路径推荐，无需LLM生成。
 *
 * @author system
 */
public interface LearningPathService {

    /**
     * 生成学习路径
     *
     * @param request 请求DTO
     * @return 学习路径项列表，按推荐顺序排列
     */
    List<LearningPathItemDTO> generateLearningPath(LearningPathRequestDTO request);
}
