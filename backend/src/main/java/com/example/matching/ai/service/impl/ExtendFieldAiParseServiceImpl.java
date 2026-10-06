package com.example.matching.ai.service.impl;

import com.example.matching.ai.service.ExtendFieldAiParseService;
import com.example.matching.ai.service.LangChain4jChatService;
import com.example.matching.ai.service.PromptTemplateService;
import com.example.matching.resilience.AiServiceResilience;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义扩展字段AI解析服务实现
 * <p>
 * 使用大模型解析非结构化的自定义字段内容（如简历、项目描述），
 * 提取结构化的能力标签和等级信息。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExtendFieldAiParseServiceImpl implements ExtendFieldAiParseService {

    private final LangChain4jChatService langChain4jChatService;
    private final PromptTemplateService promptTemplateService;
    private final AiServiceResilience aiServiceResilience;
    private final ObjectMapper objectMapper;
    private final com.example.matching.infrastructure.llm.LlmResponseParser llmResponseParser;

    @Override
    public Map<String, Object> parseExtendFields(String businessModule, String rawContent) {
        if (rawContent == null || rawContent.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> dataModel = new HashMap<>();
            dataModel.put("businessModule", businessModule);
            dataModel.put("rawContent", rawContent);

            String prompt = promptTemplateService.render("extend-field-parse-prompt", dataModel);
            if (prompt == null || prompt.isBlank()) {
                log.warn("extend-field-parse prompt 渲染失败，返回空结果");
                return Collections.emptyMap();
            }

            String aiResponse = langChain4jChatService.chat("extend-field-parse", prompt, () -> "{}");

            if (aiResponse == null || aiResponse.isBlank()) {
                aiResponse = "{}";
            }
            String json = llmResponseParser.extractJson(aiResponse);
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("AI解析扩展字段失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Integer> extractAbilitiesFromResume(String resumeText) {
        if (resumeText == null || resumeText.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> parsed = parseExtendFields("EMPLOYEE", resumeText);
            List<Map<String, Object>> abilities = (List<Map<String, Object>>) parsed.get("abilities");
            if (abilities == null) {
                return Collections.emptyMap();
            }

            Map<String, Integer> result = new HashMap<>();
            for (Map<String, Object> ability : abilities) {
                String tagName = (String) ability.get("tagName");
                Integer level = (Integer) ability.get("masteryLevel");
                if (tagName != null && level != null) {
                    result.put(tagName, level);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("从简历提取能力失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}
