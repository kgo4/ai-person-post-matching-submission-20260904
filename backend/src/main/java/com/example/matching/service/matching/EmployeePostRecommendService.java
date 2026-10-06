package com.example.matching.service.matching;

import com.example.matching.dto.matching.PostRecommendDTO;

/**
 * 员工推荐岗位服务接口
 * <p>
 * 基于员工能力画像，通过向量召回 + L2预评分，为员工推荐最适配的岗位。
 */
public interface EmployeePostRecommendService {

    /**
     * 为员工推荐适配岗位
     * <p>
     * 流程：读取员工能力画像 → 向量召回候选岗位 Top K → L2预评分 → 返回推荐卡片
     *
     * @param request 推荐请求（包含员工ID、TopK等参数）
     * @return 推荐结果（包含岗位推荐卡片列表）
     */
    PostRecommendDTO.Response recommendPostsForEmployee(PostRecommendDTO.Request request);
}
