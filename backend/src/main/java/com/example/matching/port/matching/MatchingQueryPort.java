package com.example.matching.port.matching;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 匹配域查询端口 — 公开只读接口。
 * <p>
 * 其他域只能通过此接口查询匹配记录，禁止直接注入 matching 包的 Mapper。
 */
public interface MatchingQueryPort {

    /** 匹配记录只读 DTO */
    record MatchingRecordDTO(
            Long id,
            Long empId,
            Long postId,
            String postName,
            BigDecimal finalMatchScore,
            BigDecimal aiMatchScore,
            BigDecimal vectorScore,
            BigDecimal l2Score,
            BigDecimal ragScore,
            BigDecimal llmScore,
            String weightProfileVersion,
            Integer matchStatus,
            Integer screeningLevel,
            String quantitativeReport,
            LocalDateTime createdTime
    ) {
        public static MatchingRecordDTO from(com.example.matching.entity.matching.MatchingRecord r) {
            return new MatchingRecordDTO(r.getId(), r.getEmpId(), r.getPostId(), r.getPostName(),
                    r.getFinalMatchScore(), r.getAiMatchScore(), r.getVectorScore(),
                    r.getL2Score(), r.getRagScore(), r.getLlmScore(),
                    r.getWeightProfileVersion(), r.getMatchStatus(), r.getScreeningLevel(),
                    r.getQuantitativeReport(), r.getCreatedTime());
        }
    }

    MatchingRecordDTO getById(Long recordId);

    List<MatchingRecordDTO> listByEmpId(Long empId);

    List<MatchingRecordDTO> listByPostId(Long postId);

    List<MatchingRecordDTO> listByEmpIdAndPostId(Long empId, Long postId);

    /** 分页列出所有匹配记录 */
    List<MatchingRecordDTO> listRecordsPaginated(int page, int size);

    /** 匹配反馈数据集 DTO */
    record MatchingFeedbackDTO(
            Long id,
            Long matchingRecordId,
            String feedbackComment
    ) {
        public static MatchingFeedbackDTO from(com.example.matching.entity.matching.MatchingFeedbackDataset f) {
            return new MatchingFeedbackDTO(f.getId(), f.getMatchingRecordId(), f.getFeedbackComment());
        }
    }

    /** 分页列出最近的匹配反馈，用于证据回填 */
    List<MatchingFeedbackDTO> listRecentFeedback(int limit);

    /** 带 AI 评分的匹配记录数量（报表统计用） */
    long countAllRecordsWithAiScore();

    /** 带 AI 评分的全量匹配记录（报表统计用） */
    List<MatchingRecordDTO> listAllRecordsWithAiScore();

    /** 员工在指定岗位集合、指定时间之后的未删除匹配记录 */
    List<MatchingRecordDTO> listRecentRecordsByEmpAndPosts(Long empId, List<Long> postIds, LocalDateTime since);
}
