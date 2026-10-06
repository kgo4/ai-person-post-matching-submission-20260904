package com.example.matching.mapper.post;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.matching.entity.post.PostAbilityModel;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

/**
 * 岗位能力模型 Mapper
 */
@Mapper
public interface PostAbilityModelMapper extends BaseMapper<PostAbilityModel> {

    /**
     * 物理删除指定岗位的能力模型配置（绕过逻辑删除）
     */
    @Delete("DELETE FROM post_ability_model WHERE post_id = #{postId}")
    int physicalDeleteByPostId(Long postId);
}
