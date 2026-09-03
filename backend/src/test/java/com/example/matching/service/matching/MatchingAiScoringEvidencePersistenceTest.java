package com.example.matching.service.matching;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.example.matching.common.constant.AiConstant;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.mapper.matching.MatchingRecordMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C-02 回归测试：AI 评分完成时必须把证据可信度分持久化到
 * evidence_credibility_score 列（而非 transient 的 evidenceScore 字段）。
 * <p>
 * 验证：写入后重新从数据库读取并断言字段值，证明 Mapper 映射和实际持久化成功。
 */
@SpringBootTest(classes = MatchingAiScoringEvidencePersistenceTest.TestConfig.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = Replace.NONE)
class MatchingAiScoringEvidencePersistenceTest {

    @Configuration
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            MybatisPlusAutoConfiguration.class
    })
    @MapperScan("com.example.matching.mapper.matching")
    static class TestConfig {
    }

    @Autowired
    private MatchingRecordMapper recordMapper;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        try (Statement stmt = dataSource.getConnection().createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS matching_record (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        batch_no VARCHAR(64),
                        emp_id BIGINT,
                        post_id BIGINT,
                        post_model_version VARCHAR(64),
                        ai_match_score DECIMAL(10,2),
                        vector_score DECIMAL(10,2),
                        l2_score DECIMAL(10,2),
                        ai_score DECIMAL(10,2),
                        post_model_score DECIMAL(10,2),
                        evidence_credibility_score DECIMAL(10,2),
                        evidence_coverage_score DECIMAL(10,2),
                        llm_score DECIMAL(10,2),
                        rag_score DECIMAL(10,2),
                        model_quality_coefficient DECIMAL(10,2),
                        feedback_calibration DECIMAL(10,2),
                        final_match_score DECIMAL(10,2),
                        match_status INT,
                        screening_level INT,
                        forced_by_list INT,
                        hard_condition_result CLOB,
                        quantitative_report CLOB,
                        ai_analysis_report CLOB,
                        manual_remark VARCHAR(512),
                        weight_profile_version VARCHAR(64),
                        weight_snapshot_json CLOB,
                        score_breakdown_json CLOB,
                        manual_breakdown_json CLOB,
                        semantic_missing INT,
                        approval_status INT,
                        is_locked INT,
                        locked_by BIGINT,
                        locked_time TIMESTAMP,
                        is_deleted INT DEFAULT 0,
                        created_by BIGINT,
                        created_time TIMESTAMP,
                        updated_by BIGINT,
                        updated_time TIMESTAMP,
                        version INT,
                        ai_scoring_status VARCHAR(32),
                        ai_scoring_fail_reason VARCHAR(512),
                        ai_scoring_attempt_count INT,
                        ai_scoring_last_attempt_at TIMESTAMP,
                        ai_scoring_next_retry_at TIMESTAMP,
                        used_provisional_abilities INT DEFAULT 0,
                        provisional_ability_count INT DEFAULT 0,
                        provisional_snapshot_json CLOB,
                        provisional_risk_flags_json VARCHAR(512)
                    )
                    """);
        }
    }

    @AfterEach
    void tearDown() {
        recordMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>());
    }

    @Test
    void completeIfProcessingPersistsEvidenceCredibilityScoreToDatabase() {
        MatchingRecord record = new MatchingRecord();
        record.setEmpId(1L);
        record.setPostId(2L);
        record.setAiScoringStatus(AiConstant.AI_SCORING_PROCESSING);
        record.setIsLocked(0);
        recordMapper.insert(record);

        MatchingAiScoringResult result = new MatchingAiScoringResult(
                new BigDecimal("75.00"), new BigDecimal("80.00"), new BigDecimal("62.50"),
                new BigDecimal("80.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "ai report", "quantitative report", 1);

        MatchingAiScoringStateMachine stateMachine = new MatchingAiScoringStateMachine(recordMapper);
        boolean completed = stateMachine.completeIfProcessing(record.getId(), result);
        assertThat(completed).isTrue();

        MatchingRecord reloaded = recordMapper.selectById(record.getId());
        assertThat(reloaded.getAiScoringStatus()).isEqualTo(AiConstant.AI_SCORING_COMPLETED);
        assertThat(reloaded.getScreeningLevel()).isEqualTo(3);
        assertThat(reloaded.getEvidenceCredibilityScore()).isEqualByComparingTo("62.50");
        assertThat(reloaded.getAiMatchScore()).isEqualByComparingTo("80.00");
    }
}
