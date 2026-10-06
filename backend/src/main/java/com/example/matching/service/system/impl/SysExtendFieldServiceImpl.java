package com.example.matching.service.system.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.matching.dto.system.ExtendFieldConfigDTO;
import com.example.matching.entity.system.SysExtendField;
import com.example.matching.mapper.system.SysExtendFieldMapper;
import com.example.matching.service.system.SysExtendFieldService;
import com.example.matching.vo.system.ExtendFieldVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class SysExtendFieldServiceImpl extends ServiceImpl<SysExtendFieldMapper, SysExtendField> implements SysExtendFieldService {

    @Override
    public void saveField(ExtendFieldConfigDTO dto) {
        SysExtendField field;
        if (dto.getId() == null) {
            field = new SysExtendField();
        } else {
            field = getById(dto.getId());
            if (field == null) {
                field = new SysExtendField();
            }
        }
        BeanUtils.copyProperties(dto, field);
        if (field.getStatus() == null) {
            field.setStatus(1);
        }
        saveOrUpdate(field);
    }

    @Override
    public List<ExtendFieldVO> listByModule(String businessModule) {
        LambdaQueryWrapper<SysExtendField> wrapper = Wrappers.<SysExtendField>lambdaQuery()
                .eq(SysExtendField::getBusinessModule, businessModule)
                .eq(SysExtendField::getStatus, 1)
                .orderByAsc(SysExtendField::getSortOrder);
        return list(wrapper).stream().map(this::convertToVO).collect(Collectors.toList());
    }

    @Override
    public IPage<ExtendFieldVO> pageFields(IPage<SysExtendField> page, String businessModule) {
        LambdaQueryWrapper<SysExtendField> wrapper = Wrappers.<SysExtendField>lambdaQuery();
        if (StringUtils.hasText(businessModule)) {
            wrapper.eq(SysExtendField::getBusinessModule, businessModule);
        }
        wrapper.orderByAsc(SysExtendField::getSortOrder);
        return page(page, wrapper).convert(this::convertToVO);
    }

    private ExtendFieldVO convertToVO(SysExtendField field) {
        ExtendFieldVO vo = new ExtendFieldVO();
        BeanUtils.copyProperties(field, vo);
        return vo;
    }
}
