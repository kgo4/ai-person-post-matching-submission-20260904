package com.example.matching.agent.lc4j;

import com.example.matching.agent.dto.PostTrendAiResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 岗位趋势抽取 Agent。
 * <p>
 * 输入是「编号材料片段 + 材料元信息」拼成的上下文，输出是结构化的岗位与能力清单。
 * 未启用 LangChain4j 时本接口无实现 Bean，调用方必须用 {@code ObjectProvider} 取，
 * 并在拿不到实例时给出可读诊断，而不是静默产出空候选。
 */
public interface PostTrendAiService {

    @SystemMessage(fromResource = "ai/prompt/post-trend-system.txt")
    @UserMessage("""
        以下是权威材料片段（编号即 evidenceRef 的取值来源）：

        {{context}}

        Extract the emerging/changing posts from these fragments.
        Follow the system output schema exactly.
        """)
    PostTrendAiResult analyze(@V("context") String context);
}
