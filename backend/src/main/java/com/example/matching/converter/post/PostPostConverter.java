package com.example.matching.converter.post;

import com.example.matching.entity.post.PostPost;
import com.example.matching.vo.post.PostAbilityModelVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 岗位对象转换器（MapStruct自动生成实现）
 */
@Mapper(componentModel = "spring")
public interface PostPostConverter {

    /**
     * M17：Entity 基础字段 -> 能力模型VO（业务明细由调用方补充）
     */
    @Mapping(source = "id", target = "postId")
    PostAbilityModelVO toAbilityModelVO(PostPost entity);
}
