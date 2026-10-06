package com.example.matching.ai.context.dto;

import lombok.Data;

import java.util.List;

/**
 * 来源引用校验请求DTO
 *
 * @author system
 */
@Data
public class ValidateSourceRefsDTO {

    /** 上下文hash */
    private String contextHash;

    /** 待校验的来源引用列表 */
    private List<String> sourceRefs;
}
