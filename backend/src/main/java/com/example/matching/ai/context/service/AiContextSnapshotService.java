package com.example.matching.ai.context.service;

import com.example.matching.ai.context.dto.AiContextPackageDTO;
import com.example.matching.ai.context.entity.AiContextPackageSnapshot;

/**
 * AI上下文快照服务
 *
 * @author system
 */
public interface AiContextSnapshotService {

    /**
     * 保存上下文快照
     */
    void saveSnapshot(AiContextPackageDTO context);

    /**
     * 查询最近快照
     */
    AiContextPackageSnapshot findLatest(String scenario, String businessKey);

    /**
     * 根据hash查询快照
     */
    AiContextPackageSnapshot findByHash(String contextHash);
}
