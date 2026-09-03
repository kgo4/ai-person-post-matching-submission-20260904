package com.example.matching.mapper.employee;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.matching.entity.employee.EmpVideoInterviewSession;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Mapper;

/**
 * 视频面试会话 Mapper
 */
@Mapper
public interface EmpVideoInterviewSessionMapper extends BaseMapper<EmpVideoInterviewSession> {

    @Update("""
            UPDATE emp_video_interview_session
            SET conversation_state = #{targetState}, session_version = session_version + 1
            WHERE id = #{sessionId}
              AND conversation_state = #{expectedState}
              AND session_version = #{expectedVersion}
            """)
    int compareAndSetConversationState(@Param("sessionId") Long sessionId,
                                       @Param("expectedState") String expectedState,
                                       @Param("expectedVersion") Long expectedVersion,
                                       @Param("targetState") String targetState);

    /**
     * 会话状态 CAS 迁移：仅当当前状态匹配时才切换（面试后分析状态机用）。
     *
     * @return 1=迁移成功；0=状态不匹配
     */
    @Update("""
            UPDATE emp_video_interview_session
            SET status = #{toStatus}
            WHERE id = #{sessionId} AND status = #{fromStatus}
            """)
    int transitionStatus(@Param("sessionId") Long sessionId,
                         @Param("fromStatus") int fromStatus,
                         @Param("toStatus") int toStatus);

    /**
     * 分析完成回写：ANALYZING(4) -> COMPLETED(5) + 报告字段，CAS 防并发覆盖。
     */
    @Update("""
            UPDATE emp_video_interview_session
            SET status = 5, overall_score = #{overallScore}, summary_report = #{summaryReport},
                analysis_retry_count = 0, analysis_failed_reason = NULL
            WHERE id = #{sessionId} AND status = 4
            """)
    int completeAnalysis(@Param("sessionId") Long sessionId,
                         @Param("overallScore") java.math.BigDecimal overallScore,
                         @Param("summaryReport") String summaryReport);

    /**
     * 记录分析失败原因（仅当会话仍处于待重试/分析中状态时写入）。
     */
    @Update("""
            UPDATE emp_video_interview_session
            SET analysis_failed_reason = #{reason}
            WHERE id = #{sessionId} AND status IN (3, 4)
            """)
    int markAnalysisFailure(@Param("sessionId") Long sessionId, @Param("reason") String reason);

    /**
     * Allow an explicit user retry after post-interview analysis failed. The
     * conversation-evaluation failure path (status=2) is intentionally excluded.
     */
    @Update("""
            UPDATE emp_video_interview_session
            SET status = 3, analysis_retry_count = 0,
                error_message = NULL, analysis_failed_reason = NULL
            WHERE id = #{sessionId}
              AND status = 7
              AND (analysis_failed_reason IS NOT NULL OR error_message LIKE '%AI%')
            """)
    int resetFailedAnalysisToFinished(@Param("sessionId") Long sessionId);
}
