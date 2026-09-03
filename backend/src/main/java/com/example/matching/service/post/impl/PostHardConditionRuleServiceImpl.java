package com.example.matching.service.post.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.dto.matching.MatchingExecuteDTO.HardCondition;
import com.example.matching.dto.post.PostHardConditionRuleDTO;
import com.example.matching.entity.post.PostHardConditionRule;
import com.example.matching.mapper.post.PostHardConditionRuleMapper;
import com.example.matching.service.post.PostHardConditionRuleService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class PostHardConditionRuleServiceImpl
        extends ServiceImpl<PostHardConditionRuleMapper, PostHardConditionRule>
        implements PostHardConditionRuleService {

    @Override
    public List<PostHardConditionRule> listByPostId(Long postId) {
        return list(Wrappers.<PostHardConditionRule>lambdaQuery()
                .eq(PostHardConditionRule::getPostId, postId)
                .orderByAsc(PostHardConditionRule::getSortOrder)
                .orderByAsc(PostHardConditionRule::getId));
    }

    @Override
    public List<PostHardConditionRule> listEnabledByPostId(Long postId) {
        return list(Wrappers.<PostHardConditionRule>lambdaQuery()
                .eq(PostHardConditionRule::getPostId, postId)
                .eq(PostHardConditionRule::getEnabled, 1)
                .orderByAsc(PostHardConditionRule::getSortOrder)
                .orderByAsc(PostHardConditionRule::getId));
    }

    @Override
    @Transactional
    public void saveRule(PostHardConditionRuleDTO dto) {
        validateRule(dto);
        PostHardConditionRule rule = dto.getId() == null ? new PostHardConditionRule() : getById(dto.getId());
        if (rule == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "岗位硬性规则不存在");
        }
        BeanUtils.copyProperties(dto, rule);
        if (rule.getEnabled() == null) {
            rule.setEnabled(1);
        }
        if (rule.getSortOrder() == null) {
            rule.setSortOrder(0);
        }
        saveOrUpdate(rule);
    }

    @Override
    @Transactional
    public void batchConfig(Long postId, List<PostHardConditionRuleDTO> list) {
        baseMapper.physicalDeleteByPostId(postId);
        if (list == null || list.isEmpty()) {
            return;
        }

        Set<String> uniqueFields = new HashSet<>();
        List<PostHardConditionRule> rules = list.stream().map(dto -> {
            dto.setPostId(postId);
            validateRule(dto);
            String uniqueKey = dto.getFieldName() + "#" + dto.getOperator() + "#" + dto.getExpectedValue();
            if (!uniqueFields.add(uniqueKey)) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "岗位硬性规则重复：" + dto.getFieldLabel());
            }

            PostHardConditionRule rule = new PostHardConditionRule();
            BeanUtils.copyProperties(dto, rule);
            rule.setId(null);
            if (rule.getEnabled() == null) {
                rule.setEnabled(1);
            }
            if (rule.getSortOrder() == null) {
                rule.setSortOrder(0);
            }
            return rule;
        }).toList();
        saveBatch(rules);
    }

    @Override
    public List<HardCondition> toHardConditions(Long postId) {
        return listEnabledByPostId(postId).stream().map(rule -> {
            HardCondition condition = new HardCondition();
            condition.setField(rule.getFieldName());
            condition.setOperator(rule.getOperator());
            condition.setValue(rule.getExpectedValue());
            condition.setFieldType(rule.getFieldType());
            condition.setValueRankJson(rule.getValueRankJson());
            condition.setLabel(buildConditionLabel(rule));
            return condition;
        }).toList();
    }

    private void validateRule(PostHardConditionRuleDTO dto) {
        if (dto.getPostId() == null || isBlank(dto.getFieldName()) || isBlank(dto.getOperator()) || isBlank(dto.getExpectedValue())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "岗位硬性规则缺少必要字段");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String buildConditionLabel(PostHardConditionRule rule) {
        String label = rule.getFieldLabel() != null ? rule.getFieldLabel() : rule.getFieldName();
        return label + " " + rule.getOperator() + " " + rule.getExpectedValue();
    }
}
