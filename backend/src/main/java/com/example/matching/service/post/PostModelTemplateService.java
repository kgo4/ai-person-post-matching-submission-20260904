package com.example.matching.service.post;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.matching.dto.post.PostTemplateSaveDTO;
import com.example.matching.entity.post.PostModelTemplate;

/**
 * 岗位模型模板 服务接口
 */
public interface PostModelTemplateService extends IService<PostModelTemplate> {

    void saveTemplate(PostTemplateSaveDTO dto);

    IPage<PostModelTemplate> pageTemplates(IPage<PostModelTemplate> page, String keyword);
}
