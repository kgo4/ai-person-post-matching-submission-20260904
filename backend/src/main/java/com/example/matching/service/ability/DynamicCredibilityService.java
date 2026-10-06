package com.example.matching.service.ability;

/**
 * 动态来源可信度服务
 * <p>
 * 基于RAG反馈和交叉验证结果，动态调整不同来源的可信度权重。
 * 替代原有的静态枚举配置，实现可信度的自适应优化。
 *
 * @author system
 */
public interface DynamicCredibilityService {

    /**
     * 获取来源可信度权重
     * <p>
     * 优先返回动态调整后的权重，如果无动态数据则返回静态默认值
     *
     * @param source 来源标识（如 "RESUME_PARSE", "AI_TEST" 等）
     * @return 可信度权重（0.00-1.00）
     */
    double getWeight(String source);

    /**
     * 记录来源验证反馈
     * <p>
     * 当能力数据被人工确认或修正时调用，用于调整来源可信度
     *
     * @param source       来源标识
     * @param isConfirmed  是否被确认（true=确认，false=修正）
     * @param correctionLevel 修正后的等级（确认时为null）
     */
    void recordFeedback(String source, boolean isConfirmed, Integer correctionLevel);

    /**
     * 基于RAG检索结果评估来源质量
     * <p>
     * 检索该来源的历史证据，分析一致性和准确率
     *
     * @param source 来源标识
     * @return 质量评分（0-100）
     */
    int evaluateSourceQuality(String source);

    /**
     * 重置来源可信度为默认值
     *
     * @param source 来源标识，null表示重置所有
     */
    void resetToDefault(String source);

    /**
     * 获取来源可信度详情
     *
     * @param source 来源标识
     * @return 可信度详情JSON字符串
     */
    String getCredibilityDetail(String source);
}
