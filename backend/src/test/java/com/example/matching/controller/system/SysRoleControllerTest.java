package com.example.matching.controller.system;

import com.example.matching.application.system.RoleApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.system.api.RoleCreateRequest;
import com.example.matching.dto.system.api.RoleResponse;
import com.example.matching.vo.system.RoleVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SysRoleControllerTest {

    private static RoleVO createRoleVO(Long id, String roleCode) {
        RoleVO vo = new RoleVO();
        vo.setId(id);
        vo.setRoleCode(roleCode);
        vo.setRoleName("管理员");
        vo.setDescription("系统管理");
        vo.setDataScope(1);
        vo.setStatus(1);
        return vo;
    }

    private static RoleCreateRequest createRequest() {
        return new RoleCreateRequest("ADMIN", "管理员", "系统管理", 1, 1);
    }

    private static RoleResponse createResponse(Long id, String roleCode) {
        return new RoleResponse(id, roleCode, "管理员", "系统管理", 1, 1, null, null);
    }

    @Test
    void pageReturnsRolePage() {
        RoleApiFacade facade = mock(RoleApiFacade.class);
        SysRoleController controller = new SysRoleController(facade);

        RoleVO vo = createRoleVO(1L, "ADMIN");
        PageResponse<RoleVO> page = new PageResponse<>(List.of(vo), 1, 1, 10, 1);
        when(facade.page(1, 10, "admin")).thenReturn(page);

        R<PageResponse<RoleVO>> response = controller.page(1, 10, "admin");

        assertThat(response.getData().records()).containsExactly(vo);
    }

    @Test
    void listEnabledReturnsEnabledRoles() {
        RoleApiFacade facade = mock(RoleApiFacade.class);
        SysRoleController controller = new SysRoleController(facade);

        RoleVO vo = createRoleVO(1L, "ADMIN");
        when(facade.listEnabled()).thenReturn(List.of(vo));

        R<List<RoleVO>> response = controller.listEnabled();

        assertThat(response.getData()).containsExactly(vo);
    }

    @Test
    void getByIdReturnsRoleDetail() {
        RoleApiFacade facade = mock(RoleApiFacade.class);
        SysRoleController controller = new SysRoleController(facade);

        RoleResponse role = createResponse(1L, "ADMIN");
        when(facade.get(1L)).thenReturn(role);

        R<RoleResponse> response = controller.getById(1L);

        assertThat(response.getData()).isEqualTo(role);
    }

    @Test
    void saveCreatesRole() {
        RoleApiFacade facade = mock(RoleApiFacade.class);
        SysRoleController controller = new SysRoleController(facade);

        RoleCreateRequest request = createRequest();

        R<Void> response = controller.save(request);

        assertThat(response.getCode()).isEqualTo(200);
        verify(facade).create(request);
    }

    @Test
    void updateUpdatesRole() {
        RoleApiFacade facade = mock(RoleApiFacade.class);
        SysRoleController controller = new SysRoleController(facade);

        RoleCreateRequest request = createRequest();

        R<Void> response = controller.update(1L, request);

        assertThat(response.getCode()).isEqualTo(200);
        verify(facade).update(1L, request);
    }

    @Test
    void deleteDeletesRole() {
        RoleApiFacade facade = mock(RoleApiFacade.class);
        SysRoleController controller = new SysRoleController(facade);

        R<Void> response = controller.delete(1L);

        assertThat(response.getCode()).isEqualTo(200);
        verify(facade).delete(1L);
    }

    @Test
    void assignRolesAssignsRolesToUser() {
        RoleApiFacade facade = mock(RoleApiFacade.class);
        SysRoleController controller = new SysRoleController(facade);

        List<Long> roleIds = List.of(2L, 3L);

        R<Void> response = controller.assignRoles(1L, roleIds);

        assertThat(response.getCode()).isEqualTo(200);
        verify(facade).assignRoles(1L, roleIds);
    }

    @Test
    void getUserRolesReturnsRoleIds() {
        RoleApiFacade facade = mock(RoleApiFacade.class);
        SysRoleController controller = new SysRoleController(facade);

        when(facade.getUserRoles(1L)).thenReturn(List.of(2L, 3L));

        R<List<Long>> response = controller.getUserRoles(1L);

        assertThat(response.getData()).containsExactly(2L, 3L);
    }
}
