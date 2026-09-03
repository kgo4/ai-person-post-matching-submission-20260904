package com.example.matching.mapper.employee;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.matching.entity.employee.EmpAiTest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI测试记录Mapper
 * <p>
 * 任务状态机：PENDING -> PROCESSING -> SUCCEEDED | FAILED
 * 所有抢占/状态迁移均为条件更新，更新条数不是 1 时消费者直接返回，
 * 保证同一消息并发投递只执行一次。
 */
@Mapper
public interface EmpAiTestMapper extends BaseMapper<EmpAiTest> {

    // ==================== 题目生成 ====================

    @Update("""
            UPDATE emp_ai_test
            SET generation_state = 'PROCESSING', processing_started_at = NOW(), last_error_type = NULL, last_error_message = NULL
            WHERE id = #{id} AND generation_state = 'PENDING'
            """)
    int claimGeneration(@Param("id") Long id);

    @Update("""
            UPDATE emp_ai_test
            SET generation_state = 'SUCCEEDED', retry_count = 0
            WHERE id = #{id} AND generation_state = 'PROCESSING'
            """)
    int markGenerationSucceeded(@Param("id") Long id);

    @Update("""
            UPDATE emp_ai_test
            SET generation_state = 'PENDING', retry_count = retry_count + 1,
                last_error_type = #{errorType}, last_error_message = #{errorMessage}
            WHERE id = #{id} AND generation_state = 'PROCESSING' AND retry_count < 3
            """)
    int retryGeneration(@Param("id") Long id, @Param("errorType") String errorType,
                        @Param("errorMessage") String errorMessage);

    @Update("""
            UPDATE emp_ai_test
            SET generation_state = 'FAILED',
                last_error_type = #{errorType}, last_error_message = #{errorMessage}
            WHERE id = #{id} AND generation_state = 'PROCESSING'
            """)
    int failGeneration(@Param("id") Long id, @Param("errorType") String errorType,
                       @Param("errorMessage") String errorMessage);

    @Update("""
            UPDATE emp_ai_test
            SET generation_state = 'PENDING', retry_count = retry_count + 1,
                last_error_type = #{errorType}, last_error_message = #{errorMessage}
            WHERE id = #{id} AND generation_state = 'PROCESSING' AND retry_count < 3
            """)
    int recoverGeneration(@Param("id") Long id, @Param("errorType") String errorType,
                          @Param("errorMessage") String errorMessage);

    @Select("""
            SELECT * FROM emp_ai_test
            WHERE generation_state = 'PROCESSING' AND processing_started_at < #{before}
            """)
    List<EmpAiTest> selectZombieGeneration(@Param("before") LocalDateTime before);

    // ==================== 评分 ====================

    @Update("""
            UPDATE emp_ai_test
            SET evaluation_state = 'PROCESSING', processing_started_at = NOW(), last_error_type = NULL, last_error_message = NULL
            WHERE id = #{id} AND evaluation_state = 'PENDING'
            """)
    int claimEvaluation(@Param("id") Long id);

    @Update("""
            UPDATE emp_ai_test
            SET evaluation_state = 'SUCCEEDED', retry_count = 0
            WHERE id = #{id} AND evaluation_state = 'PROCESSING'
            """)
    int markEvaluationSucceeded(@Param("id") Long id);

    @Update("""
            UPDATE emp_ai_test
            SET evaluation_state = 'PENDING', retry_count = retry_count + 1,
                last_error_type = #{errorType}, last_error_message = #{errorMessage}
            WHERE id = #{id} AND evaluation_state = 'PROCESSING' AND retry_count < 3
            """)
    int retryEvaluation(@Param("id") Long id, @Param("errorType") String errorType,
                        @Param("errorMessage") String errorMessage);

    @Update("""
            UPDATE emp_ai_test
            SET evaluation_state = 'FAILED',
                last_error_type = #{errorType}, last_error_message = #{errorMessage}
            WHERE id = #{id} AND evaluation_state = 'PROCESSING'
            """)
    int failEvaluation(@Param("id") Long id, @Param("errorType") String errorType,
                       @Param("errorMessage") String errorMessage);

    @Update("""
            UPDATE emp_ai_test
            SET evaluation_state = 'PENDING', retry_count = retry_count + 1,
                last_error_type = #{errorType}, last_error_message = #{errorMessage}
            WHERE id = #{id} AND evaluation_state = 'PROCESSING' AND retry_count < 3
            """)
    int recoverEvaluation(@Param("id") Long id, @Param("errorType") String errorType,
                          @Param("errorMessage") String errorMessage);

    @Select("""
            SELECT * FROM emp_ai_test
            WHERE evaluation_state = 'PROCESSING' AND processing_started_at < #{before}
            """)
    List<EmpAiTest> selectZombieEvaluation(@Param("before") LocalDateTime before);

    // ==================== 人工重放：仅允许 FAILED -> PENDING ====================

    @Update("""
            UPDATE emp_ai_test
            SET generation_state = 'PENDING', retry_count = 0, last_error_type = NULL, last_error_message = NULL
            WHERE id = #{id} AND generation_state = 'FAILED'
            """)
    int resetGenerationToPending(@Param("id") Long id);

    @Update("""
            UPDATE emp_ai_test
            SET evaluation_state = 'PENDING', retry_count = 0, last_error_type = NULL, last_error_message = NULL
            WHERE id = #{id} AND evaluation_state = 'FAILED'
            """)
    int resetEvaluationToPending(@Param("id") Long id);
}
