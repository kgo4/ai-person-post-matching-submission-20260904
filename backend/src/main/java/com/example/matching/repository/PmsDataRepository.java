package com.example.matching.repository;

import lombok.extern.slf4j.Slf4j;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PreDestroy;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;

/**
 * PMS项目管理系统数据访问层
 * <p>
 * 内部创建独立数据源，只读访问PMS数据库。
 * 不注册为Spring Bean，完全隔离于主数据源。
 */
@Slf4j
@Repository
public class PmsDataRepository {

    @Value("${pms.datasource.url:}")
    private String url;

    @Value("${pms.datasource.username:}")
    private String username;

    @Value("${pms.datasource.password:}")
    private String password;

    @Value("${pms.datasource.driver-class-name:com.mysql.cj.jdbc.Driver}")
    private String driverClassName;

    private JdbcTemplate jdbcTemplate;
    private HikariDataSource dataSource;
    private boolean available = false;

    @PostConstruct
    public void init() {
        if (url == null || url.isEmpty()) {
            log.warn("PMS数据源URL未配置，PMS功能不可用");
            return;
        }
        try {
            HikariConfig config = new HikariConfig();
            config.setDriverClassName(driverClassName);
            config.setJdbcUrl(url);
            config.setUsername(username);
            config.setPassword(password);
            config.setPoolName("pms-read-pool");
            config.setMaximumPoolSize(5);
            config.setMinimumIdle(0);
            config.setConnectionTimeout(5_000);
            config.setValidationTimeout(3_000);
            config.setInitializationFailTimeout(-1);
            this.dataSource = new HikariDataSource(config);
            this.jdbcTemplate = new JdbcTemplate(dataSource);
            this.available = true;
            log.info("PMS数据源初始化成功: {}", url);
        } catch (Exception e) {
            log.warn("PMS数据源初始化失败，PMS功能不可用: {}", e.getMessage());
            this.available = false;
        }
    }

