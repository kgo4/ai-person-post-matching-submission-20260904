package com.example.matching.mapper.kg;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.matching.entity.kg.KgGraphEdge;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 知识图谱边 Mapper
 */
@Mapper
public interface KgGraphEdgeMapper extends BaseMapper<KgGraphEdge> {

    @Insert("<script>"
            + "INSERT INTO kg_graph_edge (edge_key, source_node_key, target_node_key, edge_type, weight_value, confidence_score, metadata_json, created_time, updated_time) VALUES "
            + "<foreach collection='edges' item='edge' separator=','>"
            + "(#{edge.edgeKey}, #{edge.sourceNodeKey}, #{edge.targetNodeKey}, #{edge.edgeType}, #{edge.weightValue}, #{edge.confidenceScore}, #{edge.metadataJson}, NOW(), NOW())"
            + "</foreach>"
            + "</script>")
    int insertBatch(@Param("edges") List<KgGraphEdge> edges);
}
