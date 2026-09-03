package com.example.matching.service.rag;

import java.util.List;

/**
 * 知识文档分块器接口
 *
 * @author system
 */
public interface KnowledgeChunker {

    /**
     * 将文本分块
     * <p>
     * 规则：
     * - 分块大小：800中文字符
     * - 重叠：120中文字符
     * - 去除重复空白
     * - 保持原始顺序
     * - 跳过短于20字符的分块
     *
     * @param text 原始文本
     * @return 分块文本列表
     */
    List<String> chunk(String text);

    /**
     * 按来源配置分块（默认实现使用通用配置）。
     *
     * @param text    原始文本
     * @param profile 来源分块配置
     * @return 分块文本列表
     */
    default List<String> chunk(String text, ChunkingProfile profile) {
        return chunk(text);
    }
}
