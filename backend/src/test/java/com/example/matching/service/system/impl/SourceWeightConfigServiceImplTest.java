package com.example.matching.service.system.impl;

import com.example.matching.entity.system.SourceWeightConfig;
import com.example.matching.mapper.system.SourceWeightConfigMapper;
import com.example.matching.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SourceWeightConfigServiceImplTest {

    @Mock
    private SourceWeightConfigMapper mapper;

    private SourceWeightConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SourceWeightConfigServiceImpl(mapper);
    }

    @Test
    void aliasLookupUsesTheCanonicalConfiguration() {
        when(mapper.selectList(any())).thenReturn(List.of(config(1L, "AI_PROJECT", "0.70")));

        assertThat(service.getWeight("PMS")).isEqualByComparingTo("0.70");
    }

    @Test
    void partialUpdateUsesTheConfigIdWhenSourceTypeIsNotSubmitted() {
        SourceWeightConfig patch = new SourceWeightConfig();
        patch.setId(9L);
        patch.setWeight(new BigDecimal("0.66"));
        when(mapper.selectById(9L)).thenReturn(config(9L, "AI_TEST", "0.70"));
        when(mapper.selectList(any())).thenReturn(List.of(config(9L, "AI_TEST", "0.66")));

        service.batchUpdate(List.of(patch));

        ArgumentCaptor<SourceWeightConfig> captor = ArgumentCaptor.forClass(SourceWeightConfig.class);
        verify(mapper).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(9L);
        assertThat(captor.getValue().getWeight()).isEqualByComparingTo("0.66");
    }

    @Test
    void listExcludesDeprecatedSourcesFromTheAdminControl() {
        when(mapper.selectList(any())).thenReturn(List.of(
                config(1L, "RESUME_PARSE", "0.70"),
                config(2L, "AI_PROJECT", "0.70"),
                config(4L, "MANUAL", "1.00"),
                config(3L, "PROFILE_FUSED", "0.95")));

        assertThat(service.listAll()).extracting(SourceWeightConfig::getSourceType)
                .containsExactly("RESUME_PARSE");
    }

    @Test
    void rejectsUpdatingDeprecatedSourceById() {
        SourceWeightConfig patch = new SourceWeightConfig();
        patch.setId(9L);
        patch.setWeight(new BigDecimal("0.66"));
        when(mapper.selectById(9L)).thenReturn(config(9L, "PROFILE_FUSED", "0.70"));

        assertThatThrownBy(() -> service.batchUpdate(List.of(patch)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已废弃");
    }

    private SourceWeightConfig config(Long id, String sourceType, String weight) {
        SourceWeightConfig config = new SourceWeightConfig();
        config.setId(id);
        config.setSourceType(sourceType);
        config.setSourceLabel(sourceType);
        config.setWeight(new BigDecimal(weight));
        config.setIsActive(1);
        config.setSortOrder(1);
        return config;
    }
}
