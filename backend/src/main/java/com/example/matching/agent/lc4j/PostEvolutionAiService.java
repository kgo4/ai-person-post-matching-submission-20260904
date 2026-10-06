package com.example.matching.agent.lc4j;

import com.example.matching.agent.dto.PostEvolutionAiResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 岗位演化 AI Agent（LangChain4j AiService）
 * <p>
 * 输入：岗位名称/行业/业务域 + 当前岗位能力状态 + 检索证据片段列表（行业/内部/市场/知乎）。
 * 输出：结构化变更建议（能力名 + 动作 + 新值 + 理由 + 证据引用编号）。
 * 能力来源与防幻觉由服务端在调用后校验，本接口只负责结构化生成。
 */
public interface PostEvolutionAiService {

    @SystemMessage(fromResource = "ai/prompt/post-evolution-system.txt")
    @UserMessage("""
        基于以下岗位演化分析上下文，输出能力变更建议 JSON：

        {{context}}

        Follow the system output schema exactly.
        """)
    PostEvolutionAiResult analyze(@V("context") String context);
}
