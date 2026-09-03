package com.example.matching.service.matching.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.dto.matching.MatchingApprovalDTO;
import com.example.matching.entity.matching.MatchingApprovalFlow;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.mapper.matching.MatchingApprovalFlowMapper;
import com.example.matching.mapper.matching.MatchingRecordMapper;
import com.example.matching.service.matching.MatchingApprovalFlowService;
import com.example.matching.config.RedisCacheNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 匹配审批流程服务实现
 * <p>
 * 单级审批：HR 发起 → 管理员审核 → 通过/驳回。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingApprovalFlowServiceImpl extends ServiceImpl<MatchingApprovalFlowMapper, MatchingApprovalFlow> implements MatchingApprovalFlowService {

    private final MatchingRecordMapper matchingRecordMapper;

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = RedisCacheNames.MATCHING_RECORD_DETAIL, key = "#matchingRecordId"),
            @CacheEvict(cacheNames = RedisCacheNames.MATCHING_RECORD_PAGE, allEntries = true),
            @CacheEvict(cacheNames = RedisCacheNames.DASHBOARD_STATS, allEntries = true)
    })
    public void initiateApproval(Long matchingRecordId, Long adminApproverId) {
        MatchingRecord record = matchingRecordMapper.selectById(matchingRecordId);
        if (record == null) {
            throw new BusinessException(404, "匹配记录不存在");
        }
        if (record.getApprovalStatus() != null && record.getApprovalStatus() == 1) {
            throw new BusinessException(400, "该匹配记录已在审批中");
        }
        if (record.getApprovalStatus() != null
                && (record.getApprovalStatus() == 2 || record.getApprovalStatus() == 3)) {
            throw new BusinessException(400, "Approval record is already finalized");
        }
        if (record.getIsLocked() != null && record.getIsLocked() == 1) {
            throw new BusinessException(400, "该匹配记录已被锁定，无法发起审批");
        }

        saveFlowNode(matchingRecordId, 1, "管理员审核", adminApproverId);
        record.setApprovalStatus(1);
        matchingRecordMapper.updateById(record);
        log.info("审批已发起: recordId={}, adminApprover={}", matchingRecordId, adminApproverId);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = RedisCacheNames.MATCHING_RECORD_DETAIL, key = "#dto.matchingRecordId"),
            @CacheEvict(cacheNames = RedisCacheNames.MATCHING_RECORD_PAGE, allEntries = true),
            @CacheEvict(cacheNames = RedisCacheNames.DASHBOARD_STATS, allEntries = true)
    })
    public void approve(MatchingApprovalDTO dto) {
        if (dto == null || (dto.getApprovalStatus() == null
                || (dto.getApprovalStatus() != 1 && dto.getApprovalStatus() != 2))) {
            throw new BusinessException(400, "Invalid approval status; only 1 (approve) or 2 (reject) is allowed");
        }
        MatchingRecord record = matchingRecordMapper.selectById(dto.getMatchingRecordId());
        if (record == null) {
            throw new BusinessException(404, "匹配记录不存在");
        }

        List<MatchingApprovalFlow> flows = list(Wrappers.<MatchingApprovalFlow>lambdaQuery()
                .eq(MatchingApprovalFlow::getMatchingRecordId, dto.getMatchingRecordId())
                .eq(MatchingApprovalFlow::getApprovalStatus, 0)
                .orderByAsc(MatchingApprovalFlow::getNodeOrder));

        if (flows.isEmpty()) {
            throw new BusinessException(404, "无待审批节点");
        }

        MatchingApprovalFlow currentNode = flows.get(0);
        boolean approved = dto.getApprovalStatus() == 1;
        currentNode.setApprovalStatus(approved ? 1 : 2); // 1=通过, 2=驳回
        currentNode.setApprovalRemark(dto.getApprovalRemark());
        currentNode.setApprovalTime(LocalDateTime.now());
        boolean claimed = update(Wrappers.<MatchingApprovalFlow>update()
                .eq("id", currentNode.getId())
                .eq("approval_status", 0)
                .set("approval_status", currentNode.getApprovalStatus())
                .set("approval_remark", currentNode.getApprovalRemark())
                .set("approval_time", currentNode.getApprovalTime()));
        if (!claimed) {
            throw new BusinessException(409, "审批节点已被其他请求处理");
        }

        record.setApprovalStatus(approved ? 2 : 3); // 2=已通过, 3=已驳回
        matchingRecordMapper.updateById(record);
    }

    @Override
    public List<MatchingApprovalFlow> listByRecordId(Long matchingRecordId) {
        return list(Wrappers.<MatchingApprovalFlow>lambdaQuery()
                .eq(MatchingApprovalFlow::getMatchingRecordId, matchingRecordId)
                .orderByAsc(MatchingApprovalFlow::getNodeOrder));
    }

    /** 获取待办任务（查询本地表） */
    public List<Map<String, Object>> getPendingTasks(Long userId) {
        List<MatchingApprovalFlow> localTasks = list(Wrappers.<MatchingApprovalFlow>lambdaQuery()
                .eq(MatchingApprovalFlow::getApproverId, userId)
                .eq(MatchingApprovalFlow::getApprovalStatus, 0)
                .orderByDesc(MatchingApprovalFlow::getCreatedTime));

        List<Map<String, Object>> result = new ArrayList<>();
        for (MatchingApprovalFlow flow : localTasks) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("taskId", String.valueOf(flow.getId()));
            item.put("taskName", flow.getNodeName());
            item.put("matchingRecordId", flow.getMatchingRecordId());
            item.put("createTime", flow.getCreatedTime());
            result.add(item);
        }
        return result;
    }

    private void saveFlowNode(Long matchingRecordId, int order, String name, Long approverId) {
        MatchingApprovalFlow flow = new MatchingApprovalFlow();
        flow.setMatchingRecordId(matchingRecordId);
        flow.setNodeOrder(order);
        flow.setNodeName(name);
        flow.setApproverId(approverId);
        flow.setApprovalStatus(0);
        save(flow);
    }
}
