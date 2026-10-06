package com.example.matching.application.system;

import com.example.matching.dto.system.AbilityTagSaveDTO;
import com.example.matching.dto.system.api.AbilityTagCreateRequest;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.service.common.BusinessCodeGenerator;
import com.example.matching.service.system.AbilityTagService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AbilityTagApiFacadeTest {

    @Test
    void creates_tag_with_generated_code_when_request_code_is_blank() {
        AbilityTagService tagService = mock(AbilityTagService.class);
        when(tagService.saveTag(any())).thenReturn(7L);
        AbilityTag saved = new AbilityTag();
        saved.setId(7L);
        saved.setTagCode("TAG_saved");
        when(tagService.getById(7L)).thenReturn(saved);
        AbilityTagApiFacade facade = new AbilityTagApiFacade(tagService, new BusinessCodeGenerator());

        facade.create(new AbilityTagCreateRequest("", "Java", 0L, "TECHNICAL", 1, null, 1, 1));

        ArgumentCaptor<AbilityTagSaveDTO> captor = ArgumentCaptor.forClass(AbilityTagSaveDTO.class);
        verify(tagService).saveTag(captor.capture());
        assertThat(captor.getValue().getTagCode()).startsWith("TAG_");
    }

    @Test
    void preserves_supplied_tag_code_for_external_creation() {
        AbilityTagService tagService = mock(AbilityTagService.class);
        when(tagService.saveTag(any())).thenReturn(7L);
        AbilityTag saved = new AbilityTag();
        saved.setId(7L);
        saved.setTagCode("EXT-TAG-001");
        when(tagService.getById(7L)).thenReturn(saved);
        AbilityTagApiFacade facade = new AbilityTagApiFacade(tagService, new BusinessCodeGenerator());

        facade.create(new AbilityTagCreateRequest("EXT-TAG-001", "Java", 0L, "TECHNICAL", 1, null, 1, 1));

        ArgumentCaptor<AbilityTagSaveDTO> captor = ArgumentCaptor.forClass(AbilityTagSaveDTO.class);
        verify(tagService).saveTag(captor.capture());
        assertThat(captor.getValue().getTagCode()).isEqualTo("EXT-TAG-001");
    }
}
