package com.example.matching.integration.db;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.dto.kg.GraphBuildResultDTO;
import com.example.matching.entity.kg.KgGraphEdge;
import com.example.matching.entity.kg.KgGraphNode;
import com.example.matching.infrastructure.persistence.GraphRebuildTableRouter;
import com.example.matching.mapper.kg.KgGraphEdgeMapper;
import com.example.matching.mapper.kg.KgGraphNodeMapper;
import com.example.matching.service.kg.KnowledgeGraphBuildService;
import com.example.matching.service.kg.build.GraphEdgeProjectionService;
import com.example.matching.service.kg.build.GraphNodeProjectionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 知识图谱影子表全量重建的真实 MySQL 集成测试：
 * 覆盖 V77 迁移（影子表创建）、DynamicTableNameInnerInterceptor 路由、
 * 以及 DELETE 主表 + INSERT..SELECT 回填的事务交换。
 * <p>
 * 无 Docker 环境自动跳过（不影响默认 unit profile 基线）。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("integration")
@DisplayName("KnowledgeGraph shadow-table rebuild (real MySQL)")
class KnowledgeGraphShadowSwapIT {

    private static final boolean HAS_DOCKER = DockerClientFactory.instance().isDockerAvailable();

    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.0")
                    .withDatabaseName("matching_test")
                    .withUsername("test")
                    .withPassword("test")
                    .withStartupTimeout(Duration.ofMinutes(2));

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static final RabbitMQContainer RABBIT =
            new RabbitMQContainer("rabbitmq:3-management");

