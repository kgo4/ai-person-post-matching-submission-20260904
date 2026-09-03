package com.example.matching.ai.context.service;

import com.example.matching.ai.context.dto.AiContextPackageDTO;

/**
 * AI上下文压缩服务
 *
 * @author system
 */
public interface AiContextCompressorService {

    /**
     * 压缩上下文包
     * <p>
     * 规则：
     * 1. 岗位核心能力优先保留
     * 2. 能力差距优先保留
     * 3. 已审核证据优先保留
     * 4. 高可信度证据优先保留
     * 5. AI自生成证据降权
     * 6. 每类列表设上限
     *
     * @param context 原始上下文包
     * @return 压缩后的上下文包
     */
    AiContextPackageDTO compress(AiContextPackageDTO context);
}
