package com.example.matching.service.post.impl;

import com.example.matching.dto.post.PostTemplateSaveDTO;
import com.example.matching.entity.post.PostModelTemplate;
import com.example.matching.mapper.post.PostModelTemplateMapper;
import com.example.matching.service.common.BusinessCodeGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PostModelTemplateServiceImplTest {

    @Test
    void creates_template_with_generated_code_when_request_code_is_blank() {
        PostModelTemplateMapper mapper = mock(PostModelTemplateMapper.class);
        PostModelTemplateServiceImpl service = new PostModelTemplateServiceImpl(new BusinessCodeGenerator());
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        PostTemplateSaveDTO dto = new PostTemplateSaveDTO();
        dto.setTemplateCode("");
        dto.setTemplateName("Engineering baseline");
        dto.setPostSequence("TECHNICAL");

        service.saveTemplate(dto);

        ArgumentCaptor<PostModelTemplate> captor = ArgumentCaptor.forClass(PostModelTemplate.class);
        verify(mapper).insertOrUpdate(captor.capture());
        assertThat(captor.getValue().getTemplateCode()).startsWith("TPL_");
    }

    @Test
    void preserves_supplied_template_code_for_external_creation() {
        PostModelTemplateMapper mapper = mock(PostModelTemplateMapper.class);
        PostModelTemplateServiceImpl service = new PostModelTemplateServiceImpl(new BusinessCodeGenerator());
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        PostTemplateSaveDTO dto = new PostTemplateSaveDTO();
        dto.setTemplateCode("EXT-TEMPLATE-001");
        dto.setTemplateName("Engineering baseline");
        dto.setPostSequence("TECHNICAL");

        service.saveTemplate(dto);

        ArgumentCaptor<PostModelTemplate> captor = ArgumentCaptor.forClass(PostModelTemplate.class);
        verify(mapper).insertOrUpdate(captor.capture());
        assertThat(captor.getValue().getTemplateCode()).isEqualTo("EXT-TEMPLATE-001");
    }
}
