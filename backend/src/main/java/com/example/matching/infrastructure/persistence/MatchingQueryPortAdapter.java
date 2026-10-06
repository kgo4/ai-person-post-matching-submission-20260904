package com.example.matching.infrastructure.persistence;

import com.example.matching.entity.matching.MatchingFeedbackDataset;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.mapper.matching.MatchingFeedbackDatasetMapper;
import com.example.matching.mapper.matching.MatchingRecordMapper;
import com.example.matching.port.matching.MatchingQueryPort;
import com.example.matching.port.matching.MatchingQueryPort.MatchingFeedbackDTO;
import com.example.matching.port.matching.MatchingQueryPort.MatchingRecordDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MatchingQueryPortAdapter implements MatchingQueryPort {

    private final MatchingRecordMapper matchingRecordMapper;
    private final MatchingFeedbackDatasetMapper matchingFeedbackDatasetMapper;

    @Override
    public MatchingRecordDTO getById(Long recordId) {
        MatchingRecord r = matchingRecordMapper.selectById(recordId);
        return r != null ? MatchingRecordDTO.from(r) : null;
    }

    @Override
    public List<MatchingRecordDTO> listByEmpId(Long empId) {
        if (empId == null) return List.of();
        return matchingRecordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MatchingRecord>()
                        .eq(MatchingRecord::getEmpId, empId)
        ).stream().map(MatchingRecordDTO::from).collect(Collectors.toList());
    }

    @Override
    public List<MatchingRecordDTO> listByPostId(Long postId) {
        if (postId == null) return List.of();
        return matchingRecordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MatchingRecord>()
                        .eq(MatchingRecord::getPostId, postId)
        ).stream().map(MatchingRecordDTO::from).collect(Collectors.toList());
    }

    @Override
    public List<MatchingRecordDTO> listByEmpIdAndPostId(Long empId, Long postId) {
        if (empId == null || postId == null) return List.of();
        return matchingRecordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MatchingRecord>()
                        .eq(MatchingRecord::getEmpId, empId)
                        .eq(MatchingRecord::getPostId, postId)
        ).stream().map(MatchingRecordDTO::from).collect(Collectors.toList());
    }

    @Override
    public List<MatchingRecordDTO> listRecordsPaginated(int page, int size) {
        var p = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<MatchingRecord>(page, size);
        return matchingRecordMapper.selectPage(p,
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MatchingRecord>()
                        .orderByAsc(MatchingRecord::getId)
        ).getRecords().stream().map(MatchingRecordDTO::from).collect(Collectors.toList());
    }

    @Override
    public List<MatchingFeedbackDTO> listRecentFeedback(int limit) {
        var w = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MatchingFeedbackDataset>()
                .orderByDesc(MatchingFeedbackDataset::getFeedbackTime);
        if (limit > 0) w.last("LIMIT " + limit);
        return matchingFeedbackDatasetMapper.selectList(w).stream()
                .map(MatchingFeedbackDTO::from).collect(Collectors.toList());
    }

    @Override
    public long countAllRecordsWithAiScore() {
        Long count = matchingRecordMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MatchingRecord>()
                        .isNotNull(MatchingRecord::getAiMatchScore));
        return count == null ? 0L : count;
    }

    @Override
    public List<MatchingRecordDTO> listAllRecordsWithAiScore() {
        return matchingRecordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MatchingRecord>()
                        .isNotNull(MatchingRecord::getAiMatchScore)
        ).stream().map(MatchingRecordDTO::from).collect(Collectors.toList());
    }

    @Override
    public List<MatchingRecordDTO> listRecentRecordsByEmpAndPosts(Long empId, List<Long> postIds, LocalDateTime since) {
        if (empId == null || postIds == null || postIds.isEmpty() || since == null) return List.of();
        return matchingRecordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MatchingRecord>()
                        .eq(MatchingRecord::getEmpId, empId)
                        .in(MatchingRecord::getPostId, postIds)
                        .ge(MatchingRecord::getCreatedTime, since)
                        .eq(MatchingRecord::getIsDeleted, 0)
        ).stream().map(MatchingRecordDTO::from).collect(Collectors.toList());
    }
}
