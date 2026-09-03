package com.example.matching.service.assessment;

import com.example.matching.dto.assessment.EligibilityPrecheckResult;
import com.example.matching.dto.assessment.ProvisionalAbilitySnapshotDTO;

import java.util.List;

/**
 * 临时能力匹配快照服务接口
 * <p>
 * 仅在强制匹配时构建临时能力快照，支持预检。
 *
 * @author system
 */
public interface ProvisionalMatchingSnapshotService {

    /**
     * 匹配预检：检查人员是否具备匹配资格。
     */
    List<EligibilityPrecheckResult> precheck(List<Long> empIds, List<Long> postIds);

    /**
     * 构建临时能力快照（INCLUDE_SOFT_EVIDENCE 模式）。
     *
     * @param empId         员工ID
     * @param acknowledged  是否已确认风险
     * @param operatorId    操作人
     * @return 快照（含令牌）；无待确立能力时返回 null
     */
    ProvisionalAbilitySnapshotDTO buildSnapshot(Long empId, boolean acknowledged, Long operatorId);

    /**
     * 校验快照令牌有效性。
     */
    boolean validateSnapshotToken(String snapshotToken, Long empId);
}
