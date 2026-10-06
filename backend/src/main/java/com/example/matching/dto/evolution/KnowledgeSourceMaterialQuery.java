package com.example.matching.dto.evolution;

import lombok.Data;

/**
 * 权威材料列表查询条件。
 *
 * <p>材料管理只面向「人工上传的权威材料」（{@code sourceType = CLOUD_KNOWLEDGE_INTERNAL}），
 * 不把云知识库同步、招聘 JD 导入等自动来源混进来 —— 那些不该被人工删除。
 */
@Data
public class KnowledgeSourceMaterialQuery {

    /** 页码，从 1 开始 */
    private long current = 1;

    /** 每页条数 */
    private long size = 10;

    /** 资料类别筛选：POLICY_DOCUMENT / MARKET_REPORT / OCCUPATION_STANDARD / INDUSTRY_WHITEPAPER；空 = 全部 */
    private String sourceCategory;

    /** 标题关键词（模糊匹配） */
    private String keyword;

    /**
     * 是否包含「仅试算」材料。
     *
     * <p>默认 true：试算材料在被自动清理前也应该可见，
     * 否则用户会以为上传丢了；列表上会打「仅试算」标记说明它会被自动清理。
     */
    private Boolean includeEphemeral = Boolean.TRUE;
}
