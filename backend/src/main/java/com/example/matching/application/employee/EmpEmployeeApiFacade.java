package com.example.matching.application.employee;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.application.common.SelfScopeSupport;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.enums.MatchStatusEnum;
import com.example.matching.dto.employee.api.EmployeeCreateRequest;
import com.example.matching.dto.employee.api.EmployeeResponse;
import com.example.matching.dto.employee.api.EmployeeUpdateRequest;
import com.example.matching.dto.employee.api.PassedPostResponse;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.service.common.EmpCodeGenerator;
import com.example.matching.service.common.ExcelService;
import com.example.matching.service.employee.EmpEmployeeService;
import com.example.matching.service.matching.MatchingRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.matching.application.common.FileContent;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmpEmployeeApiFacade {

    private final EmpEmployeeService empEmployeeService;
    private final EmpCodeGenerator empCodeGenerator;
    private final ExcelService excelService;
    private final MatchingRecordService matchingRecordService;
    private final SelfScopeSupport selfScopeSupport;

    public PageResponse<EmployeeResponse> page(long current, long size, String keyword, Integer status) {
        IPage<EmpEmployee> page = empEmployeeService.pageEmployees(
                new Page<>(current, size), keyword, status);
        return PageResponse.from(page, this::toResponse);
    }

    public EmployeeResponse getById(Long id) {
        return toResponse(empEmployeeService.getById(id));
    }

    /**
     * 当前登录账号关联的人员档案。
     * <p>员工角色走“仅本人”链路时的身份来源；账号未绑定人员档案时返回 null，
     * 由调用方决定是提示绑定还是按无员工身份处理。</p>
     */
    public EmployeeResponse getByUserId(Long userId) {
        if (userId == null) {
            return null;
        }
        return toResponse(empEmployeeService.getByUserId(userId));
    }

    /**
     * 取当前账号的人员档案；**不存在时按需自动建档**。
     *
     * <p>为什么需要：管理端账号（典型是超级管理员、平台管理员）由后台建号或 SQL 直建，
     * 走的是「后台建号不自动建档」路径，因此没有 emp_id。而超级管理员被授予了
     * <b>全部角色全部功能</b>，侧边栏里能看到「我的能力 / 我的评估流程 / 发起匹配 /
     * 我的学习路径」等员工侧入口 —— 点进去后前端用 {@code userStore.empId} 作为参数，
     * 拿不到就只能静默返回（"开始评估"点了没反应）。
     *
     * <p>这里在建档时复用与自助注册完全相同的规则（工号自动生成、组织信息留空交 HR 维护），
     * 保证「谁用本人链路，谁就有本人档案」这一不变量；已存在则原样返回，不会重复建档。
     *
     * <p>⚠️ 只在**本人身份入口**（{@code GET /api/employees/me}）调用，不要在通用查询里调用 ——
     * 那会让"查一个不存在的 userId"这类读操作产生写副作用。
     *
     * @param userId 当前登录账号ID
     * @return 人员档案（已存在或新建）；userId 为空时返回 null
     */
    @Transactional(rollbackFor = Exception.class)
    public EmployeeResponse ensureProfileForUser(Long userId) {
        if (userId == null) {
            return null;
        }
        EmpEmployee existing = empEmployeeService.getByUserId(userId);
        if (existing != null) {
            return toResponse(existing);
        }
        // 姓名取当前账号名做占位：档案列表里若显示空白，HR 无法判断这是谁，
        // 反而以为数据坏了。真实姓名/组织信息仍由 HR 在人员档案里补全。
        createProfileForRegisteredUser(userId,
                com.example.matching.utils.SecurityUtils.getCurrentUsername(), null, null);
        return toResponse(empEmployeeService.getByUserId(userId));
    }

    /**
     * 为自助注册的账号创建人员档案并建立账号绑定。
     * <p>员工自助注册即代表其组织身份，因此注册成功时必须同时落一份人员档案，
     * 使 HR 侧“人员档案”立即可见，并让后续能力评估、发起匹配等“仅本人”链路
     * 能通过 userId 反查到 empId。工号统一由 {@link EmpCodeGenerator} 生成，
     * 与后台建档共用同一套规则，避免两处各写一遍。</p>
     *
     * @param userId   新建系统用户ID
     * @param realName 真实姓名（注册表单必填）
     * @param phone    手机号，可为空
     * @param email    邮箱，可为空
     * @return 新建人员档案ID
     */
    public Long createProfileForRegisteredUser(Long userId, String realName, String phone, String email) {
        EmpEmployee entity = new EmpEmployee();
        entity.setUserId(userId);
        entity.setRealName(realName);
        entity.setPhone(phone);
        entity.setEmail(email);
        entity.setEmpCode(empCodeGenerator.generateNext());
        // 组织归属信息注册阶段无法确定，留空交给 HR 后续在人员档案中维护。
        entity.setDepartmentId(null);
        entity.setCurrentPostId(null);
        entity.setEntryDate(null);
        entity.setLevel(null);
        entity.setIsLocked(0);
        entity.setStatus(1);
        empEmployeeService.save(entity);
        return entity.getId();
    }

    public void save(EmployeeCreateRequest req) {        EmpEmployee entity = toEntity(req);
        // 工号为空时自动生成
        if (entity.getEmpCode() == null || entity.getEmpCode().isBlank()) {
            entity.setEmpCode(empCodeGenerator.generateNext());
        } else {
            // 手动输入工号时校验唯一性（含逻辑删除行：物理行仍占用 uk_emp_code 唯一索引）
            if (empEmployeeService.isEmpCodeDuplicate(entity.getEmpCode())) {
                throw new BusinessException(ErrorCodeEnum.EMPLOYEE_CODE_DUPLICATE);
            }
        }
        entity.setDepartmentId(null);
        entity.setCurrentPostId(null);
        entity.setEntryDate(null);
        entity.setLevel(null);
        empEmployeeService.save(entity);
    }

    public void update(Long id, EmployeeUpdateRequest req) {
        EmpEmployee entity = toEntity(req);
        entity.setId(id);
        entity.setDepartmentId(null);
        entity.setCurrentPostId(null);
        entity.setEntryDate(null);
        entity.setLevel(null);
        empEmployeeService.updateById(entity);
    }

    public int batchImport(List<EmployeeCreateRequest> list) {
        List<EmpEmployee> entities = list.stream()
                .map(this::toEntity)
                .peek(e -> {
                    // 工号为空时自动生成
                    if (e.getEmpCode() == null || e.getEmpCode().isBlank()) {
                        e.setEmpCode(empCodeGenerator.generateNext());
                    }
                })
                .toList();
        return empEmployeeService.batchImport(entities);
    }

    public int importExcel(String fileName, InputStream inputStream) {
        return excelService.importEmployees(fileName, inputStream);
    }

    public FileContent exportExcel() {
        return new FileContent("employees.xlsx", excelService.exportEmployees());
    }

    public FileContent downloadTemplate() {
        return new FileContent("employee-import-template.xlsx", excelService.downloadEmployeeTemplate());
    }

    public void lock(Long id) {
        empEmployeeService.lockEmployee(id);
    }

    public void unlock(Long id) {
        empEmployeeService.unlockEmployee(id);
    }

    /**
     * 启用 / 禁用员工档案。
     *
     * <p>HR 对员工档案唯一的“下线”手段：不删除任何历史数据，只改变在册状态
     * （{@code status=0} 禁用 / {@code 1} 启用）。</p>
     */
    public void updateStatus(Long id, Integer status) {
        empEmployeeService.updateStatus(id, status);
    }

    /**
     * 作废员工档案（逻辑删除），仅用于误建档案纠错。
     *
     * <p>取代原先的 {@code delete(id)}：原实现会级联物理删除 25 张表的业务数据，
     * 属不可逆操作，已按「不允许删除员工、只能禁用」的口径收敛为纯逻辑删除。</p>
     *
     * @param id     员工档案ID
     * @param reason 作废原因，必填
     */
    public void archive(Long id, String reason) {
        empEmployeeService.archive(id, reason);
    }

    /**
     * 员工「已通过岗位」聚合（闭环设计 P3）。
     *
     * <p>口径：HR 审核通过且已推送、匹配状态为「强适配 / 适配」的岗位，按岗位去重取最新一条。
     * 员工侧只能查本人 —— 「仅本人」调用方传入他人 empId 一律 403（由 {@link SelfScopeSupport} 收口）。</p>
     */
    public List<PassedPostResponse> listPassedPosts(Long empId) {
        if (empId == null) {
            return List.of();
        }
        selfScopeSupport.assertEmployeeAccessible(empId);
        return loadPassedPosts(empId);
    }

    /** 本人视角：人员范围由登录身份固定为本人，天然无越权风险 */
    public List<PassedPostResponse> listMyPassedPosts() {
        EmpEmployee self = selfScopeSupport.currentEmployee();
        return self == null ? List.of() : loadPassedPosts(self.getId());
    }

    private List<PassedPostResponse> loadPassedPosts(Long empId) {
        return matchingRecordService.listPassedPosts(empId).stream()
                .map(this::toPassedPost)
                .toList();
    }

    private PassedPostResponse toPassedPost(MatchingRecord record) {
        // 分数口径与管理端、员工侧匹配结果页保持一致：优先 finalMatchScore，缺失时回退 aiMatchScore
        BigDecimal score = record.getFinalMatchScore() != null
                ? record.getFinalMatchScore()
                : record.getAiMatchScore();
        Integer matchStatus = record.getMatchStatus();
        return new PassedPostResponse(
                record.getPostId(),
                record.getPostName(),
                matchStatus,
                matchStatus == null ? null : MatchStatusEnum.getNameByCode(matchStatus),
                score,
                record.getUpdatedTime());
    }

    public Map<String, Long> stats() {
        long total = empEmployeeService.count();
        long enabled = empEmployeeService.lambdaQuery()
                .eq(EmpEmployee::getStatus, 1).count();
        long locked = empEmployeeService.lambdaQuery()
                .eq(EmpEmployee::getIsLocked, 1).count();
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("enabled", enabled);
        stats.put("locked", locked);
        return stats;
    }

    private EmployeeResponse toResponse(EmpEmployee e) {
        if (e == null) return null;
        return new EmployeeResponse(
                e.getId(),
                e.getEmpCode(),
                e.getRealName(),
                e.getGender(),
                e.getPhone(),
                e.getEmail(),
                e.getDepartmentId(),
                e.getCurrentPostId(),
                e.getEntryDate(),
                e.getLevel(),
                e.getExtendFields(),
                e.getIsLocked(),
                e.getStatus(),
                e.getCreatedTime(),
                e.getUpdatedTime());
    }

    private EmpEmployee toEntity(EmployeeCreateRequest req) {
        EmpEmployee e = new EmpEmployee();
        e.setEmpCode(req.empCode());
        e.setRealName(req.realName());
        e.setGender(req.gender());
        e.setIdCard(req.idCard());
        e.setPhone(req.phone());
        e.setEmail(req.email());
        e.setExtendFields(req.extendFields());
        return e;
    }

    private EmpEmployee toEntity(EmployeeUpdateRequest req) {
        EmpEmployee e = new EmpEmployee();
        e.setEmpCode(req.empCode());
        e.setRealName(req.realName());
        e.setGender(req.gender());
        e.setIdCard(req.idCard());
        e.setPhone(req.phone());
        e.setEmail(req.email());
        e.setExtendFields(req.extendFields());
        return e;
    }
}
