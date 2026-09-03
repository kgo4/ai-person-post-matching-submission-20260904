package com.example.matching.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LangChain4j Agent 配置属性
 *
 * @author system
 */
@Data
@ConfigurationProperties(prefix = "langchain4j.agents")
public class LangChain4jAgentProperties {
    /** 是否启用LangChain4j Agent */
    private boolean enabled = false;

    /** API基础URL */
    private String baseUrl;

    /** API密钥 */
    private String apiKey;

    /** 模型名称 */
    private String modelName = "deepseek-chat";

    /** 温度参数（结构化提取类任务建议接近 0，保证输出确定性） */
    private double temperature = 0.1d;

    /** 超时时间(秒) */
    private long timeoutSeconds = 300L;

    /** SDK transport retry count. Service-level resilience owns business retries. */
    private int maxRetries = 1;

    /** Allow the provider to fetch independent agent tools concurrently. */
    private boolean parallelToolCalls = true;

    /** Maximum number of database-backed tool calls for a single agent invocation. */
    private int maxToolCallsPerRequest = 20;

    /** Total time available for all tool calls in a single agent invocation. */
    private long toolExecutionTimeoutSeconds = 300L;

    /** 是否记录请求日志 */
    private boolean logRequests = false;

    /** 是否记录响应日志 */
    private boolean logResponses = false;
}
