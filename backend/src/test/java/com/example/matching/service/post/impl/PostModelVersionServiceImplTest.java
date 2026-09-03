package com.example.matching.service.post.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.matching.entity.post.PostModelVersion;
import com.example.matching.mapper.post.PostModelVersionItemMapper;
import com.example.matching.mapper.post.PostModelVersionMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.service.post.PostAbilityModelService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostModelVersionServiceImplTest {

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                PostModelVersion.class);
    }

    @Mock private PostModelVersionMapper versionMapper;
    @Mock private PostModelVersionItemMapper versionItemMapper;
    @Mock private PostAbilityModelService postAbilityModelService;
    @Mock private AbilityTagMapper abilityTagMapper;

    private PostModelVersionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PostModelVersionServiceImpl(versionItemMapper, postAbilityModelService, abilityTagMapper);
        ReflectionTestUtils.setField(service, "baseMapper", versionMapper);
    }

    @Test
    void incrementStatisticsForBinding_updatesSummaryAtomicallyOnlyWhenEditable() {
        when(versionMapper.update(isNull(), any())).thenReturn(1);

        assertThat(service.incrementStatisticsForBinding(7L, new BigDecimal("12.50"))).isTrue();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<PostModelVersion>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(versionMapper).update(isNull(), wrapperCaptor.capture());

        String sqlSet = wrapperCaptor.getValue().getSqlSet();
        assertThat(sqlSet).contains("item_count = COALESCE(item_count, 0) + 1")
                .contains("total_weight = COALESCE(total_weight, 0) +")
                .contains("CASE WHEN status = 'REVIEW_REQUIRED' THEN 'DRAFT'");
        assertThat(wrapperCaptor.getValue().getExpression().getNormal().getSqlSegment())
                .contains("status IN");
    }
}
