package com.example.matching.application.common;

import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.service.employee.EmpEmployeeService;
import com.example.matching.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * “仅本人”数据范围的统一判定。
 *
 * <p>本系统的业务角色分两类：一类持有管理端权限（HR、岗位体系、AI 配置、权限管理员），
 * 另一类只持有 SELF 类权限（员工本人，数据范围仅本人）。凡是同一接口同时服务两类角色，
 * 都要用这里的方法把员工侧收口到本人，避免通过参数越权读取他人数据。</p>
 *
 * <p>判定沿用“命中任意管理端权限即视为管理端”的策略：管理端角色同样持有
 * ASSESSMENT:SELF 等权限码，不能只看是否含 SELF 码。</p>
 */
@Service
@RequiredArgsConstructor
public class SelfScopeSupport {

    /** 只具备这些权限时按“仅本人”处理 */
    private static final String[] SELF_AUTHORITIES = {
            "ASSESSMENT:SELF", "NOTIFICATION:SELF", "LEARNING:SELF", "MATCHING:SELF"
    };

    /** 命中任意一项即视为管理端调用方 */
    private static final String[] MANAGEMENT_AUTHORITIES = {
            "EMPLOYEE:READ", "ASSESSMENT:MANAGE", "MATCHING:READ", "MATCHING:EXECUTE",
            "MATCHING:APPROVE", "MATCHING:CONFIG", "POST:READ", "POST:MANAGE",
            "POST:EVOLUTION", "AI:CONFIG", "ASSESSMENT:CONFIG", "USER:MANAGE",
            "ROLE:MANAGE", "AUDIT:READ"
    };

    private final EmpEmployeeService empEmployeeService;

    /** 调用方是否只具备“仅本人”类权限 */
    public boolean isSelfOnlyCaller() {
        if (!SecurityUtils.hasAnyAuthority(SELF_AUTHORITIES)) {
            return false;
        }
        return !SecurityUtils.hasAnyAuthority(MANAGEMENT_AUTHORITIES);
    }

    /** 当前登录账号关联的人员档案；未绑定或未登录时返回 null */
    public EmpEmployee currentEmployee() {
        Long userId = SecurityUtils.getCurrentUserId();
        return userId == null ? null : empEmployeeService.getByUserId(userId);
    }

    /** 当前登录账号关联的人员档案 ID；未绑定时返回 null */
    public Long currentEmpId() {
        EmpEmployee employee = currentEmployee();
        return employee == null ? null : employee.getId();
    }

    /**
     * 校验“人员维度”参数的归属：员工本人只能传自己的 empId，管理端不受限。
     *
     * @param empId 调用方传入的人员档案 ID
     * @throws BusinessException 员工传入了他人 empId（含未绑定档案却传了任意 empId）
     */
    public void assertEmployeeAccessible(Long empId) {
        if (!isSelfOnlyCaller()) {
            return;
        }
        Long selfEmpId = currentEmpId();
        if (selfEmpId == null || !selfEmpId.equals(empId)) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN.getCode(), "无权访问他人数据");
        }
    }

    /**
     * 校验“资源归属人员”的归属：员工本人只能访问归属自己的资源，管理端不受限。
     *
     * <p>用于以 workflowId / recordId 等代理主键为入参的接口，避免枚举 ID 越权。</p>
     *
     * @param ownerEmpId 该资源归属的人员档案 ID
     * @param resourceName 资源中文名，用于错误提示
     */
    public void assertResourceOwnerAccessible(Long ownerEmpId, String resourceName) {
        if (!isSelfOnlyCaller()) {
            return;
        }
        Long selfEmpId = currentEmpId();
        if (selfEmpId == null || !selfEmpId.equals(ownerEmpId)) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN.getCode(),
                    "无权访问该" + (resourceName == null ? "资源" : resourceName));
        }
    }

    /**
     * 校验“仅管理端可用”的动作：员工本人（SELF 类权限）不得执行人工复核、等级确认、
     * 策略重算等管理动作，避免自评自批。
     *
     * @param actionName 动作中文名
     */
    public void assertManagementOnly(String actionName) {
        if (isSelfOnlyCaller()) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN.getCode(),
                    "无权执行该操作：" + (actionName == null ? "仅管理端可操作" : actionName));
        }
    }
}
