package com.example.matching.service.employee.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.system.PmsAnalysisTask;
import com.example.matching.entity.system.PmsUserMapping;
import com.example.matching.common.enums.AbilitySourceCredibility;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.system.PmsAnalysisTaskMapper;
import com.example.matching.mapper.system.PmsUserMappingMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PMS 能力导入引擎：任务解析、原始证据校验、人员能力写入、用户映射构建。
 * <p>
 * 从 PmsAbilityAnalysisServiceImpl（500 行）中拆分的 PMS 导入组件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PmsAbilityImportEngine {

    private static final String SOURCE_TYPE = AbilitySourceCredibility.AI_PROJECT.getSource();

    private final PmsUserMappingMapper userMappingMapper;
    private final PmsAnalysisTaskMapper analysisTaskMapper;
    private final EmpAbilityMapper empAbilityMapper;
    private final ObjectMapper objectMapper;
    @SuppressWarnings("unchecked")
    public int importAbilities(Long empId, Long taskId, List<Integer> indexes) {
        PmsAnalysisTask task = analysisTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "analysis task not found");
        }
        if (!Integer.valueOf(2).equals(task.getAnalysisStatus()) && !Integer.valueOf(6).equals(task.getAnalysisStatus())) {
            throw new BusinessException(400, "analysis task is not completed");
        }

        List<Map<String, Object>> abilities;
        try {
            Map<String, Object> parsed = objectMapper.readValue(task.getAiRawResponse(), new TypeReference<Map<String, Object>>() {});
            abilities = (List<Map<String, Object>>) parsed.getOrDefault("abilities", List.of());
        } catch (Exception e) {
            throw new BusinessException(500, "failed to read agent analysis result: " + e.getMessage());
        }

        int count = 0;
        for (int i = 0; i < abilities.size(); i++) {
            if (indexes != null && !indexes.isEmpty() && !indexes.contains(i)) {
                continue;
            }
            if (importAbility(empId, taskId, abilities.get(i))) {
                count++;
            }
        }

        if (count > 0) {
            task.setAnalysisStatus(6);
            analysisTaskMapper.updateById(task);
        }
        return count;
    }

    public boolean importAbility(Long empId, Long taskId, Map<String, Object> ability) {
        String tagName = toStringValue(ability.get("tagName"));
        Integer level = toInt(ability.get("level"));
        BigDecimal confidence = toBigDecimal(ability.get("confidence"), BigDecimal.valueOf(0.8));
        String evidence = toStringValue(ability.get("evidence"));
        List<String> sourceRefs = toSourceRefs(ability.get("sourceRefs"));

        if (tagName == null || tagName.isBlank() || level == null || level < 1 || level > 5
                || evidence == null || evidence.isBlank() || sourceRefs.stream().noneMatch(this::isConcretePmsRef)) {
            log.warn("跳过没有原始 PMS 证据的能力: taskId={}, ability={}", taskId, tagName);
            return false;
        }

        // PMS is an independent personnel-ability extraction path. It stores the ability name
        // directly and deliberately does not create/reuse a system tag or enter Harness.
        EmpAbility existing = empAbilityMapper.selectOne(Wrappers.<EmpAbility>lambdaQuery()
                .eq(EmpAbility::getEmpId, empId)
                .eq(EmpAbility::getAbilityName, tagName)
                .eq(EmpAbility::getEvaluationSource, SOURCE_TYPE)
                .eq(EmpAbility::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (existing == null) {
            EmpAbility imported = new EmpAbility();
            imported.setEmpId(empId);
            imported.setAbilityName(tagName);
            imported.setMasteryLevel(level);
            imported.setAbilityLevel(level);
            imported.setEvaluationSource(SOURCE_TYPE);
            imported.setSourceWeight(BigDecimal.valueOf(AbilitySourceCredibility.AI_PROJECT.getWeight()));
            imported.setEvaluationDate(java.time.LocalDate.now());
            imported.setRemark(buildEvidenceRemark(taskId, evidence));
            imported.setIsDeleted(0);
            empAbilityMapper.insert(imported);
        } else {
            existing.setMasteryLevel(level);
            existing.setAbilityLevel(level);
            existing.setSourceWeight(BigDecimal.valueOf(AbilitySourceCredibility.AI_PROJECT.getWeight()));
            existing.setEvaluationDate(java.time.LocalDate.now());
            existing.setRemark(buildEvidenceRemark(taskId, evidence));
            empAbilityMapper.updateById(existing);
        }
        log.info("PMS能力已直接写入人员画像: empId={}, ability={}, taskId={}", empId, tagName, taskId);
        return true;
    }

    public String buildAgentResponse(List<com.example.matching.agent.dto.person.PersonAbilityClaim> claims) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", "PMS/project abilities extracted by PmsAbilityAnalysisAgent");
        List<Map<String, Object>> abilities = new ArrayList<>();
        for (com.example.matching.agent.dto.person.PersonAbilityClaim claim : claims) {
            String abilityName = claim.getAbilityName();

            Map<String, Object> ability = new LinkedHashMap<>();
            ability.put("tagName", abilityName);
            ability.put("tagCategory", "TECHNICAL");
            ability.put("level", claim.getMasteryLevel());
            ability.put("confidence", claim.getConfidenceScore() != null
                    ? claim.getConfidenceScore().divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                    : BigDecimal.valueOf(0.8));
            ability.put("evidence", claim.getEvidenceText());
            ability.put("sourceRefs", claim.getSourceRefs());
            ability.put("sourceType", claim.getSourceType());
            ability.put("sourceRefId", claim.getSourceRefId());
            abilities.add(ability);
        }
        result.put("abilities", abilities);
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return "{\"summary\":\"\",\"abilities\":[]}";
        }
    }

    public PmsUserMapping buildMapping(Long empId, Map<String, Object> pmsUser) {
        PmsUserMapping mapping = new PmsUserMapping();
        // empId 允许为 null：同步 PMS 人员时先落一行「已同步未绑定」，
        // 等员工注册后由 HR 绑定（见 PmsAbilityAnalysisService#bindRosterUser）。
        mapping.setEmpId(empId);
        mapping.setPmsUserId(toLong(pmsUser.get("id")));
        mapping.setPmsUsername(toStringValue(pmsUser.get("username")));
        mapping.setPmsNickname(toStringValue(pmsUser.get("nickname")));
        mapping.setPmsEmployeeId(toStringValue(pmsUser.get("employee_id")));
        mapping.setCreatedTime(LocalDateTime.now());
        return mapping;
    }

    /*
     * 【2026-09-04 删除】原 findOrCreateEmployee 已移除。
     *
     * 它有两个已确认的问题，因此整段删除而不是改造：
     *   1. 污染人员库：同步 PMS 人员时真的 insert 一行 emp_employee
     *      （工号兜底 "PMS_<id>"），于是 PMS 上的人出现在本系统人员列表里，
     *      并被当成真实员工参与后续流程 —— 而 PMS 上的人与本平台无关。
     *   2. 重名认错人：兜底匹配用 eq(realName, nickname) + selectOne，
     *      同名员工时 MyBatis-Plus 会抛异常或错误命中。
     *
     * 现在同步只写 pms_user_mapping；绑定是 HR 的显式动作
     * （PmsAbilityAnalysisServiceImpl#bindRosterUser），只改映射、不改人员库。
     */

    private String buildEvidenceRemark(Long taskId, String evidence) {
        return truncate("PMS任务#" + taskId + "：" + evidence, 1000);
    }

    private List<String> toSourceRefs(Object value) {
        if (!(value instanceof List<?> refs)) {
            return List.of();
        }
        return refs.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }

    private boolean isConcretePmsRef(String sourceRef) {
        return sourceRef != null && (sourceRef.startsWith("source:PMS_TASK:")
                || sourceRef.startsWith("source:PMS_PROJECT:")
                || sourceRef.startsWith("source:PMS_BUG:")
                || sourceRef.startsWith("source:PMS_TEST_CASE:"));
    }

    public Long toLong(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Long value) return value;
        if (obj instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Integer toInt(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Integer value) return value;
        if (obj instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public BigDecimal toBigDecimal(Object obj, BigDecimal fallback) {
        if (obj == null) return fallback;
        if (obj instanceof BigDecimal value) return value;
        if (obj instanceof Number number) return new BigDecimal(number.toString());
        try {
            return new BigDecimal(obj.toString());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public String toStringValue(Object obj) {
        return obj == null ? null : obj.toString();
    }

    public String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    public String safeText(String text) {
        return text != null ? text : "";
    }
}


