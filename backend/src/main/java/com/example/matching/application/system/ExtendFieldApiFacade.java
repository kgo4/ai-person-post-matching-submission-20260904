package com.example.matching.application.system;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.dto.system.ExtendFieldConfigDTO;
import com.example.matching.dto.system.api.ExtendFieldRequest;
import com.example.matching.dto.system.api.ExtendFieldResponse;
import com.example.matching.entity.system.SysExtendField;
import com.example.matching.service.system.SysExtendFieldService;
import com.example.matching.vo.system.ExtendFieldVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ExtendFieldApiFacade {

    private final SysExtendFieldService sysExtendFieldService;

    public List<ExtendFieldVO> listByModule(String businessModule) {
        return sysExtendFieldService.listByModule(businessModule);
    }

    public PageResponse<ExtendFieldVO> page(long current, long size, String businessModule) {
        IPage<ExtendFieldVO> page = sysExtendFieldService.pageFields(new Page<>(current, size), businessModule);
        return new PageResponse<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }

    public ExtendFieldResponse get(Long id) {
        SysExtendField entity = sysExtendFieldService.getById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND);
        }
        return toResponse(entity);
    }

    public void create(ExtendFieldRequest request) {
        ExtendFieldConfigDTO dto = new ExtendFieldConfigDTO();
        dto.setBusinessModule(request.businessModule());
        dto.setFieldName(request.fieldName());
        dto.setFieldLabel(request.fieldLabel());
        dto.setFieldType(request.fieldType());
        dto.setSelectOptions(request.selectOptions());
        dto.setIsRequired(request.isRequired());
        dto.setSortOrder(request.sortOrder());
        dto.setStatus(request.status());
        sysExtendFieldService.saveField(dto);
    }

    public void update(Long id, ExtendFieldRequest request) {
        ExtendFieldConfigDTO dto = new ExtendFieldConfigDTO();
        dto.setId(id);
        dto.setBusinessModule(request.businessModule());
        dto.setFieldName(request.fieldName());
        dto.setFieldLabel(request.fieldLabel());
        dto.setFieldType(request.fieldType());
        dto.setSelectOptions(request.selectOptions());
        dto.setIsRequired(request.isRequired());
        dto.setSortOrder(request.sortOrder());
        dto.setStatus(request.status());
        sysExtendFieldService.saveField(dto);
    }

    public void delete(Long id) {
        sysExtendFieldService.removeById(id);
    }

    private ExtendFieldResponse toResponse(SysExtendField entity) {
        return new ExtendFieldResponse(
            entity.getId(), entity.getBusinessModule(), entity.getFieldName(),
            entity.getFieldLabel(), entity.getFieldType(), entity.getSelectOptions(),
            entity.getIsRequired(), entity.getSortOrder(), entity.getStatus(),
            entity.getCreatedTime(), entity.getUpdatedTime()
        );
    }
}
