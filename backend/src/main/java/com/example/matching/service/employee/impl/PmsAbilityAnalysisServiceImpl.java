package com.example.matching.service.employee.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.common.enums.AbilitySourceCredibility;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.dto.harness.AiHarnessClaimDTO;
import com.example.matching.dto.harness.AiHarnessDecisionDTO;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.entity.system.PmsAnalysisTask;
import com.example.matching.entity.system.PmsUserMapping;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.mapper.system.PmsAnalysisTaskMapper;
import com.example.matching.mapper.system.PmsUserMappingMapper;
import com.example.matching.repository.PmsDataRepository;
import com.example.matching.service.employee.PmsAbilityAnalysisService;
import com.example.matching.service.ability.AbilityEvidenceIngestionService;
import com.example.matching.service.agent.AgentBusinessApplyService;
import com.example.matching.agent.dto.person.PersonAbilityClaim;
import com.example.matching.agent.dto.person.PersonAbilityExtractionResult;
import com.example.matching.service.employee.PmsAbilityAnalysisAgent;
import com.example.matching.service.harness.AiTrustHarnessService;
import com.example.matching.service.system.AbilityAdmissionService;
import com.example.matching.service.system.AbilityTagService;
import com.example.matching.dto.system.TagAdmissionContext;
import com.example.matching.dto.system.TagAdmissionResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PmsAbilityAnalysisServiceImpl implements PmsAbilityAnalysisService {

    private static final String SOURCE_TYPE = AbilitySourceCredibility.AI_PROJECT.getSource();
    private static final double SOURCE_WEIGHT = AbilitySourceCredibility.AI_PROJECT.getWeight();

    private final PmsDataRepository pmsDataRepository;
    private final PmsUserMappingMapper userMappingMapper;
    private final PmsAnalysisTaskMapper analysisTaskMapper;
    private final EmpEmployeeMapper employeeMapper;
    private final EmpAbilityMapper empAbilityMapper;
    private final AbilityEvidenceIngestionService abilityEvidenceIngestionService;
    private final PmsAbilityAnalysisAgent pmsAbilityAnalysisAgent;
    private final AbilityTagService abilityTagService;
    private final AiTrustHarnessService aiTrustHarnessService;
    private final ObjectMapper objectMapper;
    private final AbilityAdmissionService abilityAdmissionService;
    private final AgentBusinessApplyService agentBusinessApplyService;
    private final PmsAbilityImportEngine importEngine;

    @Override
    @Transactional
    public PmsUserMapping autoMapUser(Long empId) {
        EmpEmployee employee = employeeMapper.selectById(empId);
        if (employee == null) {
            throw new BusinessException(404, "employee not found");
        }

        PmsUserMapping existing = userMappingMapper.selectByEmpId(empId);
        if (existing != null) {
            return existing;
        }

        Map<String, Object> pmsUser = null;
        if (employee.getEmpCode() != null && !employee.getEmpCode().isBlank()) {
            pmsUser = pmsDataRepository.findUserByEmployeeId(employee.getEmpCode());
        }
        if (pmsUser == null && employee.getRealName() != null) {
            List<Map<String, Object>> users = pmsDataRepository.findUsersByNickname(employee.getRealName());
            if (users.size() == 1) {
                pmsUser = users.get(0);
            }
        }
        if (pmsUser == null) {
            return null;
        }

        Long pmsUserId = importEngine.toLong(pmsUser.get("id"));
        PmsUserMapping existingByPms = userMappingMapper.selectByPmsUserId(pmsUserId);
        if (existingByPms != null) {
            throw new BusinessException(409, "PMS user already mapped to empId=" + existingByPms.getEmpId());
        }

        PmsUserMapping mapping = importEngine.buildMapping(empId, pmsUser);
        userMappingMapper.insert(mapping);
        return mapping;
    }

    @Override
    @Transactional
    public PmsUserMapping manualMapUser(Long empId, Long pmsUserId) {
        EmpEmployee employee = employeeMapper.selectById(empId);
        if (employee == null) {
            throw new BusinessException(404, "employee not found");
        }

        PmsUserMapping existing = userMappingMapper.selectByEmpId(empId);
        if (existing != null) {
            userMappingMapper.deleteById(existing.getId());
        }
        PmsUserMapping existingByPms = userMappingMapper.selectByPmsUserId(pmsUserId);
        if (existingByPms != null) {
            userMappingMapper.deleteById(existingByPms.getId());
        }

        Map<String, Object> pmsUser = pmsDataRepository.findAllUsers().stream()
                .filter(user -> pmsUserId.equals(importEngine.toLong(user.get("id"))))
                .findFirst()
                .orElseThrow(() -> new BusinessException(404, "PMS user not found"));

        PmsUserMapping mapping = importEngine.buildMapping(empId, pmsUser);
        userMappingMapper.insert(mapping);
        return mapping;
    }

    @Override
    public PmsUserMapping getMapping(Long empId) {
        return userMappingMapper.selectByEmpId(empId);
    }

    @Override
    @Transactional
    public PmsAnalysisTask analyzeEmployee(Long empId, int dateRangeMonths) {
        PmsUserMapping mapping = userMappingMapper.selectByEmpId(empId);
        if (mapping == null) {
            mapping = autoMapUser(empId);
            if (mapping == null) {
                throw new BusinessException(400, "PMS user mapping not found");
            }
        }

        PmsAnalysisTask task = new PmsAnalysisTask();
        task.setEmpId(empId);
        task.setPmsUserId(mapping.getPmsUserId());
        task.setAnalysisStatus(1);
        task.setDateRangeMonths(dateRangeMonths);
        task.setCreatedBy(empId);
        task.setCreatedTime(LocalDateTime.now());
        analysisTaskMapper.insert(task);

        try {
            List<Map<String, Object>> workOrders = pmsDataRepository.getWorkOrders(mapping.getPmsUserId(), dateRangeMonths);
            List<Map<String, Object>> bugs = pmsDataRepository.getBugs(mapping.getPmsUserId(), dateRangeMonths);
            List<Map<String, Object>> testCases = pmsDataRepository.getTestCases(mapping.getPmsUserId(), dateRangeMonths);
            List<Map<String, Object>> projects = pmsDataRepository.getProjectParticipation(mapping.getPmsUserId());

            task.setWorkOrderCount(workOrders.size());
            task.setBugCount(bugs.size());
            task.setTestCaseCount(testCases.size());
            task.setProjectCount(projects.size());

            // 使用专属 PMS Agent 提取能力
            Map<String, Object> sourcePayload = new LinkedHashMap<>();
            sourcePayload.put("workOrders", workOrders);
            sourcePayload.put("bugs", bugs);
            sourcePayload.put("testCases", testCases);
            sourcePayload.put("projects", projects);
            PersonAbilityExtractionResult extractionResult = pmsAbilityAnalysisAgent.extractCombined(
                    empId, task.getId(), sourcePayload);
            List<PersonAbilityClaim> claims = extractionResult.getClaims() != null
                    ? extractionResult.getClaims() : new ArrayList<>();

            task.setAiRawResponse(importEngine.buildAgentResponse(claims));
            task.setExtractedAbilityCount(claims.size());
            task.setAnalysisStatus(2);
            analysisTaskMapper.updateById(task);

            log.info("PMS analysis delegated to PmsAbilityAnalysisAgent, empId={}, abilities={}", empId, claims.size());
            return task;
        } catch (Exception e) {
            task.setAnalysisStatus(3);
            task.setErrorMessage(importEngine.truncate(e.getMessage(), 500));
            analysisTaskMapper.updateById(task);
            throw new BusinessException(500, "PMS analysis failed: " + e.getMessage());
        }
    }

    @Override
    public List<PmsAnalysisTask> getAnalysisHistory(Long empId) {
        return analysisTaskMapper.selectList(
                Wrappers.<PmsAnalysisTask>lambdaQuery()
                        .eq(PmsAnalysisTask::getEmpId, empId)
                        .orderByDesc(PmsAnalysisTask::getCreatedTime)
        );
    }

    @Override
    public List<Map<String, Object>> listPmsUsers() {
        return pmsDataRepository.findAllUsers();
    }

    @Override
    public boolean testConnection() {
        return pmsDataRepository.testConnection();
    }

    @Override
    @Transactional
    public int[] syncPmsUsers() {
        List<Map<String, Object>> pmsUsers = pmsDataRepository.findAllUsers();
        int totalPmsUsers = pmsUsers.size();
        int newMapped = 0;
        int alreadyMapped = 0;
        int newCreated = 0;

        for (Map<String, Object> pmsUser : pmsUsers) {
            Long pmsUserId = importEngine.toLong(pmsUser.get("id"));
            if (userMappingMapper.selectByPmsUserId(pmsUserId) != null) {
                alreadyMapped++;
                continue;
            }

            EmpEmployee employee = importEngine.findOrCreateEmployee(pmsUser);
            PmsUserMapping mapping = importEngine.buildMapping(employee.getId(), pmsUser);
            userMappingMapper.insert(mapping);
            newMapped++;
            if (employee.getCreatedTime() != null && employee.getCreatedTime().isAfter(LocalDateTime.now().minusSeconds(5))) {
                newCreated++;
            }
        }

        return new int[]{newMapped, totalPmsUsers, alreadyMapped, newCreated};
    }

    @Override
    public Map<String, Object> getAnalysisDetail(Long taskId) {
        PmsAnalysisTask task = analysisTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "analysis task not found");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("task", task);
        try {
            Map<String, Object> parsed = objectMapper.readValue(
                    task.getAiRawResponse() != null ? task.getAiRawResponse() : "{}",
                    new TypeReference<Map<String, Object>>() {}
            );
            result.put("summary", parsed.getOrDefault("summary", ""));
            result.put("abilities", parsed.getOrDefault("abilities", List.of()));
        } catch (Exception e) {
            result.put("summary", "");
            result.put("abilities", List.of());
        }
        return result;
    }

    @Override
    @Transactional
    public int importAbilities(Long empId, Long taskId, List<Integer> indexes) {
        return importEngine.importAbilities(empId, taskId, indexes);
    }
}
