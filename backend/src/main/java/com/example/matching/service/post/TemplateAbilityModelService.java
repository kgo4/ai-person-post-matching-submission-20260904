package com.example.matching.service.post;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.matching.entity.post.TemplateAbilityModel;

import java.util.List;

/**
 * 模板能力要求服务接口
 */
public interface TemplateAbilityModelService extends IService<TemplateAbilityModel> {

    /**
     * 根据模板ID查询能力要求列表
     */
    List<TemplateAbilityModel> listByTemplateId(Long templateId);

    /**
     * 批量保存模板能力要求
     */
    void batchSave(Long templateId, List<TemplateAbilityModel> list);

    /**
     * 应用模板到岗位（复制模板能力要求到岗位能力模型）
     */
    void applyTemplateToPost(Long templateId, Long postId);
}
