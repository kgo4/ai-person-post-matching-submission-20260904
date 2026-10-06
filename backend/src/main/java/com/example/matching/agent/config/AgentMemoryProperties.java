package com.example.matching.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent 记忆配置属性
 * <p>
 * 全局总开关：关闭后人员能力提取 Agent 不再检索/应用任何 HR 修正累积的记忆；
 * 逐条记忆仍可通过 enable/disable 单独控制。
 *
 * @author system
 */
@Data
@ConfigurationProperties(prefix = "app.agent-memory")
public class AgentMemoryProperties {
    /** 是否启用 Agent 记忆（人员能力提取偏好），默认开启 */
    private boolean enabled = true;
}
