package com.example.matching.ai.service;

import com.example.matching.config.PromptAbConfig;
import com.example.matching.entity.system.PromptInvocationLog;
import com.example.matching.utils.SecurityUtils;
import freemarker.template.Configuration;
import freemarker.template.Template;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 统一 Prompt 模板服务 —— A/B 实验 + 调用了埋点 + 热重载。
 */
@Slf4j
@Service
public class PromptTemplateService {

    private final Configuration freemarkerConfig;
    private final ResourceLoader resourceLoader;
    private final PromptMetadataResolver metadataResolver;

    @Autowired
    private PromptAbConfig abConfig;

    @Autowired
    private PromptInvocationLogger invocationLogger;

    @Value("${ai.json.contract.enabled:true}")
    private boolean jsonContractEnabled;

    private volatile String jsonContractSnippet;

    public PromptTemplateService(Configuration freemarkerConfig, ResourceLoader resourceLoader,
                                  PromptMetadataResolver metadataResolver) {
        this.freemarkerConfig = freemarkerConfig;
        this.resourceLoader = resourceLoader;
        this.metadataResolver = metadataResolver;
    }

    /**
     * 加载并渲染 FTL 模板（含 A/B 路由和埋点）
     */
    public String render(String templateName, Map<String, Object> dataModel) {
        long start = System.currentTimeMillis();
        String actualFile = templateName;
        String version = "unknown";
        boolean fallback = false;
        boolean success = false;
        int outputChars = 0;

        try {
            // A/B 路由
            PromptAbConfig.Experiment exp = abConfig.findExperiment(templateName);
            if (exp != null) {
                Long userId = SecurityUtils.getCurrentUserId();
                int verIdx = exp.selectVersion(userId);
                if (verIdx > 0) {
                    actualFile = exp.getVersionFileName(verIdx);
                    log.debug("A/B 路由: {} -> {} (userId={}, version={})", templateName, actualFile, userId, exp.getVersions().get(verIdx));
                }
            }

            // 读版本号
            Template template = freemarkerConfig.getTemplate(actualFile + ".ftl");
            version = readVersion(actualFile);

            StringWriter writer = new StringWriter();
            template.process(dataModel, writer);
            String result = writer.toString();
            // 第 1 层：统一 JSON 输出契约段 + few-shot 标准件（可在配置 ai.json.contract.enabled=false 关闭）
            if (jsonContractEnabled) {
                result = result + "\n\n" + jsonContract() + "\n\n"
                        + com.example.matching.agent.json.JsonFewShotRegistry.forScene(extractScenario(templateName));
            }
            outputChars = result.length();
            success = true;
            return result;
        } catch (Exception e) {
            log.error("渲染 Prompt 模板失败: {}", templateName, e);
            fallback = true;
            return "";
        } finally {
            long latency = System.currentTimeMillis() - start;
            try {
                PromptInvocationLog entry = invocationLogger.buildEntry(
                        templateName, version, extractScenario(templateName),
                        success, fallback, latency,
                        dataModel != null ? dataModel.toString().length() : 0, outputChars);
                invocationLogger.logInvocation(entry);
            } catch (Exception ex) {
                log.warn("埋点写入异常: {}", ex.getMessage());
            }
        }
    }

    public String loadRaw(String templateName) {
        try {
            Template template = freemarkerConfig.getTemplate(templateName + ".ftl");
            return template.toString();
        } catch (Exception e) {
            log.error("加载 Prompt 模板原始文本失败: {}", templateName, e);
            return "";
        }
    }

    private String jsonContract() {
        if (!jsonContractEnabled) {
            return "";
        }
        if (jsonContractSnippet == null) {
            try {
                Resource resource =
                        resourceLoader.getResource("classpath:templates/json_contract.ftl");
                jsonContractSnippet = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                log.warn("加载 json_contract.ftl 失败，契约段跳过: {}", e.getMessage());
                jsonContractSnippet = "";
            }
        }
        return jsonContractSnippet;
    }

    private String readVersion(String templateName) {
        try {
            PromptMetadataResolver.PromptMetadata metadata = metadataResolver.resolve(templateName + ".ftl");
            return metadata.version();
        } catch (Exception e) {
            log.warn("无法解析 Prompt 版本: templateName={}, error={}", templateName, e.getMessage());
            return "unknown";
        }
    }

    private String extractScenario(String name) {
        if (name.contains("matching") && !name.contains("overview")) return "MATCHING";
        if (name.contains("gap")) return "GAP_DIAGNOSIS";
        if (name.contains("learning")) return "LEARNING";
        if (name.contains("interview")) return "INTERVIEW";
        if (name.contains("excel")) return "EXCEL_IMPORT";
        if (name.contains("extend")) return "EXTEND_FIELD";
        if (name.contains("ai-test")) return "AI_TEST";
        if (name.contains("overview")) return "REPORT";
        if (name.contains("job-summary")) return "JD_EXTRACT";
        return "GENERAL";
    }
}