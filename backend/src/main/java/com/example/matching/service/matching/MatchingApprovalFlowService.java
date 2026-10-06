package com.example.matching.service.matching;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.matching.dto.matching.MatchingApprovalDTO;
import com.example.matching.entity.matching.MatchingApprovalFlow;

import java.util.List;
import java.util.Map;

public interface MatchingApprovalFlowService extends IService<MatchingApprovalFlow> {

    /** 发起审批：HR 发起 → 管理员审核 */
    void initiateApproval(Long matchingRecordId, Long adminApproverId);

    /** 审批（通过/驳回） */
    void approve(MatchingApprovalDTO dto);

    /** 按匹配记录ID查询审批节点 */
    List<MatchingApprovalFlow> listByRecordId(Long matchingRecordId);

    /** 获取待审批任务列表 */
    List<Map<String, Object>> getPendingTasks(Long userId);
}
