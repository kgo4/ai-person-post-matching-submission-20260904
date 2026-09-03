package com.example.matching.port.kg;

import java.util.Map;

/**
 * 知识图谱查询端口 — 公开只读接口。
 */
public interface GraphQueryPort {

    /**
     * 按节点类型统计图谱节点数量，用于报表与健康检查。
     *
     * @return nodeType -> 数量
     */
    Map<String, Long> countNodesByType();
}
