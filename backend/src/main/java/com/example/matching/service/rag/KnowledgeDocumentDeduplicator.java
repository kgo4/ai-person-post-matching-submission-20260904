package com.example.matching.service.rag;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;

/**
 * 知识文档规范化去重器 —— 保守的规范重复检测。
 * <p>
 * 规则（明确不做的事）：
 * <ul>
 *   <li>不做中文词干提取或自造同义词匹配</li>
 *   <li>不做模糊删除（相似但不同文档保持独立，需人工审核队列）</li>
 *   <li>别名归一化只复用治理后的能力标签目录，不用编辑距离或 LLM</li>
 * </ul>
 */
@Component
public class KnowledgeDocumentDeduplicator {

    private static final int HASH_BYTES = 32;

    /**
     * 计算规范化内容哈希（Unicode 归一化 + 拉丁小写 + 折叠空白 + 去标点仅用于比较）。
     */
    public String canonicalHash(String content) {
        if (content == null) {
            content = "";
        }
        String canonical = canonicalize(content);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(HASH_BYTES * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * 规范化文本（仅用于比较，不存储）。
     */
    public String canonicalize(String content) {
        if (content == null) {
            return "";
        }
        String normalized = Normalizer.normalize(content, Normalizer.Form.NFKC);
        // 折叠空白
        normalized = normalized.replaceAll("\\s+", " ");
        // 拉丁小写
        normalized = normalized.toLowerCase(java.util.Locale.ROOT);
        // 去标点（仅比较用途）
        normalized = normalized.replaceAll("[\\p{Punct}\\p{IsPunctuation}]", "");
        return normalized.trim();
    }

    /**
     * 来源分组：同一组内的精确内容重复合并为一个规范文档。
     */
    public String sourceGroup(String sourceType) {
        if (sourceType == null) {
            return "UNKNOWN";
        }
        return switch (sourceType) {
            case "POST_ABILITY_MODEL", "JD_IMPORT", "POST_PROTOTYPE" -> "POST_REQUIREMENT";
            case "CONTEST_EVIDENCE", "EMP_ABILITY" -> "EVIDENCE";
            case "LEARNING_RESOURCE" -> "LEARNING";
            case "ABILITY_TAG" -> "ABILITY_TAG";
            default -> "GENERAL";
        };
    }
}
