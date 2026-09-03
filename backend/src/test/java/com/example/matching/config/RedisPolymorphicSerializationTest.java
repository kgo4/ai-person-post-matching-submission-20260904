package com.example.matching.config;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.matching.MatchingRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 Redis 多态序列化严格白名单。
 * 白名单仅包含：项目 DTO + 具体 JDK 容器/值类型 + Java Time。
 * 不包含任何宽泛包前缀（java.lang.*、java.util.* 等）。
 */
class RedisPolymorphicSerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Map.class)
                .allowIfBaseType(List.class)
                .allowIfBaseType(Set.class)
                .allowIfBaseType(Number.class)
                .allowIfBaseType(CharSequence.class)
                .allowIfSubType("com.example.matching.")
                .allowIfSubType(Page.class)
                .allowIfSubType(java.util.LinkedHashMap.class)
                .allowIfSubType(java.util.HashMap.class)
                .allowIfSubType(java.util.ArrayList.class)
                .allowIfSubType(java.util.LinkedList.class)
                .allowIfSubType(java.util.HashSet.class)
                .allowIfSubType(java.util.LinkedHashSet.class)
                .allowIfSubType(java.util.TreeMap.class)
                .allowIfSubType(java.util.TreeSet.class)
                .allowIfSubType(java.util.Collections.emptyMap().getClass())
                .allowIfSubType(java.util.Collections.emptyList().getClass())
                .allowIfSubType(java.util.Collections.emptySet().getClass())
                .allowIfSubType(List.of().getClass())
                .allowIfSubType(Set.of().getClass())
                .allowIfSubType(Map.of().getClass())
                .allowIfSubType(List.of(1).getClass())
                .allowIfSubType(Set.of(1).getClass())
                .allowIfSubType(Map.of("a", 1).getClass())
                .allowIfSubType(String.class)
                .allowIfSubType(Integer.class)
                .allowIfSubType(Long.class)
                .allowIfSubType(Double.class)
                .allowIfSubType(Float.class)
                .allowIfSubType(Boolean.class)
                .allowIfSubType(BigDecimal.class)
                .allowIfSubType(java.math.BigInteger.class)
                .allowIfSubType(java.time.LocalDateTime.class)
                .allowIfSubType(java.time.LocalDate.class)
                .allowIfSubType(java.time.LocalTime.class)
                .allowIfSubType(java.time.Instant.class)
                .allowIfSubType(java.time.Duration.class)
                .build();
        objectMapper.activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.NON_FINAL);
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Test
    @DisplayName("项目实体 round-trip")
    void roundTripEntity() throws Exception {
        EmpEmployee emp = new EmpEmployee();
        emp.setId(1L);
        emp.setRealName("张三");
        emp.setEmpCode("E001");

        String json = objectMapper.writeValueAsString(emp);
        EmpEmployee deserialized = objectMapper.readValue(json, EmpEmployee.class);
        assertThat(deserialized.getId()).isEqualTo(1L);
        assertThat(deserialized.getRealName()).isEqualTo("张三");
    }

    @Test
    @DisplayName("LinkedHashMap + ArrayList + BigDecimal round-trip")
    void roundTripCollections() throws Exception {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("count", 42);
        map.put("tags", List.of("Java", "Spring"));
        map.put("score", new BigDecimal("95.50"));
        map.put("timestamp", LocalDateTime.of(2025, 1, 15, 10, 30));

        String json = objectMapper.writeValueAsString(map);
        @SuppressWarnings("unchecked")
        Map<String, Object> deserialized = objectMapper.readValue(json, Map.class);

        assertThat(deserialized.get("count")).isEqualTo(42);
        assertThat(deserialized.get("score")).isEqualTo(new BigDecimal("95.50"));
    }

    @Test
    @DisplayName("HashSet round-trip")
    void roundTripHashSet() throws Exception {
        Set<String> set = new java.util.HashSet<>(Set.of("Java", "Python", "Go"));

        String json = objectMapper.writeValueAsString(set);
        @SuppressWarnings("unchecked")
        Set<String> deserialized = objectMapper.readValue(json, Set.class);

        assertThat(deserialized).containsExactlyInAnyOrder("Java", "Python", "Go");
    }

    @Test
    @DisplayName("MatchingRecord round-trip")
    void roundTripMatchingRecord() throws Exception {
        MatchingRecord record = new MatchingRecord();
        record.setId(100L);
        record.setAiMatchScore(new BigDecimal("85.50"));

        String json = objectMapper.writeValueAsString(record);
        MatchingRecord deserialized = objectMapper.readValue(json, MatchingRecord.class);
        assertThat(deserialized.getId()).isEqualTo(100L);
        assertThat(deserialized.getAiMatchScore()).isEqualByComparingTo("85.50");
    }

    @Test
    @DisplayName("MyBatis-Plus Page can be read through the Spring Cache Object entry point")
    void roundTripMyBatisPlusPageThroughObject() throws Exception {
        EmpEmployee employee = new EmpEmployee();
        employee.setId(1L);
        employee.setRealName("Zhang San");

        Page<EmpEmployee> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(employee));

        String json = objectMapper.writeValueAsString(page);
        Object deserialized = objectMapper.readValue(json, Object.class);

        assertThat(deserialized).isInstanceOf(Page.class);
        Page<?> restoredPage = (Page<?>) deserialized;
        assertThat(restoredPage.getCurrent()).isEqualTo(1L);
        assertThat(restoredPage.getRecords()).hasSize(1);
    }

    @Test
    @DisplayName("拒绝 javax.swing.JFrame（不在白名单中）")
    void rejectsJFrame() {
        assertThatThrownBy(() -> objectMapper.readValue(
                "[\"javax.swing.JFrame\",{\"title\":\"hack\"}]", Object.class))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("拒绝 java.lang.ProcessBuilder（在 java.lang 包中但不在白名单）")
    void rejectsProcessBuilder() {
        assertThatThrownBy(() -> objectMapper.readValue(
                "[\"java.lang.ProcessBuilder\",[\"calc.exe\"]]", Object.class))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("拒绝 java.lang.Runtime（在 java.lang 包中但不在白名单）")
    void rejectsRuntime() {
        assertThatThrownBy(() -> objectMapper.readValue(
                "[\"java.lang.Runtime\",{}]", Object.class))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("拒绝 java.net.URL（在 java.net 包中但不在白名单）")
    void rejectsUrl() {
        assertThatThrownBy(() -> objectMapper.readValue(
                "[\"java.net.URL\",[\"http://evil.com\"]]", Object.class))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("拒绝 org.springframework 类")
    void rejectsSpringClasses() {
        assertThatThrownBy(() -> objectMapper.readValue(
                "[\"org.springframework.context.support.ClassPathXmlApplicationContext\",[\"evil.xml\"]]",
                Object.class))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("拒绝 com.sun 类")
    void rejectsComSun() {
        assertThatThrownBy(() -> objectMapper.readValue(
                "[\"com.sun.rowset.CachedRowSetImpl\",{}]", Object.class))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("null 值正常处理")
    void allowsNull() throws Exception {
        assertThat(objectMapper.readValue(objectMapper.writeValueAsString(null), Object.class)).isNull();
    }

    @Test
    @DisplayName("嵌套结构 round-trip")
    void roundTripNested() throws Exception {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("data", Map.of("items", List.of(Map.of("id", 1L))));

        String json = objectMapper.writeValueAsString(nested);
        @SuppressWarnings("unchecked")
        Map<String, Object> deserialized = objectMapper.readValue(json, Map.class);
        assertThat(deserialized).containsKey("data");
    }
}
