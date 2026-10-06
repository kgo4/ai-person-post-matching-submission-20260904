package com.example.matching.ai.service;

import java.util.Map;

/**
 * 自定义扩展字段AI解析服务
 */
public interface ExtendFieldAiParseService {

    /**
     * 解析扩展字段内容
     *
     * @param businessModule 业务模块
     * @param rawContent     原始内容
     * @return 解析后的结构化数据
     */
    Map<String, Object> parseExtendFields(String businessModule, String rawContent);

    /**
     * 从非结构化简历中提取能力信息
     *
     * @param resumeText 简历文本
     * @return 提取的能力标签映射
     */
    Map<String, Integer> extractAbilitiesFromResume(String resumeText);
}