    static {
        if (HAS_DOCKER) {
            MYSQL.start();
            REDIS.start();
            RABBIT.start();
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        if (!HAS_DOCKER) {
            return;
        }
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", () -> RABBIT.getMappedPort(5672));
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @MockitoBean
    private GraphNodeProjectionService graphNodeProjectionService;

    @MockitoBean
    private GraphEdgeProjectionService graphEdgeProjectionService;

    @Autowired
    private KnowledgeGraphBuildService buildService;

    @Autowired
    private KgGraphNodeMapper nodeMapper;

    @Autowired
    private KgGraphEdgeMapper edgeMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void ensureBaseTables() {
        // 基线脚本才创建 kg_graph_node/edge（不在 Flyway 迁移中），测试内补齐
        jdbcTemplate.execute(NODE_TABLE_DDL);
        jdbcTemplate.execute(EDGE_TABLE_DDL);
    }

    @AfterEach
    void cleanUp() {
        GraphRebuildTableRouter.end();
        jdbcTemplate.execute("DELETE FROM kg_graph_node");
        jdbcTemplate.execute("DELETE FROM kg_graph_edge");
        jdbcTemplate.execute("DELETE FROM kg_graph_node_new");
        jdbcTemplate.execute("DELETE FROM kg_graph_edge_new");
    }

    @Test
    @DisplayName("V77 迁移创建了影子表")
    void shadowTablesCreatedByMigration() {
        assertThat(tableExists("kg_graph_node_new")).isTrue();
        assertThat(tableExists("kg_graph_edge_new")).isTrue();
    }

    @Test
    @DisplayName("路由开启后 mapper 写操作落到影子表，主表不受影响")
    void routingDirectsWritesToShadowTable() {
        nodeMapper.insert(sampleNode("POST:1", "Java后端"));
        assertThat(nodeMapper.selectCount(Wrappers.<KgGraphNode>lambdaQuery())).isEqualTo(1);

        GraphRebuildTableRouter.begin();
        try {
            // 路由状态下的 DELETE 应作用于影子表
            nodeMapper.delete(Wrappers.<KgGraphNode>lambdaQuery());
            assertThat(nodeMapper.selectCount(Wrappers.<KgGraphNode>lambdaQuery())).isZero();

            // 主表数据仍在
            Integer mainCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM kg_graph_node", Integer.class);
            assertThat(mainCount).isEqualTo(1);
        } finally {
            GraphRebuildTableRouter.end();
        }
    }

    @Test
    @DisplayName("事务交换：DELETE 主表 + INSERT..SELECT 影子表回填")
    void transactionalSwapReplacesMainFromShadow() {
        jdbcTemplate.update("INSERT INTO kg_graph_node_new (node_key, node_type, label, status) "
                + "VALUES ('POST:9', 'POST', '新岗位', 'ACTIVE')");
        jdbcTemplate.update("INSERT INTO kg_graph_node_new (node_key, node_type, label, status) "
                + "VALUES ('ABILITY:9', 'ABILITY', '新能力', 'ACTIVE')");
        jdbcTemplate.update("INSERT INTO kg_graph_edge_new (edge_key, source_node_key, target_node_key, edge_type) "
                + "VALUES ('REQUIRES_POST:9_ABILITY:9', 'POST:9', 'ABILITY:9', 'REQUIRES')");

        jdbcTemplate.update("DELETE FROM kg_graph_node");
        jdbcTemplate.update("INSERT INTO kg_graph_node SELECT * FROM kg_graph_node_new");
        jdbcTemplate.update("DELETE FROM kg_graph_edge");
        jdbcTemplate.update("INSERT INTO kg_graph_edge SELECT * FROM kg_graph_edge_new");

        List<String> labels = jdbcTemplate.queryForList(
                "SELECT label FROM kg_graph_node ORDER BY node_key", String.class);
        assertThat(labels).containsExactly("新能力", "新岗位");

        Integer edges = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kg_graph_edge", Integer.class);
        assertThat(edges).isEqualTo(1);
    }

    @Test
    @DisplayName("全量重建端到端：影子表投影 + 原子交换，主表被新图替换")
    void rebuildFullGraphSwapsShadowIntoMain() {
        nodeMapper.insert(sampleNode("POST:1", "旧岗位"));
        edgeMapper.insert(sampleEdge("REQUIRES_POST:1_ABILITY:1"));
        when(graphNodeProjectionService.projectNodes(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Map.of("POST", 1));
        when(graphEdgeProjectionService.projectEdges(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Map.of("REQUIRES", 1));

        GraphBuildResultDTO result = buildService.rebuildFullGraph();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getNodeCount()).isEqualTo(1);
        // 影子表被清空后回填（投影为 mock，未写真实行），主表与影子表一致
        Integer mainNodes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kg_graph_node", Integer.class);
        Integer shadowNodes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kg_graph_node_new", Integer.class);
        assertThat(mainNodes).isEqualTo(shadowNodes);
        assertThat(GraphRebuildTableRouter.isRouting()).isFalse();
    }

    @Test
    @DisplayName("投影为空时重建中止，主表数据保持完整")
    void rebuildAbortsLeavingMainIntactWhenProjectionEmpty() {
        nodeMapper.insert(sampleNode("POST:1", "旧岗位"));
        when(graphNodeProjectionService.projectNodes(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Map.of());
        when(graphEdgeProjectionService.projectEdges(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Map.of("REQUIRES", 1));

        assertThatThrownBy(() -> buildService.rebuildFullGraph())
                .isInstanceOf(IllegalStateException.class);

        Integer mainNodes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kg_graph_node", Integer.class);
        assertThat(mainNodes).isEqualTo(1);
        assertThat(GraphRebuildTableRouter.isRouting()).isFalse();
    }

    private boolean tableExists(String table) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class, table);
        return count != null && count > 0;
    }

    private KgGraphNode sampleNode(String nodeKey, String label) {
        KgGraphNode node = new KgGraphNode();
        node.setNodeKey(nodeKey);
        node.setNodeType("POST");
        node.setLabel(label);
        node.setStatus("ACTIVE");
        return node;
    }

    private KgGraphEdge sampleEdge(String edgeKey) {
        KgGraphEdge edge = new KgGraphEdge();
        edge.setEdgeKey(edgeKey);
        edge.setSourceNodeKey("POST:1");
        edge.setTargetNodeKey("ABILITY:1");
        edge.setEdgeType("REQUIRES");
        return edge;
    }

    private static final String NODE_TABLE_DDL =
            "CREATE TABLE IF NOT EXISTS kg_graph_node ("
                    + "`id` bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                    + "`node_key` varchar(128) NOT NULL,"
                    + "`node_type` varchar(32) NOT NULL,"
                    + "`ref_id` bigint NULL,"
                    + "`label` varchar(256) NOT NULL,"
                    + "`category` varchar(64) NULL,"
                    + "`level_value` int NULL,"
                    + "`status` varchar(32) NOT NULL DEFAULT 'ACTIVE',"
                    + "`weight_value` decimal(10,2) NULL,"
                    + "`metadata_json` longtext NULL,"
                    + "`created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "`updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                    + "UNIQUE INDEX `uk_node_key`(`node_key`)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";

    private static final String EDGE_TABLE_DDL =
            "CREATE TABLE IF NOT EXISTS kg_graph_edge ("
                    + "`id` bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                    + "`edge_key` varchar(160) NOT NULL,"
                    + "`source_node_key` varchar(128) NOT NULL,"
                    + "`target_node_key` varchar(128) NOT NULL,"
                    + "`edge_type` varchar(32) NOT NULL,"
                    + "`weight_value` decimal(10,2) NULL,"
                    + "`confidence_score` decimal(5,2) NULL,"
                    + "`metadata_json` longtext NULL,"
                    + "`created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "`updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                    + "UNIQUE INDEX `uk_edge_key`(`edge_key`)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
}
