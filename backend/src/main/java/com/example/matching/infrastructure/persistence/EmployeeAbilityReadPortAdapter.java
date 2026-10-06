package com.example.matching.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.dto.matching.MatchingAbilitySnapshot;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.port.employee.EmployeeAbilityReadPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 员工权威能力读取实现（方案第八章）。
 * <p>
 * 匹配、Agent 解释和图谱子图均只消费 emp_ability 正式人员能力表。
 * 工作流画像和能力证据仅用于计算、审计和待确认提示，不能覆盖人工维护的正式能力。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmployeeAbilityReadPortAdapter implements EmployeeAbilityReadPort {

    private final EmpAbilityMapper empAbilityMapper;
    private final AbilityTagMapper abilityTagMapper;

    @Override
    public Map<Long, List<MatchingAbilitySnapshot>> loadAuthoritativeAbilities(List<Long> empIds) {
        Map<Long, List<MatchingAbilitySnapshot>> result = new HashMap<>();
        if (empIds == null || empIds.isEmpty()) {
            return result;
        }

        List<EmpAbility> abilities = empAbilityMapper.selectList(
                Wrappers.<EmpAbility>lambdaQuery()
                        .in(EmpAbility::getEmpId, empIds)
                        .eq(EmpAbility::getIsDeleted, 0));
        for (EmpAbility ability : abilities) {
            result.computeIfAbsent(ability.getEmpId(), k -> new ArrayList<>()).add(toSnapshot(ability));
        }
        log.info("正式能力加载：employees={}, abilities={}", empIds.size(), abilities.size());

        // 批量补充能力名称
        fillAbilityNames(result);
        return result;
    }

    private MatchingAbilitySnapshot toSnapshot(EmpAbility ability) {
        BigDecimal sourceWeight = ability.getSourceWeight() != null ? ability.getSourceWeight()
                : (ability.getEvaluationSource() != null ? new BigDecimal("0.8") : null);
        Integer level = ability.getMasteryLevel() != null ? ability.getMasteryLevel() : ability.getAbilityLevel();
        return new MatchingAbilitySnapshot(
                ability.getId(), ability.getTagId(), ability.getAbilityName(),
                level != null ? level : 0,
                sourceWeight, ability.getEvaluationSource() != null ? ability.getEvaluationSource() : "EMP_ABILITY",
                sourceWeight, ability.getEvaluationDate());
    }

    private void fillAbilityNames(Map<Long, List<MatchingAbilitySnapshot>> byEmp) {
        Set<Long> tagIds = byEmp.values().stream()
                .flatMap(List::stream)
                .map(MatchingAbilitySnapshot::tagId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (tagIds.isEmpty()) {
            return;
        }
        Map<Long, String> nameByTag = abilityTagMapper.selectList(
                        Wrappers.<AbilityTag>lambdaQuery().in(AbilityTag::getId, tagIds))
                .stream().collect(Collectors.toMap(AbilityTag::getId, AbilityTag::getTagName, (a, b) -> a));
        byEmp.values().forEach(list -> list.replaceAll(snapshot -> {
            String name = snapshot.abilityName() != null ? snapshot.abilityName()
                    : nameByTag.get(snapshot.tagId());
            if (name == null) {
                return snapshot;
            }
            return new MatchingAbilitySnapshot(
                    snapshot.abilityId(), snapshot.tagId(), name, snapshot.level(),
                    snapshot.confidence(), snapshot.sourceType(), snapshot.sourceWeight(),
                    snapshot.evaluationDate());
        }));
    }

}
