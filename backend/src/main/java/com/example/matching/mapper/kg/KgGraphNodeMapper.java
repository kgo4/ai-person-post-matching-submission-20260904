package com.example.matching.mapper.kg;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.matching.entity.kg.KgGraphNode;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 知识图谱节点 Mapper
 */
@Mapper
public interface KgGraphNodeMapper extends BaseMapper<KgGraphNode> {

    @Insert("<script>"
            + "INSERT INTO kg_graph_node (node_key, node_type, ref_id, label, category, level_value, status, weight_value, metadata_json, created_time, updated_time) VALUES "
            + "<foreach collection='nodes' item='node' separator=','>"
            + "(#{node.nodeKey}, #{node.nodeType}, #{node.refId}, #{node.label}, #{node.category}, #{node.levelValue}, #{node.status}, #{node.weightValue}, #{node.metadataJson}, NOW(), NOW())"
            + "</foreach>"
            + "</script>")
    int insertBatch(@Param("nodes") List<KgGraphNode> nodes);
}
