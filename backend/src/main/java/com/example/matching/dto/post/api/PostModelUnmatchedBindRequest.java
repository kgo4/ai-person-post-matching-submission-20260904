package com.example.matching.dto.post.api;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 绑定未匹配能力到已有标签的请求
 */
@Data
@Schema(description = "绑定未匹配能力到已有标签请求")
public class PostModelUnmatchedBindRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "正式标签ID", required = true, example = "123")
    private Long tagId;
}
