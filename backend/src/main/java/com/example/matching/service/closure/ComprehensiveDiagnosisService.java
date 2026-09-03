package com.example.matching.service.closure;

import com.example.matching.dto.closure.ComprehensiveDiagnosisResultDTO;

/**
 * 综合差距诊断服务接口
 * <p>
 * 核心原则：
 * - 事实诊断包由系统构造，包含所有可量化的事实数据
 * - AI 只负责综合解释、归纳优先级和生成改进建议
 * - AI 生成的是"诊断解释"，不是"诊断事实"
 * <p>
 * 第一期：返回事实诊断包（多维度结构化数据）
 * 第二期：接入 RAG + AI 综合分析
 * 第三期：接入幻觉审计
 *
 * @author system
 */
public interface ComprehensiveDiagnosisService {

    /**
     * 执行综合差距诊断
     *
     * @param matchingRecordId 匹配记录ID
     * @return 综合诊断结果（含事实诊断包 + AI 分析）
     */
    ComprehensiveDiagnosisResultDTO diagnose(Long matchingRecordId);
}
