package com.example.matching.application.system;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.dto.system.RoleSaveDTO;
import com.example.matching.dto.system.api.RoleCreateRequest;
import com.example.matching.dto.system.api.RoleResponse;
import com.example.matching.entity.system.SysRole;
import com.example.matching.service.system.SysRoleService;
import com.example.matching.vo.system.RoleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleApiFacade {

    private final SysRoleService sysRoleService;

    public PageResponse<RoleVO> page(long current, long size, String keyword) {
        IPage<RoleVO> page = sysRoleService.pageRoles(new Page<>(current, size), keyword);
        return new PageResponse<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }

    public List<RoleVO> listEnabled() {
        return sysRoleService.listEnabled();
    }

    public RoleResponse get(Long id) {
        SysRole entity = sysRoleService.getById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND);
        }
        return toResponse(entity);
    }

    public void create(RoleCreateRequest request) {
        RoleSaveDTO dto = new RoleSaveDTO();
        dto.setRoleCode(request.roleCode());
        dto.setRoleName(request.roleName());
        dto.setDescription(request.description());
        dto.setDataScope(request.dataScope());
        dto.setStatus(request.status());
        sysRoleService.saveRole(dto);
    }

    public void update(Long id, RoleCreateRequest request) {
        RoleSaveDTO dto = new RoleSaveDTO();
        dto.setId(id);
        dto.setRoleCode(request.roleCode());
        dto.setRoleName(request.roleName());
        dto.setDescription(request.description());
        dto.setDataScope(request.dataScope());
        dto.setStatus(request.status());
        sysRoleService.saveRole(dto);
    }

    public void delete(Long id) {
        sysRoleService.removeById(id);
    }

    public void assignRoles(Long userId, List<Long> roleIds) {
        sysRoleService.assignRolesToUser(userId, roleIds);
    }

    public List<Long> getUserRoles(Long userId) {
        return sysRoleService.getUserRoleIds(userId);
    }

    private RoleResponse toResponse(SysRole entity) {
        return new RoleResponse(
            entity.getId(), entity.getRoleCode(), entity.getRoleName(),
            entity.getDescription(), entity.getDataScope(), entity.getStatus(),
            entity.getCreatedTime(), entity.getUpdatedTime()
        );
    }
}
