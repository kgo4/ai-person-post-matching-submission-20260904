package com.example.matching.controller.system;

import com.example.matching.application.system.ExtendFieldApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.system.api.ExtendFieldRequest;
import com.example.matching.dto.system.api.ExtendFieldResponse;
import com.example.matching.vo.system.ExtendFieldVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SysExtendFieldControllerTest {

    private static ExtendFieldVO createVO(Long id, String fieldName) {
        ExtendFieldVO vo = new ExtendFieldVO();
        vo.setId(id);
        vo.setBusinessModule("EMPLOYEE");
        vo.setFieldName(fieldName);
        vo.setFieldLabel("测试字段");
        vo.setFieldType("TEXT");
        vo.setIsRequired(1);
        vo.setSortOrder(1);
        vo.setStatus(1);
        return vo;
    }

    private static ExtendFieldRequest createRequest(String fieldName) {
        return new ExtendFieldRequest(
                "EMPLOYEE", fieldName, "测试字段", "TEXT", null, 1, 1, 1);
    }

    private static ExtendFieldResponse createResponse(Long id, String fieldName) {
        return new ExtendFieldResponse(
                id, "EMPLOYEE", fieldName, "测试字段", "TEXT", null,
                1, 1, 1, null, null);
    }

    @Test
    void listByModuleReturnsFields() {
        ExtendFieldApiFacade facade = mock(ExtendFieldApiFacade.class);
        SysExtendFieldController controller = new SysExtendFieldController(facade);

        ExtendFieldVO vo = createVO(1L, "customField");
        when(facade.listByModule("EMPLOYEE")).thenReturn(List.of(vo));

        R<List<ExtendFieldVO>> response = controller.listByModule("EMPLOYEE");

        assertThat(response.getData()).containsExactly(vo);
    }

    @Test
    void pageReturnsFieldPage() {
        ExtendFieldApiFacade facade = mock(ExtendFieldApiFacade.class);
        SysExtendFieldController controller = new SysExtendFieldController(facade);

        ExtendFieldVO vo = createVO(1L, "customField");
        PageResponse<ExtendFieldVO> page = new PageResponse<>(List.of(vo), 1, 1, 10, 1);
        when(facade.page(1, 10, "EMPLOYEE")).thenReturn(page);

        R<PageResponse<ExtendFieldVO>> response = controller.page(1, 10, "EMPLOYEE");

        assertThat(response.getData().records()).containsExactly(vo);
    }

    @Test
    void getByIdReturnsFieldDetail() {
        ExtendFieldApiFacade facade = mock(ExtendFieldApiFacade.class);
        SysExtendFieldController controller = new SysExtendFieldController(facade);

        ExtendFieldResponse field = createResponse(1L, "customField");
        when(facade.get(1L)).thenReturn(field);

        R<ExtendFieldResponse> response = controller.getById(1L);

        assertThat(response.getData()).isEqualTo(field);
    }

    @Test
    void saveCreatesField() {
        ExtendFieldApiFacade facade = mock(ExtendFieldApiFacade.class);
        SysExtendFieldController controller = new SysExtendFieldController(facade);

        ExtendFieldRequest request = createRequest("customField");

        R<Void> response = controller.save(request);

        assertThat(response.getCode()).isEqualTo(200);
        verify(facade).create(request);
    }

    @Test
    void updateUpdatesField() {
        ExtendFieldApiFacade facade = mock(ExtendFieldApiFacade.class);
        SysExtendFieldController controller = new SysExtendFieldController(facade);

        ExtendFieldRequest request = createRequest("customField");

        R<Void> response = controller.update(1L, request);

        assertThat(response.getCode()).isEqualTo(200);
        verify(facade).update(1L, request);
    }

    @Test
    void deleteDeletesField() {
        ExtendFieldApiFacade facade = mock(ExtendFieldApiFacade.class);
        SysExtendFieldController controller = new SysExtendFieldController(facade);

        R<Void> response = controller.delete(1L);

        assertThat(response.getCode()).isEqualTo(200);
        verify(facade).delete(1L);
    }
}
