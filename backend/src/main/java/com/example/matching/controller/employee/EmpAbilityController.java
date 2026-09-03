package com.example.matching.controller.employee;

import com.example.matching.application.employee.EmpAbilityApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.employee.EmpAbilitySaveDTO;
import com.example.matching.dto.employee.api.EmployeeAbilityCreateRequest;
import com.example.matching.dto.employee.api.EmployeeAbilityResponse;
import com.example.matching.dto.employee.api.EmployeeAbilityUpdateRequest;
import com.example.matching.dto.employee.api.PendingAbilityClaimResponse;
import com.example.matching.vo.employee.EmpAbilityProfileVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "员工能力档案", description = "员工能力档案的管理接口，包括能力画像查询、能力记录的增删改查与批量保存")
@RestController
@RequestMapping("/api/employee/ability")
@RequiredArgsConstructor
public class EmpAbilityController {

    private final EmpAbilityApiFacade empAbilityApiFacade;

    @Operation(summary = "获取员工能力画像", description = "根据员工ID获取该员工的综合能力画像数据，包括各项能力标签的名称、等级和评分等汇总信息")
    @GetMapping("/profile/{empId}")
    public R<EmpAbilityProfileVO> profile(
            @Parameter(description = "员工ID", required = true) @PathVariable Long empId) {
        return R.ok(empAbilityApiFacade.getProfile(empId));
    }

    @Operation(summary = "按员工ID查询能力列表", description = "根据员工ID查询该员工所有能力记录的明细列表，每条记录包含能力标签和对应等级")
    @GetMapping("/{empId}")
    public R<List<EmployeeAbilityResponse>> listByEmpId(
            @Parameter(description = "员工ID", required = true) @PathVariable Long empId) {
        return R.ok(empAbilityApiFacade.listByEmpId(empId));
    }

    @Operation(summary = "获取待融合能力声明", description = "返回已进入 Harness 审核、尚未融合到正式人员画像的原始能力声明")
    @GetMapping("/pending/{empId}")
    public R<List<PendingAbilityClaimResponse>> listPendingClaims(
            @Parameter(description = "员工ID", required = true) @PathVariable Long empId) {
        return R.ok(empAbilityApiFacade.listPendingClaims(empId));
    }

    @Operation(summary = "新增能力记录", description = "为指定员工新增一条能力记录，包含能力标签ID和对应的等级/评分信息")
    @PostMapping
    public R<Void> save(
            @Parameter(description = "能力创建请求") @Valid @RequestBody EmployeeAbilityCreateRequest req) {
        empAbilityApiFacade.save(req);
        return R.ok();
    }

    @Operation(summary = "更新能力记录", description = "根据能力记录ID更新已有能力记录的等级或评分信息")
    @PutMapping("/{id}")
    public R<Void> update(
            @Parameter(description = "能力记录ID", required = true) @PathVariable Long id,
            @Parameter(description = "能力更新请求") @Valid @RequestBody EmployeeAbilityUpdateRequest req) {
        empAbilityApiFacade.update(id, req);
        return R.ok();
    }

    @Operation(summary = "批量保存能力", description = "批量保存员工的多项能力记录，可用于一次性配置或更新员工的全部能力项")
    @PostMapping("/batch")
    public R<Void> batchSave(
            @Parameter(description = "能力保存DTO列表，每条记录包含员工ID、能力标签ID和等级评分") @Valid @RequestBody List<EmpAbilitySaveDTO> list) {
        empAbilityApiFacade.batchSave(list);
        return R.ok();
    }

    @Operation(summary = "删除能力记录", description = "根据能力记录ID删除指定能力记录，删除后该记录不可恢复")
    @DeleteMapping("/{id}")
    public R<Void> delete(
            @Parameter(description = "能力记录ID", required = true) @PathVariable Long id) {
        empAbilityApiFacade.delete(id);
        return R.ok();
    }
}
