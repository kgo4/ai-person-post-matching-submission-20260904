package com.example.matching.mapper.post;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.matching.entity.post.PostHardConditionRule;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

/**
 * 岗位硬性条件规则 Mapper。
 */
@Mapper
public interface PostHardConditionRuleMapper extends BaseMapper<PostHardConditionRule> {

    @Delete("DELETE FROM post_hard_condition_rule WHERE post_id = #{postId}")
    int physicalDeleteByPostId(Long postId);
}
