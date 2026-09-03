package com.example.matching.service.harness;

import com.example.matching.dto.harness.AiHarnessClaimDTO;
import com.example.matching.dto.harness.AiHarnessDecisionDTO;

import java.util.List;

public interface AiTrustHarnessService {

    AiHarnessDecisionDTO verify(AiHarnessClaimDTO claim);

    /**
     * 批量校验（聚合 Harness 场景）：一次性校验多个能力主张并批量落审计日志。
     * 业务上仍是一轮审核，避免调用方逐条循环。
     *
     * @param claims 能力主张列表
     * @return 与输入顺序一致的决策列表
     */
    List<AiHarnessDecisionDTO> verifyBatch(List<AiHarnessClaimDTO> claims);
}
