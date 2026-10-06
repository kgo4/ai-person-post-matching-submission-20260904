package com.example.matching.integration.db;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.infra.AbstractIntegrationTest;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MyBatis-Plus mapper integration tests for the {@code emp_ability} table.
 * <p>
 * Runs on a real MySQL 8 instance via Testcontainers with all Flyway migrations
 * applied.  Each test is wrapped in a transaction that is rolled back afterwards
 * so data does not leak between tests.
 */
@Transactional
@Rollback
class EmpAbilityMapperIT extends AbstractIntegrationTest {

    @Autowired
    private EmpAbilityMapper mapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ------------------------------------------------------------------ //
    //  Soft delete (@TableLogic on isDeleted)                              //
    // ------------------------------------------------------------------ //

    @Test
    void softDelete_excludesRecordFromSelectById() {
        EmpAbility ability = sampleAbility(1L, 100L, "MANUAL");
        mapper.insert(ability);
        Long id = ability.getId();
        assertThat(id).isNotNull();

        // Before delete: visible
        assertThat(mapper.selectById(id)).isNotNull();

        // Soft delete via MyBatis-Plus (generates UPDATE is_deleted = 1)
        mapper.deleteById(id);

        // After delete: invisible through mapper
        assertThat(mapper.selectById(id)).isNull();

        // But the row still exists in the database with is_deleted = 1
        Integer isDeleted = jdbcTemplate.queryForObject(
                "SELECT is_deleted FROM emp_ability WHERE id = ?", Integer.class, id);
        assertThat(isDeleted).isEqualTo(1);
    }

    @Test
    void softDelete_excludesRecordsFromSelectList() {
        EmpAbility a1 = sampleAbility(10L, 100L, "MANUAL");
        EmpAbility a2 = sampleAbility(10L, 101L, "AI_ASSESSMENT");
        mapper.insert(a1);
        mapper.insert(a2);
        mapper.deleteById(a1.getId());

        LambdaQueryWrapper<EmpAbility> wrapper = new LambdaQueryWrapper<EmpAbility>()
                .eq(EmpAbility::getEmpId, 10L);
        List<EmpAbility> result = mapper.selectList(wrapper);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTagId()).isEqualTo(101L);
    }

    // ------------------------------------------------------------------ //
    //  Unique constraint (emp_id, tag_id, evaluation_source)               //
    // ------------------------------------------------------------------ //

    @Test
    void uniqueConstraint_preventsDuplicateTriple() {
        EmpAbility first = sampleAbility(20L, 200L, "MANUAL");
        mapper.insert(first);

        EmpAbility duplicate = sampleAbility(20L, 200L, "MANUAL");
        assertThatThrownBy(() -> mapper.insert(duplicate))
                .hasRootCauseInstanceOf(java.sql.SQLIntegrityConstraintViolationException.class);
    }

    @Test
    void uniqueConstraint_allowsSameEmpTagWithDifferentSource() {
        EmpAbility first = sampleAbility(21L, 210L, "MANUAL");
        mapper.insert(first);

        EmpAbility second = sampleAbility(21L, 210L, "AI_ASSESSMENT");
        // Different evaluation_source — should succeed
        mapper.insert(second);
        assertThat(second.getId()).isNotNull();
    }

    @Test
    void uniqueConstraint_allowsSameEmpTagWithDefaultUnknown() {
        // After V69, omitting evaluation_source gives DEFAULT 'UNKNOWN'.
        // Two such inserts for the same (emp_id, tag_id) must violate the
        // unique constraint because both get 'UNKNOWN'.
        jdbcTemplate.update(
                "INSERT INTO emp_ability (emp_id, tag_id, is_deleted, version) VALUES (22, 220, 0, 1)");
        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "INSERT INTO emp_ability (emp_id, tag_id, is_deleted, version) VALUES (22, 220, 0, 1)"))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    // ------------------------------------------------------------------ //
    //  Optimistic lock (@Version)                                          //
    // ------------------------------------------------------------------ //

    @Test
    void optimisticLock_rejectsStaleUpdate() {
        EmpAbility entity = sampleAbility(30L, 300L, "MANUAL");
        mapper.insert(entity);
        Long id = entity.getId();

        // Load two references to the same row (both version = 1)
        EmpAbility ref1 = mapper.selectById(id);
        EmpAbility ref2 = mapper.selectById(id);
        assertThat(ref1.getVersion()).isEqualTo(1);
        assertThat(ref2.getVersion()).isEqualTo(1);

        // First update succeeds → version becomes 2
        ref1.setRemark("update-1");
        int rows1 = mapper.updateById(ref1);
        assertThat(rows1).isEqualTo(1);

        // Second update with stale version → 0 rows affected
        ref2.setRemark("update-2");
        int rows2 = mapper.updateById(ref2);
        assertThat(rows2).isEqualTo(0);

        // Verify the first update stuck
        EmpAbility reloaded = mapper.selectById(id);
        assertThat(reloaded.getRemark()).isEqualTo("update-1");
        assertThat(reloaded.getVersion()).isEqualTo(2);
    }

    @Test
    void optimisticLock_versionIncrementsOnEachUpdate() {
        EmpAbility entity = sampleAbility(31L, 310L, "MANUAL");
        mapper.insert(entity);
        Long id = entity.getId();

        EmpAbility ref = mapper.selectById(id);
        assertThat(ref.getVersion()).isEqualTo(1);

        ref.setRemark("v2");
        mapper.updateById(ref);
        ref = mapper.selectById(id);
        assertThat(ref.getVersion()).isEqualTo(2);

        ref.setRemark("v3");
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
                "EXPLAIN SELECT * FROM emp_ability WHERE emp_id = 1 AND is_deleted = 0");
        String key = (String) plan.get(0).get("key");
        assertThat(key)
                .as("query on emp_id should use idx_emp_id index")
                .isEqualTo("idx_emp_id");
    }

    @Test
    void indexUsedForTagIdLookup() {
        List<Map<String, Object>> plan = jdbcTemplate.queryForList(
                "EXPLAIN SELECT * FROM emp_ability WHERE tag_id = 100 AND is_deleted = 0");
        String key = (String) plan.get(0).get("key");
        assertThat(key)
                .as("query on tag_id should use idx_tag_id index")
                .isEqualTo("idx_tag_id");
    }

    @Test
    void uniqueIndexUsedForCompoundLookup() {
        List<Map<String, Object>> plan = jdbcTemplate.queryForList(
                "EXPLAIN SELECT * FROM emp_ability "
                        + "WHERE emp_id = 1 AND tag_id = 100 AND evaluation_source = 'MANUAL' "
                        + "AND is_deleted = 0");
        String key = (String) plan.get(0).get("key");
        assertThat(key)
                .as("compound lookup should use uk_emp_tag_source unique index")
                .isEqualTo("uk_emp_tag_source");
    }

    // ------------------------------------------------------------------ //
    //  Factory helper                                                     //
    // ------------------------------------------------------------------ //

    private static EmpAbility sampleAbility(Long empId, Long tagId, String source) {
        EmpAbility a = new EmpAbility();
        a.setEmpId(empId);
        a.setTagId(tagId);
        a.setEvaluationSource(source);
        a.setMasteryLevel(3);
        a.setSourceWeight(new BigDecimal("0.80"));
        a.setEvaluationDate(LocalDate.now());
        a.setIsDeleted(0);
        a.setVersion(1);
        return a;
    }
}
