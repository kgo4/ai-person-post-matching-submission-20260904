package com.example.matching.controller.system;

import com.example.matching.application.system.ExtendFieldApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.system.api.ExtendFieldRequest;
import com.example.matching.dto.system.api.ExtendFieldResponse;
import com.example.matching.vo.system.ExtendFieldVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "扩展字段配置", description = "按业务模块查询字段、字段CRUD，支持员工/岗位/能力模块的动态字段扩展")
@RestController
@RequestMapping("/api/system/extend-field")
@RequiredArgsConstructor
public class SysExtendFieldController {

    private final ExtendFieldApiFacade facade;

    @Operation(summary = "按业务模块查询扩展字段", description = "根据业务模块（EMPLOYEE-员工、POST-岗位、ABILITY-能力）获取该模块下所有扩展字段配置，用于动态表单渲染")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功")
    })
    @GetMapping("/module/{businessModule}")
    public R<List<ExtendFieldVO>> listByModule(
            @Parameter(description = "业务模块：EMPLOYEE-员工，POST-岗位，ABILITY-能力", required = true) @PathVariable String businessModule) {
        return R.ok(facade.listByModule(businessModule));
    }

    @Operation(summary = "分页查询扩展字段", description = "按业务模块筛选，支持分页返回扩展字段配置列表")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功")
    })
    @GetMapping("/page")
    public R<PageResponse<ExtendFieldVO>> page(
            @Parameter(description = "当前页码，从1开始", example = "1") @RequestParam(defaultValue = "1") long current,
            @Parameter(description = "每页记录数，默认10条", example = "10") @RequestParam(defaultValue = "10") long size,
            @Parameter(description = "业务模块：EMPLOYEE-员工，POST-岗位，ABILITY-能力") @RequestParam(required = false) String businessModule) {
        return R.ok(facade.page(current, size, businessModule));
    }

    @Operation(summary = "获取扩展字段详情", description = "根据字段ID查询单个扩展字段的完整配置信息")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "404", description = "扩展字段不存在")
    })
    @GetMapping("/{id}")
    public R<ExtendFieldResponse> getById(
            @Parameter(description = "扩展字段ID", required = true, example = "1") @PathVariable Long id) {
        return R.ok(facade.get(id));
    }

    @Operation(summary = "新增扩展字段", description = "创建新的扩展字段配置，需填写业务模块、字段名称、显示标签、字段类型等")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "创建成功"),
            @ApiResponse(responseCode = "400", description = "字段名称重复或必填字段为空")
    })
    @PostMapping
    public R<Void> save(
            @Parameter(description = "扩展字段配置请求") @Valid @RequestBody ExtendFieldRequest request) {
        facade.create(request);
        return R.ok();
    }

    @Operation(summary = "更新扩展字段", description = "根据字段ID修改扩展字段的配置信息，如显示标签、类型、是否必填等")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "更新成功"),
            @ApiResponse(responseCode = "404", description = "扩展字段不存在")
    })
    @PutMapping("/{id}")
    public R<Void> update(
            @Parameter(description = "扩展字段ID", required = true, example = "1") @PathVariable Long id,
            @Parameter(description = "扩展字段配置请求") @Valid @RequestBody ExtendFieldRequest request) {
        facade.update(id, request);
        return R.ok();
    }

    @Operation(summary = "删除扩展字段", description = "根据字段ID删除指定扩展字段配置，删除后不可恢复")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "删除成功"),
            @ApiResponse(responseCode = "404", description = "扩展字段不存在")
    })
    @DeleteMapping("/{id}")
    public R<Void> delete(
            @Parameter(description = "扩展字段ID", required = true, example = "1") @PathVariable Long id) {
        facade.delete(id);
        return R.ok();
    }
}
