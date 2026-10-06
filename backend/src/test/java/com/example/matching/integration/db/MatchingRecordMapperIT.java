package com.example.matching.integration.db;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.infra.AbstractIntegrationTest;
import com.example.matching.mapper.matching.MatchingRecordMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MyBatis-Plus mapper integration tests for the {@code matching_record} table.
 * <p>
 * Runs on a real MySQL 8 instance via Testcontainers with all Flyway migrations
 * applied.  Each test is wrapped in a transaction that is rolled back afterwards
 * so data does not leak between tests.
 */
@Transactional
@Rollback
class MatchingRecordMapperIT extends AbstractIntegrationTest {

    @Autowired
    private MatchingRecordMapper mapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ------------------------------------------------------------------ //
    //  Soft delete (@TableLogic on isDeleted)                              //
    // ------------------------------------------------------------------ //

    @Test
    void softDelete_excludesRecordFromSelectById() {
        MatchingRecord record = sampleRecord(1L, 10L);
        mapper.insert(record);
        Long id = record.getId();
        assertThat(id).isNotNull();

        // Before delete: visible
        assertThat(mapper.selectById(id)).isNotNull();

        // Soft delete
        mapper.deleteById(id);

        // After delete: invisible through mapper
        assertThat(mapper.selectById(id)).isNull();

        // Row still present with is_deleted = 1
        Integer isDeleted = jdbcTemplate.queryForObject(
                "SELECT is_deleted FROM matching_record WHERE id = ?", Integer.class, id);
        assertThat(isDeleted).isEqualTo(1);
    }

    @Test
    void softDelete_excludesRecordsFromSelectList() {
        MatchingRecord r1 = sampleRecord(2L, 10L);
        MatchingRecord r2 = sampleRecord(2L, 11L);
        mapper.insert(r1);
        mapper.insert(r2);
        mapper.deleteById(r1.getId());

        LambdaQueryWrapper<MatchingRecord> wrapper = new LambdaQueryWrapper<MatchingRecord>()
                .eq(MatchingRecord::getEmpId, 2L);
        List<MatchingRecord> result = mapper.selectList(wrapper);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPostId()).isEqualTo(11L);
    }

    // ------------------------------------------------------------------ //
    //  Optimistic lock (@Version)                                          //
    // ------------------------------------------------------------------ //

    @Test
    void optimisticLock_rejectsStaleUpdate() {
        MatchingRecord record = sampleRecord(10L, 100L);
        mapper.insert(record);
        Long id = record.getId();

        // Two references to the same row (both version = 1)
        MatchingRecord ref1 = mapper.selectById(id);
        MatchingRecord ref2 = mapper.selectById(id);
        assertThat(ref1.getVersion()).isEqualTo(1);
        assertThat(ref2.getVersion()).isEqualTo(1);

        // First update succeeds → version becomes 2
        ref1.setManualRemark("first-update");
        int rows1 = mapper.updateById(ref1);
        assertThat(rows1).isEqualTo(1);

        // Second update with stale version → 0 rows affected
        ref2.setManualRemark("second-update");
        int rows2 = mapper.updateById(ref2);
        assertThat(rows2).isEqualTo(0);

        // Verify the first update persisted
        MatchingRecord reloaded = mapper.selectById(id);
        assertThat(reloaded.getManualRemark()).isEqualTo("first-update");
        assertThat(reloaded.getVersion()).isEqualTo(2);
    }

    @Test
    void optimisticLock_versionIncrementsOnEachUpdate() {
        MatchingRecord record = sampleRecord(11L, 110L);
        mapper.insert(record);
        Long id = record.getId();

        MatchingRecord ref = mapper.selectById(id);
        assertThat(ref.getVersion()).isEqualTo(1);

        ref.setManualRemark("v2");
        mapper.updateById(ref);
        ref = mapper.selectById(id);
        assertThat(ref.getVersion()).isEqualTo(2);

        ref.setManualRemark("v3");
        mapper.updateById(ref);
        ref = mapper.selectById(id);
        assertThat(ref.getVersion()).isEqualTo(3);
    }

    // ------------------------------------------------------------------ //
    //  Index usage (EXPLAIN)                                               //
    // ------------------------------------------------------------------ //

    @Test
    void indexUsedForEmpIdLookup() {
        List<Map<String, Object>> plan = jdbcTemplate.queryForList(
                "EXPLAIN SELECT * FROM matching_record WHERE emp_id = 1 AND is_deleted = 0");
        String key = (String) plan.get(0).get("key");
        assertThat(key)
                .as("query on emp_id should use idx_emp_id index")
                .isEqualTo("idx_emp_id");
    }

    @Test
    void indexUsedForPostIdLookup() {
        List<Map<String, Object>> plan = jdbcTemplate.queryForList(
                "EXPLAIN SELECT * FROM matching_record WHERE post_id = 10 AND is_deleted = 0");
        String key = (String) plan.get(0).get("key");
        assertThat(key)
                .as("query on post_id should use idx_post_id index")
                .isEqualTo("idx_post_id");
    }

    @Test
    void compoundIndexUsedForEmpPostLookup() {
        List<Map<String, Object>> plan = jdbcTemplate.queryForList(
                "EXPLAIN SELECT * FROM matching_record "
                        + "WHERE emp_id = 1 AND post_id = 10 AND is_deleted = 0");
        String key = (String) plan.get(0).get("key");
        assertThat(key)
                .as("compound (emp_id, post_id) query should use idx_matching_record_emp_post index")
                .isEqualTo("idx_matching_record_emp_post");
    }

    @Test
    void indexUsedForBatchNoLookup() {
        List<Map<String, Object>> plan = jdbcTemplate.queryForList(
                "EXPLAIN SELECT * FROM matching_record WHERE batch_no = 'BATCH001' AND is_deleted = 0");
        String key = (String) plan.get(0).get("key");
        assertThat(key)
                .as("query on batch_no should use idx_batch_no index")
                .isEqualTo("idx_batch_no");
    }

    // ------------------------------------------------------------------ //
    //  Factory helper                                                     //
    // ------------------------------------------------------------------ //

    private static MatchingRecord sampleRecord(Long empId, Long postId) {
        MatchingRecord r = new MatchingRecord();
        r.setEmpId(empId);
        r.setPostId(postId);
        r.setBatchNo("BATCH001");
        r.setAiMatchScore(new BigDecimal("85.50"));
        r.setMatchStatus(0);
        r.setApprovalStatus(0);
        r.setIsLocked(0);
        r.setIsDeleted(0);
        r.setVersion(1);
        return r;
    }
}
