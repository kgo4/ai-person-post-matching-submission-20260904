package com.example.matching.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 岗位趋势发现配置。
 * <p>
 * 三个阈值都是**经验值**，上线后需要用任务诊断数据回看误分率再校准（见设计文档 R1）：
 * <ul>
 *   <li>{@code similarityThreshold} —— 决定候选走进「新岗位」还是「既有岗位能力变更」；</li>
 *   <li>{@code minEmphasis} —— 决定材料里顺嘴提到的岗位要不要产出候选；</li>
 *   <li>{@code tagResolveSimilarity} —— 决定 AI 提出的能力名能否直接归位到既有标签。</li>
 * </ul>
 * 调错任一个的后果都不是报错，而是**静默地多建岗位 / 少建岗位**，所以全部做成可配。
 */
@Data
@Component
@ConfigurationProperties(prefix = "trend")
public class PostTrendProperties {

    /** 与既有岗位的向量相似度 ≥ 该值 → 判定为「既有岗位的能力变更」；否则判为「新岗位候选」 */
    private double similarityThreshold = 0.82D;

    /** 材料内强调度低于该值的岗位不产出候选，仅计入任务诊断 */
    private double minEmphasis = 3.0D;

    /** 单次解析产出的岗位上限（超出按强调度取前 N，其余计入诊断） */
    private int maxExtractedPosts = 30;

    /** 送给 LLM 的材料片段上限（跨文档轮转取样，保证每份材料都有代表片段） */
    private int maxMaterialSegments = 60;

    /** 每份材料最多取多少条切片，防止单份长文档霸占取样轮转 */
    private int maxChunksPerDocument = 40;

    /** 送给 LLM 的材料总字符上限，防止超出上下文窗口 */
    private int maxMaterialChars = 24_000;

    /** 能力名与既有标签的相似度 ≥ 该值 → 直接归位为既有标签 */
    private double tagResolveSimilarity = 0.88D;

    /** 相似度 ≥ 该值但未达归位线时，把「最相似标签」记入候选供人工参考 */
    private double tagReportSimilarity = 0.70D;

    /** 岗位相似度检索的召回条数（取最相似的一条作为锚点，多取便于诊断） */
    private int postMatchTopK = 5;
}
