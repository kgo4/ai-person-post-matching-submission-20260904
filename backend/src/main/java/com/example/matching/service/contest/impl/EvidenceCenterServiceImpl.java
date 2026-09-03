package com.example.matching.service.contest.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.dto.contest.EvidenceCreateDTO;
import com.example.matching.dto.contest.EvidenceQueryDTO;
import com.example.matching.dto.contest.EvidenceReviewDTO;
import com.example.matching.entity.contest.ContestEvidenceItem;
import com.example.matching.mapper.contest.ContestEvidenceItemMapper;
import com.example.matching.port.post.PostQueryPort;
import com.example.matching.port.post.PostQueryPort.PostAbilityDTO;
import com.example.matching.port.post.PostQueryPort.PostDTO;
import com.example.matching.port.tag.TagQueryPort;
import com.example.matching.port.tag.TagQueryPort.TagDTO;
import com.example.matching.port.talent.TalentQueryPort;
import com.example.matching.port.talent.TalentQueryPort.EmployeeAbilityDTO;
import com.example.matching.port.talent.TalentQueryPort.EmployeeDTO;
import com.example.matching.service.contest.EvidenceCenterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 证据中心服务实现
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvidenceCenterServiceImpl implements EvidenceCenterService {

    private final ContestEvidenceItemMapper evidenceItemMapper;
    private final TalentQueryPort talentQueryPort;
    private final PostQueryPort postQueryPort;
    private final TagQueryPort tagQueryPort;
    private final EvidenceBackfillService backfillService;

    private static final DateTimeFormatter CODE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final AtomicLong SEQUENCE = new AtomicLong(0);

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContestEvidenceItem createEvidence(EvidenceCreateDTO dto) {
        ContestEvidenceItem item = new ContestEvidenceItem();
        item.setEvidenceCode(generateEvidenceCode());
        item.setSourceType(dto.getSourceType());
        item.setSourceRefId(dto.getSourceRefId());
        item.setSourceTitle(dto.getSourceTitle());
        item.setSourceText(dto.getSourceText());
        item.setTargetType(dto.getTargetType());
        item.setTargetRefId(dto.getTargetRefId());
        item.setAbilityName(dto.getAbilityName());
        item.setTagId(dto.getTagId());
        item.setConfidenceScore(clampScore(dto.getConfidenceScore()));
        item.setCredibilityScore(clampScore(dto.getCredibilityScore()));
        item.setEvidenceStatus("PENDING");

        evidenceItemMapper.insert(item);
        return item;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewEvidence(Long id, EvidenceReviewDTO dto, Long userId) {
        ContestEvidenceItem item = evidenceItemMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(ErrorCodeEnum.INTERNAL_ERROR, "证据不存在: " + id);
        }

        String status = dto.getEvidenceStatus();
        if (!"VERIFIED".equals(status) && !"REJECTED".equals(status)) {
            throw new BusinessException(ErrorCodeEnum.INTERNAL_ERROR, "无效的审核状态: " + status);
        }

        item.setEvidenceStatus(status);
        item.setReviewComment(dto.getReviewComment());
        item.setReviewedBy(userId);
        item.setReviewedTime(LocalDateTime.now());
        evidenceItemMapper.updateById(item);
    }

    @Override
    public IPage<ContestEvidenceItem> pageEvidence(Page<ContestEvidenceItem> page, EvidenceQueryDTO query) {
        LambdaQueryWrapper<ContestEvidenceItem> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(query.getSourceType())) {
            wrapper.eq(ContestEvidenceItem::getSourceType, query.getSourceType());
        }
        if (StringUtils.hasText(query.getTargetType())) {
            wrapper.eq(ContestEvidenceItem::getTargetType, query.getTargetType());
        }
        if (StringUtils.hasText(query.getEvidenceStatus())) {
            wrapper.eq(ContestEvidenceItem::getEvidenceStatus, query.getEvidenceStatus());
        }
        if (StringUtils.hasText(query.getAbilityName())) {
            wrapper.like(ContestEvidenceItem::getAbilityName, query.getAbilityName());
        }

        wrapper.orderByDesc(ContestEvidenceItem::getCreatedTime);
        return evidenceItemMapper.selectPage(page, wrapper);
    }

    @Override
    public ContestEvidenceItem getEvidenceById(Long id) {
        ContestEvidenceItem item = evidenceItemMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(ErrorCodeEnum.INTERNAL_ERROR, "证据不存在: " + id);
        }
        return item;
    }

    @Override
    public Map<String, Object> getEvidenceSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();

        Long totalCount = evidenceItemMapper.countAllActive();
        summary.put("totalCount", totalCount != null ? totalCount : 0L);

        Map<String, Long> sourceTypeCount = new LinkedHashMap<>();
        for (Map<String, Object> row : evidenceItemMapper.countGroupBySourceType()) {
            sourceTypeCount.put(String.valueOf(row.get("sourceType")),
                    ((Number) row.get("cnt")).longValue());
        }
        summary.put("sourceTypeDistribution", sourceTypeCount);

        Map<String, Long> statusCount = new LinkedHashMap<>();
        for (Map<String, Object> row : evidenceItemMapper.countGroupByStatus()) {
            statusCount.put(String.valueOf(row.get("status")),
                    ((Number) row.get("cnt")).longValue());
        }
        summary.put("statusDistribution", statusCount);

        BigDecimal avgCredibility = evidenceItemMapper.averageCredibility();
        summary.put("averageCredibility", avgCredibility != null
                ? avgCredibility.setScale(2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        return summary;
    }

    @Override
    public Map<String, Object> getEmployeeEvidenceChain(Long empId) {
        EmployeeDTO employee = talentQueryPort.getEmployeeById(empId);
        if (employee == null) {
            throw new BusinessException(ErrorCodeEnum.INTERNAL_ERROR, "employee not found: " + empId);
        }

        List<EmployeeAbilityDTO> abilities = talentQueryPort.listAbilitiesByEmpId(empId);

        List<Map<String, Object>> abilityChains = new ArrayList<>();
        List<ContestEvidenceItem> allEvidence = new ArrayList<>();
        for (EmployeeAbilityDTO ability : abilities) {
            TagDTO tag = tagQueryPort.getTagById(ability.tagId());
            String abilityName = StringUtils.hasText(ability.abilityName()) ? ability.abilityName()
                    : tag != null ? tag.tagName() : null;
            if (!StringUtils.hasText(abilityName)) continue;
            List<ContestEvidenceItem> evidences = backfillService.loadEvidenceForTarget("EMP_ABILITY", ability.id());
            allEvidence.addAll(evidences);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("abilityId", ability.id());
            item.put("tagId", ability.tagId());
            item.put("abilityName", abilityName);
            item.put("level", ability.masteryLevel());
            item.put("source", ability.evaluationSource());
            item.put("sourceWeight", ability.sourceWeight());
            item.put("evaluationDate", ability.evaluationDate());
            item.put("remark", ability.remark());
            item.put("evidenceCount", evidences.size());
            item.put("averageConfidence", backfillService.averageScore(evidences, true));
            item.put("averageCredibility", backfillService.averageScore(evidences, false));
            item.put("evidences", backfillService.toEvidenceCards(evidences));
            abilityChains.add(item);
        }

        return backfillService.buildChainResult("EMPLOYEE", employee.id(), employee.employeeNo(), employee.realName(), abilityChains, allEvidence);
    }

    @Override
    public Map<String, Object> getPostEvidenceChain(Long postId) {
        PostDTO post = postQueryPort.getPostById(postId);
        if (post == null) {
            throw new BusinessException(ErrorCodeEnum.INTERNAL_ERROR, "post not found: " + postId);
        }

        List<PostAbilityDTO> models = postQueryPort.listRequirementsByPostId(postId);

        List<Map<String, Object>> abilityChains = new ArrayList<>();
        List<ContestEvidenceItem> allEvidence = new ArrayList<>();
        for (PostAbilityDTO model : models) {
            TagDTO tag = tagQueryPort.getTagById(model.tagId());
            String abilityName = StringUtils.hasText(model.abilityName()) ? model.abilityName()
                    : tag != null ? tag.tagName() : null;
            if (!StringUtils.hasText(abilityName)) continue;
            List<ContestEvidenceItem> evidences = backfillService.loadEvidenceForTarget("POST_ABILITY_MODEL", model.id());
            allEvidence.addAll(evidences);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("abilityId", model.id());
            item.put("tagId", model.tagId());
            item.put("abilityName", abilityName);
            item.put("level", model.minRequiredLevel());
            item.put("weight", model.weight());
            item.put("required", Integer.valueOf(1).equals(model.isRequired()));
            item.put("core", Integer.valueOf(1).equals(model.isCore()));
            item.put("modelVersion", model.modelVersion());
            item.put("source", "JD");
            item.put("remark", model.remark());
            item.put("evidenceCount", evidences.size());
            item.put("averageConfidence", backfillService.averageScore(evidences, true));
            item.put("averageCredibility", backfillService.averageScore(evidences, false));
            item.put("evidences", backfillService.toEvidenceCards(evidences));
            abilityChains.add(item);
        }

        return backfillService.buildChainResult("POST", post.id(), post.postCode(), post.postName(), abilityChains, allEvidence);
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public int backfillEvidence(String sourceType, int limit) {
        return backfillService.backfillEvidence(sourceType, limit);
    }

    private String generateEvidenceCode() {
        String timestamp = LocalDateTime.now().format(CODE_FORMATTER);
        long seq = SEQUENCE.incrementAndGet() % 1000;
        return String.format("EVD_%s_%03d", timestamp, seq);
    }

    /**
     * 将分数限制在 0-100 范围内
     */
    private BigDecimal clampScore(BigDecimal score) {
        if (score == null) {
            return BigDecimal.ZERO;
        }
        if (score.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (score.compareTo(new BigDecimal("100")) > 0) {
            return new BigDecimal("100");
        }
        return score;
    }
}
