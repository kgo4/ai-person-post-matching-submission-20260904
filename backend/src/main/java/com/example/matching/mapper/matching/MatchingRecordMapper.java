package com.example.matching.mapper.matching;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.matching.entity.matching.MatchingRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 人岗匹配记录 Mapper
 */
@Mapper
public interface MatchingRecordMapper extends BaseMapper<MatchingRecord> {

    @Select("""
            SELECT
                COUNT(*) AS totalCount,
                COUNT(CASE WHEN ai_match_score >= 90 THEN 1 END) AS score90,
                COUNT(CASE WHEN ai_match_score >= 75 AND ai_match_score < 90 THEN 1 END) AS score75,
                COUNT(CASE WHEN ai_match_score >= 60 AND ai_match_score < 75 THEN 1 END) AS score60,
                COUNT(CASE WHEN ai_match_score < 60 THEN 1 END) AS scoreBelow60,
                COUNT(CASE WHEN match_status = 0 THEN 1 END) AS status0,
                COUNT(CASE WHEN match_status = 1 THEN 1 END) AS status1,
                COUNT(CASE WHEN match_status = 2 THEN 1 END) AS status2,
                COUNT(CASE WHEN match_status = 3 THEN 1 END) AS status3,
                COUNT(CASE WHEN match_status = 4 THEN 1 END) AS status4
            FROM matching_record
            WHERE is_deleted = 0
            """)
    Map<String, Long> selectDashboardSummary();

    @Select("SELECT * FROM matching_record WHERE is_deleted = 0 ORDER BY created_time DESC LIMIT 10")
    List<MatchingRecord> selectRecentTen();
}
