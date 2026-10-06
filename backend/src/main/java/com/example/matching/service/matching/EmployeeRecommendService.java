package com.example.matching.service.matching;

import com.example.matching.dto.matching.EmployeeRecommendDTO;

/**
 * 岗位推荐员工服务接口
 * <p>
 * 与 EmployeePostRecommendService（员工推荐岗位）对称，
 * 实现"岗位找人"的预览推荐能力。
 */
public interface EmployeeRecommendService {

    /**
     * 为岗位推荐适配员工
     * <p>
     * 流程：加载岗位能力模型 → 向量召回候选员工 Top K → L2预评分 → 返回推荐卡片
     *
     * @param request 推荐请求（含岗位ID、Top K、预览开关）
     * @return 推荐结果（含候选员工列表及评分详情）
     */
    EmployeeRecommendDTO.Response recommendEmployeesForPost(EmployeeRecommendDTO.Request request);
}
