package com.example.matching.service.evolution;

import com.example.matching.entity.rag.KnowledgeSourceDocument;

/**
 * 权威材料上传结果。
 *
 * <p>之所以不直接返回 {@link KnowledgeSourceDocument}：上传有两种结局，
 * 调用方必须能区分并给出正确反馈，也要知道**还需不需要建索引** ——
 * <ul>
 *   <li><b>新建</b>：正常入库，随后由调用方建索引；</li>
 *   <li><b>复用</b>：同一份材料此前已上传过，直接挂回既有材料，
 *       <b>不再重复建 RAG 文档与向量索引</b> —— 这正是「重复材料不重复索引」的落点。
 *       但如果被复用的那份材料上次索引失败过，仍需补一次索引。</li>
 * </ul>
 *
 * <p>文案由调用方组装：只有调用方知道最终索引出多少个片段，能写出「已索引 N 个片段」这种
 * 带具体数字的提示。这里只给结构化事实。
 *
 * @param document      命中的知识源文档（新建的或复用的）
 * @param reused        是否复用了既有材料
 * @param needsIndexing 调用方是否还需要调用 {@code indexKnowledgeSource}
 */
public record SourceUploadOutcome(KnowledgeSourceDocument document, boolean reused, boolean needsIndexing) {
}
