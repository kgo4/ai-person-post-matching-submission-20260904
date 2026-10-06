package com.example.matching.service.post.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.dto.post.*;
import com.example.matching.entity.post.PostDataCleaningRecord;
import com.example.matching.entity.post.PostPrototype;
import com.example.matching.mapper.post.PostDataCleaningRecordMapper;
import com.example.matching.mapper.post.PostPrototypeMapper;
import com.example.matching.service.post.PostDataCleaningService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 岗位数据清洗服务实现
 * <p>
 * 系统内部自动处理：清洗去噪、质量评估、去重检测。
 * 用户不需要手动触发清洗，但可以通过前端查看清洗记录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostDataCleaningServiceImpl implements PostDataCleaningService {

    private final PostDataCleaningRecordMapper cleaningRecordMapper;
    private final PostPrototypeMapper postPrototypeMapper;
    private final ObjectMapper objectMapper;
    private final PostCleaningRulesEngine rulesEngine;

    // ========== 阈值配置 ==========

    /** 强重复阻断阈值 */

    @Override
    public PostCleaningResult cleanAndDetect(PostRawInput input) {
        long startTime = System.currentTimeMillis();
        log.info("开始岗位数据清洗: postName={}, sourceType={}", input.getPostName(), input.getSourceType());

        PostCleaningResult result = new PostCleaningResult();

        // 1. 文本清洗去噪
        String cleanedText = rulesEngine.cleanText(input.getRawText());
        String removedNoise = rulesEngine.extractNoise(input.getRawText(), cleanedText);
        result.setCleanedText(cleanedText);
        result.setRemovedNoiseText(removedNoise);

        // 2. 岗位名称清洗
        String cleanedPostName = rulesEngine.cleanPostName(input.getPostName());
        result.setCleanedPostName(cleanedPostName);

        // 3. 结构化提取（职责、要求分离）
        List<String> responsibilities = rulesEngine.extractResponsibilities(cleanedText);
        List<String> requirements = rulesEngine.extractRequirements(cleanedText);
        result.setResponsibilities(responsibilities);
        result.setRequirements(requirements);

        // 4. 质量评分
        PostCleaningResult.QualityDetails qualityDetails = rulesEngine.calculateQualityScore(cleanedText, responsibilities, requirements);
        result.setQualityScore(qualityDetails.getStructureScore().multiply(new BigDecimal("0.3"))
                .add(qualityDetails.getLengthScore().multiply(new BigDecimal("0.3")))
                .add(qualityDetails.getKeywordScore().multiply(new BigDecimal("0.2")))
                .add(qualityDetails.getGenericRatioScore().multiply(new BigDecimal("0.2"))));
        result.setQualityDetails(qualityDetails);

        // 5. 去重检测
        rulesEngine.detectDuplicate(result, cleanedPostName, cleanedText);

        // 6. 阻断判定
        rulesEngine.determineBlock(result);

        // 7. 持久化清洗记录
        long durationMs = System.currentTimeMillis() - startTime;
        Long recordId = persistRecord(input, result, durationMs);
        result.setCleaningRecordId(recordId);

        log.info("岗位数据清洗完成: postName={}, qualityScore={}, duplicateStatus={}, blocked={}, durationMs={}",
                cleanedPostName, result.getQualityScore(), result.getDuplicateStatus(),
                result.isBlocked(), durationMs);

        return result;
    }

    @Override
    @Transactional
    public void markEnteredAgent(Long cleaningRecordId, String agentInputSnapshot) {
        PostDataCleaningRecord record = cleaningRecordMapper.selectById(cleaningRecordId);
        if (record != null) {
            record.setEnteredAgent(1);
            record.setAgentInputSnapshot(agentInputSnapshot);
            cleaningRecordMapper.updateById(record);
            log.info("已标记清洗记录进入Agent: cleaningRecordId={}", cleaningRecordId);
        }
    }

    @Override
    public Page<PostCleaningRecordVO> pageRecords(PostCleaningRecordPageQuery query) {
        Page<PostDataCleaningRecord> page = new Page<>(query.getPageNum(), query.getPageSize());

        LambdaQueryWrapper<PostDataCleaningRecord> wrapper = Wrappers.lambdaQuery();

        if (query.getSourceType() != null && !query.getSourceType().isEmpty()) {
            wrapper.eq(PostDataCleaningRecord::getSourceType, query.getSourceType());
        }
        if (query.getDuplicateStatus() != null && !query.getDuplicateStatus().isEmpty()) {
            wrapper.eq(PostDataCleaningRecord::getDuplicateStatus, query.getDuplicateStatus());
        }
        if (query.getBlocked() != null) {
            wrapper.eq(PostDataCleaningRecord::getBlocked, query.getBlocked() ? 1 : 0);
        }
        if (query.getEnteredAgent() != null) {
            wrapper.eq(PostDataCleaningRecord::getEnteredAgent, query.getEnteredAgent() ? 1 : 0);
        }
        if (query.getPostName() != null && !query.getPostName().isEmpty()) {
            wrapper.and(w -> w
                    .like(PostDataCleaningRecord::getRawPostName, query.getPostName())
                    .or()
                    .like(PostDataCleaningRecord::getCleanedPostName, query.getPostName()));
        }
        if (query.getQualityScoreMin() != null) {
            wrapper.ge(PostDataCleaningRecord::getQualityScore, query.getQualityScoreMin());
        }
        if (query.getQualityScoreMax() != null) {
            wrapper.le(PostDataCleaningRecord::getQualityScore, query.getQualityScoreMax());
        }

        wrapper.orderByDesc(PostDataCleaningRecord::getCreatedTime);

        Page<PostDataCleaningRecord> resultPage = cleaningRecordMapper.selectPage(page, wrapper);

        // 转换为VO
        Page<PostCleaningRecordVO> voPage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        voPage.setRecords(resultPage.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public PostCleaningRecordVO getRecordDetail(Long id) {
        PostDataCleaningRecord record = cleaningRecordMapper.selectById(id);
        if (record == null) {
            return null;
        }
        return convertToVO(record);
    }

    @Override
    @Transactional
    public PostCleaningResult reparse(Long id) {
        PostDataCleaningRecord record = cleaningRecordMapper.selectById(id);
        if (record == null) {
            throw new IllegalArgumentException("清洗记录不存在: id=" + id);
        }

        // 重新走清洗流程
        PostRawInput input = PostRawInput.builder()
                .postName(record.getRawPostName())
                .rawText(record.getRawText())
                .sourceType(record.getSourceType())
                .sourceRefId(record.getSourceRefId())
                .build();

        return cleanAndDetect(input);
    }

    // ========== 内部方法 ==========

    /**
     * 文本清洗去噪
     */

    /**
     * 持久化清洗记录
     */
    private Long persistRecord(PostRawInput input, PostCleaningResult result, long durationMs) {
        PostDataCleaningRecord record = new PostDataCleaningRecord();

        // 来源信息
        record.setSourceType(input.getSourceType());
        record.setSourceRefId(input.getSourceRefId());

        // 原始数据
        record.setRawPostName(input.getPostName());
        record.setRawText(input.getRawText());

        // 清洗后数据
        record.setCleanedPostName(result.getCleanedPostName());
        record.setCleanedText(result.getCleanedText());
        record.setRemovedNoiseText(result.getRemovedNoiseText());

        // 结构化数据（需要序列化为JSON）
        try {
            record.setResponsibilities(result.getResponsibilities());
            record.setRequirements(result.getRequirements());
        } catch (Exception e) {
            log.warn("序列化结构化数据失败: {}", e.getMessage());
        }

        // 质量评估
        record.setQualityScore(result.getQualityScore());
        try {
            record.setQualityDetails(objectMapper.writeValueAsString(result.getQualityDetails()));
        } catch (JsonProcessingException e) {
            log.warn("序列化质量评估详情失败: {}", e.getMessage());
        }

        // 去重检测
        record.setDuplicateStatus(result.getDuplicateStatus());
        record.setDuplicatePostId(result.getDuplicatePostId());
        record.setDuplicateScore(result.getDuplicateScore());
        record.setDuplicatePostName(result.getDuplicatePostName());

        // 阻断信息
        record.setBlocked(result.isBlocked() ? 1 : 0);
        record.setBlockReason(result.getBlockReason());

        // Agent信息（初始为未进入）
        record.setEnteredAgent(0);

        // 清洗耗时
        record.setCleaningDurationMs((int) durationMs);

        // 保存
        cleaningRecordMapper.insert(record);

        log.info("清洗记录已持久化: id={}, postName={}, blocked={}", record.getId(), record.getCleanedPostName(), record.getBlocked());
        return record.getId();
    }

    /**
     * 实体转VO
     */
    private PostCleaningRecordVO convertToVO(PostDataCleaningRecord record) {
        PostCleaningRecordVO vo = new PostCleaningRecordVO();
        vo.setId(record.getId());
        vo.setSourceType(record.getSourceType());
        vo.setSourceRefId(record.getSourceRefId());
        vo.setRawPostName(record.getRawPostName());
        vo.setRawText(record.getRawText());
        vo.setCleanedPostName(record.getCleanedPostName());
        vo.setCleanedText(record.getCleanedText());
        vo.setRemovedNoiseText(record.getRemovedNoiseText());
        vo.setResponsibilities(record.getResponsibilities());
        vo.setRequirements(record.getRequirements());
        vo.setQualityScore(record.getQualityScore());
        vo.setQualityDetails(record.getQualityDetails());
        vo.setDuplicateStatus(record.getDuplicateStatus());
        vo.setDuplicatePostId(record.getDuplicatePostId());
        vo.setDuplicateScore(record.getDuplicateScore());
        vo.setDuplicatePostName(record.getDuplicatePostName());
        vo.setBlocked(record.getBlocked() != null && record.getBlocked() == 1);
        vo.setBlockReason(record.getBlockReason());
        vo.setEnteredAgent(record.getEnteredAgent() != null && record.getEnteredAgent() == 1);
        vo.setAgentInputSnapshot(record.getAgentInputSnapshot());
        vo.setCleaningDurationMs(record.getCleaningDurationMs());
        vo.setCreatedTime(record.getCreatedTime());
        return vo;
    }
}
