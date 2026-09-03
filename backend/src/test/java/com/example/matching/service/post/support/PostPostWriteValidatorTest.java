package com.example.matching.service.post.support;

import com.example.matching.entity.post.PostPost;
import com.example.matching.mapper.post.PostPostMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

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
}
