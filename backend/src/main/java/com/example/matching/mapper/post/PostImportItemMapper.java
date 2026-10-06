package com.example.matching.mapper.post;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.matching.entity.post.PostImportItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

import java.util.List;
import java.util.Map;

@Mapper
public interface PostImportItemMapper extends BaseMapper<PostImportItem> {

    @Delete("DELETE FROM post_import_item WHERE batch_id = #{batchId}")
    int deleteByBatchId(@Param("batchId") Long batchId);

    /**
     * 按批次ID分组统计各分析状态的数量
     *
     * @param batchIds 批次ID列表
     * @return 每条记录包含 batch_id, analysis_status, cnt
     */
    @Select("<script>" +
            "SELECT batch_id, analysis_status, COUNT(*) as cnt " +
            "FROM post_import_item " +
            "WHERE batch_id IN " +
            "<foreach item='id' collection='batchIds' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach> " +
            "GROUP BY batch_id, analysis_status" +
            "</script>")
    List<Map<String, Object>> countByBatchIds(@Param("batchIds") List<Long> batchIds);
}
