package com.example.matching.service.evolution;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.entity.evolution.PostEvolutionTask;
import com.example.matching.mapper.evolution.PostEvolutionTaskMapper;
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
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = PostEvolutionTaskClaimTest.TestConfig.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = Replace.NONE)
class PostEvolutionTaskClaimTest {

    @Configuration
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            MybatisPlusAutoConfiguration.class
    })
    @MapperScan("com.example.matching.mapper.evolution")
    static class TestConfig {
    }

    @Autowired
    private PostEvolutionTaskMapper taskMapper;

    @Autowired
    private DataSource dataSource;

    private static final String PENDING = "PENDING";
    private static final String RUNNING = "RUNNING";

    @BeforeEach
    void setUp() throws Exception {
        try (Statement stmt = dataSource.getConnection().createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS post_evolution_task (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        task_code VARCHAR(64),
                        post_id BIGINT,
                        task_name VARCHAR(256),
                        baseline_version VARCHAR(64),
                        new_jd_text CLOB,
                        rag_query_log_id BIGINT,
                        task_status VARCHAR(32),
                        summary_json CLOB,
                        error_message VARCHAR(1024),
                        source_type VARCHAR(64),
                        source_document_id BIGINT,
                        business_domain VARCHAR(128),
                        industry VARCHAR(128),
                        trigger_type VARCHAR(64),
                        context_hash VARCHAR(64),
                        context_snapshot_id BIGINT,
                        evidence_summary CLOB,
                        agent_trace CLOB,
                        harness_summary CLOB,
                        progress_status VARCHAR(64),
                        progress_percent INT,
                        source_document_ids CLOB,
                        created_by BIGINT,
                        created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
        }
    }

    @AfterEach
    void tearDown() {
        taskMapper.delete(new LambdaQueryWrapper<>());
    }

    @Test
    void claimPendingTaskSucceedsForFirstCaller() {
        PostEvolutionTask task = new PostEvolutionTask();
        task.setTaskStatus(PENDING);
        task.setPostId(999L);
        task.setCreatedBy(1L);
        taskMapper.insert(task);

        int claimed = taskMapper.claimPendingTask(task.getId(), PENDING, RUNNING);
        assertThat(claimed).isEqualTo(1);
    }

    @Test
    void claimPendingTaskFailsForSecondCaller() {
        PostEvolutionTask task = new PostEvolutionTask();
        task.setTaskStatus(PENDING);
        task.setPostId(999L);
        task.setCreatedBy(1L);
        taskMapper.insert(task);

        int first = taskMapper.claimPendingTask(task.getId(), PENDING, RUNNING);
        int second = taskMapper.claimPendingTask(task.getId(), PENDING, RUNNING);
        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(0);
    }

    @Test
    void claimPendingTaskFailsForAlreadyRunning() {
        PostEvolutionTask task = new PostEvolutionTask();
        task.setTaskStatus(RUNNING);
        task.setPostId(999L);
        task.setCreatedBy(1L);
        taskMapper.insert(task);

        int claimed = taskMapper.claimPendingTask(task.getId(), PENDING, RUNNING);
        assertThat(claimed).isEqualTo(0);
    }
}
