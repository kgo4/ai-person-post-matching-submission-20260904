package com.example.matching.service.closure.impl;

import com.example.matching.dto.closure.ComprehensiveDiagnosisFactDTO;
import com.example.matching.dto.closure.ComprehensiveDiagnosisResultDTO;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.mapper.matching.MatchingRecordMapper;
import com.example.matching.service.closure.ComprehensiveDiagnosisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 综合诊断服务：编排事实包构建与 AI 分析。
 * <p>
 * 从 700+ 行精简为聚合入口，事实构建与 AI 分析已拆分为
 * {@link ComprehensiveDiagnosisFactBuilder} 与 {@link DiagnosisAiAnalyzer}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComprehensiveDiagnosisServiceImpl implements ComprehensiveDiagnosisService {

    private final MatchingRecordMapper matchingRecordMapper;
    private final ComprehensiveDiagnosisFactBuilder factBuilder;
    private final DiagnosisAiAnalyzer aiAnalyzer;

    @Override
    public ComprehensiveDiagnosisResultDTO diagnose(Long matchingRecordId) {
        ComprehensiveDiagnosisResultDTO result = new ComprehensiveDiagnosisResultDTO();
        result.setMatchingRecordId(matchingRecordId);

        // 1. 加载匹配记录
        MatchingRecord record = matchingRecordMapper.selectById(matchingRecordId);
        if (record == null) {
            result.setFactPackage(factBuilder.buildEmptyFactPackage(matchingRecordId));
            return result;
        }

        result.setEmpId(record.getEmpId());
        result.setPostId(record.getPostId());

        // 2. 构建事实诊断包
        ComprehensiveDiagnosisFactDTO factPackage = factBuilder.buildFactPackage(record);
        result.setFactPackage(factPackage);

        // 3. AI 综合分析（失败时优雅降级，不影响事实包返回）
        try {
            result.setAiAnalysis(aiAnalyzer.buildAiAnalysis(factPackage));
        } catch (Exception e) {
            log.warn("AI综合分析失败，降级为仅返回事实包。matchingRecordId={}, error={}",
                    matchingRecordId, e.getMessage());
        }

        return result;
    }
}
