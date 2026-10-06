package com.example.matching.infra;

import com.example.matching.entity.common.EventOutbox;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.matching.MatchingBlackWhiteList;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.post.PostPost;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.entity.system.SysUser;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Static factory methods for creating fixed test data.
 * <p>
 * All IDs start from known offsets to avoid collision:
 * admin=1, employee1=10, employee2=11, post1=100, post2=101, tag1=200, tag2=201
 */
public final class TestFixtures {

    private TestFixtures() {}

    // ── Users ───────────────────────────────────────────────────────────

    public static SysUser adminUser() {
        SysUser u = new SysUser();
        u.setId(1L);
        u.setUsername("admin");
        u.setPassword("$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH"); // "admin123" bcrypt
        u.setRealName("管理员");
        u.setStatus(1);
        u.setIsDeleted(0);
        return u;
    }

    // ── Employees ───────────────────────────────────────────────────────

    public static EmpEmployee employee1() {
        EmpEmployee e = new EmpEmployee();
        e.setId(10L);
        e.setRealName("张三");
        e.setEmpCode("EMP010");
        e.setDepartmentId(1L);
        e.setStatus(1);
        e.setIsDeleted(0);
        return e;
    }

    public static EmpEmployee employee2() {
        EmpEmployee e = new EmpEmployee();
        e.setId(11L);
        e.setRealName("李四");
        e.setEmpCode("EMP011");
        e.setDepartmentId(1L);
        e.setStatus(1);
        e.setIsDeleted(0);
        return e;
    }

    // ── Posts ────────────────────────────────────────────────────────────

    public static PostPost post1() {
        PostPost p = new PostPost();
        p.setId(100L);
        p.setPostName("Java后端工程师");
        p.setJobDescription("负责后端接口开发");
        p.setStatus(1);
        p.setIsDeleted(0);
        return p;
    }

    public static PostPost post2() {
        PostPost p = new PostPost();
        p.setId(101L);
        p.setPostName("前端开发工程师");
        p.setJobDescription("负责前端页面开发");
        p.setStatus(1);
        p.setIsDeleted(0);
        return p;
    }

    // ── Ability Tags ────────────────────────────────────────────────────

    public static AbilityTag tagJava() {
        AbilityTag t = new AbilityTag();
        t.setId(200L);
        t.setTagName("Java开发");
        t.setTagCategory("TECHNICAL");
        t.setStatus(1);
        t.setIsDeleted(0);
        return t;
    }

    public static AbilityTag tagSpring() {
        AbilityTag t = new AbilityTag();
        t.setId(201L);
        t.setTagName("Spring框架");
        t.setTagCategory("TECHNICAL");
        t.setStatus(1);
        t.setIsDeleted(0);
        return t;
    }

    // ── Employee Abilities ──────────────────────────────────────────────

    public static EmpAbility ability1() {
        EmpAbility a = new EmpAbility();
        a.setId(300L);
        a.setEmpId(10L);
        a.setTagId(200L);
        a.setMasteryLevel(4);
        a.setAbilityLevel(4);
        a.setEvaluationSource("MANUAL");
        a.setSourceWeight(new BigDecimal("0.90"));
        a.setIsDeleted(0);
        a.setVersion(0);
        return a;
    }

    public static EmpAbility ability2() {
        EmpAbility a = new EmpAbility();
        a.setId(301L);
        a.setEmpId(10L);
        a.setTagId(201L);
        a.setMasteryLevel(3);
        a.setAbilityLevel(3);
        a.setEvaluationSource("AI_ASSESSMENT");
        a.setSourceWeight(new BigDecimal("0.80"));
        a.setIsDeleted(0);
        a.setVersion(0);
        return a;
    }

    // ── Post Ability Models ─────────────────────────────────────────────

    public static PostAbilityModel postModel1() {
        PostAbilityModel m = new PostAbilityModel();
        m.setId(400L);
        m.setPostId(100L);
        m.setTagId(200L);
        m.setMinRequiredLevel(3);
        m.setWeight(new BigDecimal("0.60"));
        m.setIsCore(1);
        m.setIsRequired(1);
        m.setIsDeleted(0);
        return m;
    }

    public static PostAbilityModel postModel2() {
        PostAbilityModel m = new PostAbilityModel();
        m.setId(401L);
        m.setPostId(100L);
        m.setTagId(201L);
        m.setMinRequiredLevel(2);
        m.setWeight(new BigDecimal("0.40"));
        m.setIsCore(0);
        m.setIsRequired(1);
        m.setIsDeleted(0);
        return m;
    }

    // ── Matching Records ────────────────────────────────────────────────

    public static MatchingRecord matchingRecord1() {
        MatchingRecord r = new MatchingRecord();
        r.setId(500L);
        r.setBatchNo("BATCH-001");
        r.setEmpId(10L);
        r.setPostId(100L);
        r.setAiMatchScore(new BigDecimal("85.00"));
        r.setMatchStatus(2); // 适配
        r.setIsLocked(0);
        r.setApprovalStatus(0);
        r.setIsDeleted(0);
        r.setVersion(0);
        return r;
    }

    // ── Black/White List ────────────────────────────────────────────────

    public static MatchingBlackWhiteList blacklistEntry() {
        MatchingBlackWhiteList e = new MatchingBlackWhiteList();
        e.setEmpId(11L);
        e.setPostId(100L);
        e.setListType(2); // 黑名单
        e.setIsDeleted(0);
        return e;
    }

    public static MatchingBlackWhiteList whitelistEntry() {
        MatchingBlackWhiteList e = new MatchingBlackWhiteList();
        e.setEmpId(10L);
        e.setPostId(100L);
        e.setListType(1); // 白名单
        e.setIsDeleted(0);
        return e;
    }

    // ── Outbox Events ───────────────────────────────────────────────────

    public static EventOutbox pendingEvent(String eventType, String exchange, String routingKey) {
        EventOutbox e = new EventOutbox();
        e.setEventType(eventType);
        e.setExchange(exchange);
        e.setRoutingKey(routingKey);
        e.setPayload("{\"test\":true}");
        e.setStatus("PENDING");
        e.setAttemptCount(0);
        e.setMaxAttempts(10);
        e.setCreatedTime(LocalDateTime.now());
        return e;
    }
}
