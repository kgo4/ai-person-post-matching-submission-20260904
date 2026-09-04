package com.example.matching.service.post.support;

import com.example.matching.entity.post.PostPost;
import com.example.matching.mapper.post.PostPostMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostPostWriteValidatorTest {
    @Test
    void rejectsNumericOnlyPostNames() {
        PostPostWriteValidator validator = new PostPostWriteValidator(mock(PostPostMapper.class));
        PostPost post = new PostPost();
        post.setPostCode("TEST-1");
        post.setPostName("30");
        assertThatThrownBy(() -> validator.validateBeforeSave(post))
                .hasMessageContaining("岗位名称不能使用序号或纯数字");
    }

    @Test
    void normalizesPostFieldsBeforeSaving() {
        PostPostMapper mapper = mock(PostPostMapper.class);
        when(mapper.selectOne(any())).thenReturn(null);
        PostPostWriteValidator validator = new PostPostWriteValidator(mapper);
        PostPost post = new PostPost();
        post.setPostCode("  java-ai-01 ");
        post.setPostName(" Java　 AI\n工程师 ");
        post.setJobDescription("line1\r\nline2\u0007");

        validator.validateBeforeSave(post);

        assertThat(post.getPostCode()).isEqualTo("JAVA-AI-01");
        assertThat(post.getPostName()).isEqualTo("Java AI 工程师");
        assertThat(post.getJobDescription()).isEqualTo("line1\nline2");
        verify(mapper).selectOne(any());
    }

    @Test
    void rejectsBlankNameAndCode() {
        PostPostWriteValidator validator = new PostPostWriteValidator(mock(PostPostMapper.class));
        PostPost blankName = new PostPost();
        blankName.setPostCode("A-1");
        blankName.setPostName(" ");
        assertThatThrownBy(() -> validator.validateBeforeSave(blankName))
                .hasMessageContaining("岗位名称不能为空");

        PostPost blankCode = new PostPost();
        blankCode.setPostName("Java Engineer");
        assertThatThrownBy(() -> validator.validateBeforeSave(blankCode))
                .hasMessageContaining("岗位编码不能为空");
    }

    @Test
    void rejectsNormalizedDuplicateCodeAndExcludesCurrentIdOnUpdate() {
        PostPostMapper mapper = mock(PostPostMapper.class);
        PostPostWriteValidator validator = new PostPostWriteValidator(mapper);
        PostPost duplicate = new PostPost();
        duplicate.setId(2L);
        when(mapper.selectOne(any())).thenReturn(duplicate);

        PostPost create = new PostPost();
        create.setPostCode("java-1");
        create.setPostName("Java Engineer");
        assertThatThrownBy(() -> validator.validateBeforeSave(create))
                .hasMessageContaining("岗位编码已存在: JAVA-1");

        PostPost update = new PostPost();
        update.setId(9L);
        update.setPostCode("java-2");
        update.setPostName("Java Engineer");
        assertThatThrownBy(() -> validator.validateBeforeUpdate(update))
                .hasMessageContaining("岗位编码已存在: JAVA-2");
    }
}
