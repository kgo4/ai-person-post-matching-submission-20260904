package com.example.matching.application.employee;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.dto.employee.api.EmployeeCreateRequest;
import com.example.matching.dto.employee.api.EmployeeResponse;
import com.example.matching.dto.employee.api.EmployeeUpdateRequest;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.service.common.EmpCodeGenerator;
import com.example.matching.service.common.ExcelService;
import com.example.matching.service.employee.EmpEmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.example.matching.application.common.FileContent;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmpEmployeeApiFacade {

    private final EmpEmployeeService empEmployeeService;
    private final EmpCodeGenerator empCodeGenerator;
    private final ExcelService excelService;

    public PageResponse<EmployeeResponse> page(long current, long size, String keyword, Integer status) {
        IPage<EmpEmployee> page = empEmployeeService.pageEmployees(
                new Page<>(current, size), keyword, status);
        return PageResponse.from(page, this::toResponse);
    }

    public EmployeeResponse getById(Long id) {
        return toResponse(empEmployeeService.getById(id));
    }

    public void save(EmployeeCreateRequest req) {
        EmpEmployee entity = toEntity(req);
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

    public void delete(Long id) {
        empEmployeeService.removeById(id);
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
