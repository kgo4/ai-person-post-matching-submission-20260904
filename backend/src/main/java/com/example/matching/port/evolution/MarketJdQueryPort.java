package com.example.matching.port.evolution;

import java.util.List;

/**
 * 市场JD查询端口 — 公开只读接口。
 * <p>
 * 其他域（ai.context / harness / evolution 等）只能通过本接口查询市场 JD，
 * 禁止直接注入 MarketJdDataMapper（架构门禁：跨域查询必须走 port）。
 */
public interface MarketJdQueryPort {

    /**
     * 获取可作为证据的市场 JD 快照；未找到、重复、噪声阻断的 JD 返回 null（不得作为证据）。
     */
    MarketJdSnapshot getAdmissibleSnapshot(Long jdId);

    /**
     * 获取市场 JD 的公司多样性键（用于 Harness 多公司分组判定）；未找到返回 null。
     */
    String getCompanyDiversityKey(Long jdId);

    /** 获取近期被治理阻断的 JD 文本，供规则治理 AI 建议使用。 */
    List<String> findFilteredTexts(int limit);

    /**
     * 市场 JD 只读快照（不暴露 Entity）
     */
    record MarketJdSnapshot(Long id, String postName, String jobDescription, String companyDiversityKey) {
    }
}
