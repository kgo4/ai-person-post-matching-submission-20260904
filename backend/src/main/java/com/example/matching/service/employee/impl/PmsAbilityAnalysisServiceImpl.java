package com.example.matching.service.employee.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.util.UserFacingMessage;
import com.example.matching.dto.employee.api.PmsRosterItemResponse;
import com.example.matching.dto.employee.api.PmsRosterResponse;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.system.PmsAnalysisTask;
import com.example.matching.entity.system.PmsUserMapping;
import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.mapper.system.PmsAnalysisTaskMapper;
import com.example.matching.mapper.system.PmsUserMappingMapper;
import com.example.matching.repository.PmsDataRepository;
import com.example.matching.service.employee.PmsAbilityAnalysisService;
import com.example.matching.service.employee.PmsAbilityAnalysisAgent;
import com.example.matching.agent.dto.person.PersonAbilityClaim;
import com.example.matching.agent.dto.person.PersonAbilityExtractionResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * PMS 项目分析服务实现。
 *
 * <p>主键是 <b>PMS 人员（pmsUserId）</b>而不是本地 empId。原因见接口 javadoc：
 * PMS 上的人先于本系统存在，「同步过来但还没绑定」是常态而不是异常。
 *
 * <p>三类状态要分清（本类所有方法都围绕它们）：
 * <ul>
 *   <li><b>未同步</b>：PMS 库里有这个人，但 {@code pms_user_mapping} 没有对应行。
 *       花名册直接读 PMS 库，所以这类人也出现在列表里，绑定/分析时会被 {@link #ensureMapping} 补一行。</li>
 *   <li><b>已同步未绑定</b>：有映射行但 {@code emp_id} 为 null。可以分析，不能导入能力。</li>
 *   <li><b>已绑定</b>：{@code emp_id} 非空。可以分析、可以导入能力。</li>
 * </ul>
 *
 * <p>⚠️ 前置条件（手工 SQL，见 {@code sql/pms_roster_allow_unbound.sql}）：
 * {@code pms_user_mapping.emp_id} 必须允许 NULL，否则同步未绑定人员会直接违反 NOT NULL 约束。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PmsAbilityAnalysisServiceImpl implements PmsAbilityAnalysisService {

    private final PmsDataRepository pmsDataRepository;
    private final PmsUserMappingMapper userMappingMapper;
    private final PmsAnalysisTaskMapper analysisTaskMapper;
    private final EmpEmployeeMapper employeeMapper;
    private final PmsAbilityAnalysisAgent pmsAbilityAnalysisAgent;
    private final ObjectMapper objectMapper;
    private final PmsAbilityImportEngine importEngine;

    // ------------------------------------------------------------------
    // 以本地员工为入口（保留给内部复用与兼容，不再是页面主路径）
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public PmsUserMapping autoMapUser(Long empId) {
        EmpEmployee employee = employeeMapper.selectById(empId);
        if (employee == null) {
            throw new BusinessException(404, "未找到该员工档案");
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
        return bind(pmsUserId, empId, false);
    }

    @Override
    @Transactional
    public PmsUserMapping manualMapUser(Long empId, Long pmsUserId) {
        // 显式指定映射 = HR 明说要覆盖现有关系，因此允许替换。
        // 新的花名册页面走 bindRosterUser（冲突时返回 409，不静默抢绑）。
        return bind(pmsUserId, empId, true);
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
                throw new BusinessException(400,
                        "未找到该员工对应的 PMS 人员映射，请先在 PMS 项目分析页完成绑定");
            }
        }
        return runAnalysis(mapping, dateRangeMonths);
    }

    @Override
    public List<PmsAnalysisTask> getAnalysisHistory(Long empId) {
        return analysisTaskMapper.selectList(
                Wrappers.<PmsAnalysisTask>lambdaQuery()
                        .eq(PmsAnalysisTask::getEmpId, empId)
                        .orderByDesc(PmsAnalysisTask::getCreatedTime)
                        .orderByDesc(PmsAnalysisTask::getId)
        );
    }

    // ------------------------------------------------------------------
    // 以 PMS 人员为入口（HR 独立功能的主动线）
    // ------------------------------------------------------------------

    @Override
    public PmsRosterResponse listRoster() {
        if (!pmsDataRepository.isAvailable()) {
            return PmsRosterResponse.empty(
                    "PMS 数据源未配置或不可用：请先配置 pms.datasource.url / username / password，再回到本页同步。");
        }

        List<Map<String, Object>> pmsUsers;
        try {
            pmsUsers = pmsDataRepository.findAllUsers();
        } catch (Exception e) {
            log.warn("读取 PMS 人员列表失败: {}", e.getMessage());
            return PmsRosterResponse.empty("连接 PMS 库读取人员失败：" + e.getMessage());
        }
        if (pmsUsers.isEmpty()) {
            return PmsRosterResponse.empty("PMS 库中没有可同步的人员（pms_user 表为空或记录均已删除）。");
        }

        List<Long> pmsUserIds = pmsUsers.stream()
                .map(user -> importEngine.toLong(user.get("id")))
                .filter(Objects::nonNull)
                .toList();

        Map<Long, PmsUserMapping> mappingByPmsId = pmsUserIds.isEmpty() ? Map.of()
                : userMappingMapper.selectList(Wrappers.<PmsUserMapping>lambdaQuery()
                        .in(PmsUserMapping::getPmsUserId, pmsUserIds))
                .stream()
                .filter(mapping -> mapping.getPmsUserId() != null)
                .collect(Collectors.toMap(PmsUserMapping::getPmsUserId, mapping -> mapping, (a, b) -> a));

        List<Long> boundEmpIds = mappingByPmsId.values().stream()
                .map(PmsUserMapping::getEmpId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, EmpEmployee> employeeById = boundEmpIds.isEmpty() ? Map.of()
                : employeeMapper.selectBatchIds(boundEmpIds).stream()
                        .collect(Collectors.toMap(EmpEmployee::getId, employee -> employee, (a, b) -> a));

        // 分析情况按 PMS 人员聚合。最近一次 = created_time 最大、同刻取 id 最大，
        // 所以这里排序后直接取第一个，避免每条再发一次 MAX 子查询。
        Map<Long, List<PmsAnalysisTask>> tasksByPmsId = pmsUserIds.isEmpty() ? Map.of()
                : analysisTaskMapper.selectList(Wrappers.<PmsAnalysisTask>lambdaQuery()
                        .in(PmsAnalysisTask::getPmsUserId, pmsUserIds)
                        .orderByDesc(PmsAnalysisTask::getCreatedTime)
                        .orderByDesc(PmsAnalysisTask::getId))
                .stream()
                .filter(task -> task.getPmsUserId() != null)
                .collect(Collectors.groupingBy(PmsAnalysisTask::getPmsUserId, LinkedHashMap::new, Collectors.toList()));

        List<PmsRosterItemResponse> items = new ArrayList<>(pmsUsers.size());
        for (Map<String, Object> pmsUser : pmsUsers) {
            Long pmsUserId = importEngine.toLong(pmsUser.get("id"));
            if (pmsUserId == null) {
                continue;
            }

            PmsUserMapping mapping = mappingByPmsId.get(pmsUserId);
            Long empId = mapping != null ? mapping.getEmpId() : null;
            EmpEmployee employee = empId != null ? employeeById.get(empId) : null;

            List<PmsAnalysisTask> tasks = tasksByPmsId.getOrDefault(pmsUserId, List.of());
            PmsAnalysisTask latest = tasks.isEmpty() ? null : tasks.get(0);

            items.add(new PmsRosterItemResponse(
                    pmsUserId,
                    importEngine.toStringValue(pmsUser.get("username")),
                    importEngine.toStringValue(pmsUser.get("nickname")),
                    importEngine.toStringValue(pmsUser.get("employee_id")),
                    importEngine.toStringValue(pmsUser.get("email")),
                    importEngine.toStringValue(pmsUser.get("phone")),
                    importEngine.toStringValue(pmsUser.get("role")),
                    empId != null,
                    empId,
                    employee != null ? employee.getRealName() : null,
                    employee != null ? employee.getEmpCode() : null,
                    employee != null ? employee.getUserId() != null : null,
                    tasks.size(),
                    latest != null ? latest.getAnalysisStatus() : null,
                    latest != null ? latest.getCreatedTime() : null));
        }

        // 「待处理」（未绑定）排在前面：HR 打开这一页要做的第一件事就是找还没绑的人。
        // 布尔自然序 false < true，正好就是未绑定优先。
        items.sort(Comparator.comparing(PmsRosterItemResponse::bound)
                .thenComparing(PmsRosterItemResponse::pmsUserId));

        int boundCount = (int) items.stream().filter(PmsRosterItemResponse::bound).count();
        // 已分析口径 = 历史上至少成功过一次（2=成功 / 6=已导入），而不是「最近一次成功」——
        // 导入后再次分析失败不该让这个人从已分析里消失。
        int analyzedCount = (int) pmsUserIds.stream()
                .filter(id -> tasksByPmsId.getOrDefault(id, List.of()).stream()
                        .anyMatch(task -> Integer.valueOf(2).equals(task.getAnalysisStatus())
                                || Integer.valueOf(6).equals(task.getAnalysisStatus())))
                .count();

        return new PmsRosterResponse(
                true,
                items.size(),
                boundCount,
                items.size() - boundCount,
                analyzedCount,
                items.stream().map(PmsRosterItemResponse::empId).filter(Objects::nonNull).distinct().toList(),
                items,
                null);
    }

    @Override
    @Transactional
    public PmsUserMapping bindRosterUser(Long pmsUserId, Long empId) {
        return bind(pmsUserId, empId, false);
    }

    @Override
    @Transactional
    public void unbindRosterUser(Long pmsUserId) {
        PmsUserMapping mapping = userMappingMapper.selectByPmsUserId(pmsUserId);
        if (mapping == null) {
            throw new BusinessException(404, "该 PMS 人员尚未同步到本系统");
        }
        if (mapping.getEmpId() == null) {
            return;
        }

        Long previousEmpId = mapping.getEmpId();
        // ⚠️ MyBatis-Plus 的 updateById 默认忽略 null 字段，写成
        // mapping.setEmpId(null) + updateById 会把「解绑」变成一次无声的 no-op。
        // 必须用 update wrapper 显式 set(null)。
        userMappingMapper.update(null, Wrappers.<PmsUserMapping>lambdaUpdate()
                .set(PmsUserMapping::getEmpId, null)
                .eq(PmsUserMapping::getId, mapping.getId()));
        clearTaskEmpId(pmsUserId);

        // 已导入的员工能力**刻意保留**：删数据不可逆，且 HR 可能只是想换绑。
        // 如需清理，走员工能力档案的显式删除。
        log.info("解除 PMS 人员绑定: pmsUserId={}, 原 empId={}", pmsUserId, previousEmpId);
    }

    @Override
    @Transactional
    public PmsAnalysisTask analyzeByPmsUser(Long pmsUserId, int dateRangeMonths) {
        return runAnalysis(ensureMapping(pmsUserId), dateRangeMonths);
    }

    @Override
    public List<PmsAnalysisTask> getAnalysisHistoryByPmsUser(Long pmsUserId) {
        return analysisTaskMapper.selectList(
                Wrappers.<PmsAnalysisTask>lambdaQuery()
                        .eq(PmsAnalysisTask::getPmsUserId, pmsUserId)
                        .orderByDesc(PmsAnalysisTask::getCreatedTime)
                        .orderByDesc(PmsAnalysisTask::getId)
        );
    }

    // ------------------------------------------------------------------
    // 同步 / 连接 / 详情 / 导入
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public int[] syncPmsUsers() {
        if (!pmsDataRepository.isAvailable()) {
            log.warn("PMS 数据源不可用，跳过同步");
            return new int[]{0, 0, 0, 0};
        }

        List<Map<String, Object>> pmsUsers;
        try {
            pmsUsers = pmsDataRepository.findAllUsers();
        } catch (Exception e) {
            log.warn("同步 PMS 人员失败: {}", e.getMessage());
            return new int[]{0, 0, 0, 0};
        }

        int totalPmsUsers = pmsUsers.size();
        int newSynced = 0;
        int alreadySynced = 0;
        int autoBound = 0;

        // 一个本地员工只能被一个 PMS 人员占用（uk_emp 唯一约束）。先整体读一次，
        // 避免循环里逐条判断时被同一批同步内的并发写绕过。
        Set<Long> occupiedEmpIds = userMappingMapper.selectList(null).stream()
                .map(PmsUserMapping::getEmpId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (Map<String, Object> pmsUser : pmsUsers) {
            Long pmsUserId = importEngine.toLong(pmsUser.get("id"));
            if (pmsUserId == null) {
                continue;
            }

            PmsUserMapping mapping = userMappingMapper.selectByPmsUserId(pmsUserId);
            if (mapping == null) {
                // 只落映射行（emp_id 为空 = 已同步未绑定），绝不写 emp_employee。
                mapping = importEngine.buildMapping(null, pmsUser);
                userMappingMapper.insert(mapping);
                newSynced++;
            } else {
                alreadySynced++;
            }

            if (mapping.getEmpId() != null) {
                continue;
            }

            Long candidate = matchEmployeeByEmpCode(pmsUser, occupiedEmpIds);
            if (candidate == null) {
                continue;
            }
            userMappingMapper.update(null, Wrappers.<PmsUserMapping>lambdaUpdate()
                    .set(PmsUserMapping::getEmpId, candidate)
                    .eq(PmsUserMapping::getId, mapping.getId()));
            backfillTaskEmpId(pmsUserId, candidate);
            occupiedEmpIds.add(candidate);
            autoBound++;
        }

        if (newSynced > 0 || autoBound > 0) {
            log.info("PMS 人员同步完成: 新同步={}, 已同步={}, 自动绑定={}, PMS 总数={}",
                    newSynced, alreadySynced, autoBound, totalPmsUsers);
        }
        return new int[]{newSynced, totalPmsUsers, alreadySynced, autoBound};
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
    public Map<String, Object> getAnalysisDetail(Long taskId) {
        PmsAnalysisTask task = analysisTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "未找到该分析任务");
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
        PmsAnalysisTask task = analysisTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "未找到该分析任务");
        }

        // 【本链路唯一需要绑定关系的动作】能力最终落在 emp_ability 上，必须挂在确定的 empId 上；
        // 未绑定的 PMS 人员可以分析、可以看结果，但不能导入到某个员工画像里。
        // 两道校验缺一不可：目标员工确实绑了 PMS 人员，且绑的正是这份分析的来源。
        PmsUserMapping bound = userMappingMapper.selectByEmpId(empId);
        if (bound == null) {
            throw new BusinessException(400, "该员工尚未绑定 PMS 人员，无法导入 PMS 项目能力；请先在 PMS 项目分析页完成绑定");
        }
        if (task.getPmsUserId() != null && !task.getPmsUserId().equals(bound.getPmsUserId())) {
            throw new BusinessException(400, "该分析结果不属于该员工当前绑定的 PMS 人员，不能导入");
        }

        return importEngine.importAbilities(empId, taskId, indexes);
    }

    // ------------------------------------------------------------------
    // 内部原语
    // ------------------------------------------------------------------

    /**
     * 绑定原语：把 PMS 人员与本地员工绑成一对。
     *
     * @param forceReplace true = 允许覆盖已有绑定（manualMapUser 语义）；
     *                     false = 冲突时返回 409，让 HR 先解绑（花名册页面语义）
     */
    private PmsUserMapping bind(Long pmsUserId, Long empId, boolean forceReplace) {
        EmpEmployee employee = employeeMapper.selectById(empId);
        if (employee == null) {
            throw new BusinessException(404, "未找到该员工档案");
        }

        PmsUserMapping target = ensureMapping(pmsUserId);

        PmsUserMapping occupant = userMappingMapper.selectByEmpId(empId);
        if (occupant != null && !occupant.getId().equals(target.getId())) {
            if (!forceReplace) {
                throw new BusinessException(409,
                        "该员工已绑定 PMS 人员「" + rosterLabel(occupant) + "」，请先解绑后再绑定");
            }
            userMappingMapper.deleteById(occupant.getId());
        }

        if (target.getEmpId() != null && !target.getEmpId().equals(empId) && !forceReplace) {
            throw new BusinessException(409,
                    "该 PMS 人员已绑定员工「" + resolveEmpLabel(target.getEmpId()) + "」，请先解绑后再绑定");
        }

        userMappingMapper.update(null, Wrappers.<PmsUserMapping>lambdaUpdate()
                .set(PmsUserMapping::getEmpId, empId)
                .eq(PmsUserMapping::getId, target.getId()));
        target.setEmpId(empId);

        // 未绑定期间做的分析 emp_id 是 null，绑定后回填，员工侧的 PMS 历史才完整。
        backfillTaskEmpId(pmsUserId, empId);
        log.info("PMS 人员绑定: pmsUserId={}, empId={}", pmsUserId, empId);
        return target;
    }

    /** 取映射行；不存在时先补一行「已同步未绑定」。 */
    private PmsUserMapping ensureMapping(Long pmsUserId) {
        if (pmsUserId == null) {
            throw new BusinessException(400, "缺少 PMS 人员ID");
        }
        PmsUserMapping existing = userMappingMapper.selectByPmsUserId(pmsUserId);
        if (existing != null) {
            return existing;
        }

        Map<String, Object> pmsUser = findPmsUserOrNull(pmsUserId);
        if (pmsUser == null) {
            throw new BusinessException(404, "未找到该 PMS 人员（pmsUserId=" + pmsUserId + "）");
        }
        PmsUserMapping mapping = importEngine.buildMapping(null, pmsUser);
        userMappingMapper.insert(mapping);
        return mapping;
    }

    private Map<String, Object> findPmsUserOrNull(Long pmsUserId) {
        if (!pmsDataRepository.isAvailable()) {
            return null;
        }
        try {
            return pmsDataRepository.findAllUsers().stream()
                    .filter(user -> pmsUserId.equals(importEngine.toLong(user.get("id"))))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.warn("读取 PMS 用户失败: pmsUserId={}, error={}", pmsUserId, e.getMessage());
            return null;
        }
    }

    /**
     * 实际执行一次分析。未绑定时 mapping.getEmpId() 为 null，任务 emp_id 记 null ——
     * 分析本身只依赖 PMS 数据，不需要员工账号。
     */
    private PmsAnalysisTask runAnalysis(PmsUserMapping mapping, int dateRangeMonths) {
        PmsAnalysisTask task = new PmsAnalysisTask();
        task.setEmpId(mapping.getEmpId());
        task.setPmsUserId(mapping.getPmsUserId());
        task.setAnalysisStatus(1);
        task.setDateRangeMonths(dateRangeMonths);
        task.setCreatedTime(LocalDateTime.now());
        // createdBy 由 MyMetaObjectHandler 填成当前登录用户（HR），不再是员工本人。
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
                    mapping.getEmpId(), task.getId(), sourcePayload);
            List<PersonAbilityClaim> claims = extractionResult.getClaims() != null
                    ? extractionResult.getClaims() : new ArrayList<>();

            task.setAiRawResponse(importEngine.buildAgentResponse(claims));
            task.setExtractedAbilityCount(claims.size());
            task.setAnalysisStatus(2);
            analysisTaskMapper.updateById(task);

            log.info("PMS analysis delegated to PmsAbilityAnalysisAgent, pmsUserId={}, empId={}, abilities={}",
                    mapping.getPmsUserId(), mapping.getEmpId(), claims.size());
            return task;
        } catch (Exception e) {
            task.setAnalysisStatus(3);
            // 完整原因必须落库：接口只回一句可读提示，这里是 HR 唯一能看到「哪一步出错」的地方。
            // 兜底取类名，避免 e.getMessage() 为 null 时原因列变成空白、事后无从追查。
            task.setErrorMessage(importEngine.truncate(
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(), 500));
            analysisTaskMapper.updateById(task);
            log.warn("PMS 项目分析失败: pmsUserId={}, empId={}, error={}",
                    mapping.getPmsUserId(), mapping.getEmpId(), e.getMessage(), e);
            // 文案必须自带中文：原来写成 "PMS analysis failed: " + e.getMessage()，
            // 底层是英文技术文案时整条会被 UserFacingMessage 判为技术文本，
            // 在 GlobalExceptionHandler 里被换成「操作失败，请稍后重试或联系管理员」——
            // HR 看到的就是这句毫无指向性的话（真实故障现象）。
            throw new BusinessException(500, UserFacingMessage.withCause(
                    "PMS 项目分析失败：PMS 数据接口或 AI 能力提取环节出错，完整原因已记入本次分析任务",
                    e.getMessage()));
        }
    }

    private void backfillTaskEmpId(Long pmsUserId, Long empId) {
        analysisTaskMapper.update(null, Wrappers.<PmsAnalysisTask>lambdaUpdate()
                .set(PmsAnalysisTask::getEmpId, empId)
                .eq(PmsAnalysisTask::getPmsUserId, pmsUserId)
                .isNull(PmsAnalysisTask::getEmpId));
    }

    /**
     * 解绑时撤回任务上的 emp_id。
     * <p>不回撤的话，解绑后该员工的「分析历史」里还挂着别人的分析结果。
     */
    private void clearTaskEmpId(Long pmsUserId) {
        analysisTaskMapper.update(null, Wrappers.<PmsAnalysisTask>lambdaUpdate()
                .set(PmsAnalysisTask::getEmpId, null)
                .eq(PmsAnalysisTask::getPmsUserId, pmsUserId));
    }

    /**
     * 保守的自动绑定：只有 PMS 工号在本系统**唯一**命中一个未删除员工、
     * 并且该员工还没被其他 PMS 人员占用时才返回 empId。
     * <p>命中 0 个或多个（工号重复）一律返回 null —— 宁可留给 HR 手工绑定，也不要猜错人。
     */
    private Long matchEmployeeByEmpCode(Map<String, Object> pmsUser, Set<Long> occupiedEmpIds) {
        String empCode = importEngine.toStringValue(pmsUser.get("employee_id"));
        if (empCode == null || empCode.isBlank()) {
            return null;
        }
        List<EmpEmployee> matches = employeeMapper.selectList(Wrappers.<EmpEmployee>lambdaQuery()
                .eq(EmpEmployee::getEmpCode, empCode)
                .eq(EmpEmployee::getIsDeleted, 0)
                .last("LIMIT 2"));
        if (matches.size() != 1) {
            return null;
        }
        Long empId = matches.get(0).getId();
        return occupiedEmpIds.contains(empId) ? null : empId;
    }

    private String rosterLabel(PmsUserMapping mapping) {
        if (mapping.getPmsNickname() != null && !mapping.getPmsNickname().isBlank()) {
            return mapping.getPmsNickname();
        }
        if (mapping.getPmsUsername() != null && !mapping.getPmsUsername().isBlank()) {
            return mapping.getPmsUsername();
        }
        return "PMS#" + mapping.getPmsUserId();
    }

    private String resolveEmpLabel(Long empId) {
        EmpEmployee employee = employeeMapper.selectById(empId);
        if (employee == null) {
            return "#" + empId;
        }
        return employee.getRealName() != null ? employee.getRealName() : "#" + empId;
    }
}


