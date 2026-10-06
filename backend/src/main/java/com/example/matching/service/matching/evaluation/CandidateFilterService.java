package com.example.matching.service.matching.evaluation;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.post.PostPost;
import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.mapper.post.PostPostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 统一候选人过滤服务
 * <p>
 * 在向量召回之后、评估之前，对候选人进行统一的活跃状态过滤。
 * 确保推荐预览和正式匹配使用相同的过滤规则。
 * <p>
 * 过滤规则：
 * - 员工：status=1 且 isLocked=0
 * - 岗位：status=1
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandidateFilterService {

    private final EmpEmployeeMapper empEmployeeMapper;
    private final PostPostMapper postPostMapper;

    /**
     * 过滤员工候选人（移除未激活或锁定的员工）
     *
     * @param candidateIds 候选员工ID列表
     * @return 通过过滤的员工列表
     */
    public List<EmpEmployee> filterActiveEmployees(List<Long> candidateIds) {
        if (candidateIds == null || candidateIds.isEmpty()) {
            return List.of();
        }
        return empEmployeeMapper.selectList(
                Wrappers.<EmpEmployee>lambdaQuery()
                        .in(EmpEmployee::getId, candidateIds)
                        .eq(EmpEmployee::getStatus, 1)
                        .eq(EmpEmployee::getIsLocked, 0));
    }

    /**
     * 过滤岗位候选人（移除未激活的岗位）
     *
     * @param candidateIds 候选岗位ID列表
     * @return 通过过滤的岗位列表
     */
    public List<PostPost> filterActivePosts(List<Long> candidateIds) {
        if (candidateIds == null || candidateIds.isEmpty()) {
            return List.of();
        }
        return postPostMapper.selectList(
                Wrappers.<PostPost>lambdaQuery()
                        .in(PostPost::getId, candidateIds)
                        .eq(PostPost::getStatus, 1));
    }

    /**
     * 从向量召回结果中提取候选ID
     */
    public List<Long> extractCandidateIds(List<Map<String, Object>> vectorResults) {
        List<Long> ids = new ArrayList<>();
        for (Map<String, Object> result : vectorResults) {
            try {
                ids.add(Long.parseLong(String.valueOf(result.get("refId"))));
            } catch (NumberFormatException e) {
                log.warn("无法解析向量召回结果的refId: {}", result.get("refId"));
            }
        }
        return ids;
    }
}
