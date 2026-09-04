package com.example.matching.service.system.impl;

import com.example.matching.dto.system.AbilityTagSaveDTO;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.event.PostAbilityTagGovernanceRequestedEvent;
import com.example.matching.service.system.AbilityTagService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostAbilityTagGovernanceServiceImplTest {

    @Test
    void ignoresBlankInputWithoutTouchingTagLibrary() {
        AbilityTagService tagService = mock(AbilityTagService.class);
        PostAbilityTagGovernanceServiceImpl service = new PostAbilityTagGovernanceServiceImpl(tagService);

        service.govern(null);
        service.govern(event("  ", null));

        verify(tagService, never()).findByName(any());
        verify(tagService, never()).saveTag(any());
    }

    @Test
    void reusesExistingNameOrAliasWithoutCreatingDuplicateTag() {
        AbilityTagService tagService = mock(AbilityTagService.class);
        AbilityTag existing = new AbilityTag();
        existing.setId(7L);
        when(tagService.findByName("Java")).thenReturn(existing);
        PostAbilityTagGovernanceServiceImpl service = new PostAbilityTagGovernanceServiceImpl(tagService);

        service.govern(event(" Java ", "TECHNICAL"));

        verify(tagService).findByName("Java");
        verify(tagService, never()).findByAlias(any());
        verify(tagService, never()).saveTag(any());
    }

    @Test
    void createsFlatTechnicalTagWhenNoNameOrAliasExists() {
        AbilityTagService tagService = mock(AbilityTagService.class);
        PostAbilityTagGovernanceServiceImpl service = new PostAbilityTagGovernanceServiceImpl(tagService);

        service.govern(event("Kafka", null));

        ArgumentCaptor<AbilityTagSaveDTO> captor = ArgumentCaptor.forClass(AbilityTagSaveDTO.class);
        verify(tagService).saveTag(captor.capture());
        AbilityTagSaveDTO created = captor.getValue();
        assertThatCode(() -> {
            org.assertj.core.api.Assertions.assertThat(created.getTagName()).isEqualTo("Kafka");
            org.assertj.core.api.Assertions.assertThat(created.getTagCategory()).isEqualTo("TECHNICAL");
            org.assertj.core.api.Assertions.assertThat(created.getParentId()).isEqualTo(0L);
            org.assertj.core.api.Assertions.assertThat(created.getTagLevel()).isZero();
        }).doesNotThrowAnyException();
    }

    @Test
    void tagLibraryFailureIsAbsorbedSoPostFlowCannotBeBlocked() {
        AbilityTagService tagService = mock(AbilityTagService.class);
        when(tagService.saveTag(any())).thenThrow(new IllegalStateException("tag library unavailable"));
        PostAbilityTagGovernanceServiceImpl service = new PostAbilityTagGovernanceServiceImpl(tagService);

        assertThatCode(() -> service.govern(event("Redis", "DATA"))).doesNotThrowAnyException();
        verify(tagService).saveTag(any());
    }

    private PostAbilityTagGovernanceRequestedEvent event(String abilityName, String category) {
        return new PostAbilityTagGovernanceRequestedEvent(1L, abilityName, category, "JD_IMPORT", 2L,
                "evidence", "test");
    }
}
