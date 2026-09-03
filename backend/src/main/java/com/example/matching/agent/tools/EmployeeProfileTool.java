package com.example.matching.agent.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 员工画像工具 - 供LangChain4j Agent调用
 *
 * @author system
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmployeeProfileTool {

    private final EmpEmployeeMapper empEmployeeMapper;
    private final EmpAbilityMapper empAbilityMapper;
    private final AbilityTagMapper abilityTagMapper;

    @Tool("获取员工基本信息")
    public Map<String, Object> getEmployeeInfo(Long empId) {
        Optional<String> validation = AgentToolInputValidator.validatePositive("empId", empId);
        if (validation.isPresent()) {
            log.warn("getEmployeeInfo invalid input: {}", validation.get());
            return Map.of("available", false, "found", false, "reason", validation.get());
        }

        log.info("Agent调用: getEmployeeInfo(empId={})", empId);
        try {
            EmpEmployee emp = empEmployeeMapper.selectById(empId);
            if (emp == null || emp.getIsDeleted() == 1) {
                return Map.of("available", true, "found", false, "reason", "employee not found");
            }

            Map<String, Object> item = new HashMap<>();
            item.put("id", emp.getId());
            item.put("empCode", emp.getEmpCode());
            item.put("realName", emp.getRealName());
            item.put("level", emp.getLevel());
            item.put("departmentId", emp.getDepartmentId());
            item.put("currentPostId", emp.getCurrentPostId());
            item.put("entryDate", emp.getEntryDate());

            return Map.of("available", true, "found", true, "item", item);
        } catch (Exception e) {
            log.error("getEmployeeInfo 查询失败: empId={}", empId, e);
            return Map.of("available", false, "found", false, "reason", "employee_data_unavailable");
        }
    }

    @Tool("获取员工能力列表")
    public Map<String, Object> getEmployeeAbilities(Long empId) {
        Optional<String> validation = AgentToolInputValidator.validatePositive("empId", empId);
        if (validation.isPresent()) {
            log.warn("getEmployeeAbilities invalid input: {}", validation.get());
            return Map.of("available", false, "items", List.of(), "reason", validation.get());
        }

        log.info("Agent调用: getEmployeeAbilities(empId={})", empId);
        try {
            // 修复：限制返回条数（防全量能力注入 prompt 撑爆上下文/放大幻觉面）
            LambdaQueryWrapper<EmpAbility> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(EmpAbility::getEmpId, empId)
                    .eq(EmpAbility::getIsDeleted, 0)
                    .orderByDesc(EmpAbility::getMasteryLevel)
                    .last("LIMIT 50");
            List<EmpAbility> abilities = empAbilityMapper.selectList(wrapper);

            List<Long> tagIds = abilities.stream()
                    .map(EmpAbility::getTagId)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .toList();

            Map<Long, AbilityTag> tagsById;
            if (tagIds.isEmpty()) {
                tagsById = Map.of();
            } else {
                tagsById = abilityTagMapper.selectBatchIds(tagIds).stream()
                        .collect(Collectors.toMap(AbilityTag::getId, Function.identity(), (a, b) -> a));
            }

            List<Map<String, Object>> items = abilities.stream().map(ability -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", ability.getId());
                map.put("tagId", ability.getTagId());
                map.put("masteryLevel", ability.getMasteryLevel());
                map.put("evaluationSource", ability.getEvaluationSource());
                map.put("sourceWeight", ability.getSourceWeight());

                AbilityTag tag = tagsById.get(ability.getTagId());
                String abilityName = ability.getAbilityName();
                if (abilityName == null || abilityName.isBlank()) {
                    abilityName = tag != null ? tag.getTagName() : null;
                }
                if (abilityName != null && !abilityName.isBlank()) {
                    map.put("abilityName", abilityName);
                }
                if (tag != null) {
                    map.put("tagCategory", tag.getTagCategory());
                }

                return map;
            }).collect(Collectors.toList());

            return Map.of("available", true, "items", items);
        } catch (Exception e) {
            log.error("getEmployeeAbilities 查询失败: empId={}", empId, e);
            return Map.of("available", false, "items", List.of(), "reason", "employee_abilities_unavailable");
        }
    }
}
