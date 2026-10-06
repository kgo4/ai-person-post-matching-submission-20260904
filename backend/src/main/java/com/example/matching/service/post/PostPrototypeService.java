package com.example.matching.service.post;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.matching.dto.post.PostPrototypeSaveDTO;
import com.example.matching.dto.post.PostPrototypeVO;
import com.example.matching.entity.post.PostPrototype;

import java.util.List;

/**
 * 岗位原型服务接口
 * <p>
 * 管理岗位族/原型模板，支撑：
 * - 原型CRUD
 * - 基于向量的原型召回（给定描述找最像的原型）
 * - 将原型能力模板应用到具体岗位
 */
public interface PostPrototypeService extends IService<PostPrototype> {

    /**
     * 分页查询原型
     */
    IPage<PostPrototype> pagePrototypes(IPage<PostPrototype> page, String keyword, String industry, String category);

    /**
     * 查询所有启用的原型
     */
    List<PostPrototype> listEnabled();

    /**
     * 获取原型详情（含标签）
     */
    PostPrototypeVO getDetail(Long id);

    /**
     * 保存原型（新增或更新）
     */
    void savePrototype(PostPrototypeSaveDTO dto);

    /**
     * 删除原型
     */
    void deletePrototype(Long id);

    /**
     * 向量召回最相似的原型
     *
     * @param description 岗位描述文本
     * @param topN        返回前N个
     * @return 相似原型列表（按相似度降序）
     */
    List<PostPrototypeVO> recallByDescription(String description, int topN);

    /**
     * 将原型的能力模板应用到指定岗位
     *
     * @param prototypeId 原型ID
     * @param postId      岗位ID
     */
    void applyPrototypeToPost(Long prototypeId, Long postId);
}
