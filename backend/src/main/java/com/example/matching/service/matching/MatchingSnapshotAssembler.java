package com.example.matching.service.matching;

import com.example.matching.dto.matching.MatchingAbilitySnapshot;
import com.example.matching.dto.matching.MatchingEmployeeProfile;
import com.example.matching.dto.matching.MatchingPostProfile;
import com.example.matching.dto.matching.MatchingRequirementSnapshot;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.post.PostPost;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 匹配层 Entity -> DTO 转换器（M-12）
 * <p>
 * Entity 只存在于持久层边界，由 {@link MatchingDataQueryService} 实现类调用本转换器，
 * 匹配算法与评分层不再直接读取 Entity 字段。
 */
public final class MatchingSnapshotAssembler {

    private MatchingSnapshotAssembler() {
    }

    /**
     * 正式人员能力 -> 匹配能力快照
     */
    public static MatchingAbilitySnapshot toAbilitySnapshot(EmpAbility ability, String abilityName) {
        if (ability == null) {
            return null;
        }
        Integer level = ability.getMasteryLevel() != null ? ability.getMasteryLevel() : ability.getAbilityLevel();
        BigDecimal confidence = ability.getSourceWeight() != null
                ? ability.getSourceWeight()
                : (level != null ? BigDecimal.ONE : null);
        String effectiveName = abilityName != null && !abilityName.isBlank()
                ? abilityName : ability.getAbilityName();
        return new MatchingAbilitySnapshot(
                ability.getId(),
                ability.getTagId(),
                effectiveName,
                level,
                confidence,
                ability.getEvaluationSource(),
                ability.getSourceWeight(),
                ability.getEvaluationDate()
        );
    }

    /**
     * 岗位要求 -> 要求快照
     */
    public static MatchingRequirementSnapshot toRequirementSnapshot(PostAbilityModel model, String abilityName) {
        if (model == null) {
            return null;
        }
        String effectiveName = abilityName != null && !abilityName.isBlank()
                ? abilityName : model.getAbilityName();
        return new MatchingRequirementSnapshot(
                model.getTagId(),
                effectiveName,
                model.getMinRequiredLevel(),
                model.getWeight(),
                model.getIsRequired(),
                model.getIsCore(),
                model.getModelVersion()
        );
    }

    /**
     * 员工 -> 员工画像（无能力快照；硬条件检查等仅需档案字段的场景）
     */
    public static MatchingEmployeeProfile toEmployeeProfile(EmpEmployee employee) {
        return toEmployeeProfile(employee, null);
    }

    /**
     * 员工 -> 员工画像（能力快照从快照映射中按 empId 取）
     */
    public static MatchingEmployeeProfile toEmployeeProfile(EmpEmployee employee,
                                                            Map<Long, List<MatchingAbilitySnapshot>> abilitiesByEmp) {
        if (employee == null) {
            return null;
        }
        List<MatchingAbilitySnapshot> abilities = abilitiesByEmp != null
                ? abilitiesByEmp.getOrDefault(employee.getId(), List.of())
                : List.of();
        return new MatchingEmployeeProfile(
                employee.getId(),
                employee.getEmpCode(),
                employee.getRealName(),
                employee.getLevel(),
                employee.getGender(),
                employee.getExtendFields(),
                abilities
        );
    }

    /**
     * 岗位 -> 岗位画像
     */
    public static MatchingPostProfile toPostProfile(PostPost post, List<MatchingRequirementSnapshot> requirements) {
        if (post == null) {
            return null;
        }
        return new MatchingPostProfile(
                post.getId(),
                post.getPostCode(),
                post.getPostName(),
                post.getPostLevel(),
                post.getJobDescription(),
                post.getExtendFields(),
                requirements != null ? requirements : List.of()
        );
    }
}
