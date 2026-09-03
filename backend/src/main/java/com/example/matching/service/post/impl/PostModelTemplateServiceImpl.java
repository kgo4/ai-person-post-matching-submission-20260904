package com.example.matching.service.post.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.matching.dto.post.PostTemplateSaveDTO;
import com.example.matching.entity.post.PostModelTemplate;
import com.example.matching.mapper.post.PostModelTemplateMapper;
import com.example.matching.service.common.BusinessCodeGenerator;
import com.example.matching.service.post.PostModelTemplateService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PostModelTemplateServiceImpl extends ServiceImpl<PostModelTemplateMapper, PostModelTemplate> implements PostModelTemplateService {

    private final BusinessCodeGenerator businessCodeGenerator;

    @Override
    public void saveTemplate(PostTemplateSaveDTO dto) {
        PostModelTemplate template;
        if (dto.getId() == null) {
            template = new PostModelTemplate();
        } else {
            template = getById(dto.getId());
            if (template == null) {
                template = new PostModelTemplate();
            }
        }
        BeanUtils.copyProperties(dto, template);
        if (dto.getId() == null && !StringUtils.hasText(template.getTemplateCode())) {
            template.setTemplateCode(businessCodeGenerator.nextTemplateCode());
        }
        if (template.getStatus() == null) {
            template.setStatus(1);
        }
        saveOrUpdate(template);
    }

    @Override
    public IPage<PostModelTemplate> pageTemplates(IPage<PostModelTemplate> page, String keyword) {
        LambdaQueryWrapper<PostModelTemplate> wrapper = Wrappers.<PostModelTemplate>lambdaQuery();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(PostModelTemplate::getTemplateCode, keyword)
                    .or().like(PostModelTemplate::getTemplateName, keyword));
        }
        wrapper.orderByAsc(PostModelTemplate::getTemplateCode);
        return page(page, wrapper);
    }
}
