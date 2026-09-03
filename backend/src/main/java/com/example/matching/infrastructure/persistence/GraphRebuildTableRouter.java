package com.example.matching.infrastructure.persistence;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图谱全量重建期间的表名路由：通过 ThreadLocal 将 kg_graph_node/kg_graph_edge
 * 的 MyBatis 写操作路由到影子表，投影成功后再由
 * {@code KnowledgeGraphBuildServiceImpl} 事务内原子回填主表。
 * <p>
 * 路由必须在 finally 中清除，防止泄漏到其他线程的请求。
 */
public final class GraphRebuildTableRouter {

    private static final ThreadLocal<Map<String, String>> ROUTES = new ThreadLocal<>();

    private static final Map<String, String> SHADOW_ROUTES = Map.of(
            "kg_graph_node", "kg_graph_node_new",
            "kg_graph_edge", "kg_graph_edge_new"
    );

    private GraphRebuildTableRouter() {
    }

    /** 开启影子表路由（幂等）。 */
    public static void begin() {
        ROUTES.set(new ConcurrentHashMap<>(SHADOW_ROUTES));
    }

    /** 关闭路由并清理 ThreadLocal（必须放在 finally）。 */
    public static void end() {
        ROUTES.remove();
    }

    /** 是否处于重建路由模式。 */
    public static boolean isRouting() {
        return ROUTES.get() != null;
    }

    /**
     * 动态表名处理器回调：返回目标表名；非路由状态返回原表名。
     */
    public static String targetFor(String tableName) {
        Map<String, String> routes = ROUTES.get();
        if (routes == null) {
            return tableName;
        }
        return routes.getOrDefault(tableName, tableName);
    }
}
