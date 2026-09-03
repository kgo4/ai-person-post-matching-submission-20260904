package com.example.matching.controller.system;

import com.example.matching.application.system.AbilityTagApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.system.api.AbilityTagCreateRequest;
import com.example.matching.dto.system.api.AbilityTagResponse;
import com.example.matching.vo.system.AbilityTagTreeVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AbilityTagControllerTest {

    private static AbilityTagTreeVO createTree(Long id, String name) {
        AbilityTagTreeVO vo = new AbilityTagTreeVO();
        vo.setId(id);
        vo.setTagCode("TAG_00" + id);
        vo.setTagName(name);
        vo.setTagCategory("TECHNICAL");
        vo.setTagLevel(1);
        vo.setChildren(List.of());
        return vo;
    }

    private static AbilityTagResponse createResponse(Long id, String code, String name) {
        return new AbilityTagResponse(
                id, code, name, null, "TECHNICAL", "java", 1,
                "描述", 1, 1, "MANUAL", 1, null, null);
    }

    @Test
    void treeReturnsTagTree() {
        AbilityTagApiFacade facade = mock(AbilityTagApiFacade.class);
        AbilityTagController controller = new AbilityTagController(facade);

        AbilityTagTreeVO root = createTree(1L, "Java");
        when(facade.getTree()).thenReturn(List.of(root));

        R<List<AbilityTagTreeVO>> response = controller.tree();

        assertThat(response.getData()).containsExactly(root);
    }

    @Test
    void treeByCategoryReturnsCategoryTree() {
        AbilityTagApiFacade facade = mock(AbilityTagApiFacade.class);
        AbilityTagController controller = new AbilityTagController(facade);

        AbilityTagTreeVO node = createTree(2L, "Kubernetes");
        when(facade.getByCategory("TECHNICAL")).thenReturn(List.of(node));

        R<List<AbilityTagTreeVO>> response = controller.treeByCategory("TECHNICAL");

        assertThat(response.getData()).containsExactly(node);
    }

    @Test
    void pageReturnsTagPage() {
        AbilityTagApiFacade facade = mock(AbilityTagApiFacade.class);
        AbilityTagController controller = new AbilityTagController(facade);

        AbilityTagResponse tag = createResponse(1L, "TAG_001", "Java并发");
        PageResponse<AbilityTagResponse> page = new PageResponse<>(List.of(tag), 1, 1, 20, 1);
        when(facade.page(1, 20, "Java", "TECHNICAL")).thenReturn(page);

        R<PageResponse<AbilityTagResponse>> response = controller.page(1, 20, "Java", "TECHNICAL");

        assertThat(response.getData().records()).containsExactly(tag);
    }

    @Test
    void getByIdReturnsTagDetail() {
        AbilityTagApiFacade facade = mock(AbilityTagApiFacade.class);
        AbilityTagController controller = new AbilityTagController(facade);

        AbilityTagResponse tag = createResponse(1L, "TAG_001", "Java并发");
        when(facade.get(1L)).thenReturn(tag);

        R<AbilityTagResponse> response = controller.getById(1L);

        assertThat(response.getData()).isEqualTo(tag);
    }

    @Test
    void saveCreatesTag() {
        AbilityTagApiFacade facade = mock(AbilityTagApiFacade.class);
        AbilityTagController controller = new AbilityTagController(facade);

        AbilityTagCreateRequest request =
                new AbilityTagCreateRequest("TAG_001", "Java并发", null, "TECHNICAL", 1, "描述", 1, 1);
        AbilityTagResponse created = createResponse(100L, "TAG_001", "Java并发");
        when(facade.create(request)).thenReturn(created);

        R<AbilityTagResponse> response = controller.save(request);

        assertThat(response.getData().id()).isEqualTo(100L);
        verify(facade).create(request);
    }

    @Test
    void updateUpdatesTag() {
        AbilityTagApiFacade facade = mock(AbilityTagApiFacade.class);
        AbilityTagController controller = new AbilityTagController(facade);

        AbilityTagCreateRequest request =
                new AbilityTagCreateRequest("TAG_001", "Java并发", null, "TECHNICAL", 1, "描述", 1, 1);

        R<Void> response = controller.update(1L, request);

        assertThat(response.getCode()).isEqualTo(200);
        verify(facade).update(1L, request);
    }

    @Test
    void updateStatusUpdatesTagStatus() {
        AbilityTagApiFacade facade = mock(AbilityTagApiFacade.class);
        AbilityTagController controller = new AbilityTagController(facade);

        R<Void> response = controller.updateStatus(1L, 0);

        assertThat(response.getCode()).isEqualTo(200);
        verify(facade).updateStatus(1L, 0);
    }

    @Test
    void batchGenerateVectorsReturnsCount() {
        AbilityTagApiFacade facade = mock(AbilityTagApiFacade.class);
        AbilityTagController controller = new AbilityTagController(facade);

        when(facade.batchGenerateVectors()).thenReturn(3);

        R<Integer> response = controller.batchGenerateVectors();

        assertThat(response.getData()).isEqualTo(3);
    }

    @Test
    void deleteDeletesTag() {
        AbilityTagApiFacade facade = mock(AbilityTagApiFacade.class);
        AbilityTagController controller = new AbilityTagController(facade);

        R<Void> response = controller.delete(1L);

        assertThat(response.getCode()).isEqualTo(200);
        verify(facade).delete(1L);
    }
}