    @PreDestroy
    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
        available = false;
    }

    /**
     * 检查PMS数据源是否可用
     */
    public boolean isAvailable() {
        return available && jdbcTemplate != null;
    }

    /**
     * 根据工号查找PMS用户
     *
     * @param employeeId 工号（如EMP003）
     * @return 用户信息
     */
    public Map<String, Object> findUserByEmployeeId(String employeeId) {
        String sql = "SELECT id, username, nickname, employee_id, role, email, phone, status " +
                "FROM pms_user WHERE employee_id = ? AND deleted = 0 LIMIT 1";
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, employeeId);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * 根据用户名查找PMS用户
     *
     * @param username 用户名
     * @return 用户信息
     */
    public Map<String, Object> findUserByUsername(String username) {
        String sql = "SELECT id, username, nickname, employee_id, role, email, phone, status " +
                "FROM pms_user WHERE username = ? AND deleted = 0 LIMIT 1";
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, username);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * 根据昵称查找PMS用户（模糊匹配）
     *
     * @param nickname 昵称
     * @return 用户列表
     */
    public List<Map<String, Object>> findUsersByNickname(String nickname) {
        String sql = "SELECT id, username, nickname, employee_id, role, email, phone " +
                "FROM pms_user WHERE nickname LIKE ? AND deleted = 0";
        return jdbcTemplate.queryForList(sql, "%" + nickname + "%");
    }

    /**
     * 获取所有PMS用户
     *
     * @return 用户列表
     */
    public List<Map<String, Object>> findAllUsers() {
        String sql = "SELECT id, username, nickname, employee_id, role, email, phone " +
                "FROM pms_user WHERE deleted = 0 ORDER BY id";
        return jdbcTemplate.queryForList(sql);
    }

    /**
     * 获取用户负责的工单（指定时间范围内）
     *
     * @param userId 用户ID
     * @param months 时间范围（月）
     * @return 工单列表
     */
    public List<Map<String, Object>> getWorkOrders(Long userId, int months) {
        String sql = "SELECT wo.id, wo.title, wo.project_id, wo.product_id, wo.status, wo.priority, " +
                "wo.assignee_id, wo.assignee_name, wo.workload, wo.value_business, wo.value_technical, " +
                "wo.value_user, wo.start_date, wo.end_date, wo.description, " +
                "p.name as project_name, pr.name as product_name " +
                "FROM pms_work_order wo " +
                "LEFT JOIN pms_project p ON wo.project_id = p.id " +
                "LEFT JOIN pms_product pr ON wo.product_id = pr.id " +
                "WHERE wo.assignee_id = ? AND wo.deleted = 0 " +
                "AND wo.created_at >= DATE_SUB(NOW(), INTERVAL ? MONTH) " +
                "ORDER BY wo.created_at DESC";
        return jdbcTemplate.queryForList(sql, userId, months);
    }

    /**
     * 获取用户负责的Bug（指定时间范围内）
     *
     * @param userId 用户ID
     * @param months 时间范围（月）
     * @return Bug列表
     */
    public List<Map<String, Object>> getBugs(Long userId, int months) {
        String sql = "SELECT b.id, b.title, b.severity, b.status, b.assignee_id, b.assignee_name, " +
                "b.source, b.description, b.created_at, b.resolved_at " +
                "FROM pms_bug b " +
                "WHERE b.assignee_id = ? AND b.deleted = 0 " +
                "AND b.created_at >= DATE_SUB(NOW(), INTERVAL ? MONTH) " +
                "ORDER BY b.created_at DESC";
        return jdbcTemplate.queryForList(sql, userId, months);
    }

    /**
     * 获取用户编写的测试用例（指定时间范围内）
     *
     * @param userId 用户ID
     * @param months 时间范围（月）
     * @return 测试用例列表
     */
    public List<Map<String, Object>> getTestCases(Long userId, int months) {
        String sql = "SELECT tc.id, tc.title, tc.product_id, tc.priority, tc.status, " +
                "tc.assignee_id, tc.assignee_name, tc.work_order_id, tc.requirement_id, " +
                "pr.name as product_name, " +
                "GROUP_CONCAT(CONCAT('[步骤', s.step_number, '] ', s.action, ' -> 预期: ', IFNULL(s.expected_result, '')) SEPARATOR '\\n') as steps " +
                "FROM pms_test_case tc " +
                "LEFT JOIN pms_product pr ON tc.product_id = pr.id " +
                "LEFT JOIN pms_test_case_step s ON s.test_case_id = tc.id " +
                "WHERE tc.assignee_id = ? AND tc.deleted = 0 " +
                "AND tc.created_at >= DATE_SUB(NOW(), INTERVAL ? MONTH) " +
                "GROUP BY tc.id " +
                "ORDER BY tc.created_at DESC";
        return jdbcTemplate.queryForList(sql, userId, months);
    }

    /**
     * 获取用户参与的项目
     *
     * @param userId 用户ID
     * @return 项目列表
     */
    public List<Map<String, Object>> getProjectParticipation(Long userId) {
        String sql = "SELECT DISTINCT p.id, p.name, p.product_id, p.description, p.tags, " +
                "p.priority, p.workload, p.status, p.progress, p.created_at, " +
                "pr.name as product_name, pr.category as product_category, " +
                "pm.role as member_role " +
                "FROM pms_project p " +
                "INNER JOIN pms_product_member pm ON p.product_id = pm.product_id AND pm.user_id = ? " +
                "LEFT JOIN pms_product pr ON p.product_id = pr.id " +
                "WHERE p.deleted = 0 " +
                "ORDER BY p.created_at DESC";
        return jdbcTemplate.queryForList(sql, userId);
    }

    /**
     * 获取用户负责的迭代
     *
     * @param userId 用户ID
     * @param months 时间范围（月）
     * @return 迭代列表
     */
    public List<Map<String, Object>> getIterations(Long userId, int months) {
        String sql = "SELECT i.id, i.name, i.project_id, i.product_id, " +
                "i.status, i.start_date, i.end_date, " +
                "i.description, pr.name as product_name " +
                "FROM pms_iteration i " +
                "INNER JOIN pms_product_member pm ON i.product_id = pm.product_id AND pm.user_id = ? " +
                "LEFT JOIN pms_product pr ON i.product_id = pr.id " +
                "WHERE i.deleted = 0 " +
                "AND i.created_at >= DATE_SUB(NOW(), INTERVAL ? MONTH) " +
                "ORDER BY i.created_at DESC";
        return jdbcTemplate.queryForList(sql, userId, months);
    }

    /**
     * 获取用户完成的工单子任务
     *
     * @param userId 用户ID
     * @param months 时间范围（月）
     * @return 子任务列表
     */
    public List<Map<String, Object>> getSubtasks(Long userId, int months) {
        String sql = "SELECT s.id, s.work_order_id, s.name, s.assignee_id, s.assignee_name, " +
                "s.status, wo.title as work_order_title " +
                "FROM pms_work_order_subtask s " +
                "LEFT JOIN pms_work_order wo ON s.work_order_id = wo.id " +
                "WHERE s.assignee_id = ? " +
                "AND s.created_at >= DATE_SUB(NOW(), INTERVAL ? MONTH) " +
                "ORDER BY s.created_at DESC";
        return jdbcTemplate.queryForList(sql, userId, months);
    }

    /**
     * 测试PMS数据库连接
     *
     * @return 连接是否成功
     */
    public boolean testConnection() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return true;
        } catch (Exception e) {
            log.error("PMS数据库连接测试失败: {}", e.getMessage());
            return false;
        }
    }
}
