package com.example.matching.service.post.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.matching.dto.post.PostAbilityModelConfigDTO;
import com.example.matching.entity.post.TemplateAbilityModel;
import com.example.matching.mapper.post.TemplateAbilityModelMapper;
import com.example.matching.service.post.PostAbilityModelService;
import com.example.matching.service.post.TemplateAbilityModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 模板能力要求服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateAbilityModelServiceImpl extends ServiceImpl<TemplateAbilityModelMapper, TemplateAbilityModel>
        implements TemplateAbilityModelService {

    private final PostAbilityModelService postAbilityModelService;

    @Override
    public List<TemplateAbilityModel> listByTemplateId(Long templateId) {
        return list(Wrappers.<TemplateAbilityModel>lambdaQuery()
                .eq(TemplateAbilityModel::getTemplateId, templateId));
    }

    @Override
    @Transactional
    public void batchSave(Long templateId, List<TemplateAbilityModel> list) {
        if (list == null || list.isEmpty()) {
            return;
        }

        // 删除旧配置
        remove(Wrappers.<TemplateAbilityModel>lambdaQuery()
                .eq(TemplateAbilityModel::getTemplateId, templateId));

        // 保存新配置
        for (TemplateAbilityModel model : list) {
            model.setId(null);
            model.setTemplateId(templateId);
        }
        saveBatch(list);

        log.info("模板能力要求批量保存完成: templateId={}, count={}", templateId, list.size());
    }

    @Override
    @Transactional
    public void applyTemplateToPost(Long templateId, Long postId) {
        // 1. 查询模板能力要求
        List<TemplateAbilityModel> templateModels = listByTemplateId(templateId);
        if (templateModels.isEmpty()) {
            log.warn("模板没有配置能力要求: templateId={}", templateId);
            return;
        }

        // 2. 转换为 PostAbilityModelConfigDTO 并通过 batchConfig 写入
        //    确保缓存驱逐、质量重算、领域事件都执行
        List<PostAbilityModelConfigDTO> configList = templateModels.stream().map(template -> {
            PostAbilityModelConfigDTO dto = new PostAbilityModelConfigDTO();
            dto.setPostId(postId);
            dto.setTagId(template.getTagId());
            dto.setMinRequiredLevel(template.getMinRequiredLevel());
            dto.setWeight(template.getWeight());
            dto.setIsRequired(template.getIsRequired());
            dto.setIsCore(template.getIsCore());
            dto.setRemark(template.getRemark());
            return dto;
        }).toList();

        postAbilityModelService.batchConfig(configList);

        log.info("应用模板到岗位完成: templateId={}, postId={}, count={}", templateId, postId, configList.size());
    }
}
