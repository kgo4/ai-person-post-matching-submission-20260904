package com.example.matching.service.contest;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.dto.contest.EvidenceCreateDTO;
import com.example.matching.dto.contest.EvidenceQueryDTO;
import com.example.matching.dto.contest.EvidenceReviewDTO;
import com.example.matching.entity.contest.ContestEvidenceItem;

import java.util.Map;

/**
 * 证据中心服务接口
 *
 * @author system
 */
public interface EvidenceCenterService {

    /**
     * 创建证据
     *
     * @param dto 创建DTO
     * @return 创建的证据实体
     */
    ContestEvidenceItem createEvidence(EvidenceCreateDTO dto);

    /**
     * 审核证据
     *
     * @param id   证据ID
     * @param dto  审核DTO
     * @param userId 审核人ID
     */
    void reviewEvidence(Long id, EvidenceReviewDTO dto, Long userId);

    /**
     * 分页查询证据
     *
     * @param page  分页参数
     * @param query 查询条件
     * @return 分页结果
     */
    IPage<ContestEvidenceItem> pageEvidence(Page<ContestEvidenceItem> page, EvidenceQueryDTO query);

    /**
     * 获取证据详情
     *
     * @param id 证据ID
     * @return 证据实体
     */
    ContestEvidenceItem getEvidenceById(Long id);

    /**
     * 获取证据统计摘要
     *
     * @return 统计摘要Map
     */
    Map<String, Object> getEvidenceSummary();

    /**
     * Build the evidence chain for one employee.
     *
     * @param empId employee id
     * @return employee ability evidence chain
     */
    Map<String, Object> getEmployeeEvidenceChain(Long empId);

    /**
     * Build the evidence chain for one post.
     *
     * @param postId post id
     * @return post ability requirement evidence chain
     */
    Map<String, Object> getPostEvidenceChain(Long postId);

    /**
     * 从现有记录回填证据
     *
     * @param sourceType 来源类型：JD_IMPORT/RESUME_PARSE/MATCHING_FEEDBACK
     * @param limit      最大回填数量
     * @return 创建的证据数量
     */
    int backfillEvidence(String sourceType, int limit);
}
