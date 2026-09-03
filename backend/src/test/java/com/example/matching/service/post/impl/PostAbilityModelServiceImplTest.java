package com.example.matching.service.post.impl;

import com.example.matching.common.exception.BusinessException;
import com.example.matching.dto.post.PostAbilityModelConfigDTO;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.mapper.post.PostModelQualityMapper;
import com.example.matching.mapper.post.PostPostMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.service.ability.AbilityEvidenceIngestionService;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.post.PostPost;
import com.example.matching.vo.post.PostAbilityModelVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doReturn;

class PostAbilityModelServiceImplTest {

    @Test
    void getPostAbilityModel_returnsNameAndModelIdForUntaggedAbility() {
        PostPostMapper postPostMapper = mock(PostPostMapper.class);
        PostPost post = new PostPost();
        post.setId(1L);
        when(postPostMapper.selectById(1L)).thenReturn(post);

        PostAbilityModel untaggedAbility = new PostAbilityModel();
        untaggedAbility.setId(101L);
        untaggedAbility.setPostId(1L);
        untaggedAbility.setAbilityName("接口自动化测试");
        untaggedAbility.setMinRequiredLevel(3);
        untaggedAbility.setWeight(new BigDecimal("20"));
        untaggedAbility.setIsRequired(1);
        untaggedAbility.setIsCore(0);

        PostAbilityModelServiceImpl service = spy(service(postPostMapper));
        doReturn(List.of(untaggedAbility)).when(service).listByPostId(1L);

        PostAbilityModelVO.AbilityRequirementDetail detail = service.getPostAbilityModel(1L)
                .getAbilityRequirements().get(0);

        org.assertj.core.api.Assertions.assertThat(detail.getModelId()).isEqualTo(101L);
        org.assertj.core.api.Assertions.assertThat(detail.getTagId()).isNull();
        org.assertj.core.api.Assertions.assertThat(detail.getAbilityName()).isEqualTo("接口自动化测试");
        org.assertj.core.api.Assertions.assertThat(detail.getTagName()).isEqualTo("接口自动化测试");
    }

    @Test
    void batchConfig_rejectsNegativeWeightEvenWhenTotalIsValid() {
        PostAbilityModelServiceImpl service = service(mock(PostPostMapper.class));

        assertThatThrownBy(() -> service.batchConfig(List.of(
                config(1L, 10L, new BigDecimal("-50")),
                config(1L, 11L, new BigDecimal("150")))))
                .isInstanceOf(BusinessException.class);
    }

    private PostAbilityModelConfigDTO config(Long postId, Long tagId, BigDecimal weight) {
        PostAbilityModelConfigDTO dto = new PostAbilityModelConfigDTO();
        dto.setPostId(postId);
        dto.setTagId(tagId);
        dto.setWeight(weight);
        dto.setIsRequired(0);
        return dto;
    }

    private PostAbilityModelServiceImpl service(PostPostMapper postPostMapper) {
        return new PostAbilityModelServiceImpl(
                postPostMapper, mock(AbilityTagMapper.class),
                mock(PostModelQualityMapper.class), mock(ApplicationEventPublisher.class),
                new ObjectMapper(), mock(AbilityEvidenceIngestionService.class),
                mock(com.example.matching.service.common.VectorRecallCacheEpoch.class),
                new com.example.matching.converter.post.PostPostConverterImpl());
    }
}
