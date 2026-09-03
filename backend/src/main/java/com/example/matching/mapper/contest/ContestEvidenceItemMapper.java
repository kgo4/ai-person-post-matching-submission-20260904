package com.example.matching.mapper.contest;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.matching.entity.contest.ContestEvidenceItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 竞赛证据项 Mapper
 */
@Mapper
public interface ContestEvidenceItemMapper extends BaseMapper<ContestEvidenceItem> {

    /** 按来源类型统计证据数（用于统计摘要，避免全表加载到内存） */
    @Select("SELECT source_type AS sourceType, COUNT(*) AS cnt FROM contest_evidence_item WHERE is_deleted = 0 GROUP BY source_type")
    List<Map<String, Object>> countGroupBySourceType();

    /** 按审核状态统计证据数 */
    @Select("SELECT evidence_status AS status, COUNT(*) AS cnt FROM contest_evidence_item WHERE is_deleted = 0 GROUP BY evidence_status")
    List<Map<String, Object>> countGroupByStatus();

    /** 全部未删除证据数 */
    @Select("SELECT COUNT(*) AS total FROM contest_evidence_item WHERE is_deleted = 0")
    Long countAllActive();

    /** 平均可信度（仅统计有可信度分数的证据） */
    @Select("SELECT COALESCE(AVG(credibility_score), 0) FROM contest_evidence_item WHERE is_deleted = 0 AND credibility_score IS NOT NULL")
    BigDecimal averageCredibility();
}
